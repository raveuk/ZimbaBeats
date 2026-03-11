package com.zimbabeats.media.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main player wrapper for ZimbaBeats
 * Wraps ExoPlayer and provides kid-friendly controls
 */
@UnstableApi
class ZimbaBeatsPlayer(private val context: Context) {

    companion object {
        private const val TAG = "ZimbaBeatsPlayer"
    }

    // Custom LoadControl for better buffering on slow mobile networks
    // Based on SimpMusic reference implementation
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            30_000,   // Min buffer before playback starts (30 seconds)
            60_000,   // Max buffer to maintain (60 seconds)
            2_500,    // Buffer for playback to start (2.5 seconds)
            5_000     // Buffer for rebuffering (5 seconds)
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setSeekBackIncrementMs(10_000) // 10 seconds
        .setSeekForwardIncrementMs(10_000) // 10 seconds
        .build()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // Audio normalization using LoudnessEnhancer
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var isNormalizeAudioEnabled = false

    init {
        setupPlayerListener()
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateStr = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "Playback state: $stateStr")

                val dur = if (playbackState == Player.STATE_READY) {
                    exoPlayer.duration.coerceAtLeast(0)
                } else {
                    _playerState.value.duration
                }

                _playerState.value = _playerState.value.copy(
                    isPlaying = exoPlayer.isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isEnded = playbackState == Player.STATE_ENDED,
                    duration = dur
                )

                if (playbackState == Player.STATE_READY) {
                    Log.d(TAG, "Media ready, duration: ${dur}ms, audioSession: ${exoPlayer.audioSessionId}")
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "isPlaying changed: $isPlaying")
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _playerState.value = _playerState.value.copy(isPlaying = playWhenReady && exoPlayer.isPlaying)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player error: ${error.errorCodeName} - ${error.message}")
                _playerState.value = _playerState.value.copy(isPlaying = false)
            }
        })
    }

    fun getPlayer(): Player = exoPlayer

    fun playVideo(videoUrl: String, title: String) {
        Log.d(TAG, "Playing stream: $videoUrl")

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()

        // Detect if this is a local file or network stream
        val isLocalFile = videoUrl.startsWith("file://") || videoUrl.startsWith("/")

        val mediaSource: MediaSource = if (isLocalFile) {
            // Use DefaultDataSource for local files (supports file:// URIs)
            Log.d(TAG, "Using local file playback for: $videoUrl")
            val localDataSourceFactory = DefaultDataSource.Factory(context)
            ProgressiveMediaSource.Factory(localDataSourceFactory)
                .createMediaSource(mediaItem)
        } else {
            // Create data source factory with Android YouTube Music headers for network streams
            // This matches the ANDROID_MUSIC client used to get the stream URLs
            // Timeouts increased to 30s for slow mobile networks (based on SimpMusic)
            val networkDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("com.google.android.apps.youtube.music/7.03.52 (Linux; U; Android 14) gzip")
                .setDefaultRequestProperties(mapOf(
                    "Accept" to "*/*",
                    "Accept-Encoding" to "gzip, deflate",
                    "Accept-Language" to "en-US,en;q=0.9"
                ))
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)

            // Detect stream type and use appropriate media source
            when {
                videoUrl.contains(".m3u8") || videoUrl.contains("manifest") -> {
                    Log.d(TAG, "Using HLS media source for: $videoUrl")
                    HlsMediaSource.Factory(networkDataSourceFactory)
                        .createMediaSource(mediaItem)
                }
                else -> {
                    Log.d(TAG, "Using Progressive media source for: $videoUrl")
                    ProgressiveMediaSource.Factory(networkDataSourceFactory)
                        .createMediaSource(mediaItem)
                }
            }
        }

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.volume = 1.0f // Ensure volume is at max
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
        exoPlayer.play()
        Log.d(TAG, "Playback started, volume: ${exoPlayer.volume}")

        _playerState.value = _playerState.value.copy(
            currentVideoTitle = title,
            currentVideoUrl = videoUrl
        )

        Log.d(TAG, "Playback started for: $title")
    }

    /**
     * Play video with separate audio stream (for high quality playback)
     * Merges a video-only stream with an audio-only stream
     */
    fun playVideoWithSeparateAudio(videoUrl: String, audioUrl: String, title: String) {
        Log.d(TAG, "Playing dual-stream: video=$videoUrl, audio=$audioUrl")

        // Create data source factory with Android YouTube Music headers
        // Timeouts increased to 30s for slow mobile networks (based on SimpMusic)
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.apps.youtube.music/7.03.52 (Linux; U; Android 14) gzip")
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Accept-Encoding" to "gzip, deflate",
                "Accept-Language" to "en-US,en;q=0.9"
            ))
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)

        // Create video media source
        val videoItem = MediaItem.fromUri(videoUrl)
        val videoSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(videoItem)

        // Create audio media source
        val audioItem = MediaItem.fromUri(audioUrl)
        val audioSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(audioItem)

        // Merge video and audio sources
        val mergedSource = MergingMediaSource(videoSource, audioSource)

        exoPlayer.setMediaSource(mergedSource)
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
        exoPlayer.play()  // Explicit play call to ensure autoplay

        _playerState.value = _playerState.value.copy(
            currentVideoTitle = title,
            currentVideoUrl = videoUrl
        )

        Log.d(TAG, "Dual-stream playback started with autoplay")
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun seekForward() {
        exoPlayer.seekForward()
    }

    fun seekBackward() {
        exoPlayer.seekBack()
    }

    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    fun getDuration(): Long = exoPlayer.duration

    /**
     * Set playback speed
     * @param speed Speed multiplier (0.5f to 2.0f)
     */
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 2.0f)
        Log.d(TAG, "Setting playback speed: $clampedSpeed")
        exoPlayer.setPlaybackSpeed(clampedSpeed)
        _playerState.value = _playerState.value.copy(playbackSpeed = clampedSpeed)
    }

    /**
     * Get current playback speed
     */
    fun getPlaybackSpeed(): Float = exoPlayer.playbackParameters.speed

    /**
     * Get the audio session ID for external audio effects (like system equalizer)
     */
    fun getAudioSessionId(): Int = exoPlayer.audioSessionId

    /**
     * Enable or disable audio normalization (loudness enhancement)
     * This helps maintain consistent volume levels across different tracks
     */
    fun setNormalizeAudio(enabled: Boolean) {
        isNormalizeAudioEnabled = enabled
        Log.d(TAG, "Audio normalization: $enabled")

        if (enabled) {
            try {
                val sessionId = exoPlayer.audioSessionId
                if (sessionId != 0) {
                    // Release existing enhancer if any
                    loudnessEnhancer?.release()

                    // Create new loudness enhancer
                    loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                        // Apply +3dB gain for quieter tracks (300 millibels = 3dB)
                        setTargetGain(300)
                        setEnabled(true)
                    }
                    Log.d(TAG, "LoudnessEnhancer created for session: $sessionId")
                } else {
                    Log.w(TAG, "Cannot create LoudnessEnhancer: invalid audio session ID")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create LoudnessEnhancer", e)
            }
        } else {
            try {
                loudnessEnhancer?.setEnabled(false)
                loudnessEnhancer?.release()
                loudnessEnhancer = null
                Log.d(TAG, "LoudnessEnhancer disabled and released")
            } catch (e: Exception) {
                Log.e(TAG, "Error disabling LoudnessEnhancer", e)
            }
        }
    }

    /**
     * Re-apply audio normalization after audio session changes (e.g., new track)
     * Call this when starting a new playback
     */
    fun reapplyAudioNormalization() {
        if (isNormalizeAudioEnabled) {
            setNormalizeAudio(true)
        }
    }

    fun release() {
        // Release loudness enhancer before releasing player
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing LoudnessEnhancer", e)
        }
        exoPlayer.release()
    }
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val currentVideoTitle: String? = null,
    val currentVideoUrl: String? = null,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val playbackSpeed: Float = 1.0f
)
