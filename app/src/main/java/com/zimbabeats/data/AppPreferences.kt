package com.zimbabeats.data

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Accessibility mode options
 */
enum class AccessibilityMode(val displayName: String) {
    AUTO("Auto (follow system)"),
    ALWAYS_ON("Always on"),
    ALWAYS_OFF("Always off")
}

// DataStore extension
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zimbabeats_settings")

/**
 * App-wide preferences storage using DataStore
 */
class AppPreferences(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        // Keys
        private val KEY_PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val KEY_DOWNLOAD_NETWORK = stringPreferencesKey("download_network")
        private val KEY_ACCESSIBILITY_MODE = stringPreferencesKey("accessibility_mode")

        // Onboarding Keys
        private val KEY_FIRST_LAUNCH_COMPLETE = booleanPreferencesKey("first_launch_complete")

        // Audio Settings Keys
        private val KEY_NORMALIZE_AUDIO = booleanPreferencesKey("normalize_audio")

        // Media Storage Keys
        private val KEY_PLAYBACK_BUFFER_LIMIT = longPreferencesKey("playback_buffer_limit")
        private val KEY_SAVED_MEDIA_LIMIT = longPreferencesKey("saved_media_limit")
        private val KEY_IMAGE_CACHE_LIMIT = longPreferencesKey("image_cache_limit")

        // Update Check Keys
        private val KEY_AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        private val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")

        // Remote Config update banner — tracks which target version the user has
        // already dismissed, so we don't keep nagging them on every launch. Stored as
        // Long because Remote Config exposes version codes as Long. We re-show the
        // banner whenever RC's latest_version_code becomes larger than this.
        private val KEY_DISMISSED_REMOTE_UPDATE_VERSION = longPreferencesKey("dismissed_remote_update_version")

        // YouTube Authentication Keys
        private val KEY_YOUTUBE_COOKIE = stringPreferencesKey("youtube_cookie")
        private val KEY_YOUTUBE_LOGGED_IN = booleanPreferencesKey("youtube_logged_in")
        private val KEY_YOUTUBE_ACCOUNT_NAME = stringPreferencesKey("youtube_account_name")
        private val KEY_YOUTUBE_ACCOUNT_EMAIL = stringPreferencesKey("youtube_account_email")

        // Quality options
        const val QUALITY_AUTO = "Auto"
        const val QUALITY_HIGH = "High (1080p)"
        const val QUALITY_MEDIUM = "Medium (720p)"
        const val QUALITY_LOW = "Low (480p)"

        // Quality values for matching
        const val QUALITY_VALUE_1080 = 1080
        const val QUALITY_VALUE_720 = 720
        const val QUALITY_VALUE_480 = 480

        // Default cache limits (in bytes)
        const val DEFAULT_PLAYBACK_BUFFER_LIMIT = 100L * 1024 * 1024  // 100 MB
        const val DEFAULT_SAVED_MEDIA_LIMIT = 1024L * 1024 * 1024     // 1 GB
        const val DEFAULT_IMAGE_CACHE_LIMIT = 200L * 1024 * 1024      // 200 MB
    }

    // Theme Mode Flow
    val themeModeFlow: StateFlow<ThemeMode> = context.dataStore.data
        .map { preferences ->
            val value = preferences[KEY_THEME_MODE] ?: ThemeMode.DARK.name
            try {
                ThemeMode.valueOf(value)
            } catch (e: IllegalArgumentException) {
                ThemeMode.DARK
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.DARK)

    // Accent Color Flow
    val accentColorFlow: StateFlow<AccentColor> = context.dataStore.data
        .map { preferences ->
            val value = preferences[KEY_ACCENT_COLOR] ?: AccentColor.GREEN.name
            try {
                AccentColor.valueOf(value)
            } catch (e: IllegalArgumentException) {
                AccentColor.GREEN
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AccentColor.GREEN)

    // Preferred Quality Flow
    val preferredQualityFlow: StateFlow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_PREFERRED_QUALITY] ?: QUALITY_AUTO
        }
        .stateIn(scope, SharingStarted.Eagerly, QUALITY_AUTO)

    // Download Network Preference Flow
    val downloadNetworkFlow: StateFlow<DownloadNetworkPreference> = context.dataStore.data
        .map { preferences ->
            val value = preferences[KEY_DOWNLOAD_NETWORK] ?: DownloadNetworkPreference.WIFI_ONLY.name
            try {
                DownloadNetworkPreference.valueOf(value)
            } catch (e: IllegalArgumentException) {
                DownloadNetworkPreference.WIFI_ONLY
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, DownloadNetworkPreference.WIFI_ONLY)

    // Accessibility Mode Flow
    val accessibilityModeFlow: StateFlow<AccessibilityMode> = context.dataStore.data
        .map { preferences ->
            val value = preferences[KEY_ACCESSIBILITY_MODE] ?: AccessibilityMode.AUTO.name
            try {
                AccessibilityMode.valueOf(value)
            } catch (e: IllegalArgumentException) {
                AccessibilityMode.AUTO
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AccessibilityMode.AUTO)

    // Track system TalkBack state
    private val _systemTalkBackEnabled = MutableStateFlow(isSystemTalkBackEnabled())

    /**
     * Effective accessibility optimizations enabled
     * Combines user preference with system TalkBack state
     */
    val accessibilityOptimizationsEnabled: StateFlow<Boolean> = combine(
        accessibilityModeFlow,
        _systemTalkBackEnabled
    ) { mode, systemEnabled ->
        when (mode) {
            AccessibilityMode.AUTO -> systemEnabled
            AccessibilityMode.ALWAYS_ON -> true
            AccessibilityMode.ALWAYS_OFF -> false
        }
    }.stateIn(scope, SharingStarted.Eagerly, isSystemTalkBackEnabled())

    // Audio Settings - Normalize Audio Flow
    val normalizeAudioFlow: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_NORMALIZE_AUDIO] ?: false
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // Media Storage - Playback Buffer Limit Flow
    val playbackBufferLimitFlow: StateFlow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_PLAYBACK_BUFFER_LIMIT] ?: DEFAULT_PLAYBACK_BUFFER_LIMIT
        }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_PLAYBACK_BUFFER_LIMIT)

    // Media Storage - Saved Media Limit Flow
    val savedMediaLimitFlow: StateFlow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SAVED_MEDIA_LIMIT] ?: DEFAULT_SAVED_MEDIA_LIMIT
        }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SAVED_MEDIA_LIMIT)

    // Media Storage - Image Cache Limit Flow
    val imageCacheLimitFlow: StateFlow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_IMAGE_CACHE_LIMIT] ?: DEFAULT_IMAGE_CACHE_LIMIT
        }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_IMAGE_CACHE_LIMIT)

    // First Launch Complete Flow
    val firstLaunchCompleteFlow: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_FIRST_LAUNCH_COMPLETE] ?: false
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // Auto Update Check Flow (default: true)
    val autoUpdateCheckFlow: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_AUTO_UPDATE_CHECK] ?: true
        }
        .stateIn(scope, SharingStarted.Eagerly, true)

    // Last Update Check Timestamp Flow
    val lastUpdateCheckFlow: StateFlow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_LAST_UPDATE_CHECK] ?: 0L
        }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    // Highest target version the user has dismissed in the Remote Config update banner.
    // 0 = never dismissed.
    val dismissedRemoteUpdateVersionFlow: StateFlow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_DISMISSED_REMOTE_UPDATE_VERSION] ?: 0L
        }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    fun setDismissedRemoteUpdateVersion(versionCode: Long) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_DISMISSED_REMOTE_UPDATE_VERSION] = versionCode
            }
        }
    }

    // YouTube Cookie Flow
    val youtubeCookieFlow: StateFlow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_YOUTUBE_COOKIE] ?: ""
        }
        .stateIn(scope, SharingStarted.Eagerly, "")

    // YouTube Logged In Flow
    val youtubeLoggedInFlow: StateFlow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_YOUTUBE_LOGGED_IN] ?: false
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    // YouTube Account Name Flow
    val youtubeAccountNameFlow: StateFlow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_YOUTUBE_ACCOUNT_NAME] ?: ""
        }
        .stateIn(scope, SharingStarted.Eagerly, "")

    // YouTube Account Email Flow
    val youtubeAccountEmailFlow: StateFlow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_YOUTUBE_ACCOUNT_EMAIL] ?: ""
        }
        .stateIn(scope, SharingStarted.Eagerly, "")

    init {
        // Listen for system accessibility changes
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        accessibilityManager?.addTouchExplorationStateChangeListener { enabled ->
            _systemTalkBackEnabled.value = enabled
        }
    }

    private fun isSystemTalkBackEnabled(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return accessibilityManager?.isTouchExplorationEnabled == true
    }

    // Setters
    fun setThemeMode(mode: ThemeMode) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_THEME_MODE] = mode.name
            }
        }
    }

    fun setAccentColor(color: AccentColor) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_ACCENT_COLOR] = color.name
            }
        }
    }

    fun setPreferredQuality(quality: String) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_PREFERRED_QUALITY] = quality
            }
        }
    }

    fun setDownloadNetwork(preference: DownloadNetworkPreference) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_DOWNLOAD_NETWORK] = preference.name
            }
        }
    }

    fun setAccessibilityMode(mode: AccessibilityMode) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_ACCESSIBILITY_MODE] = mode.name
            }
        }
    }

    /**
     * Get the current preferred quality setting
     */
    fun getPreferredQuality(): String = preferredQualityFlow.value

    /**
     * Get the maximum quality value based on preference
     * Returns null for Auto (use highest available)
     */
    fun getMaxQualityValue(): Int? {
        return when (preferredQualityFlow.value) {
            QUALITY_HIGH -> QUALITY_VALUE_1080
            QUALITY_MEDIUM -> QUALITY_VALUE_720
            QUALITY_LOW -> QUALITY_VALUE_480
            else -> null // Auto - use highest
        }
    }

    /**
     * Check if mobile data downloads are allowed
     */
    fun isMobileDataDownloadAllowed(): Boolean {
        return downloadNetworkFlow.value == DownloadNetworkPreference.WIFI_AND_MOBILE
    }

    // Audio Settings setters
    fun setNormalizeAudio(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_NORMALIZE_AUDIO] = enabled
            }
        }
    }

    // Media Storage setters
    fun setPlaybackBufferLimit(bytes: Long) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_PLAYBACK_BUFFER_LIMIT] = bytes
            }
        }
    }

    fun setSavedMediaLimit(bytes: Long) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_SAVED_MEDIA_LIMIT] = bytes
            }
        }
    }

    fun setImageCacheLimit(bytes: Long) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_IMAGE_CACHE_LIMIT] = bytes
            }
        }
    }

    // Onboarding setters
    suspend fun setFirstLaunchComplete() {
        context.dataStore.edit { preferences ->
            preferences[KEY_FIRST_LAUNCH_COMPLETE] = true
        }
    }

    fun isFirstLaunchComplete(): Boolean = firstLaunchCompleteFlow.value

    // Update Check setters
    fun setAutoUpdateCheck(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_AUTO_UPDATE_CHECK] = enabled
            }
        }
    }

    fun setLastUpdateCheck(timestamp: Long) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_LAST_UPDATE_CHECK] = timestamp
            }
        }
    }

    fun getLastUpdateCheck(): Long = lastUpdateCheckFlow.value
    fun isAutoUpdateCheckEnabled(): Boolean = autoUpdateCheckFlow.value

    // YouTube Authentication setters
    fun setYouTubeCookie(cookie: String) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_YOUTUBE_COOKIE] = cookie
            }
        }
    }

    fun setYouTubeLoggedIn(loggedIn: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_YOUTUBE_LOGGED_IN] = loggedIn
            }
        }
    }

    fun setYouTubeAccountName(name: String) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_YOUTUBE_ACCOUNT_NAME] = name
            }
        }
    }

    fun setYouTubeAccountEmail(email: String) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_YOUTUBE_ACCOUNT_EMAIL] = email
            }
        }
    }

    /**
     * Save YouTube account info after successful login
     */
    fun saveYouTubeAccount(cookie: String, name: String = "", email: String = "") {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_YOUTUBE_COOKIE] = cookie
                preferences[KEY_YOUTUBE_LOGGED_IN] = true
                preferences[KEY_YOUTUBE_ACCOUNT_NAME] = name
                preferences[KEY_YOUTUBE_ACCOUNT_EMAIL] = email
            }
        }
    }

    /**
     * Clear YouTube account data (logout)
     */
    fun clearYouTubeAccount() {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_YOUTUBE_COOKIE] = ""
                preferences[KEY_YOUTUBE_LOGGED_IN] = false
                preferences[KEY_YOUTUBE_ACCOUNT_NAME] = ""
                preferences[KEY_YOUTUBE_ACCOUNT_EMAIL] = ""
            }
        }
    }

    /**
     * Check if user is logged in to YouTube
     */
    fun isYouTubeLoggedIn(): Boolean = youtubeLoggedInFlow.value

    /**
     * Get YouTube cookie for API requests
     */
    fun getYouTubeCookie(): String = youtubeCookieFlow.value

    /**
     * Extract SAPISID from cookie string for authentication header
     */
    fun getSapisid(): String? {
        val cookie = youtubeCookieFlow.value
        if (cookie.isEmpty()) return null

        return cookie.split(";")
            .map { it.trim() }
            .find { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.split("=")
            ?.getOrNull(1)
    }
}
