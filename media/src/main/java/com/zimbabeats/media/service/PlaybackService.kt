package com.zimbabeats.media.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.zimbabeats.media.R
import com.zimbabeats.media.auto.AutoContentProvider
import com.zimbabeats.media.datasource.QueueStateHolder
import com.zimbabeats.media.notification.PlaybackNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import org.koin.android.ext.android.inject

/**
 * Background playback service for ZimbaBeats with Android Auto support.
 * Handles media playback and content browsing for car infotainment systems.
 *
 * NOTE: Using single-track playback approach where MusicPlaybackManager
 * handles queue navigation. This service just plays whatever MediaItem
 * is set by the controller.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    companion object {
        private const val TAG = "PlaybackService"
    }

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var forwardingPlayer: ForwardingPlayer
    private lateinit var notificationManager: PlaybackNotificationManager
    private val autoContentProvider: AutoContentProvider by inject()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

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
                    Log.d(TAG, "ForwardingPlayer: seekToNext via QueueNavigator")
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
                    Log.d(TAG, "ForwardingPlayer: seekToPrevious via QueueNavigator")
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

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    notificationManager.showNotification()
                }
            }
        })

        Log.d(TAG, "Player initialized with ForwardingPlayer for queue navigation")
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

        // Use MediaLibrarySession for Android Auto content browsing
        mediaSession = MediaLibrarySession.Builder(this, forwardingPlayer, LibrarySessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
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
     * Callback for handling Android Auto media browsing requests.
     */
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            Log.d(TAG, "onGetLibraryRoot called by ${browser.packageName}")

            val rootItem = MediaItem.Builder()
                .setMediaId(AutoContentProvider.ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("ZimbaBeats")
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()

            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            Log.d(TAG, "onGetChildren called for parentId: $parentId")

            return serviceScope.future {
                try {
                    val items = when (parentId) {
                        AutoContentProvider.ROOT_ID -> autoContentProvider.getRootChildren()
                        AutoContentProvider.FAVORITES_ID -> autoContentProvider.getFavorites()
                        AutoContentProvider.RECENT_ID -> autoContentProvider.getRecent()
                        AutoContentProvider.PLAYLISTS_ID -> autoContentProvider.getPlaylists()
                        else -> {
                            // Check if it's a playlist ID (PLAYLIST_123)
                            if (parentId.startsWith(AutoContentProvider.PLAYLIST_PREFIX)) {
                                val playlistId = parentId
                                    .removePrefix(AutoContentProvider.PLAYLIST_PREFIX)
                                    .toLongOrNull()
                                if (playlistId != null) {
                                    autoContentProvider.getPlaylistTracks(playlistId)
                                } else {
                                    emptyList()
                                }
                            } else {
                                emptyList()
                            }
                        }
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting children for $parentId", e)
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            Log.d(TAG, "onGetItem called for mediaId: $mediaId")
            // Return empty for now - playback is handled by app's MusicPlaybackManager
            return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }
}
