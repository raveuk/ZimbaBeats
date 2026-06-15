package com.zimbabeats.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zimbabeats.R
import com.zimbabeats.core.data.remote.youtube.YouTubeService
import com.zimbabeats.core.domain.repository.DownloadRepository
import com.zimbabeats.download.DownloadTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Downloads a YouTube video to local storage as an .mp4 file.
 *
 * Three modes (selected via [KEY_MODE]):
 *  - [MODE_PASSTHROUGH]: single progressive stream → straight to disk. Legacy 360p path.
 *  - [MODE_TRANSMUX]: video-only (H.264) + audio-only (AAC) downloaded in parallel,
 *    combined into MP4 via Media3 Mp4Muxer (no re-encode).
 *  - [MODE_TRANSCODE]: video-only (VP9/AV1) + audio-only (Opus) downloaded in parallel,
 *    re-encoded to H.264/AAC via Media3 Transformer using hardware MediaCodec.
 *
 * Progress reporting: in MODE_PASSTHROUGH the byte stream drives progress directly. In
 * MODE_TRANSMUX/TRANSCODE the download phase counts 0–80% (split across the two streams)
 * and the mux/transcode phase consumes the remaining 80–100%.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val downloadRepository: DownloadRepository by inject()
    private val youTubeService: YouTubeService by inject()
    private val telemetry: DownloadTelemetry by inject()

    /**
     * Highest percent we've reported so far. Used to enforce monotonic progress when
     * the video and audio downloads run in parallel — without this, the two parallel
     * coroutines bounce uiState.downloadProgress up and down depending on which one
     * reported last, making the UI look like the download is restarting in a loop.
     */
    private val highestReportedProgress = AtomicInteger(0)

    companion object {
        private const val TAG = "DownloadWorker"

        const val KEY_VIDEO_ID = "video_id"
        const val KEY_QUALITY = "quality"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_MODE = "mode"

        // Legacy single-URL path (kept for callers that haven't migrated to options).
        const val KEY_STREAM_URL = "stream_url"

        // New paired-stream path.
        const val KEY_VIDEO_URL = "video_url"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_VIDEO_CODEC = "video_codec"
        const val KEY_AUDIO_CODEC = "audio_codec"
        const val KEY_VIDEO_CONTAINER = "video_container"
        const val KEY_AUDIO_CONTAINER = "audio_container"

        const val MODE_PASSTHROUGH = "passthrough"
        const val MODE_TRANSMUX = "transmux"
        const val MODE_TRANSCODE = "transcode"

        // Phase weights in the unified progress bar.
        private const val DOWNLOAD_PHASE_END_PERCENT = 80

        const val CHANNEL_ID = "zimbabeats_downloads"
        private const val CHANNEL_NAME = "Video downloads"
        private const val NOTIFICATION_ID_BASE = 0x10000
    }

    private val notificationManager by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /** Stable notification id per videoId so progress updates the same notification. */
    private val notificationId: Int by lazy {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: ""
        NOTIFICATION_ID_BASE + (videoId.hashCode() and 0x0FFF)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress while videos download in the background"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildForegroundInfo(progress: Int, videoTitle: String?): ForegroundInfo {
        ensureChannel()

        val openAppIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val contentIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                applicationContext,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(videoTitle ?: "Downloading video")
            .setContentText("$progress%")
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        contentIntent?.let { builder.setContentIntent(it) }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, builder.build())
        }
    }

    private fun postCompletionNotification(videoTitle: String?, succeeded: Boolean) {
        ensureChannel()
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (succeeded) "Download complete" else "Download failed")
            .setContentText(videoTitle ?: "")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(notificationId, notif)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val quality = inputData.getString(KEY_QUALITY) ?: "medium"
        val mode = inputData.getString(KEY_MODE) ?: inferLegacyMode()
        val startedAt = System.currentTimeMillis()

        Log.d(TAG, "doWork videoId=$videoId quality=$quality mode=$mode")

        runCatching {
            setForeground(buildForegroundInfo(0, "Downloading $quality"))
        }.onFailure { e ->
            Log.w(TAG, "setForeground failed (will continue as background worker): ${e.message}")
        }

        try {
            val downloadsDir = File(applicationContext.filesDir, "downloads").apply { mkdirs() }
            val finalFile = File(downloadsDir, "$videoId.mp4")

            val (resultFile, bytesWritten) = when (mode) {
                MODE_PASSTHROUGH -> handlePassthrough(videoId, finalFile)
                MODE_TRANSMUX -> handlePairedStreams(videoId, finalFile, transcode = false)
                MODE_TRANSCODE -> handlePairedStreams(videoId, finalFile, transcode = true)
                else -> {
                    Log.e(TAG, "Unknown mode: $mode")
                    null
                }
            } ?: run {
                downloadRepository.markDownloadFailed(videoId, "Download pipeline returned no file")
                // Without a way to distinguish failures from inside the pipeline, attribute
                // by mode: passthrough → network, transmux/transcode → mux/transcode.
                val failureBucket = when (mode) {
                    MODE_TRANSMUX -> DownloadTelemetry.RESULT_MUX_FAILED
                    MODE_TRANSCODE -> DownloadTelemetry.RESULT_TRANSCODE_FAILED
                    MODE_PASSTHROUGH -> DownloadTelemetry.RESULT_NETWORK_FAILED
                    else -> DownloadTelemetry.RESULT_UNKNOWN_FAILED
                }
                telemetry.trackDownloadCompleted(
                    mode = mode,
                    result = failureBucket,
                    quality = quality,
                    durationMs = System.currentTimeMillis() - startedAt,
                    fileSizeBytes = 0L
                )
                telemetry.recordNonFatal(failureBucket, null)
                return@withContext Result.failure(workDataOf(KEY_ERROR to "Download failed"))
            }

            downloadRepository.completeDownload(
                videoId = videoId,
                filePath = resultFile.absolutePath,
                fileSize = bytesWritten,
                thumbnailPath = null
            )
            postCompletionNotification(videoTitle = "Downloaded as $quality", succeeded = true)
            telemetry.trackDownloadCompleted(
                mode = mode,
                result = DownloadTelemetry.RESULT_SUCCESS,
                quality = quality,
                durationMs = System.currentTimeMillis() - startedAt,
                fileSizeBytes = bytesWritten
            )
            Result.success(workDataOf(KEY_VIDEO_ID to videoId))
        } catch (e: Exception) {
            Log.e(TAG, "Download exception for $videoId", e)
            downloadRepository.markDownloadFailed(videoId, e.message ?: "Unknown error")
            postCompletionNotification(videoTitle = e.message, succeeded = false)
            telemetry.trackDownloadCompleted(
                mode = mode,
                result = DownloadTelemetry.RESULT_UNKNOWN_FAILED,
                quality = quality,
                durationMs = System.currentTimeMillis() - startedAt,
                fileSizeBytes = 0L
            )
            telemetry.recordNonFatal(DownloadTelemetry.RESULT_UNKNOWN_FAILED, e)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
        }
    }

    /**
     * When KEY_MODE is absent (older WorkRequests already enqueued before this rewrite),
     * fall back to passthrough if KEY_STREAM_URL is present.
     */
    private fun inferLegacyMode(): String = MODE_PASSTHROUGH

    // ---------- Pipelines ----------

    private suspend fun handlePassthrough(videoId: String, target: File): Pair<File, Long>? {
        val url = inputData.getString(KEY_STREAM_URL) ?: inputData.getString(KEY_VIDEO_URL)
        if (url == null) {
            Log.e(TAG, "No stream URL provided for passthrough")
            // Best-effort: try resolving fresh.
            val resolved = resolveFirstCombinedStream(videoId) ?: return null
            return downloadToFile(videoId, resolved, target, weight = 1.0, baseProgress = 0)
                ?.let { target to it }
        }
        return downloadToFile(videoId, url, target, weight = 1.0, baseProgress = 0)
            ?.let { target to it }
    }

    private suspend fun handlePairedStreams(
        videoId: String,
        finalFile: File,
        transcode: Boolean
    ): Pair<File, Long>? = coroutineScope {
        val videoUrl = inputData.getString(KEY_VIDEO_URL) ?: run {
            Log.e(TAG, "Paired mode requires KEY_VIDEO_URL"); return@coroutineScope null
        }
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: run {
            Log.e(TAG, "Paired mode requires KEY_AUDIO_URL"); return@coroutineScope null
        }
        val videoContainer = inputData.getString(KEY_VIDEO_CONTAINER) ?: "mp4"
        val audioContainer = inputData.getString(KEY_AUDIO_CONTAINER) ?: "m4a"

        val tempDir = File(applicationContext.cacheDir, "dl-$videoId").apply { mkdirs() }
        val videoTemp = File(tempDir, "video.$videoContainer")
        val audioTemp = File(tempDir, "audio.$audioContainer")

        try {
            // Parallel download. Both streams contribute equally to the 0..DOWNLOAD_PHASE_END_PERCENT band.
            val downloadResults = listOf(
                async { downloadToFile(videoId, videoUrl, videoTemp, weight = 0.5, baseProgress = 0) },
                async { downloadToFile(videoId, audioUrl, audioTemp, weight = 0.5, baseProgress = DOWNLOAD_PHASE_END_PERCENT / 2) }
            ).awaitAll()

            if (downloadResults.any { it == null }) {
                Log.e(TAG, "One or both parallel downloads failed")
                return@coroutineScope null
            }

            // Mux or transcode into finalFile.
            val mediaProcessor = MediaProcessor(applicationContext)
            val processed = if (transcode) {
                mediaProcessor.transcode(videoTemp, audioTemp, finalFile) { pct ->
                    reportProgress(videoId, DOWNLOAD_PHASE_END_PERCENT + (pct * (100 - DOWNLOAD_PHASE_END_PERCENT) / 100))
                }
            } else {
                mediaProcessor.transmux(videoTemp, audioTemp, finalFile) { pct ->
                    reportProgress(videoId, DOWNLOAD_PHASE_END_PERCENT + (pct * (100 - DOWNLOAD_PHASE_END_PERCENT) / 100))
                }
            }

            if (!processed) {
                Log.e(TAG, "Media processor failed (transcode=$transcode)")
                return@coroutineScope null
            }
            reportProgress(videoId, 100)
            return@coroutineScope finalFile to finalFile.length()
        } finally {
            // Clean up temps.
            runCatching { videoTemp.delete() }
            runCatching { audioTemp.delete() }
            runCatching { tempDir.delete() }
        }
    }

    // ---------- Helpers ----------

    private suspend fun resolveFirstCombinedStream(videoId: String): String? {
        val streams = youTubeService.getStreamUrls(videoId)
        return streams.firstOrNull { !it.isVideoOnly && it.height > 0 }?.url
    }

    /**
     * Downloads a URL to `target`. `weight` (0..1) is the fraction of the download phase this
     * call represents; `baseProgress` is the percent already counted before this call starts.
     * Returns total bytes written, or null on failure.
     */
    private suspend fun downloadToFile(
        videoId: String,
        url: String,
        target: File,
        weight: Double,
        baseProgress: Int
    ): Long? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "com.google.android.youtube/21.03.36 (Linux; U; Android 14) gzip")
                .header("Accept", "*/*")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP ${response.code} for $url")
                response.close()
                return null
            }

            val body = response.body ?: return null
            val totalBytes = body.contentLength()
            val written = AtomicLong(0)
            var lastReported = -1

            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        output.write(buffer, 0, n)
                        val cur = written.addAndGet(n.toLong())
                        if (totalBytes > 0) {
                            val phasePct = ((cur.toDouble() / totalBytes) * 100 * weight).toInt()
                            val overall = baseProgress + phasePct.coerceAtMost((DOWNLOAD_PHASE_END_PERCENT * weight).toInt())
                            if (overall != lastReported) {
                                lastReported = overall
                                reportProgress(videoId, overall)
                            }
                        }
                    }
                }
            }
            written.get()
        } catch (e: Exception) {
            Log.e(TAG, "downloadToFile failed for $url", e)
            null
        }
    }

    private suspend fun reportProgress(videoId: String, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        // Two parallel coroutines call this concurrently (video + audio downloads).
        // updateAndGet returns the new highest; if our value isn't higher, skip the
        // notify/store so the UI never goes backwards.
        val current = highestReportedProgress.updateAndGet { prev -> maxOf(prev, clamped) }
        if (current != clamped) return

        setProgressAsync(workDataOf(KEY_VIDEO_ID to videoId, KEY_PROGRESS to clamped))
        if (clamped % 5 == 0) {
            downloadRepository.updateDownloadProgress(videoId, clamped)
        }
        runCatching {
            val quality = inputData.getString(KEY_QUALITY) ?: ""
            notificationManager.notify(notificationId, buildForegroundInfo(clamped, "Downloading $quality").notification)
        }
    }
}
