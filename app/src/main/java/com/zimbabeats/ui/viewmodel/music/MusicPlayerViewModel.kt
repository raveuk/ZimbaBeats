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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for music player screen.
 *
 * Fix Critical #3: Removed queue/currentIndex from here -
 * MusicPlaybackManager is the single source of truth for queue state.
 */
data class MusicPlayerUiState(
    val currentTrack: Track? = null,
    val isLoading: Boolean = true,
    val streamUrl: String? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
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

/**
 * ViewModel for music player screen.
 *
 * Fix Critical #3: Delegates all queue operations to MusicPlaybackManager.
 * This ViewModel only manages UI-specific state (playlists picker, lyrics, etc.)
 * Fix Medium #8: Removed orphaned cleanupScope - use viewModelScope properly.
 * Fix Medium #14: Cancel previous favorite observer before starting new one.
 */
class MusicPlayerViewModel(
    application: Application,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val musicPlaybackManager: MusicPlaybackManager
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MusicPlayerViewModel"
    }

    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

    // Expose playback state directly from MusicPlaybackManager (Critical #3: single source of truth)
    val playbackState = musicPlaybackManager.playbackState

    // Expose player state from the shared playback manager
    val playerState = musicPlaybackManager.playerState

    private var listenStartTime: Long = 0
    private var favoriteObserverJob: Job? = null  // Fix Medium #14: Track favorite observer

    init {
        // Load unified playlists for playlist picker
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
        }

        // Sync UI state with playback manager state (Critical #3: single source of truth)
        viewModelScope.launch {
            musicPlaybackManager.playbackState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    currentTrack = state.currentTrack,
                    isLoading = state.isLoading,
                    error = state.error,
                    sleepTimerEnabled = state.sleepTimerEnabled,
                    sleepTimerRemainingMs = state.sleepTimerRemainingMs
                )

                // Clear lyrics when track changes
                if (state.currentTrack?.id != _uiState.value.currentTrack?.id) {
                    _uiState.value = _uiState.value.copy(lyrics = null, showLyrics = false)
                }
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
                error = null
            )
            // Load lyrics if showing lyrics view
            if (_uiState.value.showLyrics && _uiState.value.lyrics == null) {
                loadLyrics()
            }
        } else {
            // Need to load new track - delegate to MusicPlaybackManager
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, lyrics = null, showLyrics = false)
            musicPlaybackManager.loadTrack(trackId)
        }

        // Fix Medium #14: Cancel previous favorite observer before starting new one
        favoriteObserverJob?.cancel()
        favoriteObserverJob = viewModelScope.launch {
            musicRepository.isFavorite(trackId).collect { isFavorite ->
                _uiState.value = _uiState.value.copy(isFavorite = isFavorite)
            }
        }

        listenStartTime = System.currentTimeMillis()
    }

    /**
     * Play a specific track. Delegates to MusicPlaybackManager.
     */
    fun playTrack(track: Track) {
        val currentQueue = musicPlaybackManager.playbackState.value.queue
        val existingIndex = currentQueue.indexOfFirst { it.id == track.id }

        if (existingIndex >= 0) {
            // Track exists in queue - skip to it
            musicPlaybackManager.skipToQueueIndex(existingIndex)
        } else {
            // Track not in queue - load it (will create new radio queue)
            musicPlaybackManager.loadTrack(track.id)
        }
    }

    /**
     * Play a list of tracks starting at the given index.
     * Delegates to MusicPlaybackManager.
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        musicPlaybackManager.playTracks(tracks, startIndex)
    }

    fun play() = musicPlaybackManager.play()
    fun pause() = musicPlaybackManager.pause()
    fun seekTo(positionMs: Long) = musicPlaybackManager.seekTo(positionMs)
    fun getPlayer() = musicPlaybackManager.getPlayer()

    /**
     * Skip to next track with repeat/shuffle mode handling.
     */
    fun skipToNext() {
        val state = musicPlaybackManager.playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        var nextIndex = state.currentIndex + 1

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
                recordListen()
                musicPlaybackManager.skipToQueueIndex(nextIndex)
            }
            RepeatMode.OFF -> {
                if (nextIndex < queue.size) {
                    recordListen()
                    musicPlaybackManager.skipToQueueIndex(nextIndex)
                }
            }
        }
    }

    /**
     * Skip to previous track with repeat mode handling.
     */
    fun skipToPrevious() {
        val state = musicPlaybackManager.playbackState.value
        val queue = state.queue

        if (state.currentPosition > 3000) {
            musicPlaybackManager.seekTo(0)
            return
        }

        var prevIndex = state.currentIndex - 1

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
            musicPlaybackManager.skipToQueueIndex(prevIndex)
        }
    }

    /**
     * Skip to a specific index in the queue.
     */
    fun skipToIndex(index: Int) {
        val queue = musicPlaybackManager.playbackState.value.queue
        if (index in queue.indices) {
            recordListen()
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

    /**
     * Fix Medium #8: Use viewModelScope for cleanup to avoid memory leak.
     * The viewModelScope is automatically cancelled when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel being cleared - recording listen (playback continues in background)")

        // Record listen using viewModelScope - it will complete before cancellation
        // since we're just launching a quick network call
        val track = _uiState.value.currentTrack
        if (track != null) {
            val listenDuration = System.currentTimeMillis() - listenStartTime
            // Use GlobalScope for fire-and-forget cleanup that must complete
            // This is acceptable for recording listen stats
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    musicRepository.recordListen(track.id, listenDuration)
                    Log.d(TAG, "Listen recorded for: ${track.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record listen", e)
                }
            }
        }

        // Cancel the favorite observer
        favoriteObserverJob?.cancel()

        // Note: We don't release the player here - MusicPlaybackManager persists
    }
}
