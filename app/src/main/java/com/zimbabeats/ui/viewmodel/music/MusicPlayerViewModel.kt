package com.zimbabeats.ui.viewmodel.music

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.PlaylistColor
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.repository.PlaylistRepository
import com.zimbabeats.core.domain.util.Resource
import com.zimbabeats.media.music.MusicPlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MusicPlayerUiState(
    val currentTrack: Track? = null,
    val isLoading: Boolean = true,
    val streamUrl: String? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val playlists: List<Playlist> = emptyList(),
    val showPlaylistPicker: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val sleepTimerEnabled: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val showSleepTimerPicker: Boolean = false,
    val lyrics: com.zimbabeats.core.domain.model.music.Lyrics? = null,
    val isLoadingLyrics: Boolean = false,
    val showLyrics: Boolean = false
)

enum class RepeatMode {
    OFF, ONE, ALL
}

class MusicPlayerViewModel(
    application: Application,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val musicPlaybackManager: MusicPlaybackManager
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MusicPlayerViewModel"
    }

    private var sleepTimerJob: Job? = null

    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

    // Expose player state from the shared playback manager
    val playerState = musicPlaybackManager.playerState

    private var listenStartTime: Long = 0

    init {
        // Load unified playlists for playlist picker
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
        }

        // Sync with playback manager state
        viewModelScope.launch {
            musicPlaybackManager.playbackState.collect { playbackState ->
                _uiState.value = _uiState.value.copy(
                    currentTrack = playbackState.currentTrack ?: _uiState.value.currentTrack,
                    isLoading = playbackState.isLoading,
                    queue = if (playbackState.queue.isNotEmpty()) playbackState.queue else _uiState.value.queue,
                    currentIndex = playbackState.currentIndex,
                    sleepTimerEnabled = playbackState.sleepTimerEnabled,
                    sleepTimerRemainingMs = playbackState.sleepTimerRemainingMs
                )
            }
        }
    }

    fun loadTrack(trackId: String) {
        Log.d(TAG, "Loading track: $trackId")

        // Check if this track is already playing in the playback manager
        val currentPlaybackState = musicPlaybackManager.playbackState.value
        val currentlyPlayingTrack = currentPlaybackState.currentTrack

        if (currentlyPlayingTrack != null && currentlyPlayingTrack.id == trackId) {
            // Track is already loaded/playing, just sync UI state
            Log.d(TAG, "Track already playing, syncing UI state")
            _uiState.value = _uiState.value.copy(
                currentTrack = currentlyPlayingTrack,
                isLoading = false,
                error = null,
                queue = currentPlaybackState.queue,
                currentIndex = currentPlaybackState.currentIndex
            )
            // Clear lyrics when switching tracks or returning to player
            if (_uiState.value.showLyrics && _uiState.value.lyrics == null) {
                loadLyrics()
            }
        } else {
            // Need to load new track
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, lyrics = null, showLyrics = false)
            musicPlaybackManager.loadTrack(trackId)
        }

        // Observe favorite status
        viewModelScope.launch {
            musicRepository.isFavorite(trackId).collect { isFavorite ->
                _uiState.value = _uiState.value.copy(isFavorite = isFavorite)
            }
        }

        listenStartTime = System.currentTimeMillis()
    }

    fun playTrack(track: Track) {
        val currentQueue = _uiState.value.queue.toMutableList()
        val existingIndex = currentQueue.indexOfFirst { it.id == track.id }

        if (existingIndex >= 0) {
            skipToIndex(existingIndex)
        } else {
            currentQueue.add(0, track)
            _uiState.value = _uiState.value.copy(
                queue = currentQueue,
                currentIndex = 0
            )
            loadTrack(track.id)
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            queue = tracks,
            currentIndex = startIndex
        )
        loadTrack(tracks[startIndex].id)
    }

    fun play() = musicPlaybackManager.play()
    fun pause() = musicPlaybackManager.pause()
    fun seekTo(positionMs: Long) = musicPlaybackManager.seekTo(positionMs)
    fun getPlayer() = musicPlaybackManager.getPlayer()

    fun skipToNext() {
        val queue = _uiState.value.queue
        var nextIndex = _uiState.value.currentIndex + 1

        if (_uiState.value.shuffleEnabled) {
            nextIndex = queue.indices.random()
        }

        when (_uiState.value.repeatMode) {
            RepeatMode.ONE -> {
                musicPlaybackManager.seekTo(0)
                musicPlaybackManager.play()
            }
            RepeatMode.ALL -> {
                if (nextIndex >= queue.size) nextIndex = 0
                if (queue.isNotEmpty()) {
                    recordListen()
                    _uiState.value = _uiState.value.copy(currentIndex = nextIndex)
                    loadTrack(queue[nextIndex].id)
                }
            }
            RepeatMode.OFF -> {
                if (nextIndex < queue.size) {
                    recordListen()
                    _uiState.value = _uiState.value.copy(currentIndex = nextIndex)
                    loadTrack(queue[nextIndex].id)
                }
            }
        }
    }

    fun skipToPrevious() {
        val queue = _uiState.value.queue
        var prevIndex = _uiState.value.currentIndex - 1

        val currentPos = musicPlaybackManager.playbackState.value.currentPosition
        if (currentPos > 3000) {
            musicPlaybackManager.seekTo(0)
            return
        }

        if (prevIndex < 0) {
            if (_uiState.value.repeatMode == RepeatMode.ALL) {
                prevIndex = queue.size - 1
            } else {
                musicPlaybackManager.seekTo(0)
                return
            }
        }

        if (queue.isNotEmpty()) {
            recordListen()
            _uiState.value = _uiState.value.copy(currentIndex = prevIndex)
            loadTrack(queue[prevIndex].id)
        }
    }

    fun skipToIndex(index: Int) {
        val queue = _uiState.value.queue
        if (index in queue.indices) {
            recordListen()
            _uiState.value = _uiState.value.copy(currentIndex = index)
            // Use skipToQueueIndex to load and play the track
            musicPlaybackManager.skipToQueueIndex(index)
        }
    }

    fun toggleRepeatMode() {
        val newMode = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _uiState.value = _uiState.value.copy(repeatMode = newMode)
    }

    fun toggleShuffle() {
        _uiState.value = _uiState.value.copy(
            shuffleEnabled = !_uiState.value.shuffleEnabled
        )
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val track = _uiState.value.currentTrack ?: return@launch
            val result = musicRepository.toggleFavorite(track)

            if (result is Resource.Error) {
                Log.e(TAG, "Failed to toggle favorite: ${result.message}")
            } else {
                Log.d(TAG, "Favorite toggled for track: ${track.id}")
            }
        }
    }

    fun showPlaylistPicker() {
        _uiState.value = _uiState.value.copy(showPlaylistPicker = true)
    }

    fun hidePlaylistPicker() {
        _uiState.value = _uiState.value.copy(showPlaylistPicker = false)
    }

    // Sleep Timer functions
    fun showSleepTimerPicker() {
        _uiState.value = _uiState.value.copy(showSleepTimerPicker = true)
    }

    fun hideSleepTimerPicker() {
        _uiState.value = _uiState.value.copy(showSleepTimerPicker = false)
    }

    fun setSleepTimer(minutes: Int) {
        musicPlaybackManager.setSleepTimer(minutes)
        _uiState.value = _uiState.value.copy(showSleepTimerPicker = false)
        Log.d(TAG, "Sleep timer set for $minutes minutes")
    }

    fun cancelSleepTimer() {
        musicPlaybackManager.cancelSleepTimer()
        Log.d(TAG, "Sleep timer cancelled")
    }

    // Lyrics functions
    fun toggleLyricsView() {
        val newShowLyrics = !_uiState.value.showLyrics
        _uiState.value = _uiState.value.copy(showLyrics = newShowLyrics)

        // Load lyrics when first shown
        if (newShowLyrics && _uiState.value.lyrics == null && !_uiState.value.isLoadingLyrics) {
            loadLyrics()
        }
    }

    fun loadLyrics() {
        val trackId = _uiState.value.currentTrack?.id ?: return

        _uiState.value = _uiState.value.copy(isLoadingLyrics = true)

        viewModelScope.launch {
            when (val result = musicRepository.getLyrics(trackId)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        lyrics = result.data,
                        isLoadingLyrics = false
                    )
                    Log.d(TAG, "Lyrics loaded for track: $trackId, available: ${result.data != null}")
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to load lyrics: ${result.message}")
                    _uiState.value = _uiState.value.copy(isLoadingLyrics = false)
                }
                else -> {}
            }
        }
    }

    fun addToPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val track = _uiState.value.currentTrack ?: return@launch
            val result = playlistRepository.addTrackToPlaylist(playlistId, track)

            if (result is Resource.Error) {
                Log.e(TAG, "Failed to add to playlist: ${result.message}")
            } else {
                Log.d(TAG, "Track ${track.id} added to unified playlist $playlistId")
            }
            hidePlaylistPicker()
        }
    }

    fun createPlaylistAndAddTrack(name: String) {
        viewModelScope.launch {
            val track = _uiState.value.currentTrack ?: return@launch

            when (val createResult = playlistRepository.createPlaylist(name, null, PlaylistColor.PURPLE)) {
                is Resource.Success -> {
                    val playlistId = createResult.data
                    Log.d(TAG, "Created unified playlist $playlistId: $name")

                    val addResult = playlistRepository.addTrackToPlaylist(playlistId, track)
                    if (addResult is Resource.Success) {
                        Log.d(TAG, "Track ${track.id} added to new playlist $playlistId")
                    }
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to create playlist: ${createResult.message}")
                }
                else -> {}
            }
            hidePlaylistPicker()
        }
    }

    private fun recordListen() {
        val track = _uiState.value.currentTrack ?: return
        val listenDuration = System.currentTimeMillis() - listenStartTime

        viewModelScope.launch {
            musicRepository.recordListen(track.id, listenDuration)
        }
    }

    fun getShareUrl(): String {
        val track = _uiState.value.currentTrack ?: return ""
        return "https://music.youtube.com/watch?v=${track.id}"
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel being cleared - recording listen (playback continues in background)")

        // Record listen but DON'T stop playback - let MusicPlaybackManager continue
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val track = _uiState.value.currentTrack

        cleanupScope.launch {
            if (track != null) {
                val listenDuration = System.currentTimeMillis() - listenStartTime
                try {
                    musicRepository.recordListen(track.id, listenDuration)
                    Log.d(TAG, "Listen recorded for: ${track.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record listen", e)
                }
            }
        }

        // Note: We don't release the player here - MusicPlaybackManager persists
    }
}
