package com.zimbabeats.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.PairingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Device Administrator receiver for uninstall protection.
 *
 * When activated as a device administrator:
 * - The app cannot be uninstalled without first disabling admin
 * - Notifies parent when child attempts to disable protection
 * - Provides additional security for parental controls
 */
class ZimbaBeatsDeviceAdmin : DeviceAdminReceiver(), KoinComponent {

    companion object {
        private const val TAG = "ZimbaBeatsDeviceAdmin"
        private const val PREFS_NAME = "device_admin_prefs"
        private const val KEY_DISABLE_ATTEMPTS = "disable_attempts"
        private const val KEY_LAST_DISABLE_ATTEMPT = "last_disable_attempt"

        /**
         * Get the ComponentName for this receiver.
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, ZimbaBeatsDeviceAdmin::class.java)
        }
    }

    private val pairingClient: CloudPairingClient by inject()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device admin enabled")
        Toast.makeText(context, "Uninstall protection enabled", Toast.LENGTH_SHORT).show()

        // Reset disable attempts counter
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DISABLE_ATTEMPTS, 0)
            .apply()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device admin was disabled!")
        Toast.makeText(context, "Warning: Uninstall protection disabled", Toast.LENGTH_LONG).show()

        // Notify parent that protection was disabled
        notifyParentOfTampering(context, "Uninstall protection was disabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Device admin disable requested")

        // Track disable attempts
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val attempts = prefs.getInt(KEY_DISABLE_ATTEMPTS, 0) + 1
        prefs.edit()
            .putInt(KEY_DISABLE_ATTEMPTS, attempts)
            .putLong(KEY_LAST_DISABLE_ATTEMPT, System.currentTimeMillis())
            .apply()

        // Notify parent of the attempt
        notifyParentOfTampering(context, "Child attempted to disable uninstall protection (attempt #$attempts)")

        // Show warning message
        return "WARNING: Your parent will be notified if you disable this protection.\n\n" +
               "This app is managed by your parent for your safety. " +
               "Disabling protection requires your parent's permission."
    }

    private fun notifyParentOfTampering(context: Context, message: String) {
        scope.launch {
            try {
                val status = pairingClient.pairingStatus.value
                if (status is PairingStatus.Paired) {
                    // Update Firestore with tampering alert
                    pairingClient.reportTamperingAttempt(message)
                    Log.d(TAG, "Parent notified of tampering: $message")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify parent", e)
            }
        }
    }
}
