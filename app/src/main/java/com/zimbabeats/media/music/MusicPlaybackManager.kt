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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

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
    }

    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.Main)

    // Background executor for MediaController connection (avoids blocking main thread)
    private val serviceExecutor = Executors.newSingleThreadExecutor()

    // Media controller for background playback (lazy initialized)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var isServiceConnected = false
    private var isConnecting = false

    private var loadedTrackId: String? = null
    private var sleepTimerJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var trackCompletionJob: Job? = null
    private var prefetchJob: Job? = null
    private var playbackStartTime: Long = 0L

    // Pending playback - stored when service not yet connected
    private var pendingStreamUrl: String? = null
    private var pendingTrack: Track? = null

    // Cache for prefetched stream URLs (trackId -> streamUrl)
    private val streamUrlCache = mutableMapOf<String, String>()

    private val _playbackState = MutableStateFlow(MusicPlaybackState())
    val playbackState: StateFlow<MusicPlaybackState> = _playbackState.asStateFlow()

    private val _playerState = MutableStateFlow(com.zimbabeats.media.player.PlayerState())
    val playerState: StateFlow<com.zimbabeats.media.player.PlayerState> = _playerState.asStateFlow()

    // Content filter reference
    private val contentFilter get() = cloudPairingClient?.contentFilter

    init {
        // Start position updates (lightweight, doesn't need service)
        startPositionUpdates()
        // Listen for track completion
        setupTrackCompletionListener()
    }

    /**
     * Lazily connect to PlaybackService via MediaController.
     * Called when first playing a track.
     * Uses background executor to avoid blocking main thread.
     */
    private fun ensureServiceConnected(onConnected: () -> Unit) {
        if (isServiceConnected && mediaController != null) {
            onConnected()
            return
        }

        if (isConnecting) {
            // Store callback for when connection completes
            Log.d(TAG, "Already connecting to service, waiting...")
            return
        }

        isConnecting = true
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
                isConnecting = false
                Log.d(TAG, "Connected to PlaybackService successfully")

                // Setup player listener
                setupPlayerListener()

                // Execute the pending callback on main thread
                scope.launch {
                    onConnected()

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
                isConnecting = false
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
                // Handle track transitions from notification Next/Previous buttons
                val mediaId = mediaItem?.mediaId ?: return
                Log.d(TAG, "Media item transition: $mediaId, reason: $reason")

                // Handle placeholder transitions (fallback for before prefetch completes)
                when (mediaId) {
                    "next_placeholder" -> {
                        Log.d(TAG, "Next button pressed from notification (placeholder)")
                        mediaController?.pause()
                        skipToNext()
                        return
                    }
                    "prev_placeholder" -> {
                        Log.d(TAG, "Previous button pressed from notification (placeholder)")
                        mediaController?.pause()
                        skipToPrevious()
                        return
                    }
                }

                // Handle real track transitions (when prefetched URLs are used)
                val state = _playbackState.value
                val currentTrack = state.currentTrack ?: return

                // If transitioning to a different track ID, update our playback state
                if (mediaId != currentTrack.id) {
                    val newTrackIndex = state.queue.indexOfFirst { it.id == mediaId }
                    if (newTrackIndex >= 0 && newTrackIndex != state.currentIndex) {
                        Log.d(TAG, "Track transition from notification: ${state.queue[newTrackIndex].title}")
                        _playbackState.value = state.copy(
                            currentTrack = state.queue[newTrackIndex],
                            currentIndex = newTrackIndex
                        )
                        // Prefetch nearby tracks for the new position
                        val streamUrl = streamUrlCache[mediaId]
                        if (streamUrl != null) {
                            prefetchNearbyTracks(newTrackIndex, state.queue, streamUrl, mediaId)
                        }
                    }
                }
            }
        })
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (true) {
                val controller = mediaController
                if (controller != null && isServiceConnected) {
                    val currentPos = controller.currentPosition.coerceAtLeast(0L)
                    val isPlaying = controller.isPlaying
                    val duration = controller.duration.coerceAtLeast(0L)
                    _playbackState.value = _playbackState.value.copy(
                        currentPosition = currentPos,
                        duration = duration,
                        isPlaying = isPlaying
                    )
                }
                delay(250)
            }
        }
    }

    /**
     * Listen for track completion and auto-advance to next song in queue
     */
    private fun setupTrackCompletionListener() {
        trackCompletionJob?.cancel()
        trackCompletionJob = scope.launch {
            var wasEnded = false
            _playerState.collect { state ->
                // Detect transition to ended state
                if (state.isEnded && !wasEnded) {
                    Log.d(TAG, "Track ended, checking queue for next track")
                    val playbackState = _playbackState.value
                    val nextIndex = playbackState.currentIndex + 1

                    if (nextIndex < playbackState.queue.size) {
                        Log.d(TAG, "Auto-advancing to next track: index $nextIndex of ${playbackState.queue.size}")
                        delay(300)
                        skipToNext()
                    } else {
                        Log.d(TAG, "Queue ended - no more tracks to play")
                    }
                }
                wasEnded = state.isEnded
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
                        Log.d(TAG, "Fetching radio queue for track: $trackId")
                        val radioResult = musicRepository.getRadio(trackId)
                        val queue = if (radioResult is Resource.Success) {
                            Log.d(TAG, "Radio queue fetched successfully: ${radioResult.data.size} tracks")
                            filterQueueForParentalControls(radioResult.data)
                        } else {
                            Log.e(TAG, "Radio queue fetch failed, using single track queue")
                            emptyList()
                        }

                        if (queue.none { it.id == trackId }) {
                            Log.d(TAG, "Adding current track to start of queue")
                            listOf(track) + queue
                        } else {
                            queue
                        }
                    } else {
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
     * Prefetch stream URLs for nearby tracks in the queue.
     * This enables smooth next/previous transitions in the notification.
     */
    private fun prefetchNearbyTracks(currentIndex: Int, queue: List<Track>, currentStreamUrl: String, currentTrackId: String) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            // Cache current track's URL
            streamUrlCache[currentTrackId] = currentStreamUrl

            // Prefetch next track
            if (currentIndex + 1 < queue.size) {
                val nextTrack = queue[currentIndex + 1]
                if (!streamUrlCache.containsKey(nextTrack.id)) {
                    Log.d(TAG, "Prefetching next track: ${nextTrack.title}")
                    try {
                        when (val result = musicRepository.getPlayerData(nextTrack.id)) {
                            is Resource.Success -> {
                                streamUrlCache[nextTrack.id] = result.data.streamUrl
                                Log.d(TAG, "Prefetched next track URL: ${nextTrack.title}")
                                // Update notification with real URLs
                                withContext(Dispatchers.Main) {
                                    updateMediaSessionQueue()
                                }
                            }
                            else -> Log.e(TAG, "Failed to prefetch next track")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error prefetching next track", e)
                    }
                }
            }

            // Prefetch previous track
            if (currentIndex > 0) {
                val prevTrack = queue[currentIndex - 1]
                if (!streamUrlCache.containsKey(prevTrack.id)) {
                    Log.d(TAG, "Prefetching previous track: ${prevTrack.title}")
                    try {
                        when (val result = musicRepository.getPlayerData(prevTrack.id)) {
                            is Resource.Success -> {
                                streamUrlCache[prevTrack.id] = result.data.streamUrl
                                Log.d(TAG, "Prefetched previous track URL: ${prevTrack.title}")
                                // Update notification with real URLs
                                withContext(Dispatchers.Main) {
                                    updateMediaSessionQueue()
                                }
                            }
                            else -> Log.e(TAG, "Failed to prefetch previous track")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error prefetching previous track", e)
                    }
                }
            }
        }
    }

    /**
     * Update the MediaSession queue with prefetched URLs.
     */
    private fun updateMediaSessionQueue() {
        val controller = mediaController ?: return
        val state = _playbackState.value
        val currentTrack = state.currentTrack ?: return

        val currentStreamUrl = streamUrlCache[currentTrack.id] ?: return

        val hasNextTrack = state.currentIndex < state.queue.size - 1
        val hasPrevTrack = state.currentIndex > 0

        if (!hasNextTrack && !hasPrevTrack) return

        val mediaItems = mutableListOf<MediaItem>()
        var startIndex = 0

        // Add previous track if available and prefetched
        if (hasPrevTrack) {
            val prevTrack = state.queue[state.currentIndex - 1]
            val prevStreamUrl = streamUrlCache[prevTrack.id]
            if (prevStreamUrl != null) {
                mediaItems.add(MediaItem.Builder()
                    .setMediaId(prevTrack.id)
                    .setUri(Uri.parse(prevStreamUrl))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(prevTrack.title)
                            .setArtist(prevTrack.artistName)
                            .setAlbumTitle(prevTrack.albumName)
                            .setArtworkUri(prevTrack.thumbnailUrl?.let { Uri.parse(it) })
                            .build()
                    )
                    .build())
                startIndex = 1
            }
        }

        // Add current track
        mediaItems.add(MediaItem.Builder()
            .setMediaId(currentTrack.id)
            .setUri(Uri.parse(currentStreamUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(currentTrack.title)
                    .setArtist(currentTrack.artistName)
                    .setAlbumTitle(currentTrack.albumName)
                    .setArtworkUri(currentTrack.thumbnailUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build())

        // Add next track if available and prefetched
        if (hasNextTrack) {
            val nextTrack = state.queue[state.currentIndex + 1]
            val nextStreamUrl = streamUrlCache[nextTrack.id]
            if (nextStreamUrl != null) {
                mediaItems.add(MediaItem.Builder()
                    .setMediaId(nextTrack.id)
                    .setUri(Uri.parse(nextStreamUrl))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(nextTrack.title)
                            .setArtist(nextTrack.artistName)
                            .setAlbumTitle(nextTrack.albumName)
                            .setArtworkUri(nextTrack.thumbnailUrl?.let { Uri.parse(it) })
                            .build()
                    )
                    .build())
            }
        }

        if (mediaItems.size > 1) {
            val currentPosition = controller.currentPosition
            controller.setMediaItems(mediaItems, startIndex, currentPosition)
            Log.d(TAG, "Updated MediaSession queue with ${mediaItems.size} items")
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
     * Creates a MediaItem with metadata for the notification
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

        // Check if there are more tracks in the queue to enable Next button
        val state = _playbackState.value
        val hasNextTrack = state.currentIndex < state.queue.size - 1
        val hasPrevTrack = state.currentIndex > 0

        if (hasNextTrack || hasPrevTrack) {
            // Add queue items with proper metadata to show Next/Previous buttons
            val mediaItems = mutableListOf<MediaItem>()
            var startIndex = 0

            if (hasPrevTrack) {
                // Add previous track with its metadata
                val prevTrack = state.queue[state.currentIndex - 1]
                mediaItems.add(MediaItem.Builder()
                    .setMediaId("prev_placeholder")
                    .setUri(Uri.parse(streamUrl)) // Will be intercepted before playing
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(prevTrack.title)
                            .setArtist(prevTrack.artistName)
                            .setAlbumTitle(prevTrack.albumName)
                            .setArtworkUri(prevTrack.thumbnailUrl?.let { Uri.parse(it) })
                            .build()
                    )
                    .build())
                startIndex = 1
            }

            // Add current track
            mediaItems.add(mediaItem)

            if (hasNextTrack) {
                // Add next track with its metadata
                val nextTrack = state.queue[state.currentIndex + 1]
                mediaItems.add(MediaItem.Builder()
                    .setMediaId("next_placeholder")
                    .setUri(Uri.parse(streamUrl)) // Will be intercepted before playing
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(nextTrack.title)
                            .setArtist(nextTrack.artistName)
                            .setAlbumTitle(nextTrack.albumName)
                            .setArtworkUri(nextTrack.thumbnailUrl?.let { Uri.parse(it) })
                            .build()
                    )
                    .build())
            }

            controller.setMediaItems(mediaItems, startIndex, 0)
        } else {
            controller.setMediaItem(mediaItem)
        }

        controller.prepare()
        controller.play()

        Log.d(TAG, "Playback started via service for: ${track.title}")

        // Prefetch nearby tracks for smooth next/previous transitions
        // Note: 'state' is already defined earlier in this function
        val currentState = _playbackState.value
        prefetchNearbyTracks(currentState.currentIndex, currentState.queue, streamUrl, track.id)
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
            _playbackState.value = state.copy(currentIndex = nextIndex)
            loadedTrackId = null
            loadTrack(state.queue[nextIndex].id)
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
            _playbackState.value = state.copy(currentIndex = prevIndex)
            loadedTrackId = null
            loadTrack(state.queue[prevIndex].id)
        } else {
            controller?.seekTo(0)
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
