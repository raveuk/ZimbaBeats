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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private var playbackStartTime: Long = 0L

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
                // Sync app state when ExoPlayer changes tracks (via notification or auto-advance)
                val mediaId = mediaItem?.mediaId ?: return
                val reasonStr = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "Media item transition: $mediaId, reason: $reasonStr")

                // Update app state to match ExoPlayer's current item
                val state = _playbackState.value
                val newIndex = state.queue.indexOfFirst { it.id == mediaId }
                if (newIndex >= 0 && newIndex != state.currentIndex) {
                    Log.d(TAG, "Syncing app state: index $newIndex, track: ${state.queue[newIndex].title}")
                    _playbackState.value = state.copy(
                        currentIndex = newIndex,
                        currentTrack = state.queue[newIndex]
                    )
                    loadedTrackId = mediaId
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
     *
     * FULL QUEUE APPROACH (like SimpMusic):
     * Loads ALL queue items into ExoPlayer with just track IDs as URIs.
     * The StreamResolvingDataSourceFactory resolves URLs on-demand when playback starts.
     * This allows notification next/previous buttons to work correctly.
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

        val state = _playbackState.value
        val queue = state.queue
        val currentIndex = state.currentIndex

        Log.d(TAG, "Playing via MediaController: ${track.title} (index $currentIndex of ${queue.size})")

        // Build MediaItems for ALL tracks in queue with track ID as URI
        // The ResolvingDataSource will resolve the actual stream URL on-demand
        val mediaItems = queue.map { queueTrack ->
            MediaItem.Builder()
                .setMediaId(queueTrack.id)
                // Use track ID as URI - StreamResolvingDataSourceFactory will resolve it
                .setUri(queueTrack.id)
                // CRITICAL: setCustomCacheKey ensures dataSpec.key is set for our resolver
                .setCustomCacheKey(queueTrack.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(queueTrack.title)
                        .setArtist(queueTrack.artistName)
                        .setAlbumTitle(queueTrack.albumName)
                        .setArtworkUri(queueTrack.thumbnailUrl?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }

        if (mediaItems.isEmpty()) {
            // Fallback: play single track with pre-resolved URL if queue is empty
            Log.w(TAG, "Queue empty, falling back to single track playback")
            val singleItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(streamUrl)
                .setCustomCacheKey(track.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artistName)
                        .setAlbumTitle(track.albumName)
                        .setArtworkUri(track.thumbnailUrl?.let { Uri.parse(it) })
                        .build()
                )
                .build()
            controller.setMediaItem(singleItem)
            controller.prepare()
            controller.play()
            Log.d(TAG, "Single track playback started for: ${track.title}")
            return
        }

        // Set full queue and seek to current index
        Log.d(TAG, "Setting ${mediaItems.size} items in ExoPlayer queue, starting at index $currentIndex")
        controller.setMediaItems(mediaItems, currentIndex, 0L)
        controller.prepare()
        controller.play()

        Log.d(TAG, "Full queue loaded, playback started for: ${track.title}")
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
        val controller = mediaController
        if (controller != null && controller.hasNextMediaItem()) {
            Log.d(TAG, "Skipping to next via ExoPlayer")
            controller.seekToNextMediaItem()
            // State will be updated via onMediaItemTransition listener
        } else {
            Log.d(TAG, "No next item in ExoPlayer queue")
        }
    }

    fun skipToPrevious() {
        val controller = mediaController
        if (controller == null) return

        // If more than 3 seconds played, restart current track
        if (controller.currentPosition > 3000) {
            controller.seekTo(0)
            return
        }

        // Otherwise go to previous
        if (controller.hasPreviousMediaItem()) {
            Log.d(TAG, "Skipping to previous via ExoPlayer")
            controller.seekToPreviousMediaItem()
            // State will be updated via onMediaItemTransition listener
        } else {
            controller.seekTo(0)
        }
    }

    /**
     * Seek to a specific index in the already-loaded ExoPlayer queue.
     * Use this when clicking queue items instead of loadTrack() to avoid reloading the queue.
     */
    fun seekToQueueIndex(index: Int) {
        val controller = mediaController
        if (controller == null) {
            Log.w(TAG, "Cannot seek to queue index: MediaController not connected")
            return
        }

        val mediaItemCount = controller.mediaItemCount
        if (index in 0 until mediaItemCount) {
            Log.d(TAG, "Seeking to queue index: $index of $mediaItemCount")
            controller.seekTo(index, 0L)
            // State will be updated via onMediaItemTransition listener
        } else {
            Log.w(TAG, "Invalid queue index: $index, mediaItemCount: $mediaItemCount")
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
