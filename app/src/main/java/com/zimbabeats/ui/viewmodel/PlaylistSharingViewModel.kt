package com.zimbabeats.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudContentFilter
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.PlaylistSharingClient
import com.zimbabeats.cloud.model.*
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.PlaylistColor
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.PlaylistRepository
import com.zimbabeats.core.domain.repository.VideoRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for playlist sharing operations.
 */
class PlaylistSharingViewModel(
    private val sharingClient: PlaylistSharingClient,
    private val playlistRepository: PlaylistRepository,
    private val videoRepository: VideoRepository,
    private val cloudPairingClient: CloudPairingClient
) : ViewModel() {

    companion object {
        private const val TAG = "PlaylistSharingVM"
    }

    // Share state
    private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    val shareState: StateFlow<ShareState> = _shareState.asStateFlow()

    // Import state
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    // Whether sharing is enabled (based on pairing status)
    val isSharingEnabled: StateFlow<Boolean> = cloudPairingClient.pairingStatus
        .map { it is com.zimbabeats.cloud.PairingStatus.Paired }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // My shared playlists
    val mySharedPlaylists: StateFlow<List<SharedPlaylistInfo>> = sharingClient.getMySharedPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Generate a share code for a playlist.
     */
    fun generateShareCode(playlist: Playlist, videos: List<Video>, tracks: List<Track>) {
        viewModelScope.launch {
            _shareState.value = ShareState.Generating

            val result = sharingClient.sharePlaylist(playlist, videos, tracks)

            _shareState.value = when (result) {
                is ShareResult.Success -> {
                    // Update local playlist with share code
                    val updateResult = playlistRepository.updateShareCode(
                        playlistId = playlist.id,
                        shareCode = result.shareCode,
                        sharedAt = System.currentTimeMillis()
                    )

                    // Check if local database update succeeded
                    if (updateResult is Resource.Success) {
                        ShareState.Success(result.shareCode)
                    } else {
                        ShareState.Error("Share code created but failed to save locally")
                    }
                }
                is ShareResult.Error -> ShareState.Error(result.message)
            }
        }
    }

    /**
     * Revoke a share code.
     */
    fun revokeShareCode(playlist: Playlist) {
        val shareCode = playlist.shareCode ?: return

        viewModelScope.launch {
            _shareState.value = ShareState.Loading

            val result = sharingClient.revokeShareCode(shareCode)

            result.onSuccess {
                // Clear local share code
                playlistRepository.updateShareCode(
                    playlistId = playlist.id,
                    shareCode = null,
                    sharedAt = null
                )
                _shareState.value = ShareState.Idle
            }.onFailure { error ->
                _shareState.value = ShareState.Error(error.message ?: "Failed to revoke code")
            }
        }
    }

    /**
     * Validate a share code and show preview.
     */
    fun validateCode(code: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading

            when (val result = sharingClient.validateShareCode(code)) {
                is ValidateResult.Valid -> {
                    _importState.value = ImportState.Preview(result.preview)
                }
                is ValidateResult.Invalid -> {
                    _importState.value = ImportState.Error(result.reason)
                }
                is ValidateResult.Error -> {
                    _importState.value = ImportState.Error(result.message)
                }
            }
        }
    }

    /**
     * Import a shared playlist.
     */
    fun importPlaylist(shareCode: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Importing

            val result = sharingClient.importPlaylist(shareCode)

            result.onSuccess { sharedData ->
                // Get content filter to check items
                val contentFilter = cloudPairingClient.contentFilter

                // Filter videos against parent's content rules
                var filteredVideoCount = 0
                val allowedVideos = sharedData.videos.filter { videoInfo ->
                    val blockResult = contentFilter.shouldBlockContent(
                        videoId = videoInfo.videoId,
                        title = videoInfo.title,
                        channelId = videoInfo.channelId,
                        channelName = videoInfo.channelName
                    )
                    if (blockResult.isBlocked) {
                        filteredVideoCount++
                        Log.d(TAG, "Filtered video: ${videoInfo.title} - ${blockResult.reason}")
                        false
                    } else {
                        true
                    }
                }

                // Filter tracks against parent's content rules
                var filteredTrackCount = 0
                val allowedTracks = sharedData.tracks.filter { trackInfo ->
                    val blockResult = contentFilter.shouldBlockMusicContent(
                        trackId = trackInfo.trackId,
                        title = trackInfo.title,
                        artistId = trackInfo.artistId,
                        artistName = trackInfo.artistName
                    )
                    if (blockResult.isBlocked) {
                        filteredTrackCount++
                        Log.d(TAG, "Filtered track: ${trackInfo.title} - ${blockResult.reason}")
                        false
                    } else {
                        true
                    }
                }

                // Create the imported playlist with unique name
                val color = PlaylistColor.values().find { it.hex == sharedData.color }
                    ?: PlaylistColor.PINK

                // Add "from [friend]" to name to distinguish imported playlists
                val importedName = "${sharedData.playlistName} (from ${sharedData.sharedByChildName})"

                val createResult = playlistRepository.createImportedPlaylist(
                    name = importedName,
                    description = sharedData.description,
                    color = color,
                    importedFrom = sharedData.sharedByChildName
                )

                val playlistId = createResult.getOrNull()
                if (playlistId != null) {
                    // Add videos to playlist
                    for (videoInfo in allowedVideos) {
                        // First ensure video exists in local db
                        videoRepository.saveVideo(
                            Video(
                                id = videoInfo.videoId,
                                title = videoInfo.title,
                                description = null,
                                channelId = videoInfo.channelId,
                                channelName = videoInfo.channelName,
                                thumbnailUrl = videoInfo.thumbnailUrl ?: "",
                                duration = videoInfo.durationSeconds,
                                viewCount = 0,
                                publishedAt = System.currentTimeMillis(),
                                category = null
                            )
                        )
                        playlistRepository.addVideoToPlaylist(playlistId, videoInfo.videoId)
                    }

                    // Add tracks to playlist
                    for (trackInfo in allowedTracks) {
                        val track = Track(
                            id = trackInfo.trackId,
                            title = trackInfo.title,
                            artistId = trackInfo.artistId,
                            artistName = trackInfo.artistName,
                            albumId = "",
                            albumName = trackInfo.albumName ?: "",
                            thumbnailUrl = trackInfo.thumbnailUrl ?: "",
                            duration = trackInfo.durationSeconds * 1000  // Convert seconds to MS
                        )
                        playlistRepository.addTrackToPlaylist(playlistId, track)
                    }

                    val totalFiltered = filteredVideoCount + filteredTrackCount
                    val totalImported = allowedVideos.size + allowedTracks.size

                    _importState.value = ImportState.Success(
                        playlistId = playlistId,
                        itemsImported = totalImported,
                        itemsFiltered = totalFiltered
                    )

                    Log.d(TAG, "Imported playlist: ${sharedData.playlistName} - $totalImported items, $totalFiltered filtered")
                } else {
                    _importState.value = ImportState.Error("Failed to create playlist")
                }
            }.onFailure { error ->
                _importState.value = ImportState.Error(error.message ?: "Import failed")
            }
        }
    }

    /**
     * Reset share state.
     */
    fun resetShareState() {
        _shareState.value = ShareState.Idle
    }

    /**
     * Reset import state.
     */
    fun resetImportState() {
        _importState.value = ImportState.Idle
    }
}

/**
 * Share operation state.
 */
sealed class ShareState {
    data object Idle : ShareState()
    data object Loading : ShareState()
    data object Generating : ShareState()
    data class Success(val shareCode: String) : ShareState()
    data class Error(val message: String) : ShareState()
}

/**
 * Import operation state.
 */
sealed class ImportState {
    data object Idle : ImportState()
    data object Loading : ImportState()
    data class Preview(val preview: SharedPlaylistPreview) : ImportState()
    data object Importing : ImportState()
    data class Success(val playlistId: Long, val itemsImported: Int, val itemsFiltered: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}
