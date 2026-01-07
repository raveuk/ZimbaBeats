package com.zimbabeats.media.controller

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.media.queue.PlaybackQueue
import com.zimbabeats.media.queue.RepeatMode
import com.zimbabeats.media.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages connection to PlaybackService and provides unified playback control
 */
class MediaControllerManager(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    val playbackQueue = PlaybackQueue()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    /**
     * Connect to the PlaybackService
     */
    fun connect() {
        if (controllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                controller = controllerFuture?.get()
                _isConnected.value = true
                setupPlayerListener()
            },
            MoreExecutors.directExecutor()
        )
    }

    /**
     * Disconnect from the service
     */
    fun disconnect() {
        controller?.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        _isConnected.value = false
    }

    private fun setupPlayerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updatePlaybackState()
            }
        })
    }

    private fun updatePlaybackState() {
        val player = controller ?: return
        _playbackState.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentPosition = player.currentPosition,
            duration = player.duration,
            bufferedPosition = player.bufferedPosition
        )
    }

    // Playback Control Methods

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun seekToNext() {
        if (playbackQueue.skipToNext()) {
            val nextItem = playbackQueue.getCurrentItem()
            nextItem?.let {
                // Load next video
                controller?.seekToNextMediaItem()
            }
        }
    }

    fun seekToPrevious() {
        if (playbackQueue.skipToPrevious()) {
            controller?.seekToPreviousMediaItem()
        }
    }

    // Queue Management

    fun playVideo(video: Video, playWhenReady: Boolean = true) {
        playbackQueue.setQueue(listOf(video), 0)
        setMediaItemsFromQueue()
        controller?.playWhenReady = playWhenReady
        controller?.prepare()
    }

    fun playVideos(videos: List<Video>, startIndex: Int = 0, playWhenReady: Boolean = true) {
        playbackQueue.setQueue(videos, startIndex)
        setMediaItemsFromQueue()
        controller?.seekTo(startIndex, 0)
        controller?.playWhenReady = playWhenReady
        controller?.prepare()
    }

    fun addToQueue(video: Video) {
        playbackQueue.addToQueue(video)
        setMediaItemsFromQueue()
    }

    fun playNext(video: Video) {
        playbackQueue.playNext(video)
        setMediaItemsFromQueue()
    }

    fun removeFromQueue(index: Int) {
        playbackQueue.removeFromQueue(index)
        setMediaItemsFromQueue()
    }

    fun clearQueue() {
        playbackQueue.clearQueue()
        controller?.clearMediaItems()
    }

    fun toggleShuffle() {
        playbackQueue.toggleShuffle()
        controller?.shuffleModeEnabled = playbackQueue.shuffleEnabled.value
    }

    fun cycleRepeatMode() {
        playbackQueue.cycleRepeatMode()
        val repeatMode = when (playbackQueue.repeatMode.value) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        controller?.repeatMode = repeatMode
    }

    private fun setMediaItemsFromQueue() {
        val mediaItems = playbackQueue.toMediaItems()
        controller?.setMediaItems(mediaItems)
    }

    fun getController(): MediaController? = controller

    fun getCurrentPosition(): Long = controller?.currentPosition ?: 0L

    fun getDuration(): Long = controller?.duration ?: 0L

    fun isPlaying(): Boolean = controller?.isPlaying ?: false
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L
)
