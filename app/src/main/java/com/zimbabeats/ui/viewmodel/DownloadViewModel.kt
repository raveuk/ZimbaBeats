package com.zimbabeats.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.core.domain.model.DownloadQueueItem
import com.zimbabeats.core.domain.model.VideoQuality
import com.zimbabeats.core.domain.repository.DownloadRepository
import com.zimbabeats.core.domain.util.Resource
import com.zimbabeats.data.AppPreferences
import com.zimbabeats.download.DownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadUiState(
    val downloads: List<DownloadQueueItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class DownloadViewModel(
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        loadDownloads()
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            downloadRepository.getAllQueueItems().collect { downloads ->
                _uiState.value = _uiState.value.copy(
                    downloads = downloads,
                    isLoading = false
                )
            }
        }
    }

    fun startDownload(videoId: String, quality: String = "medium") {
        viewModelScope.launch {
            val videoQuality = when (quality) {
                "high" -> VideoQuality.HD_720P
                "medium" -> VideoQuality.SD_480P
                else -> VideoQuality.SD_360P
            }
            when (val result = downloadRepository.queueDownload(videoId, videoQuality)) {
                is Resource.Success -> {
                    val requireWifiOnly = !appPreferences.isMobileDataDownloadAllowed()
                    downloadManager.downloadVideo(videoId, quality, requireWifiOnly)
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun pauseDownload(videoId: String) {
        viewModelScope.launch {
            downloadManager.pauseDownload(videoId)
        }
    }

    fun resumeDownload(videoId: String, quality: String = "medium") {
        val requireWifiOnly = !appPreferences.isMobileDataDownloadAllowed()
        downloadManager.resumeDownload(videoId, quality, requireWifiOnly)
    }

    fun cancelDownload(videoId: String) {
        viewModelScope.launch {
            downloadManager.cancelDownload(videoId)
            downloadRepository.cancelDownload(videoId)
        }
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            when (val result = downloadRepository.deleteDownload(videoId)) {
                is Resource.Success -> {
                    loadDownloads()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
