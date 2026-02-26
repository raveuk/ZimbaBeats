package com.zimbabeats.media.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zimbabeats.media.R
import com.zimbabeats.media.datasource.StreamResolvingDataSourceFactory
import com.zimbabeats.media.notification.PlaybackNotificationManager
import android.util.Log

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

        // Safely initialize notification manager only if mediaSession is available
        val session = mediaSession
        if (session != null) {
            notificationManager = PlaybackNotificationManager(this, session)

            // Configure notification with custom small icon
            val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(PlaybackNotificationManager.CHANNEL_ID)
                .setChannelName(R.string.notification_channel_name)
                .setNotificationId(PlaybackNotificationManager.NOTIFICATION_ID)
                .build()
            notificationProvider.setSmallIcon(R.drawable.ic_notification)
            setMediaNotificationProvider(notificationProvider)
        }
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Create base HTTP data source factory with YouTube TV/embedded client headers
        // TV client streams often bypass "n" parameter throttling
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version")
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/"
            ))
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)

        // Wrap with StreamResolvingDataSourceFactory for on-demand URL resolution
        // This allows loading full queue with just track IDs, resolving streams when playback starts
        val resolvingDataSourceFactory = StreamResolvingDataSourceFactory(httpDataSourceFactory)
        Log.d("PlaybackService", "Created StreamResolvingDataSourceFactory for on-demand URL resolution")

        val mediaSourceFactory = DefaultMediaSourceFactory(resolvingDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
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
