package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.RemoteConfigManager
import com.zimbabeats.core.domain.model.music.Artist
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ArtistDetailViewModel(
    private val artistId: String,
    private val musicRepository: MusicRepository,
    private val cloudPairingClient: CloudPairingClient
) : ViewModel() {

    companion object {
        private const val TAG = "ArtistDetailViewModel"
    }

    // Cloud-based MUSIC filter (Firebase) - SEPARATE whitelist-only filter for music
    private val musicFilter get() = cloudPairingClient.musicFilter

    // Global content filter (RemoteConfig) - ALWAYS applies regardless of family linking
    private val remoteConfigManager = RemoteConfigManager()

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    // Store unfiltered tracks for re-filtering when settings change
    private var unfilteredTracks: List<Track> = emptyList()

    init {
        loadArtist()
        observeFilterSettings()
    }

    /**
     * Observe music filter settings changes from Firebase (whitelist updates)
     * Re-filters artist tracks when parent changes settings
     */
    private fun observeFilterSettings() {
        viewModelScope.launch {
            musicFilter.musicSettings.collect { settings ->
                Log.d(TAG, "Music filter settings changed - re-filtering artist tracks")
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

    fun loadArtist() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load artist info
            when (val artistResult = musicRepository.getArtist(artistId)) {
                is Resource.Success -> {
                    Log.d(TAG, "Loaded artist: ${artistResult.data.name}")
                    _uiState.value = _uiState.value.copy(artist = artistResult.data)

                    // Load tracks
                    when (val tracksResult = musicRepository.getArtistTracks(artistId)) {
                        is Resource.Success -> {
                            val originalCount = tracksResult.data.size

                            // Store unfiltered tracks for re-filtering when settings change
                            unfilteredTracks = tracksResult.data

                            // Apply filtering
                            val filteredTracks = filterTracks(tracksResult.data)

                            Log.d(TAG, "Loaded ${originalCount} tracks, ${filteredTracks.size} after filtering")
                            _uiState.value = _uiState.value.copy(
                                tracks = filteredTracks,
                                isLoading = false
                            )
                        }
                        is Resource.Error -> {
                            Log.e(TAG, "Failed to load tracks: ${tracksResult.message}")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = tracksResult.message
                            )
                        }
                        else -> {}
                    }
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to load artist: ${artistResult.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = artistResult.message
                    )
                }
                else -> {}
            }
        }
    }

    fun refresh() {
        loadArtist()
    }
}
