package com.zimbabeats.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zimbabeats.BuildConfig
import com.zimbabeats.bridge.ParentalControlBridge
import com.zimbabeats.cloud.CloudPairingClient
import com.zimbabeats.cloud.PairingResult
import com.zimbabeats.data.AccentColor
import com.zimbabeats.data.AccessibilityMode
import com.zimbabeats.data.AppPreferences
import com.zimbabeats.data.DownloadNetworkPreference
import com.zimbabeats.data.ThemeMode
import com.zimbabeats.media.music.MusicPlaybackManager
import com.zimbabeats.update.ApkDownloader
import com.zimbabeats.update.DownloadState
import com.zimbabeats.update.UpdateChecker
import com.zimbabeats.update.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val preferredQuality: String = "Auto",
    val downloadNetworkPreference: DownloadNetworkPreference = DownloadNetworkPreference.WIFI_ONLY,
    val parentalControlsEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColor = AccentColor.GREEN,
    val accessibilityMode: AccessibilityMode = AccessibilityMode.AUTO,
    val accessibilityOptimizationsEnabled: Boolean = false,
    val cacheSize: Long = 0L,
    val appVersion: String = BuildConfig.VERSION_NAME,
    // Audio Settings
    val normalizeAudioEnabled: Boolean = false,
    // Media Storage
    val playbackBufferSize: Long = 0L,
    val playbackBufferLimit: Long = AppPreferences.DEFAULT_PLAYBACK_BUFFER_LIMIT,
    val savedMediaSize: Long = 0L,
    val savedMediaLimit: Long = AppPreferences.DEFAULT_SAVED_MEDIA_LIMIT,
    val imageCacheSize: Long = 0L,
    val imageCacheLimit: Long = AppPreferences.DEFAULT_IMAGE_CACHE_LIMIT,
    // Update Settings
    val autoUpdateCheckEnabled: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val isCheckingForUpdate: Boolean = false,
    val updateResult: UpdateResult? = null,
    val author: String = BuildConfig.AUTHOR,
    // In-app download state
    val downloadState: DownloadState = DownloadState.Idle,
    val isDownloading: Boolean = false,
    // Family cloud sync state
    val isFamilyLinked: Boolean = false,
    val isLinkingFamily: Boolean = false,
    val familyLinkingError: String? = null
)

class SettingsViewModel(
    private val application: Application,
    private val parentalControlBridge: ParentalControlBridge,
    private val appPreferences: AppPreferences,
    private val musicPlaybackManager: MusicPlaybackManager,
    private val cloudPairingClient: CloudPairingClient
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val updateChecker = UpdateChecker(application)
    private val apkDownloader = ApkDownloader(application)

    // Cache directories
    private val playbackBufferDir: File get() = File(application.cacheDir, "exo_cache")
    private val savedMediaDir: File get() = File(application.filesDir, "downloads")
    private val imageCacheDir: File get() = File(application.cacheDir, "image_cache")

    // 24 hours in milliseconds
    private val updateCheckCooldown = 24 * 60 * 60 * 1000L

    init {
        observePreferences()
        observeFamilyLinkState()
        observeUpdatePreferences()
        calculateCacheSize()
        calculateMediaStorageSizes()
        checkForAutoUpdate()
    }

    private fun observePreferences() {
        // Observe all preferences as flows for immediate updates
        viewModelScope.launch {
            appPreferences.preferredQualityFlow.collect { quality ->
                _uiState.value = _uiState.value.copy(preferredQuality = quality)
            }
        }

        viewModelScope.launch {
            appPreferences.themeModeFlow.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }

        viewModelScope.launch {
            appPreferences.accentColorFlow.collect { color ->
                _uiState.value = _uiState.value.copy(accentColor = color)
            }
        }

        viewModelScope.launch {
            appPreferences.downloadNetworkFlow.collect { pref ->
                _uiState.value = _uiState.value.copy(downloadNetworkPreference = pref)
            }
        }

        viewModelScope.launch {
            // Observe parental controls status from bridge (cloud or local)
            parentalControlBridge.restrictionState.collect { restrictionState ->
                // Check both cloud and local companion for parental controls
                val isActive = parentalControlBridge.isParentalControlsActive()
                _uiState.value = _uiState.value.copy(
                    parentalControlsEnabled = isActive && restrictionState.isEnabled
                )
            }
        }

        viewModelScope.launch {
            appPreferences.accessibilityModeFlow.collect { mode ->
                _uiState.value = _uiState.value.copy(accessibilityMode = mode)
            }
        }

        viewModelScope.launch {
            appPreferences.accessibilityOptimizationsEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(accessibilityOptimizationsEnabled = enabled)
            }
        }

        // Audio Settings
        viewModelScope.launch {
            appPreferences.normalizeAudioFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(normalizeAudioEnabled = enabled)
                // Apply to player
                musicPlaybackManager.setNormalizeAudio(enabled)
            }
        }

        // Media Storage Limits
        viewModelScope.launch {
            appPreferences.playbackBufferLimitFlow.collect { limit ->
                _uiState.value = _uiState.value.copy(playbackBufferLimit = limit)
            }
        }

        viewModelScope.launch {
            appPreferences.savedMediaLimitFlow.collect { limit ->
                _uiState.value = _uiState.value.copy(savedMediaLimit = limit)
            }
        }

        viewModelScope.launch {
            appPreferences.imageCacheLimitFlow.collect { limit ->
                _uiState.value = _uiState.value.copy(imageCacheLimit = limit)
            }
        }
    }

    private fun calculateMediaStorageSizes() {
        viewModelScope.launch {
            val (playbackSize, savedSize, imageSize) = withContext(Dispatchers.IO) {
                Triple(
                    if (playbackBufferDir.exists()) calculateDirectorySize(playbackBufferDir) else 0L,
                    if (savedMediaDir.exists()) calculateDirectorySize(savedMediaDir) else 0L,
                    if (imageCacheDir.exists()) calculateDirectorySize(imageCacheDir) else 0L
                )
            }

            _uiState.value = _uiState.value.copy(
                playbackBufferSize = playbackSize,
                savedMediaSize = savedSize,
                imageCacheSize = imageSize
            )
        }
    }

    private fun calculateCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                val cacheDir = application.cacheDir
                calculateDirectorySize(cacheDir)
            }
            _uiState.value = _uiState.value.copy(cacheSize = size)
        }
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    fun setPreferredQuality(quality: String) {
        appPreferences.setPreferredQuality(quality)
    }

    fun setDownloadNetworkPreference(preference: DownloadNetworkPreference) {
        appPreferences.setDownloadNetwork(preference)
    }

    fun setThemeMode(mode: ThemeMode) {
        appPreferences.setThemeMode(mode)
    }

    fun setAccentColor(color: AccentColor) {
        appPreferences.setAccentColor(color)
    }

    fun setAccessibilityMode(mode: AccessibilityMode) {
        appPreferences.setAccessibilityMode(mode)
    }

    fun clearCache() {
        viewModelScope.launch {
            application.cacheDir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
            calculateCacheSize()
        }
    }

    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes B"
        }
    }

    // ==================== Audio Settings ====================

    fun setNormalizeAudio(enabled: Boolean) {
        appPreferences.setNormalizeAudio(enabled)
    }

    /**
     * Opens the system equalizer for the current audio session.
     * Returns an Intent that should be started by the Activity.
     * Now works even without active music playback by using session 0 (global).
     */
    fun getEqualizerIntent(): Intent {
        val audioSessionId = musicPlaybackManager.getAudioSessionId()
        // Use the actual session ID if available, otherwise use 0 (global equalizer)
        val sessionToUse = if (audioSessionId > 0) audioSessionId else 0

        return Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionToUse)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, application.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
    }

    /**
     * Check if music is currently playing (for UI hints)
     */
    fun isMusicPlaying(): Boolean {
        return musicPlaybackManager.getAudioSessionId() > 0
    }

    // ==================== Media Storage ====================

    fun setPlaybackBufferLimit(bytes: Long) {
        appPreferences.setPlaybackBufferLimit(bytes)
    }

    fun setSavedMediaLimit(bytes: Long) {
        appPreferences.setSavedMediaLimit(bytes)
    }

    fun setImageCacheLimit(bytes: Long) {
        appPreferences.setImageCacheLimit(bytes)
    }

    fun clearPlaybackBuffer() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (playbackBufferDir.exists()) {
                        playbackBufferDir.listFiles()?.forEach { it.deleteRecursively() }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "Failed to clear playback buffer", e)
                }
            }
            calculateMediaStorageSizes()
            Toast.makeText(application, "Playback buffer cleared", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearSavedMedia() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (savedMediaDir.exists()) {
                        savedMediaDir.listFiles()?.forEach { it.deleteRecursively() }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "Failed to clear saved media", e)
                }
            }
            calculateMediaStorageSizes()
            Toast.makeText(application, "Saved media cleared", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (imageCacheDir.exists()) {
                        imageCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "Failed to clear image cache", e)
                }
            }
            calculateMediaStorageSizes()
            Toast.makeText(application, "Image cache cleared", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshStorageSizes() {
        calculateMediaStorageSizes()
        calculateCacheSize()
    }

    // ==================== Update Settings ====================

    private fun observeUpdatePreferences() {
        viewModelScope.launch {
            appPreferences.autoUpdateCheckFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(autoUpdateCheckEnabled = enabled)
            }
        }

        viewModelScope.launch {
            appPreferences.lastUpdateCheckFlow.collect { timestamp ->
                _uiState.value = _uiState.value.copy(lastUpdateCheck = timestamp)
            }
        }
    }

    private fun checkForAutoUpdate() {
        viewModelScope.launch {
            val autoCheckEnabled = appPreferences.isAutoUpdateCheckEnabled()
            val lastCheck = appPreferences.getLastUpdateCheck()
            val now = System.currentTimeMillis()

            // Only auto-check if enabled and cooldown has passed
            if (autoCheckEnabled && (now - lastCheck) > updateCheckCooldown) {
                checkForUpdate()
            }
        }
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        appPreferences.setAutoUpdateCheck(enabled)
        // If enabling auto-update check, reset the cooldown so it triggers on next app launch
        if (enabled) {
            appPreferences.setLastUpdateCheck(0L)
            android.util.Log.d("SettingsViewModel", "Auto-update check enabled - cooldown reset")
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingForUpdate = true)

            val result = updateChecker.checkForUpdate()

            // Update last check timestamp
            val now = System.currentTimeMillis()
            appPreferences.setLastUpdateCheck(now)

            _uiState.value = _uiState.value.copy(
                isCheckingForUpdate = false,
                updateResult = result,
                lastUpdateCheck = now
            )

            // Show toast based on result
            when (result) {
                is UpdateResult.UpdateAvailable -> {
                    Toast.makeText(
                        application,
                        "Update available: v${result.version}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is UpdateResult.NoUpdate -> {
                    Toast.makeText(
                        application,
                        "You're running the latest version",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is UpdateResult.Error -> {
                    Toast.makeText(
                        application,
                        "Update check failed: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun openUpdateDownloadPage() {
        val result = _uiState.value.updateResult
        if (result is UpdateResult.UpdateAvailable) {
            updateChecker.openDownloadPage(result.url)
        }
    }

    /**
     * Download APK in-app (bypasses browser restrictions)
     */
    fun downloadUpdate() {
        val result = _uiState.value.updateResult
        if (result !is UpdateResult.UpdateAvailable) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloading = true)

            apkDownloader.downloadApk(result.url, result.version).collect { state ->
                _uiState.value = _uiState.value.copy(downloadState = state)

                when (state) {
                    is DownloadState.Completed -> {
                        _uiState.value = _uiState.value.copy(isDownloading = false)
                        Toast.makeText(application, "Download complete! Installing...", Toast.LENGTH_SHORT).show()
                        // Auto-trigger install
                        apkDownloader.installApkFromDownloads(result.version)
                    }
                    is DownloadState.Failed -> {
                        _uiState.value = _uiState.value.copy(isDownloading = false)
                        Toast.makeText(application, "Download failed: ${state.error}", Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Cancel ongoing download
     */
    fun cancelDownload() {
        apkDownloader.cancelDownload()
        _uiState.value = _uiState.value.copy(
            isDownloading = false,
            downloadState = DownloadState.Idle
        )
    }

    /**
     * Install downloaded APK manually
     */
    fun installDownloadedUpdate() {
        val result = _uiState.value.updateResult
        if (result is UpdateResult.UpdateAvailable) {
            apkDownloader.installApkFromDownloads(result.version)
        }
    }

    fun openBuyMeCoffee() {
        updateChecker.openBuyMeCoffee()
    }

    fun clearUpdateResult() {
        _uiState.value = _uiState.value.copy(updateResult = null)
    }

    fun formatLastUpdateCheck(timestamp: Long): String {
        if (timestamp == 0L) return "Never"

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
            else -> {
                val date = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.getDefault())
                date.format(java.util.Date(timestamp))
            }
        }
    }

    // ==================== Family Cloud Sync ====================

    private fun observeFamilyLinkState() {
        _uiState.value = _uiState.value.copy(isFamilyLinked = cloudPairingClient.isPaired())
    }

    fun linkToFamily(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLinkingFamily = true, familyLinkingError = null)

            val result = cloudPairingClient.enterPairingCode(code, "Child Device")

            when (result) {
                is PairingResult.Success -> {
                    cloudPairingClient.startSettingsSync()
                    _uiState.value = _uiState.value.copy(
                        isLinkingFamily = false,
                        isFamilyLinked = true,
                        familyLinkingError = null
                    )
                    Toast.makeText(application, "Successfully linked to family", Toast.LENGTH_SHORT).show()
                }
                is PairingResult.InvalidCode -> {
                    _uiState.value = _uiState.value.copy(
                        isLinkingFamily = false,
                        familyLinkingError = result.reason
                    )
                }
                is PairingResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLinkingFamily = false,
                        familyLinkingError = result.message
                    )
                }
            }
        }
    }


    fun clearFamilyLinkError() {
        _uiState.value = _uiState.value.copy(familyLinkingError = null)
    }
}
