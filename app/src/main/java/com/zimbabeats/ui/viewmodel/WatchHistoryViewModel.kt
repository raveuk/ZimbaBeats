package com.zimbabeats.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.repository.VideoRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WatchHistoryUiState(
    val history: List<Video> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class WatchHistoryViewModel(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchHistoryUiState())
    val uiState: StateFlow<WatchHistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                videoRepository.getWatchHistory(limit = 100).collect { history ->
                    _uiState.value = _uiState.value.copy(
                        history = history,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load watch history: ${e.message}"
                )
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            when (val result = videoRepository.clearWatchHistory()) {
                is Resource.Success -> {
                    // History will automatically update via Flow
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to clear history: ${result.message}"
                    )
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun removeVideoFromHistory(videoId: String) {
        viewModelScope.launch {
            when (val result = videoRepository.removeFromWatchHistory(videoId)) {
                is Resource.Success -> {
                    // History will automatically update via Flow
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to remove from history: ${result.message}"
                    )
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun refresh() {
        loadHistory()
    }
}
