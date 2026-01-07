package com.zimbabeats.bridge

import android.os.RemoteException
import android.util.Log
import com.zimbabeats.family.ipc.IParentalControlCallback
import com.zimbabeats.family.ipc.RestrictionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Handles callbacks from the Parental Control companion app service.
 */
class CallbackHandler : IParentalControlCallback.Stub() {

    companion object {
        private const val TAG = "CallbackHandler"
    }

    // Event flows for different callback types
    private val _screenTimeWarningFlow = MutableSharedFlow<Int>(replay = 0)
    val screenTimeWarningFlow: SharedFlow<Int> = _screenTimeWarningFlow.asSharedFlow()

    private val _screenTimeLimitReachedFlow = MutableSharedFlow<ScreenTimeLimitEvent>(replay = 0)
    val screenTimeLimitReachedFlow: SharedFlow<ScreenTimeLimitEvent> = _screenTimeLimitReachedFlow.asSharedFlow()

    private val _screenTimeUnlockedFlow = MutableSharedFlow<Int>(replay = 0)
    val screenTimeUnlockedFlow: SharedFlow<Int> = _screenTimeUnlockedFlow.asSharedFlow()

    private val _bedtimeStartedFlow = MutableSharedFlow<String>(replay = 0)
    val bedtimeStartedFlow: SharedFlow<String> = _bedtimeStartedFlow.asSharedFlow()

    private val _bedtimeEndedFlow = MutableSharedFlow<Unit>(replay = 0)
    val bedtimeEndedFlow: SharedFlow<Unit> = _bedtimeEndedFlow.asSharedFlow()

    private val _bedtimeOverriddenFlow = MutableSharedFlow<Int>(replay = 0)
    val bedtimeOverriddenFlow: SharedFlow<Int> = _bedtimeOverriddenFlow.asSharedFlow()

    private val _contentBlockedFlow = MutableSharedFlow<ContentBlockedEvent>(replay = 0)
    val contentBlockedFlow: SharedFlow<ContentBlockedEvent> = _contentBlockedFlow.asSharedFlow()

    private val _settingsChangedFlow = MutableSharedFlow<RestrictionState>(replay = 1)
    val settingsChangedFlow: SharedFlow<RestrictionState> = _settingsChangedFlow.asSharedFlow()

    private val _parentalControlToggledFlow = MutableSharedFlow<Boolean>(replay = 0)
    val parentalControlToggledFlow: SharedFlow<Boolean> = _parentalControlToggledFlow.asSharedFlow()

    private val _unlockResultFlow = MutableSharedFlow<UnlockResultEvent>(replay = 0)
    val unlockResultFlow: SharedFlow<UnlockResultEvent> = _unlockResultFlow.asSharedFlow()

    @Throws(RemoteException::class)
    override fun onScreenTimeWarning(remainingMinutes: Int) {
        Log.d(TAG, "Screen time warning: $remainingMinutes minutes remaining")
        _screenTimeWarningFlow.tryEmit(remainingMinutes)
    }

    @Throws(RemoteException::class)
    override fun onScreenTimeLimitReached(usedMinutes: Int, limitMinutes: Int) {
        Log.d(TAG, "Screen time limit reached: $usedMinutes / $limitMinutes minutes")
        _screenTimeLimitReachedFlow.tryEmit(ScreenTimeLimitEvent(usedMinutes, limitMinutes))
    }

    @Throws(RemoteException::class)
    override fun onScreenTimeUnlocked(additionalMinutes: Int) {
        Log.d(TAG, "Screen time unlocked: $additionalMinutes additional minutes")
        _screenTimeUnlockedFlow.tryEmit(additionalMinutes)
    }

    @Throws(RemoteException::class)
    override fun onBedtimeStarted(endTime: String) {
        Log.d(TAG, "Bedtime started, ends at: $endTime")
        _bedtimeStartedFlow.tryEmit(endTime)
    }

    @Throws(RemoteException::class)
    override fun onBedtimeEnded() {
        Log.d(TAG, "Bedtime ended")
        _bedtimeEndedFlow.tryEmit(Unit)
    }

    @Throws(RemoteException::class)
    override fun onBedtimeOverridden(overrideMinutes: Int) {
        Log.d(TAG, "Bedtime overridden for $overrideMinutes minutes")
        _bedtimeOverriddenFlow.tryEmit(overrideMinutes)
    }

    @Throws(RemoteException::class)
    override fun onContentBlocked(contentId: String, reason: String, blockType: Int) {
        Log.d(TAG, "Content blocked: $contentId, reason: $reason, type: $blockType")
        _contentBlockedFlow.tryEmit(ContentBlockedEvent(contentId, reason, blockType))
    }

    @Throws(RemoteException::class)
    override fun onSettingsChanged(newState: RestrictionState) {
        Log.d(TAG, "Settings changed: enabled=${newState.isEnabled}")
        _settingsChangedFlow.tryEmit(newState)
    }

    @Throws(RemoteException::class)
    override fun onParentalControlToggled(enabled: Boolean) {
        Log.d(TAG, "Parental control toggled: $enabled")
        _parentalControlToggledFlow.tryEmit(enabled)
    }

    @Throws(RemoteException::class)
    override fun onUnlockResult(success: Boolean, unlockType: Int, additionalMinutes: Int) {
        Log.d(TAG, "Unlock result: success=$success, type=$unlockType, minutes=$additionalMinutes")
        _unlockResultFlow.tryEmit(UnlockResultEvent(success, unlockType, additionalMinutes))
    }

    // Event data classes
    data class ScreenTimeLimitEvent(val usedMinutes: Int, val limitMinutes: Int)
    data class ContentBlockedEvent(val contentId: String, val reason: String, val blockType: Int)
    data class UnlockResultEvent(val success: Boolean, val unlockType: Int, val additionalMinutes: Int)
}
