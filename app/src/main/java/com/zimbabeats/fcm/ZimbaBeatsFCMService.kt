package com.zimbabeats.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.PairingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Firebase Cloud Messaging service for receiving push notifications from parent app.
 *
 * Handles:
 * - Settings update notifications (triggers immediate sync)
 * - Screen time warnings
 * - Bedtime reminders
 * - Remote lock commands
 */
class ZimbaBeatsFCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "ZimbaBeatsFCMService"
        private const val CHANNEL_ID_PARENTAL = "parental_control"
        private const val CHANNEL_NAME = "Parental Controls"
        private const val NOTIFICATION_ID_SETTINGS = 1001
        private const val NOTIFICATION_ID_SCREEN_TIME = 1002
        private const val NOTIFICATION_ID_BEDTIME = 1003
        private const val NOTIFICATION_ID_LOCK = 1004

        // Action types from parent app
        const val ACTION_SETTINGS_UPDATED = "settings_updated"
        const val ACTION_SCREEN_TIME_WARNING = "screen_time_warning"
        const val ACTION_BEDTIME_WARNING = "bedtime_warning"
        const val ACTION_REMOTE_LOCK = "remote_lock"
        const val ACTION_SYNC_NOW = "sync_now"
    }

    private val pairingClient: CloudPairingClient by inject()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Called when a new FCM token is generated.
     * This happens on first app start and when token is refreshed.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")

        // Update token in Firestore if device is paired
        serviceScope.launch {
            try {
                pairingClient.updateFcmToken(token)
                Log.d(TAG, "FCM token updated in Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token", e)
            }
        }
    }

    /**
     * Called when a message is received from FCM.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        // Check if device is still paired
        val pairingStatus = pairingClient.pairingStatus.value
        if (pairingStatus !is PairingStatus.Paired) {
            Log.w(TAG, "Received FCM message but device is not paired")
            return
        }

        // Handle data payload
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            Log.d(TAG, "Message data: $data")
            handleDataMessage(data)
        }

        // Handle notification payload (for display when app is in background)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Message notification: ${notification.title} - ${notification.body}")
            showNotification(
                title = notification.title ?: "ZimbaBeats",
                body = notification.body ?: "",
                notificationId = NOTIFICATION_ID_SETTINGS
            )
        }
    }

    /**
     * Handle data messages from parent app.
     */
    private fun handleDataMessage(data: Map<String, String>) {
        val action = data["action"] ?: return

        when (action) {
            ACTION_SETTINGS_UPDATED -> {
                Log.d(TAG, "Settings updated notification received")
                // Trigger immediate settings sync
                serviceScope.launch {
                    pairingClient.startSettingsSync()
                }
                // Optionally show notification
                val message = data["message"]
                if (!message.isNullOrEmpty()) {
                    showNotification(
                        title = "Settings Updated",
                        body = message,
                        notificationId = NOTIFICATION_ID_SETTINGS
                    )
                }
            }

            ACTION_SCREEN_TIME_WARNING -> {
                val remainingMinutes = data["remaining_minutes"]?.toIntOrNull() ?: 0
                Log.d(TAG, "Screen time warning: $remainingMinutes minutes remaining")
                showNotification(
                    title = "Screen Time Warning",
                    body = "You have $remainingMinutes minutes of screen time left today.",
                    notificationId = NOTIFICATION_ID_SCREEN_TIME
                )
            }

            ACTION_BEDTIME_WARNING -> {
                val bedtimeStart = data["bedtime_start"] ?: "soon"
                Log.d(TAG, "Bedtime warning: bedtime starts at $bedtimeStart")
                showNotification(
                    title = "Bedtime Reminder",
                    body = "Bedtime starts at $bedtimeStart. Time to wrap up!",
                    notificationId = NOTIFICATION_ID_BEDTIME
                )
            }

            ACTION_REMOTE_LOCK -> {
                Log.d(TAG, "Remote lock command received")
                // Trigger settings sync to get updated lock state
                serviceScope.launch {
                    pairingClient.startSettingsSync()
                }
                showNotification(
                    title = "App Locked",
                    body = "Your parent has temporarily locked this app.",
                    notificationId = NOTIFICATION_ID_LOCK
                )
                // Broadcast to close any playing content
                sendBroadcast(Intent("com.zimbabeats.ACTION_REMOTE_LOCK"))
            }

            ACTION_SYNC_NOW -> {
                Log.d(TAG, "Sync now command received")
                serviceScope.launch {
                    pairingClient.startSettingsSync()
                    pairingClient.updateLastSeen()
                }
            }

            else -> {
                Log.w(TAG, "Unknown action: $action")
            }
        }
    }

    /**
     * Show a notification to the user.
     */
    private fun showNotification(
        title: String,
        body: String,
        notificationId: Int
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create intent to open app when notification is tapped
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_PARENTAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Replace with app icon
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_PARENTAL,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from parental controls"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
