package com.zimbabeats.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.repository.DownloadRepository
import com.zimbabeats.core.domain.repository.PlaylistRepository
import com.zimbabeats.core.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class LibraryTab {
    FAVORITES, PLAYLISTS, DOWNLOADS, HISTORY
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.FAVORITES,
    val favorites: List<Video> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val downloads: List<Video> = emptyList(),
    val watchHistory: List<Video> = emptyList(),
    val isLoading: Boolean = true
)

class LibraryViewModel(
    private val videoRepository: VideoRepository,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        viewModelScope.launch {
            combine(
                videoRepository.getFavoriteVideos(),
                playlistRepository.getAllPlaylists(),
                downloadRepository.getAllDownloadedVideos(),
                videoRepository.getWatchHistory()
            ) { favorites, playlists, downloads, history ->
                LibraryUiState(
                    favorites = favorites,
                    playlists = playlists,
                    downloads = downloads,
                    watchHistory = history,
                    isLoading = false,
                    selectedTab = _uiState.value.selectedTab
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun togglePlaylistFavorite(playlistId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            playlistRepository.updateFavoriteStatus(playlistId, !isFavorite)
        }
    }

    fun removeFromHistory(videoId: String) {
        viewModelScope.launch {
            videoRepository.removeFromWatchHistory(videoId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            videoRepository.clearWatchHistory()
        }
    }
}
