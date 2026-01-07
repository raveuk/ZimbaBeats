package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
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

    // Cloud-based content filter (Firebase)
    private val contentFilter get() = cloudPairingClient.contentFilter

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadAlbum()
    }

    /**
     * Filter tracks using Cloud Content Filter (Firebase-based).
     * When not linked to family, all tracks are allowed (unrestricted mode).
     */
    private fun filterTracks(tracks: List<Track>): List<Track> {
        val filter = contentFilter ?: return tracks // Unrestricted mode if not linked

        return tracks.filter { track ->
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
            !blockResult.isBlocked
        }
    }

    fun loadAlbum() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = musicRepository.getAlbum(albumId)) {
                is Resource.Success -> {
                    val album = result.data
                    val originalCount = album.tracks.size
                    
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
