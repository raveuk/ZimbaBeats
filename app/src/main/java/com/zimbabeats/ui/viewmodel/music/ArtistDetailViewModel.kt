package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val musicRepository: MusicRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ArtistDetailViewModel"
    }

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        loadArtist()
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
                            Log.d(TAG, "Loaded ${tracksResult.data.size} tracks")
                            _uiState.value = _uiState.value.copy(
                                tracks = tracksResult.data,
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
