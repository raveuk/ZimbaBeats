package com.zimbabeats.ui.viewmodel.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.core.domain.model.music.Album
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
    private val musicRepository: MusicRepository
) : ViewModel() {

    companion object {
        private const val TAG = "AlbumDetailViewModel"
    }

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadAlbum()
    }

    fun loadAlbum() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = musicRepository.getAlbum(albumId)) {
                is Resource.Success -> {
                    Log.d(TAG, "Loaded album: ${result.data.title} with ${result.data.tracks.size} tracks")
                    _uiState.value = _uiState.value.copy(
                        album = result.data,
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
