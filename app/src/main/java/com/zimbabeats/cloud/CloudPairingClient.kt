package com.zimbabeats.cloud

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Client for Firebase-based pairing with the ZimbaBeats Family parent app.
 *
 * This class handles:
 * - Validating and redeeming pairing codes
 * - Storing pairing information locally
 * - Syncing parental control settings from Firestore
 * - Updating device status (lastSeen, FCM token)
 */
class CloudPairingClient(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "CloudPairingClient"
        private const val PREFS_NAME = "zimbabeats_pairing"
        private const val KEY_PARENT_UID = "parent_uid"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CHILD_NAME = "child_name"
        private const val KEY_IS_PAIRED = "is_paired"
        private const val COLLECTION_PAIRING_CODES = "pairing_codes"
        private const val COLLECTION_FAMILIES = "families"
        private const val COLLECTION_DEVICES = "devices"
        private const val COLLECTION_SETTINGS = "settings"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _pairingStatus = MutableStateFlow(loadPairingStatus())
    val pairingStatus: StateFlow<PairingStatus> = _pairingStatus.asStateFlow()

    private val _cloudSettings = MutableStateFlow<CloudParentalSettings?>(null)
    val cloudSettings: StateFlow<CloudParentalSettings?> = _cloudSettings.asStateFlow()

    // Emits true when parent removes this device remotely
    private val _unlinkedByParent = MutableStateFlow(false)
    val unlinkedByParent: StateFlow<Boolean> = _unlinkedByParent.asStateFlow()

    private var settingsListener: ListenerRegistration? = null
    private var deviceListener: ListenerRegistration? = null

    // Premium Content Filter
    val contentFilter = CloudContentFilter(firestore = firestore)

    /**
     * Get the device ID, generating one if needed.
     */
    fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = generateDeviceId()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    /**
     * Get the device name for display.
     */
    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) model else "$manufacturer $model"
    }

    /**
     * Validate and redeem a pairing code.
     */
    suspend fun enterPairingCode(code: String, childName: String): PairingResult {
        val normalizedCode = code.uppercase().replace("-", "").trim()

        if (normalizedCode.length != 6) {
            return PairingResult.InvalidCode("Code must be 6 characters")
        }

        return try {
            _pairingStatus.value = PairingStatus.Pairing

            // Fetch the pairing code document
            val codeDoc = firestore.collection(COLLECTION_PAIRING_CODES)
                .document(normalizedCode)
                .get()
                .await()

            if (!codeDoc.exists()) {
                _pairingStatus.value = PairingStatus.Unpaired
                return PairingResult.InvalidCode("Code not found. Please check and try again.")
            }

            val expiresAt = codeDoc.getDate("expiresAt")
            val used = codeDoc.getBoolean("used") ?: true
            val parentUid = codeDoc.getString("parentUid")

            when {
                parentUid.isNullOrEmpty() -> {
                    _pairingStatus.value = PairingStatus.Unpaired
                    PairingResult.InvalidCode("Invalid code data")
                }
                used -> {
                    _pairingStatus.value = PairingStatus.Unpaired
                    PairingResult.InvalidCode("This code has already been used")
                }
                expiresAt != null && expiresAt.before(Date()) -> {
                    _pairingStatus.value = PairingStatus.Unpaired
                    PairingResult.InvalidCode("Code has expired. Please ask for a new code.")
                }
                else -> {
                    // Code is valid - link this device
                    linkDevice(parentUid, childName, normalizedCode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate pairing code", e)
            _pairingStatus.value = PairingStatus.Unpaired
            PairingResult.Error("Connection error: ${e.message}")
        }
    }

    /**
     * Link this device to the parent's family.
     */
    private suspend fun linkDevice(
        parentUid: String,
        childName: String,
        pairingCode: String
    ): PairingResult {
        return try {
            val deviceId = getDeviceId()
            val now = Date()

            // Create device document under parent's family
            val deviceData = hashMapOf(
                "deviceId" to deviceId,
                "deviceName" to getDeviceName(),
                "childName" to childName,
                "linkedAt" to now,
                "lastSeen" to now,
                "platform" to "android",
                "appVersion" to getAppVersion(),
                "fcmToken" to "" // Will be updated when FCM initializes
            )

            // Add device to parent's family
            firestore.collection(COLLECTION_FAMILIES)
                .document(parentUid)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .set(deviceData)
                .await()

            // Mark pairing code as used
            firestore.collection(COLLECTION_PAIRING_CODES)
                .document(pairingCode)
                .update(
                    mapOf(
                        "used" to true,
                        "usedBy" to deviceId,
                        "usedAt" to now
                    )
                )
                .await()

            // Save pairing info locally
            prefs.edit()
                .putString(KEY_PARENT_UID, parentUid)
                .putString(KEY_CHILD_NAME, childName)
                .putBoolean(KEY_IS_PAIRED, true)
                .apply()

            Log.d(TAG, "Device $deviceId successfully linked to parent $parentUid")

            _pairingStatus.value = PairingStatus.Paired(
                parentUid = parentUid,
                deviceId = deviceId,
                childName = childName
            )

            // Start listening for settings
            startSettingsSync()

            PairingResult.Success(parentUid, deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to link device", e)
            _pairingStatus.value = PairingStatus.Unpaired
            PairingResult.Error("Failed to complete pairing: ${e.message}")
        }
    }

    /**
     * Start listening for parental control settings changes.
     */
    fun startSettingsSync() {
        val status = _pairingStatus.value
        if (status !is PairingStatus.Paired) {
            Log.d(TAG, "Cannot start settings sync - not paired")
            return
        }

        stopSettingsSync()

        // Initialize content filter with pairing info
        contentFilter.initialize(status.parentUid, status.deviceId, status.childName)

        // Listen to this device's document to detect if parent removes it
        deviceListener = firestore.collection(COLLECTION_FAMILIES)
            .document(status.parentUid)
            .collection(COLLECTION_DEVICES)
            .document(status.deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for device status", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.exists()) {
                    // Device document was deleted by parent - we've been unlinked!
                    Log.w(TAG, "Device was removed by parent - unlinking locally")
                    // Use Handler to defer unlink handling to avoid deadlock
                    // (can't remove listener from within listener callback)
                    Handler(Looper.getMainLooper()).post {
                        handleRemoteUnlink()
                    }
                }
            }

        // Listen to the parent's settings document
        settingsListener = firestore.collection(COLLECTION_FAMILIES)
            .document(status.parentUid)
            .collection(COLLECTION_SETTINGS)
            .document("parental_controls")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for settings", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val settings = CloudParentalSettings(
                            isEnabled = snapshot.getBoolean("isEnabled") ?: false,
                            ageRating = snapshot.getString("ageRating") ?: "ALL",
                            screenTimeLimitMinutes = (snapshot.getLong("screenTimeLimitMinutes") ?: 0).toInt(),
                            bedtimeStart = snapshot.getString("bedtimeStart"),
                            bedtimeEnd = snapshot.getString("bedtimeEnd"),
                            bedtimeEnabled = snapshot.getBoolean("bedtimeEnabled") ?: false,
                            updatedAt = snapshot.getDate("updatedAt")
                        )
                        _cloudSettings.value = settings
                        Log.d(TAG, "Received settings update: $settings")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing settings", e)
                    }
                } else {
                    // No settings document - use defaults (unrestricted)
                    _cloudSettings.value = CloudParentalSettings(
                        isEnabled = false,
                        ageRating = "ALL",
                        screenTimeLimitMinutes = 0,
                        bedtimeStart = null,
                        bedtimeEnd = null,
                        bedtimeEnabled = false,
                        updatedAt = null
                    )
                }
            }
    }

    /**
     * Handle being unlinked remotely by parent.
     */
    private fun handleRemoteUnlink() {
        // Stop all listeners
        stopSettingsSync()

        // Clear local pairing data
        prefs.edit()
            .remove(KEY_PARENT_UID)
            .remove(KEY_CHILD_NAME)
            .putBoolean(KEY_IS_PAIRED, false)
            .apply()

        // Update status
        _pairingStatus.value = PairingStatus.Unpaired
        _cloudSettings.value = null
        _unlinkedByParent.value = true
        contentFilter.cleanup()

        Log.d(TAG, "Device unlinked remotely by parent")
    }

    /**
     * Clear the unlinked by parent flag (call after showing message to user).
     */
    fun clearUnlinkedFlag() {
        _unlinkedByParent.value = false
    }

    /**
     * Stop listening for settings updates.
     */
    fun stopSettingsSync() {
        settingsListener?.remove()
        settingsListener = null
        deviceListener?.remove()
        deviceListener = null
        contentFilter.stopListening()
    }

    /**
     * Update the device's last seen timestamp.
     */
    suspend fun updateLastSeen() {
        val status = _pairingStatus.value
        if (status !is PairingStatus.Paired) return

        try {
            firestore.collection(COLLECTION_FAMILIES)
                .document(status.parentUid)
                .collection(COLLECTION_DEVICES)
                .document(status.deviceId)
                .update("lastSeen", Date())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update last seen", e)
        }
    }

    /**
     * Update the device's FCM token.
     */
    suspend fun updateFcmToken(token: String) {
        val status = _pairingStatus.value
        if (status !is PairingStatus.Paired) return

        try {
            firestore.collection(COLLECTION_FAMILIES)
                .document(status.parentUid)
                .collection(COLLECTION_DEVICES)
                .document(status.deviceId)
                .update("fcmToken", token)
                .await()
            Log.d(TAG, "FCM token updated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update FCM token", e)
        }
    }

    /**
     * Unpair this device (for testing/reset).
     */
    suspend fun unpair() {
        val status = _pairingStatus.value
        if (status is PairingStatus.Paired) {
            try {
                // Remove device from Firestore
                firestore.collection(COLLECTION_FAMILIES)
                    .document(status.parentUid)
                    .collection(COLLECTION_DEVICES)
                    .document(status.deviceId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove device from cloud", e)
            }
        }

        // Clear local state
        stopSettingsSync()
        prefs.edit()
            .remove(KEY_PARENT_UID)
            .remove(KEY_CHILD_NAME)
            .putBoolean(KEY_IS_PAIRED, false)
            .apply()

        _pairingStatus.value = PairingStatus.Unpaired
        _cloudSettings.value = null
        contentFilter.cleanup()
        Log.d(TAG, "Device unpaired")
    }

    /**
     * Check if device is paired.
     */
    fun isPaired(): Boolean = _pairingStatus.value is PairingStatus.Paired

    /**
     * Report a tampering attempt to the parent (e.g., trying to disable device admin).
     */
    suspend fun reportTamperingAttempt(message: String) {
        val status = _pairingStatus.value
        if (status !is PairingStatus.Paired) return

        try {
            val alertData = hashMapOf(
                "type" to "tampering",
                "message" to message,
                "deviceId" to status.deviceId,
                "childName" to status.childName,
                "timestamp" to Date()
            )

            firestore.collection(COLLECTION_FAMILIES)
                .document(status.parentUid)
                .collection("alerts")
                .add(alertData)
                .await()

            // Also update device status
            firestore.collection(COLLECTION_FAMILIES)
                .document(status.parentUid)
                .collection(COLLECTION_DEVICES)
                .document(status.deviceId)
                .update(
                    mapOf(
                        "lastTamperingAttempt" to Date(),
                        "tamperingMessage" to message
                    )
                )
                .await()

            Log.w(TAG, "Tampering attempt reported: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report tampering attempt", e)
        }
    }

    /**
     * Load pairing status from shared preferences.
     */
    private fun loadPairingStatus(): PairingStatus {
        val isPaired = prefs.getBoolean(KEY_IS_PAIRED, false)
        if (!isPaired) return PairingStatus.Unpaired

        val parentUid = prefs.getString(KEY_PARENT_UID, null)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
        val childName = prefs.getString(KEY_CHILD_NAME, null)

        return if (parentUid != null && deviceId != null && childName != null) {
            PairingStatus.Paired(parentUid, deviceId, childName)
        } else {
            PairingStatus.Unpaired
        }
    }

    /**
     * Generate a unique device ID.
     */
    private fun generateDeviceId(): String {
        // Try to use Android ID first
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (androidId != null && androidId != "9774d56d682e549c") {
            "android_$androidId"
        } else {
            // Fallback to random UUID
            "device_${UUID.randomUUID()}"
        }
    }

    /**
     * Get the app version.
     */
    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}

/**
 * Status of device pairing.
 */
sealed class PairingStatus {
    object Unpaired : PairingStatus()
    object Pairing : PairingStatus()
    data class Paired(
        val parentUid: String,
        val deviceId: String,
        val childName: String
    ) : PairingStatus()
}

/**
 * Result of a pairing attempt.
 */
sealed class PairingResult {
    data class Success(val parentUid: String, val deviceId: String) : PairingResult()
    data class InvalidCode(val reason: String) : PairingResult()
    data class Error(val message: String) : PairingResult()
}

/**
 * Parental control settings synced from the cloud.
 */
data class CloudParentalSettings(
    val isEnabled: Boolean,
    val ageRating: String,
    val screenTimeLimitMinutes: Int,
    val bedtimeStart: String?,
    val bedtimeEnd: String?,
    val bedtimeEnabled: Boolean,
    val updatedAt: Date?
)
