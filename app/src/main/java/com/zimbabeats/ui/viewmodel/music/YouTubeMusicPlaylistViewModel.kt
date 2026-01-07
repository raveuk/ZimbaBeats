package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val musicRepository: MusicRepository
) : ViewModel() {

    companion object {
        private const val TAG = "YTMusicPlaylistVM"
    }

    private val _uiState = MutableStateFlow(YouTubeMusicPlaylistUiState(playlistId = playlistId))
    val uiState: StateFlow<YouTubeMusicPlaylistUiState> = _uiState.asStateFlow()

    init {
        loadPlaylist()
    }

    fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Log.d(TAG, "Loading playlist: $playlistId")

            when (val result = musicRepository.getYouTubeMusicPlaylist(playlistId)) {
                is Resource.Success -> {
                    val playlist = result.data
                    Log.d(TAG, "Loaded playlist: ${playlist.title} with ${playlist.tracks.size} tracks")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        title = playlist.title,
                        author = playlist.author,
                        thumbnailUrl = playlist.thumbnailUrl,
                        trackCount = playlist.trackCount,
                        tracks = playlist.tracks
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
