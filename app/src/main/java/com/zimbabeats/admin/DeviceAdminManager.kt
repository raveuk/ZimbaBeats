package com.zimbabeats.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Device Administrator functionality for uninstall protection.
 *
 * When device admin is enabled:
 * - App cannot be uninstalled without first disabling admin
 * - User must enter device PIN/password to disable admin
 * - Parent can require this during setup for added security
 */
class DeviceAdminManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceAdminManager"
        const val REQUEST_CODE_ENABLE_ADMIN = 1001
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val componentName = ZimbaBeatsDeviceAdmin.getComponentName(context)

    private val _isAdminActive = MutableStateFlow(false)
    val isAdminActive: StateFlow<Boolean> = _isAdminActive.asStateFlow()

    init {
        updateAdminStatus()
    }

    /**
     * Check if device admin is currently active.
     */
    fun checkAdminStatus(): Boolean {
        val active = devicePolicyManager.isAdminActive(componentName)
        _isAdminActive.value = active
        return active
    }

    /**
     * Update the admin status StateFlow.
     */
    fun updateAdminStatus() {
        _isAdminActive.value = devicePolicyManager.isAdminActive(componentName)
        Log.d(TAG, "Device admin active: ${_isAdminActive.value}")
    }

    /**
     * Create an intent to request device admin activation.
     * The calling Activity should launch this with startActivityForResult.
     */
    fun createActivationIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable uninstall protection to prevent this app from being removed " +
                "without parent permission. This helps keep parental controls secure."
            )
        }
    }

    /**
     * Request device admin activation from an Activity.
     */
    fun requestAdminActivation(activity: Activity) {
        if (!checkAdminStatus()) {
            val intent = createActivationIntent()
            activity.startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
        }
    }

    /**
     * Create an intent to open device admin settings.
     * Use this to allow user to disable admin (requires device PIN).
     */
    fun createDisableIntent(): Intent {
        return Intent().apply {
            component = android.content.ComponentName(
                "com.android.settings",
                "com.android.settings.DeviceAdminSettings"
            )
        }
    }

    /**
     * Programmatically remove device admin.
     * Note: This works without user confirmation, so should only be called
     * after parent PIN verification.
     */
    fun removeAdmin() {
        if (checkAdminStatus()) {
            devicePolicyManager.removeActiveAdmin(componentName)
            _isAdminActive.value = false
            Log.d(TAG, "Device admin removed")
        }
    }

    /**
     * Lock the device immediately.
     * Requires device admin to be active.
     */
    fun lockDevice() {
        if (checkAdminStatus()) {
            devicePolicyManager.lockNow()
            Log.d(TAG, "Device locked")
        }
    }
}
