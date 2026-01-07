package com.zimbabeats.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.PlaylistRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val videos: List<Video> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        loadPlaylistDetails()
    }

    private fun loadPlaylistDetails() {
        viewModelScope.launch {
            combine(
                playlistRepository.getPlaylistById(playlistId),
                playlistRepository.getVideosInPlaylist(playlistId),
                playlistRepository.getTracksInPlaylist(playlistId)
            ) { playlist, videos, tracks ->
                PlaylistDetailUiState(
                    playlist = playlist,
                    videos = videos,
                    tracks = tracks,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun removeVideo(videoId: String) {
        viewModelScope.launch {
            when (val result = playlistRepository.removeVideoFromPlaylist(playlistId, videoId)) {
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            when (val result = playlistRepository.removeTrackFromPlaylist(playlistId, trackId)) {
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun clearPlaylist() {
        viewModelScope.launch {
            playlistRepository.clearPlaylist(playlistId)
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            when (val result = playlistRepository.deletePlaylist(playlistId)) {
                is Resource.Success -> onDeleted()
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> {}
            }
        }
    }
}
