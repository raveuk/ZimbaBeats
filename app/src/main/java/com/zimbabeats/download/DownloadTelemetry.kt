package com.zimbabeats.download

import android.content.Context
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase

/**
 * Tracks download-pipeline events to Firebase Analytics and reports failures as
 * non-fatals to Crashlytics. Built so we can see in production:
 *
 *  - Which resolver tier (InnerTube / NewPipe / yt-dlp) is doing the work, and how
 *    that mix shifts over time. A spike in yt-dlp usage means InnerTube + NewPipe
 *    are degrading and we may need to push a yt-dlp update.
 *  - Which output path (passthrough / transmux / transcode) downloads are taking.
 *    Transcodes are slow and battery-hungry; if their share grows we may want
 *    smarter quality defaults.
 *  - How often each path fails, so we know when something silently broke.
 *
 * Two events:
 *   - `dl_qualities_loaded` — fired when DownloadManager.getAvailableQualities()
 *     returns. Params: resolver_tier, stream_count.
 *   - `dl_download_completed` — fired at end of DownloadWorker.doWork(). Params:
 *     mode, result, quality, duration_ms, file_size_bytes.
 *
 * User property `ytdlp_version` is updated each time the self-update worker succeeds,
 * so we can correlate failure rates with specific yt-dlp builds.
 */
class DownloadTelemetry(@Suppress("UNUSED_PARAMETER") context: Context) {

    companion object {
        private const val TAG = "DLTelemetry"

        // Events
        private const val EVENT_QUALITIES_LOADED = "dl_qualities_loaded"
        private const val EVENT_DOWNLOAD_COMPLETED = "dl_download_completed"

        // Params (snake_case, max 40 chars, Firebase Analytics convention)
        private const val PARAM_RESOLVER_TIER = "resolver_tier"
        private const val PARAM_STREAM_COUNT = "stream_count"
        private const val PARAM_MODE = "mode"
        private const val PARAM_RESULT = "result"
        private const val PARAM_QUALITY = "quality"
        private const val PARAM_DURATION_MS = "duration_ms"
        private const val PARAM_FILE_SIZE_BYTES = "file_size_bytes"

        // User properties
        private const val USER_PROP_YTDLP_VERSION = "ytdlp_version"

        // Resolver tier strings — keep stable; we'll query on these.
        const val TIER_INNERTUBE = "innertube"
        const val TIER_NEWPIPE = "newpipe"
        const val TIER_YTDLP = "ytdlp"
        const val TIER_NONE = "none"

        // Download result strings.
        const val RESULT_SUCCESS = "success"
        const val RESULT_RESOLVER_FAILED = "resolver_failed"
        const val RESULT_NETWORK_FAILED = "network_failed"
        const val RESULT_MUX_FAILED = "mux_failed"
        const val RESULT_TRANSCODE_FAILED = "transcode_failed"
        const val RESULT_UNKNOWN_FAILED = "unknown_failed"
    }

    private val analytics: FirebaseAnalytics by lazy { Firebase.analytics }
    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

    fun trackQualitiesLoaded(resolverTier: String, streamCount: Int) {
        Log.d(TAG, "qualities_loaded tier=$resolverTier count=$streamCount")
        analytics.logEvent(EVENT_QUALITIES_LOADED) {
            param(PARAM_RESOLVER_TIER, resolverTier)
            param(PARAM_STREAM_COUNT, streamCount.toLong())
        }
    }

    fun trackDownloadCompleted(
        mode: String,
        result: String,
        quality: String,
        durationMs: Long,
        fileSizeBytes: Long
    ) {
        Log.d(TAG, "download_completed mode=$mode result=$result quality=$quality duration=${durationMs}ms size=$fileSizeBytes")
        analytics.logEvent(EVENT_DOWNLOAD_COMPLETED) {
            param(PARAM_MODE, mode)
            param(PARAM_RESULT, result)
            param(PARAM_QUALITY, quality)
            param(PARAM_DURATION_MS, durationMs)
            param(PARAM_FILE_SIZE_BYTES, fileSizeBytes)
        }
    }

    /**
     * Set the yt-dlp version as a user property so failure rates can be sliced by build.
     * No-op if the version is null or empty.
     */
    fun setYtDlpVersion(version: String?) {
        if (version.isNullOrBlank()) return
        analytics.setUserProperty(USER_PROP_YTDLP_VERSION, version)
    }

    /**
     * Log a non-fatal to Crashlytics. Used for resolver / mux / transcode failures so we
     * see the actual stack instead of just the result-bucket. Adds the failure reason as
     * a custom key so the Crashlytics issue groups by it.
     */
    fun recordNonFatal(reason: String, throwable: Throwable?) {
        crashlytics.setCustomKey("dl_failure_reason", reason)
        if (throwable != null) {
            crashlytics.recordException(throwable)
        } else {
            // Synthesize a throwable so the issue lands in Crashlytics with a useful title.
            crashlytics.recordException(DownloadFailure(reason))
        }
    }

    /** Marker exception so synthesized failures group together in the Crashlytics UI. */
    private class DownloadFailure(message: String) : RuntimeException(message)

    /**
     * Helper to mimic Firebase's `bundleOf`-style param block without depending on
     * `firebase-analytics-ktx`'s param() extension everywhere.
     */
    private inline fun FirebaseAnalytics.logEvent(name: String, block: ParamBuilder.() -> Unit) {
        val builder = ParamBuilder()
        builder.block()
        logEvent(name, builder.build())
    }

    private class ParamBuilder {
        private val bundle = android.os.Bundle()
        fun param(key: String, value: String) = bundle.putString(key, value)
        fun param(key: String, value: Long) = bundle.putLong(key, value)
        fun build(): android.os.Bundle = bundle
    }
}
