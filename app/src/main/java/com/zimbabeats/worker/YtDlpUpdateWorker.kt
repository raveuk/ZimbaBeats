package com.zimbabeats.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zimbabeats.cloud.RemoteConfigManager
import com.zimbabeats.download.DownloadTelemetry
import com.zimbabeats.download.YtDlpUpdater
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that asks [YtDlpUpdater] to pull the latest yt-dlp script. Runs at the
 * interval configured by Remote Config (default 24h, minimum 1h enforced by RC).
 *
 * Constraints:
 *  - Unmetered network only. yt-dlp's zip is a few MB; we'd rather wait for Wi-Fi than
 *    eat a child's mobile-data quota.
 *  - Battery-not-low: a download isn't urgent enough to risk a flat battery.
 *
 * Failure handling: the worker always returns [Result.success], even on download
 * failures. The updater records the failure in SharedPreferences; rescheduling is
 * handled by the periodic schedule (next firing in `interval` hours). We don't burn
 * retries on transient network issues — the next 24h tick will try again.
 */
class YtDlpUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val updater: YtDlpUpdater by inject()
    private val telemetry: DownloadTelemetry by inject()

    companion object {
        private const val TAG = "YtDlpUpdateWorker"

        /** Unique work name — guarantees only one schedule at a time. */
        const val WORK_NAME = "ytdlp_update"

        /**
         * Enqueue (or refresh) the periodic update job. Pulls the desired interval from
         * Remote Config so changes propagate at the next launch.
         */
        fun schedule(context: Context, remoteConfigManager: RemoteConfigManager) {
            val intervalHours = remoteConfigManager.getYtDlpUpdateSettings().updateIntervalHours
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<YtDlpUpdateWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "Scheduled yt-dlp update every ${intervalHours}h (unmetered, battery not low)")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork firing")
        val result = updater.update()
        Log.d(TAG, "doWork complete: $result")
        // Refresh the user property after every success so analytics events that follow
        // are correlated to the running yt-dlp version.
        telemetry.setYtDlpVersion(updater.lastSuccessVersion())
        // Periodic workers don't gain anything from failure — the next firing will retry
        // on its own schedule. Always report success so we don't spam WorkManager's
        // backoff logic.
        return Result.success()
    }
}
