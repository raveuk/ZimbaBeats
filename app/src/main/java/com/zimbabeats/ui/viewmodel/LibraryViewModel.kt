package com.zimbabeats.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.DownloadRepository
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.repository.PlaylistRepository
import com.zimbabeats.core.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class LibraryTab {
    YOUR_LIBRARY, PLAYLISTS, FAVORITE_PLAYLISTS, DOWNLOADS
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.YOUR_LIBRARY,
    val favorites: List<Video> = emptyList(),
    val musicFavorites: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val favoritePlaylists: List<Playlist> = emptyList(),
    val downloads: List<Video> = emptyList(),
    val watchHistory: List<Video> = emptyList(),
    val mostPlayedTracks: List<Track> = emptyList(),
    val isLoading: Boolean = true
)

class LibraryViewModel(
    private val videoRepository: VideoRepository,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
    }

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
        loadMusicFavorites()
        loadFavoritePlaylists()
        loadMostPlayedTracks()
    }

    private fun loadAllData() {
        viewModelScope.launch {
            combine(
                videoRepository.getFavoriteVideos(),
                playlistRepository.getAllPlaylists(),
                downloadRepository.getAllDownloadedVideos(),
                videoRepository.getWatchHistory()
            ) { favorites, playlists, downloads, history ->
                _uiState.value.copy(
                    favorites = favorites,
                    playlists = playlists,
                    downloads = downloads,
                    watchHistory = history,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun loadMusicFavorites() {
        viewModelScope.launch {
            musicRepository.getFavoriteTracks().collect { tracks ->
                Log.d(TAG, "Loaded ${tracks.size} music favorites")
                _uiState.value = _uiState.value.copy(musicFavorites = tracks)
            }
        }
    }

    private fun loadFavoritePlaylists() {
        viewModelScope.launch {
            playlistRepository.getFavoritePlaylists().collect { playlists ->
                Log.d(TAG, "Loaded ${playlists.size} favorite playlists")
                _uiState.value = _uiState.value.copy(favoritePlaylists = playlists)
            }
        }
    }

    private fun loadMostPlayedTracks() {
        viewModelScope.launch {
            musicRepository.getMostPlayed(50).collect { tracks ->
                Log.d(TAG, "Loaded ${tracks.size} most played tracks")
                _uiState.value = _uiState.value.copy(mostPlayedTracks = tracks)
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun togglePlaylistFavorite(playlistId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "togglePlaylistFavorite: playlistId=$playlistId, currentIsFavorite=$isFavorite, newIsFavorite=${!isFavorite}")
            val result = playlistRepository.updateFavoriteStatus(playlistId, !isFavorite)
            Log.d(TAG, "togglePlaylistFavorite result: $result")
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
