package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.RemoteConfigManager
import com.zimbabeats.core.domain.model.music.MusicPlaylist
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MusicPlaylistDetailUiState(
    val playlist: MusicPlaylist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showEditDialog: Boolean = false,
    val showDeleteConfirmation: Boolean = false
)

class MusicPlaylistDetailViewModel(
    private val playlistId: Long,
    private val musicRepository: MusicRepository,
    private val cloudPairingClient: CloudPairingClient
) : ViewModel() {

    companion object {
        private const val TAG = "MusicPlaylistDetailVM"
    }

    // Cloud-based content filter (Firebase) - for family-specific rules
    private val contentFilter get() = cloudPairingClient.contentFilter

    // Global content filter (RemoteConfig) - ALWAYS applies regardless of family linking
    private val remoteConfigManager = RemoteConfigManager()

    private val _uiState = MutableStateFlow(MusicPlaylistDetailUiState())
    val uiState: StateFlow<MusicPlaylistDetailUiState> = _uiState.asStateFlow()

    // Store unfiltered tracks for re-filtering when settings change
    private var unfilteredTracks: List<Track> = emptyList()

    init {
        loadPlaylist()
        observeTracks()
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
                    _uiState.value = _uiState.value.copy(tracks = filteredTracks)
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

            when (val result = musicRepository.getPlaylist(playlistId)) {
                is Resource.Success -> {
                    Log.d(TAG, "Loaded playlist: ${result.data.name}")
                    _uiState.value = _uiState.value.copy(
                        playlist = result.data,
                        isLoading = false
                    )
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to load playlist: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }

    private fun observeTracks() {
        viewModelScope.launch {
            musicRepository.getPlaylistTracks(playlistId).collect { tracks ->
                unfilteredTracks = tracks // Store for re-filtering when settings change
                val filteredTracks = filterTracks(tracks)
                Log.d(TAG, "Loaded ${tracks.size} tracks, ${filteredTracks.size} after filtering")
                _uiState.value = _uiState.value.copy(tracks = filteredTracks)
            }
        }
    }

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            when (val result = musicRepository.removeTrackFromPlaylist(playlistId, trackId)) {
                is Resource.Success -> {
                    Log.d(TAG, "Removed track $trackId from playlist $playlistId")
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to remove track: ${result.message}")
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun showEditDialog() {
        _uiState.value = _uiState.value.copy(showEditDialog = true)
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(showEditDialog = false)
    }

    fun updatePlaylist(name: String, description: String?) {
        viewModelScope.launch {
            val currentPlaylist = _uiState.value.playlist ?: return@launch
            val updatedPlaylist = currentPlaylist.copy(
                name = name,
                description = description
            )

            when (val result = musicRepository.updateMusicPlaylist(updatedPlaylist)) {
                is Resource.Success -> {
                    Log.d(TAG, "Updated playlist $playlistId")
                    _uiState.value = _uiState.value.copy(
                        playlist = updatedPlaylist,
                        showEditDialog = false
                    )
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to update playlist: ${result.message}")
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun showDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = true)
    }

    fun hideDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = false)
    }

    suspend fun deletePlaylist(): Boolean {
        return when (val result = musicRepository.deleteMusicPlaylist(playlistId)) {
            is Resource.Success -> {
                Log.d(TAG, "Deleted playlist $playlistId")
                true
            }
            is Resource.Error -> {
                Log.e(TAG, "Failed to delete playlist: ${result.message}")
                _uiState.value = _uiState.value.copy(error = result.message)
                false
            }
            else -> false
        }
    }

    fun refresh() {
        loadPlaylist()
    }
}
