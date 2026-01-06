package com.zimbabeats.bridge

import android.content.Context
import android.os.RemoteException
import android.util.Log
import com.zimbabeats.family.ipc.ContentInfo
import com.zimbabeats.family.ipc.PlaybackVerdict
import com.zimbabeats.family.ipc.RestrictionState
import com.zimbabeats.family.ipc.UnlockResult
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.CloudParentalSettings
import com.zimbabeats.cloud.PairingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Bridge between ZimbaBeats and parental control systems.
 *
 * This class provides a unified interface for parental control functionality:
 * 1. Cloud-based: Firebase sync via CloudPairingClient (primary method)
 * 2. Local AIDL: Direct connection to companion app when installed on same device
 * 3. Unrestricted: When neither is available
 */
class ParentalControlBridge(
    context: Context,
    private val cloudPairingClient: CloudPairingClient? = null
) {

    companion object {
        private const val TAG = "ParentalControlBridge"
        private const val PROTOCOL_VERSION = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val companionChecker = CompanionAppChecker(context)
    private val connectionManager = ServiceConnectionManager(context, companionChecker)
    private val callbackHandler = CallbackHandler()

    private val _bridgeState = MutableStateFlow(BridgeState())
    val bridgeState: StateFlow<BridgeState> = _bridgeState.asStateFlow()

    private val _restrictionState = MutableStateFlow(RestrictionState.unrestricted())
    val restrictionState: StateFlow<RestrictionState> = _restrictionState.asStateFlow()

    // Track if cloud parental controls are active
    private val _isCloudControlsEnabled = MutableStateFlow(false)
    val isCloudControlsEnabled: StateFlow<Boolean> = _isCloudControlsEnabled.asStateFlow()

    init {
        // Observe connection state changes (local AIDL)
        connectionManager.connectionState.onEach { state ->
            updateBridgeState(state)
        }.launchIn(scope)

        // Observe settings changes from local companion app
        callbackHandler.settingsChangedFlow.onEach { newState ->
            // Only apply local settings if cloud is not active
            if (!_isCloudControlsEnabled.value) {
                _restrictionState.value = newState
            }
        }.launchIn(scope)

        // Observe cloud settings (takes priority over local)
        cloudPairingClient?.cloudSettings?.onEach { cloudSettings ->
            updateFromCloudSettings(cloudSettings)
        }?.launchIn(scope)

        // Observe cloud pairing status
        cloudPairingClient?.pairingStatus?.onEach { status ->
            Log.d(TAG, "Cloud pairing status: $status")
            if (status !is PairingStatus.Paired) {
                _isCloudControlsEnabled.value = false
            }
        }?.launchIn(scope)
    }

    /**
     * Update restriction state from cloud settings.
     */
    private fun updateFromCloudSettings(cloudSettings: CloudParentalSettings?) {
        if (cloudSettings == null) {
            _isCloudControlsEnabled.value = false
            return
        }

        Log.d(TAG, "Cloud settings received: isEnabled=${cloudSettings.isEnabled}, ageRating=${cloudSettings.ageRating}")

        _isCloudControlsEnabled.value = cloudSettings.isEnabled

        if (cloudSettings.isEnabled) {
            // Convert cloud settings to RestrictionState
            val isBedtime = checkBedtime(cloudSettings)
            val ageLevel = parseAgeRating(cloudSettings.ageRating)
            val restriction = RestrictionState(
                isEnabled = true,
                isScreenTimeLimitActive = cloudSettings.screenTimeLimitMinutes > 0,
                screenTimeUsedMinutes = 0, // Will be tracked separately
                screenTimeLimitMinutes = cloudSettings.screenTimeLimitMinutes,
                screenTimeRemainingMinutes = cloudSettings.screenTimeLimitMinutes,
                isBedtimeActive = cloudSettings.bedtimeEnabled,
                bedtimeStart = cloudSettings.bedtimeStart,
                bedtimeEnd = cloudSettings.bedtimeEnd,
                isBedtimeCurrentlyBlocking = isBedtime,
                selectedAgeLevel = ageLevel,
                searchAllowed = true, // Can be configured in cloud settings later
                downloadPinRequired = true
            )
            _restrictionState.value = restriction
            Log.d(TAG, "Applied cloud parental controls: $restriction")
        } else {
            // Cloud controls disabled - fall back to local companion or unrestricted
            if (!isCompanionActive()) {
                _restrictionState.value = RestrictionState.unrestricted()
            }
        }
    }

    /**
     * Check if current time is within bedtime hours.
     */
    private fun checkBedtime(settings: CloudParentalSettings): Boolean {
        if (!settings.bedtimeEnabled || settings.bedtimeStart == null || settings.bedtimeEnd == null) {
            return false
        }
        // Simple time check (can be enhanced with proper time parsing)
        try {
            val now = java.time.LocalTime.now()
            val start = java.time.LocalTime.parse(settings.bedtimeStart)
            val end = java.time.LocalTime.parse(settings.bedtimeEnd)

            return if (start.isBefore(end)) {
                now.isAfter(start) && now.isBefore(end)
            } else {
                // Overnight bedtime (e.g., 21:00 to 07:00)
                now.isAfter(start) || now.isBefore(end)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing bedtime", e)
            return false
        }
    }

    /**
     * Parse age rating string to age level constant.
     * Handles both Family app format (UNDER_X) and normalized format (X_PLUS)
     */
    private fun parseAgeRating(ageRating: String): Int {
        return when (ageRating.uppercase()) {
            "ALL", "G" -> RestrictionState.AGE_LEVEL_ALL
            // Family app format
            "UNDER_5", "5" -> RestrictionState.AGE_LEVEL_UNDER_5
            "UNDER_8", "8", "PG" -> RestrictionState.AGE_LEVEL_UNDER_8
            "UNDER_10", "10" -> RestrictionState.AGE_LEVEL_UNDER_10
            "UNDER_12", "12" -> RestrictionState.AGE_LEVEL_UNDER_12
            "UNDER_13", "13", "PG-13" -> RestrictionState.AGE_LEVEL_UNDER_13
            "UNDER_14", "14" -> RestrictionState.AGE_LEVEL_UNDER_14
            "UNDER_16", "16" -> RestrictionState.AGE_LEVEL_UNDER_16
            // Normalized format (from CloudPairingClient)
            "FIVE_PLUS", "5+" -> RestrictionState.AGE_LEVEL_UNDER_5
            "TEN_PLUS", "10+" -> RestrictionState.AGE_LEVEL_UNDER_10
            "TWELVE_PLUS", "12+" -> RestrictionState.AGE_LEVEL_UNDER_12
            "FOURTEEN_PLUS", "14+" -> RestrictionState.AGE_LEVEL_UNDER_14
            "SIXTEEN_PLUS", "16+" -> RestrictionState.AGE_LEVEL_UNDER_16
            else -> {
                Log.w(TAG, "Unknown age rating: $ageRating, defaulting to ALL")
                RestrictionState.AGE_LEVEL_ALL
            }
        }
    }

    // ========== LIFECYCLE ==========

    /**
     * Initialize the bridge. Call from Application.onCreate() or MainActivity.
     */
    fun initialize() {
        Log.d(TAG, "Initializing ParentalControlBridge")
        if (companionChecker.isCompanionInstalled()) {
            connectionManager.setCallback(callbackHandler)
            connectionManager.bind()
        } else {
            Log.d(TAG, "Companion app not installed, running in unrestricted mode")
            _bridgeState.value = _bridgeState.value.copy(
                mode = BridgeMode.UNRESTRICTED,
                companionInstalled = false
            )
        }
    }

    /**
     * Clean up resources. Call from Application.onTerminate() or when done.
     */
    fun shutdown() {
        Log.d(TAG, "Shutting down ParentalControlBridge")
        connectionManager.unbind()
    }

    /**
     * Notify that the app has become active (foreground).
     */
    fun onAppActive() {
        try {
            connectionManager.getService()?.onAppActive()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to notify app active", e)
        }
    }

    /**
     * Notify that the app is going to background.
     */
    fun onAppBackground() {
        try {
            connectionManager.getService()?.onAppBackground()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to notify app background", e)
        }
    }

    /**
     * Periodic tick for time-based checks. Call every minute.
     */
    fun tick() {
        try {
            connectionManager.getService()?.tick()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to tick", e)
        }
    }

    // ========== CONTENT GATING ==========

    /**
     * Check if content can be played.
     * Returns ALLOWED if companion is not installed or not connected.
     */
    fun canPlayContent(content: ContentInfo): PlaybackVerdict {
        Log.d(TAG, "canPlayContent: checking '${content.title}' (id=${content.contentId})")

        if (!isCompanionActive()) {
            Log.d(TAG, "canPlayContent: companion not active, ALLOWING")
            return PlaybackVerdict.allowed()
        }

        return try {
            val verdict = connectionManager.getService()?.canPlayContent(content) ?: PlaybackVerdict.allowed()
            Log.d(TAG, "canPlayContent: verdict=${if (verdict.allowed) "ALLOW" else "BLOCK"} reason=${verdict.blockMessage ?: "none"}")
            verdict
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to check content playback", e)
            PlaybackVerdict.allowed()
        }
    }

    /**
     * Notify that content playback has started.
     */
    fun onContentStarted(contentId: String, title: String) {
        try {
            connectionManager.getService()?.onContentStarted(contentId, title)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to notify content started", e)
        }
    }

    /**
     * Notify that content playback has stopped.
     */
    fun onContentStopped(contentId: String, watchedDurationSeconds: Long) {
        try {
            connectionManager.getService()?.onContentStopped(contentId, watchedDurationSeconds)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to notify content stopped", e)
        }
    }

    // ========== RESTRICTION STATE ==========

    /**
     * Get current restriction state.
     */
    fun getActiveRestrictions(): RestrictionState {
        if (!isCompanionActive()) {
            return RestrictionState.unrestricted()
        }

        return try {
            connectionManager.getService()?.activeRestrictions ?: RestrictionState.unrestricted()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get restrictions", e)
            RestrictionState.unrestricted()
        }
    }

    /**
     * Check if search is allowed.
     */
    fun isSearchAllowed(query: String): Boolean {
        if (!isCompanionActive()) {
            return true
        }

        return try {
            connectionManager.getService()?.isSearchAllowed(query) ?: true
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to check search allowed", e)
            true
        }
    }

    /**
     * Check if downloads require PIN.
     */
    fun isDownloadPinRequired(): Boolean {
        if (!isCompanionActive()) {
            return false
        }

        return try {
            connectionManager.getService()?.isDownloadPinRequired ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to check download PIN required", e)
            false
        }
    }

    // ========== PARENT UNLOCK ==========

    /**
     * Request parent unlock. Result delivered via callbackHandler.unlockResultFlow
     */
    fun requestParentUnlock(unlockType: Int) {
        try {
            connectionManager.getService()?.requestParentUnlock(unlockType)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to request unlock", e)
        }
    }

    /**
     * Verify PIN directly.
     */
    fun verifyPin(pin: String): UnlockResult {
        if (!isCompanionActive()) {
            return UnlockResult.failure()
        }

        return try {
            connectionManager.getService()?.verifyPin(pin) ?: UnlockResult.failure()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to verify PIN", e)
            UnlockResult.failure()
        }
    }

    // ========== UTILITY ==========

    /**
     * Check if local companion app is installed and connected.
     */
    fun isCompanionActive(): Boolean {
        return companionChecker.isCompanionInstalled() && connectionManager.isConnected()
    }

    /**
     * Check if any parental controls are active (cloud OR local).
     */
    fun isParentalControlsActive(): Boolean {
        return _isCloudControlsEnabled.value || isCompanionActive()
    }

    /**
     * Check if cloud-based parental controls are active.
     */
    fun isCloudActive(): Boolean {
        return cloudPairingClient?.isPaired() == true && _isCloudControlsEnabled.value
    }

    /**
     * Check if companion is installed (regardless of connection state).
     */
    fun isCompanionInstalled(): Boolean = companionChecker.isCompanionInstalled()

    /**
     * Launch the companion app for configuration.
     */
    fun launchCompanionApp(): Boolean = companionChecker.launchCompanionApp()

    /**
     * Open Play Store to install companion.
     */
    fun openPlayStoreForCompanion(): Boolean = companionChecker.openPlayStoreForCompanion()

    /**
     * Get callback handler for observing events.
     */
    fun getCallbackHandler(): CallbackHandler = callbackHandler

    /**
     * Attempt to reconnect to the companion app.
     */
    fun reconnect() {
        connectionManager.unbind()
        if (companionChecker.isCompanionInstalled()) {
            connectionManager.bind()
        }
    }

    private fun updateBridgeState(connectionState: ServiceConnectionManager.ConnectionState) {
        Log.d(TAG, "Connection state changed: $connectionState")

        val mode = when (connectionState) {
            ServiceConnectionManager.ConnectionState.CONNECTED -> BridgeMode.CONNECTED
            ServiceConnectionManager.ConnectionState.COMPANION_NOT_INSTALLED -> BridgeMode.UNRESTRICTED
            ServiceConnectionManager.ConnectionState.CONNECTING -> BridgeMode.CONNECTING
            else -> BridgeMode.DISCONNECTED
        }

        Log.d(TAG, "Bridge mode: $mode, companion installed: ${companionChecker.isCompanionInstalled()}")

        _bridgeState.value = BridgeState(
            mode = mode,
            companionInstalled = companionChecker.isCompanionInstalled(),
            connectionState = connectionState
        )

        // If connected, fetch initial restriction state
        if (mode == BridgeMode.CONNECTED) {
            try {
                connectionManager.getService()?.activeRestrictions?.let {
                    _restrictionState.value = it
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to fetch initial restrictions", e)
            }
        } else if (mode == BridgeMode.UNRESTRICTED) {
            _restrictionState.value = RestrictionState.unrestricted()
        }
    }
}

/**
 * Bridge operating mode
 */
enum class BridgeMode {
    UNRESTRICTED,    // Companion not installed, no restrictions
    CONNECTING,      // Attempting to connect to companion
    CONNECTED,       // Connected to companion service
    DISCONNECTED     // Was connected but lost connection
}

/**
 * Bridge state for UI observation
 */
data class BridgeState(
    val mode: BridgeMode = BridgeMode.UNRESTRICTED,
    val companionInstalled: Boolean = false,
    val connectionState: ServiceConnectionManager.ConnectionState = ServiceConnectionManager.ConnectionState.DISCONNECTED
) {
    val isConnected: Boolean get() = mode == BridgeMode.CONNECTED
    val isConnecting: Boolean get() = mode == BridgeMode.CONNECTING
}
