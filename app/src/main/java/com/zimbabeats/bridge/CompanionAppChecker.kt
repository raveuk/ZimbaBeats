package com.zimbabeats.bridge

import android.content.Context
import android.content.pm.PackageManager

/**
 * Utility class to check if the Parental Control companion app is installed.
 */
class CompanionAppChecker(private val context: Context) {

    companion object {
        const val COMPANION_PACKAGE_NAME = "com.zimbabeats.family"
        const val COMPANION_SERVICE_ACTION = "com.zimbabeats.family.PARENTAL_CONTROL_SERVICE"
    }

    /**
     * Check if the companion app is installed on this device.
     */
    fun isCompanionInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(COMPANION_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Alias for isCompanionInstalled() for backward compatibility.
     */
    fun isCompanionAppInstalled(): Boolean = isCompanionInstalled()

    /**
     * Get the companion app's version code if installed.
     * @return Version code or -1 if not installed
     */
    fun getCompanionVersionCode(): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(COMPANION_PACKAGE_NAME, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            -1L
        }
    }

    /**
     * Get the companion app's version name if installed.
     * @return Version name or null if not installed
     */
    fun getCompanionVersionName(): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(COMPANION_PACKAGE_NAME, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Launch the companion app's main activity (for configuration).
     */
    fun launchCompanionApp(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(COMPANION_PACKAGE_NAME)
        return if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } else {
            false
        }
    }

    /**
     * Open Play Store to install the companion app.
     */
    fun openPlayStoreForCompanion(): Boolean {
        return try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=$COMPANION_PACKAGE_NAME")
            )
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Play Store not installed, try browser
            try {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=$COMPANION_PACKAGE_NAME")
                )
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}
