package com.zimbabeats.download

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.zimbabeats.cloud.RemoteConfigManager
import com.zimbabeats.cloud.YtDlpUpdateSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Self-update channel for the bundled yt-dlp script (Piece 4 of the download rewrite).
 *
 * The youtubedl-android library ships a baseline yt-dlp script inside the APK. YouTube
 * frequently changes its player JS, breaking older yt-dlp builds every ~4-8 weeks. This
 * class pulls the latest yt-dlp release at runtime so users don't have to wait for an
 * app release to get a working extractor.
 *
 * Update sources, in priority order:
 *  1. If Remote Config sets [YtDlpUpdateSettings.customScriptUrl], download that URL
 *     directly. Used to ship a patched build from our own hosting when upstream is
 *     broken.
 *  2. Otherwise call YoutubeDL.updateYoutubeDL(channel) — pulls from yt-dlp's official
 *     GitHub releases. Channel comes from RC (STABLE | NIGHTLY | MASTER).
 *
 * State is kept in SharedPreferences:
 *  - last_success_millis: epoch ms of the last successful update
 *  - last_success_version: the yt-dlp version string we landed on
 *  - last_failure_millis: epoch ms of the most recent failure (for rate-limiting retries)
 *  - last_failure_message: error string from the most recent failure
 *
 * Returns an [UpdateResult] so the worker / telemetry can record outcomes.
 */
class YtDlpUpdater(
    private val context: Context,
    private val remoteConfigManager: RemoteConfigManager
) {

    companion object {
        private const val TAG = "YtDlpUpdater"
        private const val PREFS_NAME = "ytdlp_updater"
        private const val PREF_LAST_SUCCESS_MS = "last_success_millis"
        private const val PREF_LAST_SUCCESS_VERSION = "last_success_version"
        private const val PREF_LAST_FAILURE_MS = "last_failure_millis"
        private const val PREF_LAST_FAILURE_MSG = "last_failure_message"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Refresh RC, run the update, return the outcome. Never throws.
     */
    suspend fun update(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            // Pull the latest RC values before reading them. The SDK keeps cached values
            // between calls, so this is a no-op if we already fetched recently.
            remoteConfigManager.fetchAndActivate()
        } catch (e: Exception) {
            Log.w(TAG, "Could not refresh Remote Config (continuing with cached values): ${e.message}")
        }

        val settings = remoteConfigManager.getYtDlpUpdateSettings()
        if (!settings.autoUpdateEnabled) {
            Log.i(TAG, "Auto-update disabled via Remote Config; skipping")
            return@withContext UpdateResult.Disabled
        }

        try {
            val customUrl = settings.customScriptUrl
            val result = if (customUrl != null) {
                Log.i(TAG, "Updating yt-dlp from custom URL: $customUrl")
                runCustomScriptUpdate(customUrl)
            } else {
                val channel = parseChannel(settings.channel)
                Log.i(TAG, "Updating yt-dlp via library updateYoutubeDL(channel=$channel)")
                runLibraryUpdate(channel)
            }
            recordSuccess(result.versionAfter)
            Log.i(TAG, "yt-dlp update success: ${result.statusName} -> ${result.versionAfter}")
            result
        } catch (e: Throwable) {
            recordFailure(e.message ?: e::class.java.simpleName)
            Log.w(TAG, "yt-dlp update failed: ${e.message}", e)
            UpdateResult.Failed(e.message ?: "unknown")
        }
    }

    private fun runLibraryUpdate(channel: YoutubeDL.UpdateChannel): UpdateResult.Applied {
        // updateYoutubeDL returns an UpdateStatus enum (DONE_UPDATING, ALREADY_UP_TO_DATE,
        // etc.). The library annotates it nullable, so we substitute a fallback.
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, channel)
        val version = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
        return UpdateResult.Applied(
            statusName = status?.name ?: "UNKNOWN",
            versionAfter = version ?: "unknown",
            usedCustomUrl = false
        )
    }

    /**
     * Custom-URL path. The library doesn't expose a "swap-this-file-for-yt-dlp" API, so
     * we fall back to its built-in updater for now and just log that a custom URL was
     * configured. Wiring full custom-file replacement requires reaching into the
     * library's internal file layout (ytdlpDirName / ytdlpBin) — better left for when
     * we actually need to host a patched build.
     */
    private fun runCustomScriptUpdate(@Suppress("UNUSED_PARAMETER") customUrl: String): UpdateResult.Applied {
        Log.w(TAG, "Custom yt-dlp URL is set but not yet implemented; falling back to library updater")
        return runLibraryUpdate(YoutubeDL.UpdateChannel.STABLE)
    }

    private fun parseChannel(raw: String): YoutubeDL.UpdateChannel = when (raw.uppercase()) {
        "NIGHTLY" -> YoutubeDL.UpdateChannel.NIGHTLY
        "MASTER" -> YoutubeDL.UpdateChannel.MASTER
        else -> YoutubeDL.UpdateChannel.STABLE
    }

    private fun recordSuccess(version: String) {
        prefs.edit()
            .putLong(PREF_LAST_SUCCESS_MS, System.currentTimeMillis())
            .putString(PREF_LAST_SUCCESS_VERSION, version)
            .apply()
    }

    private fun recordFailure(message: String) {
        prefs.edit()
            .putLong(PREF_LAST_FAILURE_MS, System.currentTimeMillis())
            .putString(PREF_LAST_FAILURE_MSG, message)
            .apply()
    }

    /** Most recent successful update timestamp (epoch ms), or 0 if never. */
    fun lastSuccessMs(): Long = prefs.getLong(PREF_LAST_SUCCESS_MS, 0L)

    /** Most recent successful yt-dlp version string, or null if never. */
    fun lastSuccessVersion(): String? = prefs.getString(PREF_LAST_SUCCESS_VERSION, null)

    sealed class UpdateResult {
        /** Convenience accessors so the caller can log a result without smart-casting. */
        open val versionAfter: String get() = ""
        open val statusName: String get() = this::class.java.simpleName

        object Disabled : UpdateResult()

        data class Applied(
            override val statusName: String,
            override val versionAfter: String,
            val usedCustomUrl: Boolean
        ) : UpdateResult()

        data class Failed(val message: String) : UpdateResult() {
            override val statusName: String get() = "Failed($message)"
        }
    }
}
