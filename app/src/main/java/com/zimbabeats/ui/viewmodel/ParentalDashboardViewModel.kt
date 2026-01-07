package com.zimbabeats.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.bridge.ParentalControlBridge
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ParentalDashboardUiState(
    val isLoading: Boolean = false,
    val watchHistory: List<Video> = emptyList(),
    // Companion app status
    val companionAppInstalled: Boolean = false,
    val companionAppConnected: Boolean = false,
    // Screen time from companion app
    val screenTimeUsedToday: Int = 0,
    val screenTimeLimit: Int = 0,
    val screenTimeEnabled: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for Parental Dashboard screen.
 *
 * Parental control settings and blocking functionality are managed by the companion app.
 * This ViewModel displays watch history and current restriction status,
 * and provides functionality to open the companion app for configuration.
 */
class ParentalDashboardViewModel(
    private val videoRepository: VideoRepository,
    private val parentalControlBridge: ParentalControlBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentalDashboardUiState())
    val uiState: StateFlow<ParentalDashboardUiState> = _uiState.asStateFlow()

    init {
        loadWatchHistory()
        observeBridgeState()
    }

    private fun loadWatchHistory() {
        viewModelScope.launch {
            videoRepository.getWatchHistory(50).collect { history ->
                _uiState.value = _uiState.value.copy(watchHistory = history)
            }
        }
    }

    private fun observeBridgeState() {
        // Check if companion app is installed
        val isInstalled = parentalControlBridge.companionChecker.isCompanionAppInstalled()
        _uiState.value = _uiState.value.copy(companionAppInstalled = isInstalled)

        // Observe restriction state from companion app
        viewModelScope.launch {
            parentalControlBridge.restrictionState.collect { restrictionState ->
                val isConnected = parentalControlBridge.isCompanionActive()

                _uiState.value = _uiState.value.copy(
                    companionAppConnected = isConnected,
                    screenTimeUsedToday = restrictionState.screenTimeUsedMinutes,
                    screenTimeLimit = restrictionState.screenTimeLimitMinutes,
                    screenTimeEnabled = restrictionState.isEnabled && restrictionState.isScreenTimeLimitActive,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Open the companion app for parental control configuration.
     * Content blocking is now managed in the companion app.
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
            _uiState.value = _uiState.value.copy(
                error = "Parental Control app is not installed. Please install it to manage parental controls."
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
