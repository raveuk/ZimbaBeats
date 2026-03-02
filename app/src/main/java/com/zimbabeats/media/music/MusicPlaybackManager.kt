package com.zimbabeats.media.music

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.BlockReason
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.util.Resource
import com.zimbabeats.media.service.PlaybackService
import com.zimbabeats.media.datasource.StreamResolver
import com.zimbabeats.media.datasource.StreamResolverHolder
import com.zimbabeats.media.datasource.QueueNavigator
import com.zimbabeats.media.datasource.QueueStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
    val blockReason: String? = null,
    // Error state for UI feedback
    val error: String? = null
)

/**
 * Singleton manager for music playback that persists across screens.
 * Used by the MiniPlayer to show currently playing music.
 *
 * Uses MediaController to connect to PlaybackService for background playback.
 * The connection is lazy - only established when first playing a track.
 * This allows music to continue playing when the app is in the background
 * with media notification controls (like Spotify).
 *
 * ENTERPRISE-GRADE: Includes content filtering for parental controls.
 * All music playback is checked against parent-defined restrictions.
 */
@androidx.media3.common.util.UnstableApi
class MusicPlaybackManager(
    private val application: Application,
    private val musicRepository: MusicRepository,
    private val cloudPairingClient: CloudPairingClient? = null
) {
    companion object {
        private const val TAG = "MusicPlaybackManager"
        private const val MAX_RETRY_ATTEMPTS = 2
    }

    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.Main)

    // Background executor for MediaController connection (avoids blocking main thread)
    private val serviceExecutor = Executors.newSingleThreadExecutor()

    // Media controller for background playback (lazy initialized)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    @Volatile private var isServiceConnected = false
    private val isConnecting = AtomicBoolean(false)

    // Thread-safe track loading state to prevent race conditions (Critical #1)
    private val loadTrackMutex = Mutex()
    private var currentLoadingTrackId: String? = null
    private val loadRequestCounter = AtomicInteger(0)

    private var loadedTrackId: String? = null
    private var sleepTimerJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var trackCompletionJob: Job? = null
    private var favoriteObserverJob: Job? = null  // Fix Medium #14: Track favorite observer
    private var playbackStartTime: Long = 0L

    // Pending callbacks queue for service connection (Critical #2)
    private val pendingCallbacks = CopyOnWriteArrayList<() -> Unit>()

    // Pending playback - stored when service not yet connected
    private var pendingStreamUrl: String? = null
    private var pendingTrack: Track? = null

    private val _playbackState = MutableStateFlow(MusicPlaybackState())
    val playbackState: StateFlow<MusicPlaybackState> = _playbackState.asStateFlow()

    private val _playerState = MutableStateFlow(com.zimbabeats.media.player.PlayerState())
    val playerState: StateFlow<com.zimbabeats.media.player.PlayerState> = _playerState.asStateFlow()

    // Content filter reference
    private val contentFilter get() = cloudPairingClient?.contentFilter

    /**
     * StreamResolver implementation that resolves track IDs to stream URLs on-demand.
     * This allows loading full queue with just track IDs into ExoPlayer,
     * with streams resolved when playback actually starts (like SimpMusic).
     */
    private val streamResolver = object : StreamResolver {
        override suspend fun resolveStreamUrl(trackId: String): String? {
            Log.d(TAG, "StreamResolver: Resolving stream for track: $trackId")
            return when (val result = musicRepository.getPlayerData(trackId)) {
                is Resource.Success -> {
                    Log.d(TAG, "StreamResolver: Resolved stream URL for: ${result.data.track.title}")
                    result.data.streamUrl
                }
                is Resource.Error -> {
                    Log.e(TAG, "StreamResolver: Failed to resolve stream for $trackId: ${result.message}")
                    null
                }
                else -> null
            }
        }
    }

    init {
        // Register our stream resolver so PlaybackService can resolve track IDs to URLs
        StreamResolverHolder.resolver = streamResolver
        Log.d(TAG, "StreamResolver registered with StreamResolverHolder")

        // Register QueueNavigator so PlaybackService can show next/prev buttons in notification
        QueueStateHolder.navigator = object : QueueNavigator {
            override fun skipToNext() {
                Log.d(TAG, "QueueNavigator: skipToNext called from notification")
                this@MusicPlaybackManager.skipToNext()
            }

            override fun skipToPrevious() {
                Log.d(TAG, "QueueNavigator: skipToPrevious called from notification")
                this@MusicPlaybackManager.skipToPrevious()
            }

            override fun hasNext(): Boolean {
                val state = _playbackState.value
                return state.currentIndex < state.queue.size - 1
            }

            override fun hasPrevious(): Boolean {
                val state = _playbackState.value
                return state.currentIndex > 0
            }
        }
        Log.d(TAG, "QueueNavigator registered with QueueStateHolder")

        // Start position updates (lightweight, doesn't need service)
        startPositionUpdates()
        // Listen for track completion
        setupTrackCompletionListener()
    }

    /**
     * Lazily connect to PlaybackService via MediaController.
     * Called when first playing a track.
     * Uses background executor to avoid blocking main thread.
     *
     * Fix Critical #2: Queue callbacks when already connecting instead of dropping them.
     */
    private fun ensureServiceConnected(onConnected: () -> Unit) {
        if (isServiceConnected && mediaController != null) {
            onConnected()
            return
        }

        // Queue the callback - it will be executed when connection completes
        pendingCallbacks.add(onConnected)

        // If already connecting, the callback is queued and will be called
        if (!isConnecting.compareAndSet(false, true)) {
            Log.d(TAG, "Already connecting to service, callback queued (${pendingCallbacks.size} pending)")
            return
        }

        Log.d(TAG, "Lazily connecting to PlaybackService...")

        // Start the service explicitly first
        val serviceIntent = Intent(application, PlaybackService::class.java)
        application.startService(serviceIntent)

        val sessionToken = SessionToken(
            application,
            ComponentName(application, PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()

        // Use background executor instead of directExecutor to avoid ANR
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                isServiceConnected = true
                isConnecting.set(false)
                Log.d(TAG, "Connected to PlaybackService successfully")

                // Setup player listener
                setupPlayerListener()

                // Execute all pending callbacks on main thread (Fix Critical #2)
                scope.launch {
                    val callbacks = pendingCallbacks.toList()
                    pendingCallbacks.clear()
                    Log.d(TAG, "Executing ${callbacks.size} pending callbacks")
                    callbacks.forEach { callback ->
                        try {
                            callback()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error executing pending callback", e)
                        }
                    }

                    // Play pending track if any
                    val url = pendingStreamUrl
                    val track = pendingTrack
                    if (url != null && track != null) {
                        Log.d(TAG, "Playing pending track: ${track.title}")
                        playViaMediaController(url, track)
                        pendingStreamUrl = null
                        pendingTrack = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to PlaybackService", e)
                isServiceConnected = false
                isConnecting.set(false)
                pendingCallbacks.clear()
            }
        }, serviceExecutor)
    }

    /**
     * Setup listener for player state changes from MediaController
     */
    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val controller = mediaController ?: return

                val stateStr = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "Playback state: $stateStr")

                val duration = if (playbackState == Player.STATE_READY) {
                    controller.duration.coerceAtLeast(0)
                } else {
                    _playerState.value.duration
                }

                _playerState.value = _playerState.value.copy(
                    isPlaying = controller.isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isEnded = playbackState == Player.STATE_ENDED,
                    duration = duration
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "isPlaying changed: $isPlaying")
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                // Also update playbackState for UI consistency (Fix High #7)
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val controller = mediaController ?: return
                _playerState.value = _playerState.value.copy(isPlaying = playWhenReady && controller.isPlaying)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player error: ${error.errorCodeName} - ${error.message}")
                _playerState.value = _playerState.value.copy(isPlaying = false)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                Log.d(TAG, "Media metadata changed: ${mediaMetadata.title}")
                _playerState.value = _playerState.value.copy(
                    currentVideoTitle = mediaMetadata.title?.toString()
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val reasonStr = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "Media item transition, reason: $reasonStr, title: ${mediaItem?.mediaMetadata?.title}")
            }
        })
    }

    /**
     * Fix High #7: Only update position/duration here, let player listener handle isPlaying
     * to avoid state drift between position updates and player events.
     */
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (true) {
                val controller = mediaController
                if (controller != null && isServiceConnected) {
                    val currentPos = controller.currentPosition.coerceAtLeast(0L)
                    val duration = controller.duration.coerceAtLeast(0L)
                    // Only update position and duration - isPlaying is handled by player listener
                    _playbackState.value = _playbackState.value.copy(
                        currentPosition = currentPos,
                        duration = duration
                    )
                }
                delay(250)
            }
        }
    }

    /**
     * Listen for track completion and auto-advance to next song in queue.
     *
     * IMPORTANT: Only auto-advance if the track actually played for a meaningful duration.
     * This prevents rapid skipping when stream URLs fail to load (ExoPlayer goes
     * BUFFERING → ENDED immediately without playing anything).
     */
    private fun setupTrackCompletionListener() {
        trackCompletionJob?.cancel()
        trackCompletionJob = scope.launch {
            var wasEnded = false
            _playerState.collect { state ->
                // Detect transition to ended state
                if (state.isEnded && !wasEnded) {
                    Log.d(TAG, "Track ended, checking if it actually played")
                    val playbackState = _playbackState.value
                    val currentPosition = playbackState.currentPosition
                    val duration = playbackState.duration

                    // Check if track actually played (more than 3 seconds or > 5% of duration)
                    val minPlayTime = 3000L // 3 seconds
                    val actuallyPlayed = currentPosition > minPlayTime ||
                                         (duration > 0 && currentPosition > duration * 0.05)

                    if (actuallyPlayed) {
                        // Normal track completion - advance to next
                        val nextIndex = playbackState.currentIndex + 1
                        if (nextIndex < playbackState.queue.size) {
                            Log.d(TAG, "Auto-advancing to next track: index $nextIndex of ${playbackState.queue.size}")
                            delay(300)
                            skipToNext()
                        } else {
                            Log.d(TAG, "Queue ended - no more tracks to play")
                        }
                    } else {
                        // Track ended without playing - likely a failed stream load
                        Log.w(TAG, "Track ended without playing (pos: ${currentPosition}ms, duration: ${duration}ms)")
                        _playbackState.value = _playbackState.value.copy(
                            isLoading = false,
                            error = "Unable to play this track"
                        )
                        // Don't auto-skip - let user decide what to do
                    }
                }
                wasEnded = state.isEnded
            }
        }
    }

    /**
     * Load and play a track by ID.
     *
     * Fix Critical #1: Use mutex and request counter to prevent race conditions.
     * Fix High #6: Propagate errors to UI state.
     * Fix Medium #11: Add retry logic for stream resolution.
     */
    fun loadTrack(trackId: String, retryCount: Int = 0) {
        if (loadedTrackId == trackId && _playbackState.value.currentTrack != null) {
            Log.d(TAG, "Track $trackId already loaded, skipping")
            return
        }

        // Assign a unique request ID to track this load operation
        val requestId = loadRequestCounter.incrementAndGet()

        loadedTrackId = trackId
        _playbackState.value = _playbackState.value.copy(isLoading = true, isBlocked = false, blockReason = null, error = null)

        scope.launch {
            // Use mutex to prevent concurrent loads (Critical #1)
            loadTrackMutex.withLock {
                // Check if this request is still the latest one
                if (requestId != loadRequestCounter.get()) {
                    Log.d(TAG, "Load request $requestId superseded by newer request, aborting")
                    return@withLock
                }

                currentLoadingTrackId = trackId

                when (val result = musicRepository.getPlayerData(trackId)) {
                    is Resource.Success -> {
                        // Double-check we're still loading this track
                        if (currentLoadingTrackId != trackId || requestId != loadRequestCounter.get()) {
                            Log.d(TAG, "Track changed during load, discarding result for: $trackId")
                            return@withLock
                        }

                        val playerData = result.data
                        val track = playerData.track
                        val streamUrl = playerData.streamUrl

                        Log.d(TAG, "Loaded track: ${track.title}")

                        // ========== ENTERPRISE CONTENT FILTERING ==========
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

                        if (blockResult != null && blockResult.isBlocked) {
                            Log.w(TAG, "BLOCKED: ${track.title} - ${blockResult.message}")

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

                            // DON'T auto-skip when content is blocked - let user see the message
                            // and manually skip if they want. Auto-skipping causes rapid cycling
                            // through all blocked tracks (e.g., all The Weeknd songs).
                            Log.d(TAG, "Content blocked - NOT auto-skipping (user can skip manually)")
                            return@withLock
                        }
                        // ========== END CONTENT FILTERING ==========

                        // Only fetch radio queue if we don't already have a queue set
                        val currentQueue = _playbackState.value.queue
                        val currentIndex = _playbackState.value.currentIndex
                        val existingIndex = currentQueue.indexOfFirst { it.id == trackId }
                        Log.d(TAG, "Current queue size: ${currentQueue.size}, existingIndex: $existingIndex, currentIndex: $currentIndex")

                        // Determine final queue and index
                        val (finalQueue, finalIndex) = if (existingIndex >= 0) {
                            // Track found in existing queue - preserve queue and use correct index
                            Log.d(TAG, "Track found in existing queue at index $existingIndex, preserving queue")
                            currentQueue to existingIndex
                        } else if (currentQueue.isEmpty()) {
                            // No queue - fetch radio queue
                            Log.d(TAG, "Queue empty, fetching radio queue for track: $trackId")
                            val radioResult = musicRepository.getRadio(trackId)
                            val queue = if (radioResult is Resource.Success) {
                                Log.d(TAG, "Radio queue fetched successfully: ${radioResult.data.size} tracks")
                                filterQueueForParentalControls(radioResult.data)
                            } else {
                                Log.e(TAG, "Radio queue fetch failed, using single track queue")
                                emptyList()
                            }

                            val finalQ = if (queue.none { it.id == trackId }) {
                                Log.d(TAG, "Adding current track to start of queue")
                                listOf(track) + queue
                            } else {
                                queue
                            }
                            finalQ to finalQ.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                        } else {
                            // Track not in queue but queue exists - add track and use new queue
                            Log.d(TAG, "Track not in existing queue, adding and fetching radio")
                            val radioResult = musicRepository.getRadio(trackId)
                            val queue = if (radioResult is Resource.Success) {
                                filterQueueForParentalControls(radioResult.data)
                            } else {
                                emptyList()
                            }

                            val finalQ = if (queue.none { it.id == trackId }) {
                                listOf(track) + queue
                            } else {
                                queue
                            }
                            finalQ to finalQ.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                        }

                        Log.d(TAG, "Final queue size: ${finalQueue.size}, final index: $finalIndex")

                        _playbackState.value = _playbackState.value.copy(
                            currentTrack = track,
                            queue = finalQueue,
                            currentIndex = finalIndex,
                            isLoading = false,
                            isBlocked = false,
                            blockReason = null,
                            error = null
                        )

                        playbackStartTime = System.currentTimeMillis()

                        scope.launch(Dispatchers.IO) {
                            contentFilter?.logMusicHistory(
                                trackId = track.id,
                                title = track.title,
                                artistId = track.artistId ?: "",
                                artistName = track.artistName,
                                albumName = track.albumName,
                                thumbnailUrl = track.thumbnailUrl,
                                durationSeconds = track.duration / 1000,
                                listenedDurationSeconds = 0,
                                wasBlocked = false
                            )
                        }

                        // Play via service (lazy connect if needed)
                        if (isServiceConnected && mediaController != null) {
                            playViaMediaController(streamUrl, track)
                        } else {
                            // Store pending and connect
                            pendingStreamUrl = streamUrl
                            pendingTrack = track
                            ensureServiceConnected {
                                // Callback handled inside ensureServiceConnected
                            }
                        }
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "Failed to load track: ${result.message}")
                        // Fix Medium #11: Retry logic for stream resolution
                        if (retryCount < MAX_RETRY_ATTEMPTS) {
                            Log.d(TAG, "Retrying track load (attempt ${retryCount + 1}/$MAX_RETRY_ATTEMPTS)")
                            delay(500) // Brief delay before retry
                            currentLoadingTrackId = null
                            loadTrack(trackId, retryCount + 1)
                            return@withLock
                        }
                        // Fix High #6: Propagate error to UI
                        _playbackState.value = _playbackState.value.copy(
                            isLoading = false,
                            error = result.message ?: "Failed to load track"
                        )
                    }
                    else -> {}
                }

                currentLoadingTrackId = null
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
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        Log.d(TAG, "Playing ${tracks.size} tracks starting at index $startIndex")

        _playbackState.value = _playbackState.value.copy(
            queue = tracks,
            currentIndex = startIndex.coerceIn(0, tracks.size - 1)
        )

        loadedTrackId = null
        loadTrack(tracks[startIndex].id)
    }

    /**
     * Play media through the MediaController (PlaybackService)
     * Creates a MediaItem with metadata for the notification.
     *
     * SIMPLE SINGLE-TRACK APPROACH (proven to work):
     * Plays ONE track at a time with pre-resolved stream URL.
     * Queue navigation is handled by loadTrack() for each track.
     * This avoids track ID mismatch issues with on-demand resolution.
     */
    private fun playViaMediaController(streamUrl: String, track: Track) {
        val controller = mediaController
        if (controller == null) {
            Log.e(TAG, "MediaController not connected, storing as pending")
            pendingStreamUrl = streamUrl
            pendingTrack = track
            ensureServiceConnected {}
            return
        }

        Log.d(TAG, "Playing via MediaController: ${track.title}")

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artistName)
                    .setAlbumTitle(track.albumName)
                    .setArtworkUri(track.thumbnailUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()

        Log.d(TAG, "Playback started via service for: ${track.title}")
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun skipToNext() {
        val state = _playbackState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex < state.queue.size) {
            val nextTrack = state.queue[nextIndex]
            Log.d(TAG, "Skipping to next: ${nextTrack.title} (index $nextIndex of ${state.queue.size})")
            // Update both currentTrack AND currentIndex immediately for responsive UI
            _playbackState.value = state.copy(
                currentTrack = nextTrack,
                currentIndex = nextIndex
            )
            loadedTrackId = null
            loadTrack(nextTrack.id)
        } else {
            Log.d(TAG, "No next track in queue")
        }
    }

    fun skipToPrevious() {
        val controller = mediaController
        if (controller != null && controller.currentPosition > 3000) {
            controller.seekTo(0)
            return
        }

        val state = _playbackState.value
        val prevIndex = state.currentIndex - 1
        if (prevIndex >= 0) {
            val prevTrack = state.queue[prevIndex]
            Log.d(TAG, "Skipping to previous: ${prevTrack.title} (index $prevIndex)")
            // Update both currentTrack AND currentIndex immediately for responsive UI
            _playbackState.value = state.copy(
                currentTrack = prevTrack,
                currentIndex = prevIndex
            )
            loadedTrackId = null
            loadTrack(prevTrack.id)
        } else {
            controller?.seekTo(0)
        }
    }

    /**
     * Skip to a specific index in the queue.
     * Uses loadTrack() to properly resolve and play the track.
     */
    fun skipToQueueIndex(index: Int) {
        val state = _playbackState.value
        if (index in state.queue.indices) {
            val track = state.queue[index]
            Log.d(TAG, "Skipping to queue index: $index (${track.title})")
            // Update both currentTrack AND currentIndex immediately for responsive UI
            _playbackState.value = state.copy(
                currentTrack = track,
                currentIndex = index
            )
            loadedTrackId = null
            loadTrack(track.id)
        } else {
            Log.w(TAG, "Invalid queue index: $index, queue size: ${state.queue.size}")
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
            mediaController?.pause()
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
        mediaController?.stop()
        loadedTrackId = null
        _playbackState.value = MusicPlaybackState()
    }

    /**
     * Get the underlying Player for UI components that need direct access
     */
    fun getPlayer(): Player? = mediaController

    // ==================== Audio Settings ====================

    fun setNormalizeAudio(enabled: Boolean) {
        Log.d(TAG, "Audio normalization request: $enabled (handled by service)")
    }

    fun getAudioSessionId(): Int = 0

    fun release() {
        Log.d(TAG, "Releasing MusicPlaybackManager...")

        positionUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        trackCompletionJob?.cancel()

        // Unregister from singletons
        QueueStateHolder.navigator = null
        StreamResolverHolder.resolver = null

        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        isServiceConnected = false

        serviceExecutor.shutdown()
        scopeJob.cancel()

        Log.d(TAG, "MusicPlaybackManager released")
    }
}
