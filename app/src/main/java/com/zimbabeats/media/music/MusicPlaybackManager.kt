package com.zimbabeats.media.music

import android.app.Application
import android.util.Log
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.BlockReason
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.util.Resource
import com.zimbabeats.media.player.ZimbaBeatsPlayer
import com.zimbabeats.media.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Global music playback state for mini player
 */
data class MusicPlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val sleepTimerEnabled: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    // Content filtering
    val isBlocked: Boolean = false,
    val blockReason: String? = null
)

/**
 * Singleton manager for music playback that persists across screens.
 * Used by the MiniPlayer to show currently playing music.
 *
 * ENTERPRISE-GRADE: Includes content filtering for parental controls.
 * All music playback is checked against parent-defined restrictions.
 */
class MusicPlaybackManager(
    private val application: Application,
    private val musicRepository: MusicRepository,
    private val cloudPairingClient: CloudPairingClient? = null
) {
    companion object {
        private const val TAG = "MusicPlaybackManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = ZimbaBeatsPlayer(application)
    private var loadedTrackId: String? = null
    private var sleepTimerJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var playbackStartTime: Long = 0L

    private val _playbackState = MutableStateFlow(MusicPlaybackState())
    val playbackState: StateFlow<MusicPlaybackState> = _playbackState.asStateFlow()

    val playerState: StateFlow<PlayerState> = player.playerState

    // Content filter reference
    private val contentFilter get() = cloudPairingClient?.contentFilter

    init {
        // Start position updates
        startPositionUpdates()
    }

    private fun startPositionUpdates() {
        positionUpdateJob = scope.launch {
            while (true) {
                val currentPos = player.getCurrentPosition().toLong().coerceAtLeast(0L)
                val isPlaying = player.playerState.value.isPlaying
                _playbackState.value = _playbackState.value.copy(
                    currentPosition = currentPos,
                    duration = player.playerState.value.duration,
                    isPlaying = isPlaying
                )
                delay(250)
            }
        }
    }

    fun loadTrack(trackId: String) {
        if (loadedTrackId == trackId && _playbackState.value.currentTrack != null) {
            Log.d(TAG, "Track $trackId already loaded, skipping")
            return
        }

        loadedTrackId = trackId
        _playbackState.value = _playbackState.value.copy(isLoading = true, isBlocked = false, blockReason = null)

        scope.launch {
            when (val result = musicRepository.getPlayerData(trackId)) {
                is Resource.Success -> {
                    val playerData = result.data
                    val track = playerData.track
                    val streamUrl = playerData.streamUrl

                    Log.d(TAG, "Loaded track: ${track.title}")

                    // ========== ENTERPRISE CONTENT FILTERING ==========
                    // Check if this track is allowed by parental controls
                    val blockResult = contentFilter?.shouldBlockMusicContent(
                        trackId = track.id,
                        title = track.title,
                        artistId = track.artistId ?: "",
                        artistName = track.artistName,
                        albumName = track.albumName,
                        genre = null, // Add genre if available in Track model
                        durationSeconds = track.duration / 1000,
                        isExplicit = track.isExplicit
                    )

                    if (blockResult != null && blockResult.isBlocked) {
                        Log.w(TAG, "BLOCKED: ${track.title} - ${blockResult.message}")

                        // Report blocked attempt to parent
                        scope.launch(Dispatchers.IO) {
                            contentFilter?.reportBlockedMusicAttempt(
                                trackId = track.id,
                                title = track.title,
                                artistName = track.artistName,
                                thumbnailUrl = track.thumbnailUrl,
                                blockReason = blockResult.reason ?: BlockReason.BLOCKED_KEYWORD,
                                blockMessage = blockResult.message ?: "Content blocked"
                            )
                        }

                        _playbackState.value = _playbackState.value.copy(
                            currentTrack = track,
                            isLoading = false,
                            isBlocked = true,
                            blockReason = blockResult.message
                        )

                        // Skip to next track in queue if available
                        val state = _playbackState.value
                        if (state.queue.isNotEmpty() && state.currentIndex < state.queue.size - 1) {
                            Log.d(TAG, "Auto-skipping to next track")
                            skipToNext()
                        }
                        return@launch
                    }
                    // ========== END CONTENT FILTERING ==========

                    // Only fetch radio queue if we don't already have a queue set
                    val currentQueue = _playbackState.value.queue
                    val trackInQueue = currentQueue.any { it.id == trackId }
                    Log.d(TAG, "Current queue size: ${currentQueue.size}, track in queue: $trackInQueue")

                    val finalQueue = if (currentQueue.isEmpty() || !trackInQueue) {
                        // Get radio queue for single track playback
                        Log.d(TAG, "Fetching radio queue for track: $trackId")
                        val radioResult = musicRepository.getRadio(trackId)
                        val queue = if (radioResult is Resource.Success) {
                            Log.d(TAG, "Radio queue fetched successfully: ${radioResult.data.size} tracks")
                            // Filter the queue for allowed content
                            filterQueueForParentalControls(radioResult.data)
                        } else {
                            Log.e(TAG, "Radio queue fetch failed, using single track queue")
                            emptyList()
                        }

                        // Ensure the current track is in the queue
                        if (queue.none { it.id == trackId }) {
                            Log.d(TAG, "Adding current track to start of queue")
                            listOf(track) + queue
                        } else {
                            queue
                        }
                    } else {
                        // Use existing queue (e.g., album tracks) - already filtered
                        Log.d(TAG, "Using existing queue (album/playlist)")
                        currentQueue
                    }

                    Log.d(TAG, "Final queue size: ${finalQueue.size}")

                    _playbackState.value = _playbackState.value.copy(
                        currentTrack = track,
                        queue = finalQueue,
                        currentIndex = finalQueue.indexOfFirst { it.id == trackId }.coerceAtLeast(0),
                        isLoading = false,
                        isBlocked = false,
                        blockReason = null
                    )

                    // Record playback start time for screen time tracking
                    playbackStartTime = System.currentTimeMillis()

                    // Log music history to cloud
                    scope.launch(Dispatchers.IO) {
                        contentFilter?.logMusicHistory(
                            trackId = track.id,
                            title = track.title,
                            artistId = track.artistId ?: "",
                            artistName = track.artistName,
                            albumName = track.albumName,
                            thumbnailUrl = track.thumbnailUrl,
                            durationSeconds = track.duration / 1000,
                            listenedDurationSeconds = 0, // Will be updated when playback ends
                            wasBlocked = false
                        )
                    }

                    player.playVideo(streamUrl, track.title)
                }
                is Resource.Error -> {
                    Log.e(TAG, "Failed to load track: ${result.message}")
                    _playbackState.value = _playbackState.value.copy(isLoading = false)
                }
                else -> {}
            }
        }
    }

    /**
     * Filter a list of tracks based on parental control settings
     */
    private fun filterQueueForParentalControls(tracks: List<Track>): List<Track> {
        if (contentFilter == null) return tracks

        return tracks.filter { track ->
            val blockResult = contentFilter?.shouldBlockMusicContent(
                trackId = track.id,
                title = track.title,
                artistId = track.artistId ?: "",
                artistName = track.artistName,
                albumName = track.albumName,
                genre = null,
                durationSeconds = track.duration / 1000,
                isExplicit = track.isExplicit
            )
            val isAllowed = blockResult == null || !blockResult.isBlocked
            if (!isAllowed) {
                Log.d(TAG, "Filtered out from queue: ${track.title} - ${blockResult?.message}")
            }
            isAllowed
        }
    }

    /**
     * Play a list of tracks starting at the given index.
     * Used for playing albums, playlists, or custom queues.
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        Log.d(TAG, "Playing ${tracks.size} tracks starting at index $startIndex")

        // Set the queue and current index
        _playbackState.value = _playbackState.value.copy(
            queue = tracks,
            currentIndex = startIndex.coerceIn(0, tracks.size - 1)
        )

        // Load and play the track at startIndex
        loadedTrackId = null  // Force reload
        loadTrack(tracks[startIndex].id)
    }

    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun skipToNext() {
        val state = _playbackState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex < state.queue.size) {
            _playbackState.value = state.copy(currentIndex = nextIndex)
            loadedTrackId = null
            loadTrack(state.queue[nextIndex].id)
        }
    }

    fun skipToPrevious() {
        if (player.getCurrentPosition() > 3000) {
            player.seekTo(0)
            return
        }

        val state = _playbackState.value
        val prevIndex = state.currentIndex - 1
        if (prevIndex >= 0) {
            _playbackState.value = state.copy(currentIndex = prevIndex)
            loadedTrackId = null
            loadTrack(state.queue[prevIndex].id)
        } else {
            player.seekTo(0)
        }
    }

    fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()

        val durationMs = minutes * 60 * 1000L
        _playbackState.value = _playbackState.value.copy(
            sleepTimerEnabled = true,
            sleepTimerRemainingMs = durationMs
        )

        sleepTimerJob = scope.launch {
            var remainingMs = durationMs
            while (remainingMs > 0) {
                delay(1000L)
                remainingMs -= 1000L
                _playbackState.value = _playbackState.value.copy(sleepTimerRemainingMs = remainingMs)
            }

            Log.d(TAG, "Sleep timer finished, pausing playback")
            player.pause()
            _playbackState.value = _playbackState.value.copy(
                sleepTimerEnabled = false,
                sleepTimerRemainingMs = 0L
            )
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _playbackState.value = _playbackState.value.copy(
            sleepTimerEnabled = false,
            sleepTimerRemainingMs = 0L
        )
    }

    fun stop() {
        player.pause()
        loadedTrackId = null
        _playbackState.value = MusicPlaybackState()
    }

    fun getPlayer() = player.getPlayer()

    // ==================== Audio Settings ====================

    /**
     * Enable or disable audio normalization (loudness enhancement)
     */
    fun setNormalizeAudio(enabled: Boolean) {
        player.setNormalizeAudio(enabled)
    }

    /**
     * Get the audio session ID for external audio effects (like system equalizer)
     */
    fun getAudioSessionId(): Int = player.getAudioSessionId()

    fun release() {
        positionUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        player.release()
    }
}
