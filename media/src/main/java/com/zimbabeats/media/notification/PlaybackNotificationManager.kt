package com.zimbabeats.media.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Manages playback notifications for background playback
 */
class PlaybackNotificationManager(
    private val context: Context,
    private val mediaSession: MediaSession
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "marelikaybeats_playback"
        const val CHANNEL_NAME = "MarelikayBeats Playback"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for video playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification() {
        // Media3 handles notification creation automatically
        // through MediaSessionService
    }

    fun hideNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
