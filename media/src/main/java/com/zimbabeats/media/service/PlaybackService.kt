package com.zimbabeats.media.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zimbabeats.media.R
import com.zimbabeats.media.notification.PlaybackNotificationManager

/**
 * Background playback service for MarelikayBeats
 * Handles media playback even when app is in background
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var notificationManager: PlaybackNotificationManager

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        initializeMediaSession()
        notificationManager = PlaybackNotificationManager(this, mediaSession!!)

        // Configure notification with custom small icon
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(PlaybackNotificationManager.CHANNEL_ID)
            .setChannelName(R.string.notification_channel_name)
            .setNotificationId(PlaybackNotificationManager.NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnected
            .setWakeMode(C.WAKE_MODE_LOCAL) // Keep device awake during playback
            .setSeekBackIncrementMs(10_000) // 10 seconds
            .setSeekForwardIncrementMs(10_000) // 10 seconds
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        // Auto-play next in queue
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    notificationManager.showNotification()
                }
            }
        })
    }

    private fun initializeMediaSession() {
        // Create session activity intent (opens app when notification clicked)
        val sessionActivityIntent = packageManager?.getLaunchIntentForPackage(packageName)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Stop playback when app is swiped away from recent apps
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        notificationManager.hideNotification()
        super.onDestroy()
    }

    /**
     * Custom callback for media session commands
     */
    private inner class MediaSessionCallback : MediaSession.Callback {
        // Handle custom commands if needed
        // For now, default Media3 behavior is sufficient
    }
}
