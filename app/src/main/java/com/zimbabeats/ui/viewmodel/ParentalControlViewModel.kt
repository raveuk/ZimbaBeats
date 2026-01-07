package com.zimbabeats.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.bridge.ParentalControlBridge
import com.zimbabeats.core.domain.model.AgeRating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ParentalControlUiState(
    // Companion app status
    val companionAppInstalled: Boolean = false,
    val companionAppConnected: Boolean = false,
    // Restriction state from companion app
    val isEnabled: Boolean = false,
    val selectedAgeLevel: AgeRating = AgeRating.ALL,
    val screenTimeRemainingMinutes: Int = 0,
    val screenTimeLimitMinutes: Int = 0,
    val isBedtimeActive: Boolean = false,
    val bedtimeStart: String? = null,
    val bedtimeEnd: String? = null,
    // UI state
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for Parental Control screen.
 *
 * Parental control settings are managed by the companion app (ZimbaBeats Family).
 * This ViewModel displays the current restriction state from the companion app
 * and provides functionality to open the companion app for configuration.
 */
class ParentalControlViewModel(
    private val parentalControlBridge: ParentalControlBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentalControlUiState())
    val uiState: StateFlow<ParentalControlUiState> = _uiState.asStateFlow()

    init {
        observeBridgeState()
    }

    private fun observeBridgeState() {
        // Check if companion app is installed
        val isInstalled = parentalControlBridge.companionChecker.isCompanionAppInstalled()
        _uiState.value = _uiState.value.copy(companionAppInstalled = isInstalled)

        // Observe restriction state from companion app
        viewModelScope.launch {
            parentalControlBridge.restrictionState.collect { restrictionState ->
                val isConnected = parentalControlBridge.isCompanionActive()
                val ageLevel = AgeRating.entries.getOrNull(restrictionState.selectedAgeLevel) ?: AgeRating.ALL

                _uiState.value = _uiState.value.copy(
                    companionAppConnected = isConnected,
                    isEnabled = restrictionState.isEnabled,
                    selectedAgeLevel = ageLevel,
                    screenTimeRemainingMinutes = restrictionState.screenTimeRemainingMinutes,
                    screenTimeLimitMinutes = restrictionState.screenTimeLimitMinutes,
                    isBedtimeActive = restrictionState.isBedtimeCurrentlyBlocking,
                    bedtimeStart = restrictionState.bedtimeStart,
                    bedtimeEnd = restrictionState.bedtimeEnd,
                    isLoading = false
                )
            }
        }

        // Observe bridge state for connection changes
        viewModelScope.launch {
            parentalControlBridge.bridgeState.collect { bridgeState ->
                _uiState.value = _uiState.value.copy(
                    companionAppConnected = bridgeState.isConnected,
                    isLoading = bridgeState.isConnecting
                )
            }
        }
    }

    /**
     * Open the companion app for parental control configuration.
     * If the companion app is not installed, this will open the Play Store.
     */
    fun openCompanionApp(context: Context) {
        val packageName = "com.zimbabeats.family"

        if (_uiState.value.companionAppInstalled) {
            // Launch companion app
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } else {
            // Open Play Store to install companion app
            // For now, we'll just show a message since the app is not on Play Store yet
            _uiState.value = _uiState.value.copy(
                error = "Parental Control app is not installed. Please install it to enable parental controls."
            )
        }
    }

    /**
     * Refresh connection to companion app
     */
    fun refreshConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Re-check if companion app is installed
            val isInstalled = parentalControlBridge.companionChecker.isCompanionAppInstalled()
            _uiState.value = _uiState.value.copy(
                companionAppInstalled = isInstalled,
                isLoading = false
            )

            // Re-initialize bridge connection if installed
            if (isInstalled) {
                parentalControlBridge.initialize()
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
