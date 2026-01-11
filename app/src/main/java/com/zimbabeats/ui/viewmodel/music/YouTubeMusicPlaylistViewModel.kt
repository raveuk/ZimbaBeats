package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.RemoteConfigManager
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class YouTubeMusicPlaylistUiState(
    val isLoading: Boolean = true,
    val playlistId: String = "",
    val title: String = "",
    val author: String? = null,
    val thumbnailUrl: String = "",
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList(),
    val error: String? = null
)

class YouTubeMusicPlaylistViewModel(
    private val playlistId: String,
    private val musicRepository: MusicRepository,
    private val cloudPairingClient: CloudPairingClient
) : ViewModel() {

    companion object {
        private const val TAG = "YTMusicPlaylistVM"
    }

    // Cloud-based content filter (Firebase) - for family-specific rules
    private val contentFilter get() = cloudPairingClient.contentFilter

    // Global content filter (RemoteConfig) - ALWAYS applies regardless of family linking
    private val remoteConfigManager = RemoteConfigManager()

    private val _uiState = MutableStateFlow(YouTubeMusicPlaylistUiState(playlistId = playlistId))
    val uiState: StateFlow<YouTubeMusicPlaylistUiState> = _uiState.asStateFlow()

    // Store unfiltered tracks for re-filtering when settings change
    private var unfilteredTracks: List<Track> = emptyList()

    init {
        loadPlaylist()
        observeFilterSettings()
    }

    /**
     * Observe filter settings changes from Firebase (age rating updates)
     * Re-filters tracks when parent changes settings
     */
    private fun observeFilterSettings() {
        viewModelScope.launch {
            contentFilter?.filterSettings?.collect { settings ->
                Log.d(TAG, "Filter settings changed - re-filtering ${unfilteredTracks.size} tracks")
                if (unfilteredTracks.isNotEmpty()) {
                    val filteredTracks = filterTracks(unfilteredTracks)
                    Log.d(TAG, "After re-filter: ${filteredTracks.size} tracks visible")
                    _uiState.value = _uiState.value.copy(
                        tracks = filteredTracks,
                        trackCount = filteredTracks.size
                    )
                }
            }
        }
    }

    /**
     * Filter tracks using both Global blocks and Cloud Content Filter.
     * Global blocks ALWAYS apply regardless of family linking.
     */
    private fun filterTracks(tracks: List<Track>): List<Track> {
        return tracks.filter { track ->
            // ALWAYS check global blocks first (regardless of family linking)
            val textToCheck = "${track.title} ${track.artistName} ${track.albumName ?: ""}"
            val globalKeywordBlock = remoteConfigManager.isGloballyBlocked(textToCheck)
            if (globalKeywordBlock.isBlocked) {
                Log.d(TAG, "Track '${track.title}' blocked by global keyword filter")
                return@filter false
            }

            val globalArtistBlock = remoteConfigManager.isArtistGloballyBlocked(
                track.artistId ?: "",
                track.artistName
            )
            if (globalArtistBlock.isBlocked) {
                Log.d(TAG, "Track '${track.title}' by '${track.artistName}' blocked by global artist filter")
                return@filter false
            }

            // If linked to family, also apply family-specific rules
            val filter = contentFilter
            if (filter != null) {
                // Wait for filter settings to load before blocking content
                if (!filter.hasLoadedSettings()) {
                    Log.w(TAG, "Filter settings not yet loaded - allowing track: ${track.title}")
                    return@filter true
                }

                val blockResult = filter.shouldBlockMusicContent(
                    trackId = track.id,
                    title = track.title,
                    artistId = track.artistId ?: "",
                    artistName = track.artistName,
                    albumName = track.albumName,
                    genre = null,
                    durationSeconds = track.duration / 1000L,
                    isExplicit = track.isExplicit
                )
                if (blockResult.isBlocked) {
                    Log.d(TAG, "Track '${track.title}' blocked by family filter: ${blockResult.reason}")
                    return@filter false
                }
            }

            true // Not blocked
        }
    }

    fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Log.d(TAG, "Loading playlist: $playlistId")

            when (val result = musicRepository.getYouTubeMusicPlaylist(playlistId)) {
                is Resource.Success -> {
                    val playlist = result.data
                    unfilteredTracks = playlist.tracks // Store for re-filtering when settings change
                    val filteredTracks = filterTracks(playlist.tracks)
                    Log.d(TAG, "Loaded playlist: ${playlist.title} with ${playlist.tracks.size} tracks, ${filteredTracks.size} after filtering")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        title = playlist.title,
                        author = playlist.author,
                        thumbnailUrl = playlist.thumbnailUrl,
                        trackCount = filteredTracks.size,
                        tracks = filteredTracks
                    )
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to load playlist: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "Failed to load playlist"
                    )
                }
                else -> {}
            }
        }
    }

    fun refresh() {
        loadPlaylist()
    }
}
