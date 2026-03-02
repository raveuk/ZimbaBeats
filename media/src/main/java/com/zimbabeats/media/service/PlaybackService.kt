package com.zimbabeats.media.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zimbabeats.media.R
import com.zimbabeats.media.notification.PlaybackNotificationManager
import com.zimbabeats.media.datasource.QueueStateHolder
import android.util.Log

/**
 * Background playback service for ZimbaBeats
 * Handles media playback even when app is in background.
 *
 * NOTE: Using single-track playback approach where MusicPlaybackManager
 * handles queue navigation. This service just plays whatever MediaItem
 * is set by the controller.
 *
 * Fix High #4: Removed conflicting auto-advance logic - MusicPlaybackManager handles this.
 * Fix Medium #9: Removed unused StreamResolvingDataSourceFactory.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var forwardingPlayer: ForwardingPlayer
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
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Simple HTTP data source - streams are pre-resolved by MusicPlaybackManager
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

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnected
            .setWakeMode(C.WAKE_MODE_LOCAL) // Keep device awake during playback
            .setSeekBackIncrementMs(10_000) // 10 seconds
            .setSeekForwardIncrementMs(10_000) // 10 seconds
            .build()

        // Create a ForwardingPlayer that reports next/previous availability from our queue
        // This allows the notification to show next/prev buttons even with single-track mode
        forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                val commands = super.getAvailableCommands()
                val navigator = QueueStateHolder.navigator

                // Build new commands with next/prev based on our queue state
                return Player.Commands.Builder()
                    .addAll(commands)
                    .apply {
                        if (navigator?.hasNext() == true) {
                            add(Player.COMMAND_SEEK_TO_NEXT)
                            add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        }
                        if (navigator?.hasPrevious() == true) {
                            add(Player.COMMAND_SEEK_TO_PREVIOUS)
                            add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        }
                    }
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                val navigator = QueueStateHolder.navigator
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                        navigator?.hasNext() == true || super.isCommandAvailable(command)
                    Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                        navigator?.hasPrevious() == true || super.isCommandAvailable(command)
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean {
                return QueueStateHolder.navigator?.hasNext() == true || super.hasNextMediaItem()
            }

            override fun hasPreviousMediaItem(): Boolean {
                return QueueStateHolder.navigator?.hasPrevious() == true || super.hasPreviousMediaItem()
            }

            override fun seekToNext() {
                val navigator = QueueStateHolder.navigator
                if (navigator?.hasNext() == true) {
                    Log.d("PlaybackService", "ForwardingPlayer: seekToNext via QueueNavigator")
                    navigator.skipToNext()
                } else {
                    super.seekToNext()
                }
            }

            override fun seekToPrevious() {
                val navigator = QueueStateHolder.navigator
                // If position > 3 seconds, seek to start instead of previous track
                if (currentPosition > 3000) {
                    seekTo(0)
                    return
                }
                if (navigator?.hasPrevious() == true) {
                    Log.d("PlaybackService", "ForwardingPlayer: seekToPrevious via QueueNavigator")
                    navigator.skipToPrevious()
                } else {
                    super.seekToPrevious()
                }
            }

            override fun seekToNextMediaItem() {
                seekToNext()
            }

            override fun seekToPreviousMediaItem() {
                seekToPrevious()
            }
        }

        // Fix High #4: Removed onPlaybackStateChanged auto-advance logic
        // MusicPlaybackManager.setupTrackCompletionListener() handles auto-advance
        // to avoid conflicting queue management
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    notificationManager.showNotification()
                }
            }
        })

        Log.d("PlaybackService", "Player initialized with ForwardingPlayer for queue navigation")
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

        // Use forwardingPlayer for the session so notification shows correct next/prev state
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
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
