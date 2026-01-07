package com.zimbabeats.media.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
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

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
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

        // Create data source factory with Android YouTube Music headers
        // This matches the ANDROID_MUSIC client used to get the stream URLs
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14) gzip")
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Accept-Encoding" to "identity;q=1, *;q=0",
                "Accept-Language" to "en-US,en;q=0.9"
            ))
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()

        // Detect stream type and use appropriate media source
        val mediaSource: MediaSource = when {
            videoUrl.contains(".m3u8") || videoUrl.contains("manifest") -> {
                Log.d(TAG, "Using HLS media source for: $videoUrl")
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            else -> {
                Log.d(TAG, "Using Progressive media source for: $videoUrl")
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
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
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14) gzip")
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Accept-Encoding" to "identity;q=1, *;q=0",
                "Accept-Language" to "en-US,en;q=0.9"
            ))
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
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
    val duration: Long = 0
)
