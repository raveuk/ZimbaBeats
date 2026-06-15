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

        // yt-dlp self-update keys (Piece 4 of the download rewrite). The bundled
        // youtubedl-android library already pulls scripts from yt-dlp's official GitHub
        // releases via updateYoutubeDL(channel). These RC keys are the kill switch +
        // channel control that lets us pause/redirect the update flow without an app
        // release.
        const val KEY_YTDLP_AUTO_UPDATE_ENABLED = "ytdlp_auto_update_enabled"
        const val KEY_YTDLP_UPDATE_CHANNEL = "ytdlp_update_channel"
        const val KEY_YTDLP_CUSTOM_SCRIPT_URL = "ytdlp_custom_script_url"
        const val KEY_YTDLP_UPDATE_INTERVAL_HOURS = "ytdlp_update_interval_hours"

        // App-update prompt keys. When the in-app update checker can't reach the user
        // (e.g. broken in an older release), these let us push an update message via
        // Remote Config instead. Compared against BuildConfig.VERSION_CODE.
        // - min_version_code: hard floor. Anything below = blocking dialog, no dismiss.
        // - latest_version_code: soft nudge. Below = dismissible banner.
        // - update_url: where the user is sent when they tap "Update".
        // - update_message: body text shown in banner + dialog.
        const val KEY_MIN_VERSION_CODE = "min_version_code"
        const val KEY_LATEST_VERSION_CODE = "latest_version_code"
        const val KEY_UPDATE_URL = "update_url"
        const val KEY_UPDATE_MESSAGE = "update_message"

        // Fetch interval (15 minutes for faster emergency block updates)
        private const val FETCH_INTERVAL_SECONDS = 900L
    }

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    private val _globalSettings = MutableStateFlow(GlobalFilterSettings())
    val globalSettings: StateFlow<GlobalFilterSettings> = _globalSettings.asStateFlow()

    private val _updatePromptConfig = MutableStateFlow(UpdatePromptConfig())
    /**
     * Reactive snapshot of the four update-prompt keys from Remote Config. The UI layer
     * compares [UpdatePromptConfig.minVersionCode] / [UpdatePromptConfig.latestVersionCode]
     * against [android.os.Build]-time BuildConfig.VERSION_CODE to decide whether to show
     * a blocking dialog, a dismissible banner, or nothing. Kept as raw data so that
     * neither this class nor the data layer depends on the app's BuildConfig.
     */
    val updatePromptConfig: StateFlow<UpdatePromptConfig> = _updatePromptConfig.asStateFlow()

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
            KEY_FEATURE_LYRICS_ENABLED to false,
            KEY_YTDLP_AUTO_UPDATE_ENABLED to true,
            KEY_YTDLP_UPDATE_CHANNEL to "STABLE",
            KEY_YTDLP_CUSTOM_SCRIPT_URL to "",
            KEY_YTDLP_UPDATE_INTERVAL_HOURS to 24L,
            KEY_MIN_VERSION_CODE to 0L,
            KEY_LATEST_VERSION_CODE to 0L,
            KEY_UPDATE_URL to "https://github.com/raveuk/ZimbaBeats/releases/latest",
            KEY_UPDATE_MESSAGE to ""
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

        _updatePromptConfig.value = UpdatePromptConfig(
            minVersionCode = remoteConfig.getLong(KEY_MIN_VERSION_CODE).toInt(),
            latestVersionCode = remoteConfig.getLong(KEY_LATEST_VERSION_CODE).toInt(),
            updateUrl = remoteConfig.getString(KEY_UPDATE_URL),
            updateMessage = remoteConfig.getString(KEY_UPDATE_MESSAGE)
        )
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
     * Read the current yt-dlp self-update configuration. Cheap — returns the cached RC
     * snapshot the SDK keeps in memory; the worker calls fetchAndActivate() first to
     * refresh it.
     */
    fun getYtDlpUpdateSettings(): YtDlpUpdateSettings = YtDlpUpdateSettings(
        autoUpdateEnabled = remoteConfig.getBoolean(KEY_YTDLP_AUTO_UPDATE_ENABLED),
        channel = remoteConfig.getString(KEY_YTDLP_UPDATE_CHANNEL).uppercase().ifBlank { "STABLE" },
        customScriptUrl = remoteConfig.getString(KEY_YTDLP_CUSTOM_SCRIPT_URL).ifBlank { null },
        updateIntervalHours = remoteConfig.getLong(KEY_YTDLP_UPDATE_INTERVAL_HOURS).coerceAtLeast(1L)
    )

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

/**
 * Snapshot of the yt-dlp self-update Remote Config values.
 *
 * - [autoUpdateEnabled]: kill switch. When false, the updater is a no-op for the worker
 *   run. Lets us pause updates instantly if a new yt-dlp version breaks something.
 * - [channel]: which yt-dlp channel to pull from. "STABLE", "NIGHTLY", or "MASTER" —
 *   maps to YoutubeDL.UpdateChannel.
 * - [customScriptUrl]: full override. When non-null, the updater downloads this URL
 *   directly instead of going through youtubedl-android's built-in updater. Used to
 *   ship a patched yt-dlp from our own hosting if upstream is broken.
 * - [updateIntervalHours]: how often the periodic worker runs. Caps at 1h minimum.
 */
/**
 * Raw Remote Config values for the in-app update prompt. Evaluated against the running
 * app's version code by [UpdatePromptDecision.from] to produce the UI prompt type.
 */
data class UpdatePromptConfig(
    val minVersionCode: Int = 0,
    val latestVersionCode: Int = 0,
    val updateUrl: String = "",
    val updateMessage: String = ""
)

/**
 * Result of comparing [UpdatePromptConfig] against the running app's version code.
 *
 * - [None]: app is current (or RC keys are unset).
 * - [Recommended]: a newer version is published; show a dismissible banner.
 * - [Required]: the running version is below the minimum supported floor; show a
 *   blocking dialog the user can't dismiss until they update.
 */
sealed class UpdatePromptDecision {
    object None : UpdatePromptDecision()
    data class Recommended(val targetVersionCode: Int, val url: String, val message: String) : UpdatePromptDecision()
    data class Required(val minVersionCode: Int, val url: String, val message: String) : UpdatePromptDecision()

    companion object {
        fun from(config: UpdatePromptConfig, currentVersionCode: Int): UpdatePromptDecision {
            val url = config.updateUrl.ifBlank { "https://github.com/raveuk/ZimbaBeats/releases/latest" }
            return when {
                config.minVersionCode > 0 && currentVersionCode < config.minVersionCode ->
                    Required(config.minVersionCode, url, config.updateMessage)
                config.latestVersionCode > 0 && currentVersionCode < config.latestVersionCode ->
                    Recommended(config.latestVersionCode, url, config.updateMessage)
                else -> None
            }
        }
    }
}

data class YtDlpUpdateSettings(
    val autoUpdateEnabled: Boolean,
    val channel: String,
    val customScriptUrl: String?,
    val updateIntervalHours: Long
)
