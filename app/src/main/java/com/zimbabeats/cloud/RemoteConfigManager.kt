package com.zimbabeats.cloud

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase Remote Config for global (developer-controlled) content filtering.
 * This layer sits on top of parent-controlled Firestore settings.
 */
class RemoteConfigManager {
    companion object {
        private const val TAG = "RemoteConfigManager"

        // Remote Config keys
        const val KEY_GLOBAL_BLOCKED_KEYWORDS = "global_blocked_keywords"
        const val KEY_GLOBAL_BLOCKED_CHANNELS = "global_blocked_channels"
        const val KEY_GLOBAL_BLOCKED_ARTISTS = "global_blocked_artists"
        const val KEY_MAINTENANCE_MODE = "maintenance_mode"
        const val KEY_MIN_APP_VERSION = "min_app_version"
        const val KEY_FEATURE_LYRICS_ENABLED = "feature_lyrics_enabled"

        // Fetch interval (1 hour for production)
        private const val FETCH_INTERVAL_SECONDS = 3600L
    }

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    private val _globalSettings = MutableStateFlow(GlobalFilterSettings())
    val globalSettings: StateFlow<GlobalFilterSettings> = _globalSettings.asStateFlow()

    init {
        setupRemoteConfig()
    }

    private fun setupRemoteConfig() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = FETCH_INTERVAL_SECONDS
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set defaults
        remoteConfig.setDefaultsAsync(mapOf(
            KEY_GLOBAL_BLOCKED_KEYWORDS to "",
            KEY_GLOBAL_BLOCKED_CHANNELS to "",
            KEY_GLOBAL_BLOCKED_ARTISTS to "",
            KEY_MAINTENANCE_MODE to false,
            KEY_MIN_APP_VERSION to "1.0.0",
            KEY_FEATURE_LYRICS_ENABLED to false
        ))

        // Load cached values immediately
        updateSettingsFromConfig()
    }

    /**
     * Fetch and activate latest remote config values
     */
    suspend fun fetchAndActivate(): Boolean {
        return try {
            val updated = remoteConfig.fetchAndActivate().await()
            updateSettingsFromConfig()
            Log.d(TAG, "Remote config fetched, updated: $updated")
            updated
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote config", e)
            false
        }
    }

    private fun updateSettingsFromConfig() {
        val settings = GlobalFilterSettings(
            blockedKeywords = parseCommaSeparatedList(
                remoteConfig.getString(KEY_GLOBAL_BLOCKED_KEYWORDS)
            ),
            blockedChannels = parseCommaSeparatedList(
                remoteConfig.getString(KEY_GLOBAL_BLOCKED_CHANNELS)
            ),
            blockedArtists = parseCommaSeparatedList(
                remoteConfig.getString(KEY_GLOBAL_BLOCKED_ARTISTS)
            ),
            maintenanceMode = remoteConfig.getBoolean(KEY_MAINTENANCE_MODE),
            minAppVersion = remoteConfig.getString(KEY_MIN_APP_VERSION),
            lyricsFeatureEnabled = remoteConfig.getBoolean(KEY_FEATURE_LYRICS_ENABLED)
        )

        _globalSettings.value = settings
        Log.d(TAG, "Global settings updated: ${settings.blockedKeywords.size} keywords, ${settings.blockedChannels.size} channels")
    }

    private fun parseCommaSeparatedList(value: String): List<String> {
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Check if content matches global block list
     */
    fun isGloballyBlocked(text: String): GlobalBlockResult {
        val settings = _globalSettings.value
        val textLower = text.lowercase()

        for (keyword in settings.blockedKeywords) {
            if (textLower.contains(keyword.lowercase())) {
                return GlobalBlockResult(true, "Content blocked by platform policy")
            }
        }

        return GlobalBlockResult(false, null)
    }

    /**
     * Check if channel is globally blocked
     */
    fun isChannelGloballyBlocked(channelId: String, channelName: String): GlobalBlockResult {
        val settings = _globalSettings.value

        val blocked = settings.blockedChannels.any {
            it.equals(channelId, ignoreCase = true) ||
            it.equals(channelName, ignoreCase = true)
        }

        return if (blocked) {
            GlobalBlockResult(true, "Channel blocked by platform policy")
        } else {
            GlobalBlockResult(false, null)
        }
    }

    /**
     * Check if artist is globally blocked
     */
    fun isArtistGloballyBlocked(artistId: String, artistName: String): GlobalBlockResult {
        val settings = _globalSettings.value

        val blocked = settings.blockedArtists.any {
            it.equals(artistId, ignoreCase = true) ||
            it.equals(artistName, ignoreCase = true)
        }

        return if (blocked) {
            GlobalBlockResult(true, "Artist blocked by platform policy")
        } else {
            GlobalBlockResult(false, null)
        }
    }
}

/**
 * Global settings from Remote Config (developer-controlled)
 */
data class GlobalFilterSettings(
    val blockedKeywords: List<String> = emptyList(),
    val blockedChannels: List<String> = emptyList(),
    val blockedArtists: List<String> = emptyList(),
    val maintenanceMode: Boolean = false,
    val minAppVersion: String = "1.0.0",
    val lyricsFeatureEnabled: Boolean = false
)

/**
 * Result of global block check
 */
data class GlobalBlockResult(
    val isBlocked: Boolean,
    val message: String?
)
