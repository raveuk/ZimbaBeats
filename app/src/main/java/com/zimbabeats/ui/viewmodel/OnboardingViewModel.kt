package com.zimbabeats.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.PairingResult
import com.zimbabeats.cloud.PairingStatus
import com.zimbabeats.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentStep: Int = 0,
    val totalSteps: Int = 2,  // Welcome, Family Code (optional)
    // Family linking state
    val isLinking: Boolean = false,
    val linkingError: String? = null,
    val isLinked: Boolean = false,
    // General state
    val isCompleting: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for Onboarding flow.
 *
 * Two-step onboarding:
 * 1. Welcome screen
 * 2. Optional Family Code entry (can skip to add later in Settings)
 */
class OnboardingViewModel(
    private val appPreferences: AppPreferences,
    private val cloudPairingClient: CloudPairingClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState(
        isLinked = cloudPairingClient.isPaired()
    ))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "OnboardingViewModel"
        const val STEP_WELCOME = 0
        const val STEP_FAMILY_CODE = 1
    }

    fun nextStep() {
        val current = _uiState.value.currentStep
        Log.d(TAG, "nextStep: current=$current, totalSteps=${_uiState.value.totalSteps}")
        if (current < _uiState.value.totalSteps - 1) {
            val newStep = current + 1
            Log.d(TAG, "nextStep: advancing to step $newStep")
            _uiState.value = _uiState.value.copy(currentStep = newStep)
        } else {
            Log.d(TAG, "nextStep: already at last step, not advancing")
        }
    }

    fun previousStep() {
        val current = _uiState.value.currentStep
        if (current > 0) {
            _uiState.value = _uiState.value.copy(currentStep = current - 1)
        }
    }

    /**
     * Link device to family using a 6-digit code
     */
    fun linkWithFamily(code: String, childName: String = "Child's Device") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLinking = true,
                linkingError = null
            )

            val result = cloudPairingClient.enterPairingCode(code, childName)

            when (result) {
                is PairingResult.Success -> {
                    Log.d(TAG, "Successfully linked to family")
                    _uiState.value = _uiState.value.copy(
                        isLinking = false,
                        isLinked = true,
                        linkingError = null
                    )
                }
                is PairingResult.InvalidCode -> {
                    Log.w(TAG, "Invalid code: ${result.reason}")
                    _uiState.value = _uiState.value.copy(
                        isLinking = false,
                        linkingError = result.reason
                    )
                }
                is PairingResult.Error -> {
                    Log.e(TAG, "Linking error: ${result.message}")
                    _uiState.value = _uiState.value.copy(
                        isLinking = false,
                        linkingError = result.message
                    )
                }
            }
        }
    }

    /**
     * Complete onboarding and proceed to main app
     */
    fun completeOnboarding(onComplete: () -> Unit) {
        Log.d(TAG, "completeOnboarding: starting")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCompleting = true)

            try {
                // Mark first launch complete
                appPreferences.setFirstLaunchComplete()

                // If linked, ensure sync is started
                if (cloudPairingClient.isPaired()) {
                    cloudPairingClient.startSettingsSync()
                }

                _uiState.value = _uiState.value.copy(
                    isCompleting = false,
                    isComplete = true
                )
                Log.d(TAG, "completeOnboarding: calling onComplete callback")
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "completeOnboarding: exception", e)
                _uiState.value = _uiState.value.copy(
                    isCompleting = false,
                    error = "An error occurred: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, linkingError = null)
    }
}
