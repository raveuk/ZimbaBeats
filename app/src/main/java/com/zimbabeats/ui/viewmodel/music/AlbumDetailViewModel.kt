package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.RemoteConfigManager
import com.zimbabeats.core.domain.model.music.Album
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumDetailUiState(
    val album: Album? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class AlbumDetailViewModel(
    private val albumId: String,
    private val musicRepository: MusicRepository,
    private val cloudPairingClient: CloudPairingClient
) : ViewModel() {

    companion object {
        private const val TAG = "AlbumDetailViewModel"
    }

    // Cloud-based MUSIC filter (Firebase) - SEPARATE whitelist-only filter for music
    private val musicFilter get() = cloudPairingClient.musicFilter

    // Global content filter (RemoteConfig) - ALWAYS applies regardless of family linking
    private val remoteConfigManager = RemoteConfigManager()

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    // Store unfiltered tracks for re-filtering when settings change
    private var unfilteredTracks: List<Track> = emptyList()
    private var currentAlbum: Album? = null

    init {
        loadAlbum()
        observeFilterSettings()
    }

    /**
     * Observe music filter settings changes from Firebase (whitelist updates)
     * Re-filters album tracks when parent changes settings
     */
    private fun observeFilterSettings() {
        viewModelScope.launch {
            musicFilter.musicSettings.collect { settings ->
                Log.d(TAG, "Music filter settings changed - re-filtering album tracks")
                if (unfilteredTracks.isNotEmpty() && currentAlbum != null) {
                    val filteredTracks = filterTracks(unfilteredTracks)
                    val filteredAlbum = currentAlbum!!.copy(tracks = filteredTracks)
                    Log.d(TAG, "After re-filter: ${filteredTracks.size} tracks visible")
                    _uiState.value = _uiState.value.copy(album = filteredAlbum)
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

            // Apply MUSIC whitelist filter
            val filter = musicFilter
            // SECURITY: Block content until filter settings are loaded
            if (!filter.hasLoadedSettings()) {
                Log.w(TAG, "Music filter settings not yet loaded - BLOCKING track until loaded: ${track.title}")
                return@filter false
            }

            val blockResult = filter.shouldBlockMusic(
                trackId = track.id,
                title = track.title,
                artistName = track.artistName,
                albumName = track.albumName,
                durationSeconds = track.duration / 1000L,
                isExplicit = track.isExplicit
            )
            if (blockResult.isBlocked) {
                Log.d(TAG, "Track '${track.title}' blocked by music filter: ${blockResult.reason}")
                return@filter false
            }

            true // Not blocked
        }
    }

    fun loadAlbum() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = musicRepository.getAlbum(albumId)) {
                is Resource.Success -> {
                    val album = result.data
                    val originalCount = album.tracks.size

                    // Store unfiltered data for re-filtering when settings change
                    currentAlbum = album
                    unfilteredTracks = album.tracks

                    // Filter tracks based on parental controls
                    val filteredTracks = filterTracks(album.tracks)
                    val filteredAlbum = album.copy(tracks = filteredTracks)

                    Log.d(TAG, "Loaded album: ${album.title} with ${originalCount} tracks (${filteredTracks.size} after filtering)")

                    _uiState.value = _uiState.value.copy(
                        album = filteredAlbum,
                        isLoading = false
                    )
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to load album: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }

    fun refresh() {
        loadAlbum()
    }
}
