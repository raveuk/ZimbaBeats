package com.zimbabeats.media.player

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton PlayerManager - holds single ExoPlayer instance for background playback
 * Similar to MarelikayBeats's approach
 */
@UnstableApi
object PlayerManager {

    private const val TAG = "PlayerManager"

    private var exoPlayer: ExoPlayer? = null
    private var applicationContext: Context? = null

    private val _playerState = MutableStateFlow(MiniPlayerState())
    val playerState: StateFlow<MiniPlayerState> = _playerState.asStateFlow()

    private val _isPlayerVisible = MutableStateFlow(false)
    val isPlayerVisible: StateFlow<Boolean> = _isPlayerVisible.asStateFlow()

    fun initialize(context: Context) {
        if (exoPlayer == null) {
            applicationContext = context.applicationContext

            // Custom LoadControl for better buffering on slow mobile networks
            // Based on SimpMusic reference implementation
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,   // Min buffer before playback starts (30 seconds)
                    60_000,   // Max buffer to maintain (60 seconds)
                    2_500,    // Buffer for playback to start (2.5 seconds)
                    5_000     // Buffer for rebuffering (5 seconds)
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(loadControl)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build()

            setupPlayerListener()
            Log.d(TAG, "PlayerManager initialized with mobile-optimized buffering")
        }
    }

    private fun setupPlayerListener() {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateState()
            }
        })
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        _playerState.value = _playerState.value.copy(
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            currentPosition = player.currentPosition,
            duration = player.duration.coerceAtLeast(0)
        )
    }

    fun getPlayer(): Player? = exoPlayer

    fun playVideo(
        videoId: String,
        videoUrl: String,
        title: String,
        channelName: String,
        thumbnailUrl: String
    ) {
        val player = exoPlayer ?: return
        val context = applicationContext ?: return

        Log.d(TAG, "Playing video: $title")

        // Update state immediately
        _playerState.value = MiniPlayerState(
            videoId = videoId,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            isPlaying = true,
            isBuffering = true
        )
        _isPlayerVisible.value = true

        // Create data source factory
        // Timeouts increased to 30s for slow mobile networks (based on SimpMusic)
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.youtube/21.03.36 (Linux; U; Android 14) gzip")
            .setDefaultRequestProperties(mapOf(
                "Accept-Language" to "en-US,en;q=0.9",
                "Accept" to "*/*",
                "Accept-Encoding" to "gzip, deflate",
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/"
            ))
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMediaId(videoId)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(channelName)
                    .build()
            )
            .build()

        val mediaSource: MediaSource = when {
            videoUrl.contains(".m3u8") || videoUrl.contains("manifest") -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
            else -> {
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
        }

        player.setMediaSource(mediaSource)
        player.playWhenReady = true
        player.prepare()
        player.play()
    }

    fun play() {
        exoPlayer?.play()
        updateState()
    }

    fun pause() {
        exoPlayer?.pause()
        updateState()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updateState()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun seekForward() {
        exoPlayer?.seekForward()
    }

    fun seekBackward() {
        exoPlayer?.seekBack()
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0

    fun getDuration(): Long = exoPlayer?.duration ?: 0

    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _isPlayerVisible.value = false
        _playerState.value = MiniPlayerState()
    }

    fun hideMiniPlayer() {
        _isPlayerVisible.value = false
    }

    fun showMiniPlayer() {
        if (_playerState.value.videoId != null) {
            _isPlayerVisible.value = true
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        _isPlayerVisible.value = false
        _playerState.value = MiniPlayerState()
        Log.d(TAG, "PlayerManager released")
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun hasActiveMedia(): Boolean = _playerState.value.videoId != null
}

data class MiniPlayerState(
    val videoId: String? = null,
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0
)
