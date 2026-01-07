package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val musicRepository: MusicRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MusicPlaylistDetailVM"
    }

    private val _uiState = MutableStateFlow(MusicPlaylistDetailUiState())
    val uiState: StateFlow<MusicPlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        loadPlaylist()
        observeTracks()
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
                _uiState.value = _uiState.value.copy(tracks = tracks)
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
