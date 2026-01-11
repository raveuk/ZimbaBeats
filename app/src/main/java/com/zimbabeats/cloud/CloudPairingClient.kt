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
        private const val KEY_CHILD_ID = "child_id"  // NEW: Links to specific child profile
        private const val KEY_IS_PAIRED = "is_paired"
        private const val COLLECTION_PAIRING_CODES = "pairing_codes"
        private const val COLLECTION_FAMILIES = "families"
        private const val COLLECTION_DEVICES = "devices"
        private const val COLLECTION_SETTINGS = "settings"
        private const val COLLECTION_CHILDREN = "children"  // NEW: Per-child settings collection
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

    // Premium Content Filter (for videos)
    val contentFilter = CloudContentFilter(firestore = firestore)

    // Premium Music Filter (SEPARATE whitelist-only filter for music)
    val musicFilter = CloudMusicFilter(firestore = firestore)

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
            val childIdFromCode = codeDoc.getString("childId")  // NEW: Get child ID from pairing code
            val childNameFromCode = codeDoc.getString("childName")  // NEW: Get pre-set child name

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
                    // Use child name from code if available, otherwise use provided name
                    val finalChildName = childNameFromCode ?: childName
                    linkDevice(parentUid, finalChildName, normalizedCode, childIdFromCode)
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
     *
     * @param parentUid Parent's Firebase UID
     * @param childName Child's display name
     * @param pairingCode The pairing code used
     * @param childId Optional child profile ID for per-child settings
     */
    private suspend fun linkDevice(
        parentUid: String,
        childName: String,
        pairingCode: String,
        childId: String? = null
    ): PairingResult {
        return try {
            val deviceId = getDeviceId()
            val now = Date()

            // Create device document under parent's family
            // Only include non-null fields to avoid Firestore permission issues
            val deviceData = hashMapOf<String, Any>(
                "deviceId" to deviceId,
                "deviceName" to getDeviceName(),
                "childName" to childName,
                "linkedAt" to now,
                "lastSeen" to now,
                "platform" to "android",
                "appVersion" to getAppVersion(),
                "fcmToken" to "" // Will be updated when FCM initializes
            )
            // Only add childId if it's not null
            if (childId != null) {
                deviceData["childId"] = childId
            }

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

            // Save pairing info locally (including childId)
            prefs.edit()
                .putString(KEY_PARENT_UID, parentUid)
                .putString(KEY_CHILD_NAME, childName)
                .putString(KEY_CHILD_ID, childId)  // NEW: Store child ID
                .putBoolean(KEY_IS_PAIRED, true)
                .apply()

            Log.d(TAG, "Device $deviceId successfully linked to parent $parentUid (childId=$childId)")

            _pairingStatus.value = PairingStatus.Paired(
                parentUid = parentUid,
                deviceId = deviceId,
                childName = childName,
                childId = childId  // NEW: Include child ID in status
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
     * If device has a childId, uses per-child settings path.
     */
    fun startSettingsSync() {
        val status = _pairingStatus.value
        if (status !is PairingStatus.Paired) {
            Log.d(TAG, "Cannot start settings sync - not paired")
            return
        }

        stopSettingsSync()

        // Initialize content filter with pairing info (including childId for per-child settings)
        contentFilter.initialize(status.parentUid, status.deviceId, status.childName, status.childId)

        // Initialize music filter (SEPARATE whitelist-only filter, with childId for per-child settings)
        musicFilter.initialize(status.parentUid, status.deviceId, status.childName, status.childId)

        // Listen to this device's document to detect if parent removes it or changes childId
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
                } else if (snapshot != null && snapshot.exists()) {
                    // Check if childId was changed (e.g., parent deleted child profile or reassigned device)
                    val newChildId = snapshot.getString("childId")
                    val currentChildId = status.childId

                    if (currentChildId != null && newChildId != currentChildId) {
                        // Child ID changed - could be reassigned to different child or set to null
                        Log.w(TAG, "Child ID changed from $currentChildId to $newChildId - updating local state")
                        Handler(Looper.getMainLooper()).post {
                            handleChildIdChange(status.parentUid, status.deviceId, snapshot.getString("childName") ?: "Child", newChildId)
                        }
                    }
                }
            }

        // Listen to the parent's settings document
        // Use per-child path if childId exists, otherwise use legacy family-level path
        val settingsPath = if (status.childId != null) {
            // Per-child settings: families/{uid}/children/{childId}/settings/
            firestore.collection(COLLECTION_FAMILIES)
                .document(status.parentUid)
                .collection(COLLECTION_CHILDREN)
                .document(status.childId)
                .collection(COLLECTION_SETTINGS)
        } else {
            // Legacy family-level settings: families/{uid}/settings/
            firestore.collection(COLLECTION_FAMILIES)
                .document(status.parentUid)
                .collection(COLLECTION_SETTINGS)
        }

        val pathInfo = if (status.childId != null) "child ${status.childId}" else "family (legacy)"
        Log.d(TAG, "Listening for parental controls at $pathInfo")

        settingsListener = settingsPath.document("parental_controls")
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
                            updatedAt = snapshot.getDate("updatedAt"),
                            pinHash = snapshot.getString("pinHash")
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
                        updatedAt = null,
                        pinHash = null
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

        // Clear local pairing data (including childId)
        prefs.edit()
            .remove(KEY_PARENT_UID)
            .remove(KEY_CHILD_NAME)
            .remove(KEY_CHILD_ID)  // NEW: Clear child ID
            .putBoolean(KEY_IS_PAIRED, false)
            .apply()

        // Update status
        _pairingStatus.value = PairingStatus.Unpaired
        _cloudSettings.value = null
        _unlinkedByParent.value = true
        contentFilter.cleanup()
        musicFilter.cleanup()

        Log.d(TAG, "Device unlinked remotely by parent")
    }

    /**
     * Handle when the child ID changes (e.g., parent deleted child profile or reassigned device).
     * This restarts settings sync with the new child ID.
     */
    private fun handleChildIdChange(parentUid: String, deviceId: String, childName: String, newChildId: String?) {
        Log.d(TAG, "Handling child ID change to: $newChildId")

        // Stop current listeners
        stopSettingsSync()

        // Cleanup content and music filters before reinitializing with new childId
        contentFilter.cleanup()
        musicFilter.cleanup()

        // Update local storage with new child info (use commit() for synchronous write)
        prefs.edit()
            .putString(KEY_CHILD_NAME, childName)
            .putString(KEY_CHILD_ID, newChildId)
            .commit()  // Synchronous to avoid race condition

        // Update pairing status
        _pairingStatus.value = PairingStatus.Paired(
            parentUid = parentUid,
            deviceId = deviceId,
            childName = childName,
            childId = newChildId
        )

        // If newChildId is null, the device is now "unassigned" - notify user
        if (newChildId == null) {
            Log.w(TAG, "Device is now unassigned (child profile was deleted)")
            _unlinkedByParent.value = true  // Show notification to user
        }

        // Restart settings sync with new child ID (or legacy path if null)
        startSettingsSync()
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
        musicFilter.stopListening()
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
            .remove(KEY_CHILD_ID)  // FIX: Clear child ID on unpair
            .putBoolean(KEY_IS_PAIRED, false)
            .apply()

        _pairingStatus.value = PairingStatus.Unpaired
        _cloudSettings.value = null
        contentFilter.cleanup()
        musicFilter.cleanup()
        Log.d(TAG, "Device unpaired")
    }

    /**
     * Check if device is paired.
     */
    fun isPaired(): Boolean = _pairingStatus.value is PairingStatus.Paired

    /**
     * Verify PIN against cloud-stored hash.
     * Returns true if PIN matches the stored hash.
     */
    fun verifyCloudPin(pin: String): Boolean {
        val storedHash = _cloudSettings.value?.pinHash
        if (storedHash.isNullOrEmpty()) {
            Log.w(TAG, "No PIN hash stored in cloud settings")
            return false
        }

        val inputHash = hashPin(pin)
        val isValid = storedHash == inputHash
        Log.d(TAG, "Cloud PIN verification: ${if (isValid) "SUCCESS" else "FAILED"}")
        return isValid
    }

    /**
     * Hash PIN using SHA-256 (same algorithm as Family app)
     */
    private fun hashPin(pin: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Sync watch history entry to Firebase for parent to view.
     * Called whenever a video is watched.
     */
    suspend fun syncWatchHistory(
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String,
        durationSeconds: Long
    ) {
        val status = _pairingStatus.value
        if (status !is PairingStatus.Paired) {
            Log.d(TAG, "Not paired - skipping watch history sync")
            return
        }

        try {
            val historyEntry = hashMapOf(
                "contentType" to "video",
                "videoId" to videoId,
                "title" to title,
                "channelName" to channelName,
                "thumbnailUrl" to thumbnailUrl,
                "durationSeconds" to durationSeconds,
                "watchedAt" to Date(),
                "deviceId" to status.deviceId,
                "childName" to status.childName,
                "wasBlocked" to false
            )

            firestore.collection(COLLECTION_FAMILIES)
                .document(status.parentUid)
                .collection("watch_history")
                .document("${status.deviceId}_${videoId}_${System.currentTimeMillis()}")
                .set(historyEntry)
                .await()

            Log.d(TAG, "Watch history synced: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync watch history", e)
        }
    }

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
        val childId = prefs.getString(KEY_CHILD_ID, null)  // NEW: Load child ID

        return if (parentUid != null && deviceId != null && childName != null) {
            PairingStatus.Paired(parentUid, deviceId, childName, childId)
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
        val childName: String,
        val childId: String? = null  // NEW: Links to specific child profile for per-child settings
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
    val updatedAt: Date?,
    val pinHash: String? = null  // SHA-256 hash of parent PIN for cloud verification
)
