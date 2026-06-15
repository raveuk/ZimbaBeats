package com.zimbabeats.download

import android.content.Context
import android.util.Log
import androidx.work.*
import com.zimbabeats.core.data.remote.youtube.NewPipeStreamExtractor
import com.zimbabeats.core.data.remote.youtube.StreamUrl
import com.zimbabeats.core.data.remote.youtube.YouTubeService
import com.zimbabeats.core.data.remote.youtube.YtDlpStreamResolver
import com.zimbabeats.core.domain.model.DownloadStatus
import com.zimbabeats.core.domain.repository.DownloadRepository
import com.zimbabeats.worker.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A user-selectable download quality.
 *
 * Three shapes exist:
 *  - Pre-muxed (combined progressive): videoStream is the muxed stream, audioStream is null,
 *    requiresTransmux = false, requiresTranscode = false. DownloadWorker writes the bytes
 *    straight to disk.
 *  - Transmux (H.264 video-only + AAC audio-only): both streams set,
 *    requiresTransmux = true, requiresTranscode = false. DownloadWorker downloads both
 *    in parallel and combines them with Media3 Mp4Muxer (no re-encode).
 *  - Transcode (VP9/AV1 video-only + Opus audio-only): both streams set,
 *    requiresTranscode = true. DownloadWorker downloads both and runs Media3 Transformer
 *    with hardware MediaCodec to produce a portable .mp4.
 *
 * `url` is preserved for compatibility with callers that pass a single URL through to the
 * worker — for pre-muxed it equals videoStream.url; for paired options it is unused (the
 * worker reads videoStream/audioStream from the option directly).
 */
data class DownloadQualityOption(
    val quality: String,
    val format: String,
    val url: String,
    val sizeBytes: Long,
    val isVideoOnly: Boolean = false,
    val videoStream: StreamUrl? = null,
    val audioStream: StreamUrl? = null,
    val requiresTransmux: Boolean = false,
    val requiresTranscode: Boolean = false
) {
    val sizeFormatted: String
        get() = when {
            sizeBytes >= 1_000_000_000 -> String.format("%.1f GB", sizeBytes / 1_000_000_000.0)
            sizeBytes >= 1_000_000 -> String.format("%.1f MB", sizeBytes / 1_000_000.0)
            sizeBytes >= 1_000 -> String.format("%.1f KB", sizeBytes / 1_000.0)
            sizeBytes > 0 -> "$sizeBytes bytes"
            else -> "Unknown size"
        }

    val qualityValue: Int
        get() = quality.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

    /** Display tag describing what will happen on download. */
    val processingNote: String?
        get() = when {
            requiresTranscode -> "transcoded"
            requiresTransmux -> "muxed"
            else -> null
        }
}

/**
 * Data class for download size info (legacy, for compatibility)
 */
data class DownloadSizeInfo(
    val sizeBytes: Long,
    val quality: String,
    val format: String
) {
    val sizeFormatted: String
        get() = when {
            sizeBytes >= 1_000_000_000 -> String.format("%.1f GB", sizeBytes / 1_000_000_000.0)
            sizeBytes >= 1_000_000 -> String.format("%.1f MB", sizeBytes / 1_000_000.0)
            sizeBytes >= 1_000 -> String.format("%.1f KB", sizeBytes / 1_000.0)
            else -> "$sizeBytes bytes"
        }
}

/**
 * Manages video downloads using WorkManager
 */
class DownloadManager(
    private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val youTubeService: YouTubeService,
    private val newPipeExtractor: NewPipeStreamExtractor,
    private val ytDlpResolver: YtDlpStreamResolver,
    private val telemetry: DownloadTelemetry
) {
    companion object {
        private const val TAG = "DownloadManager"
    }

    private val workManager = WorkManager.getInstance(context)
    private val downloadsDir = java.io.File(context.filesDir, "downloads")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Start downloading a video
     * @param requireWifiOnly If true, only download on unmetered (WiFi) networks
     */
    fun downloadVideo(videoId: String, quality: String = "medium", requireWifiOnly: Boolean = true): UUID {
        // Use UNMETERED (WiFi) if WiFi only, otherwise any CONNECTED network
        val networkType = if (requireWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        // Create download request
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_VIDEO_ID to videoId,
                    DownloadWorker.KEY_QUALITY to quality
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag("download_$videoId")
            .build()

        // Enqueue the work
        workManager.enqueueUniqueWork(
            "download_$videoId",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )

        return downloadRequest.id
    }

    /**
     * Cancel a download
     */
    fun cancelDownload(videoId: String) {
        workManager.cancelAllWorkByTag("download_$videoId")
    }

    /**
     * Get download progress for a video
     */
    fun getDownloadProgress(videoId: String): Flow<Int> {
        return workManager.getWorkInfosByTagFlow("download_$videoId")
            .map { workInfos ->
                workInfos.firstOrNull()?.progress?.getInt(DownloadWorker.KEY_PROGRESS, 0) ?: 0
            }
    }

    /**
     * Get download status for a video
     */
    fun getDownloadWorkInfo(videoId: String): Flow<WorkInfo?> {
        return workManager.getWorkInfosByTagFlow("download_$videoId")
            .map { workInfos -> workInfos.firstOrNull() }
    }

    /**
     * Pause a download (cancel and mark as paused)
     */
    suspend fun pauseDownload(videoId: String) {
        cancelDownload(videoId)
        downloadRepository.pauseDownload(videoId)
    }

    /**
     * Resume a download
     */
    fun resumeDownload(videoId: String, quality: String = "medium", requireWifiOnly: Boolean = true): UUID {
        return downloadVideo(videoId, quality, requireWifiOnly)
    }

    /**
     * Delete a downloaded video
     */
    suspend fun deleteDownload(videoId: String) {
        // Cancel any ongoing download
        cancelDownload(videoId)

        // Delete the file
        downloadRepository.deleteDownload(videoId)
    }

    /**
     * Cancel all downloads
     */
    fun cancelAllDownloads() {
        workManager.cancelAllWorkByTag("download")
    }

    /**
     * Check if a video is already downloaded
     */
    fun isDownloaded(videoId: String): Boolean {
        val videoFile = java.io.File(downloadsDir, "$videoId.mp4")
        return videoFile.exists()
    }

    /**
     * Get the file path for a downloaded video
     */
    fun getDownloadedFilePath(videoId: String): String? {
        val videoFile = java.io.File(downloadsDir, "$videoId.mp4")
        return if (videoFile.exists()) videoFile.absolutePath else null
    }

    /**
     * Get download size estimate for a video
     * Returns null if size cannot be determined
     */
    suspend fun getDownloadSizeEstimate(videoId: String): DownloadSizeInfo? = withContext(Dispatchers.IO) {
        try {
            val streams = youTubeService.getStreamUrls(videoId)

            // Find combined stream (same logic as DownloadWorker)
            val combinedStreams = streams.filter { !it.isVideoOnly && !it.quality.contains("kbps") }

            val stream = combinedStreams
                .sortedByDescending { it.quality.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 }
                .firstOrNull()
                ?: combinedStreams.firstOrNull { it.quality.contains("360") || it.quality.contains("240") }

            if (stream == null) {
                Log.d(TAG, "No combined stream found for size estimate: $videoId")
                return@withContext null
            }

            // Make HEAD request to get content length
            val request = Request.Builder()
                .url(stream.url)
                .head()
                .header("User-Agent", "com.google.android.youtube/21.03.36 (Linux; U; Android 14) gzip")
                .build()

            val response = httpClient.newCall(request).execute()
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L

            response.close()

            if (contentLength > 0) {
                Log.d(TAG, "Size estimate for $videoId: $contentLength bytes (${stream.quality})")
                DownloadSizeInfo(
                    sizeBytes = contentLength,
                    quality = stream.quality,
                    format = stream.format
                )
            } else {
                // Estimate based on video duration if content-length not available
                Log.d(TAG, "Content-Length not available for $videoId, using estimate")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get size estimate for $videoId", e)
            null
        }
    }

    /**
     * Build the list of selectable download qualities.
     *
     * Three sources are merged:
     *  1. Progressive (combined) streams from InnerTube `formats[]`. Already muxed —
     *     no post-processing needed. Usually only 360p (sometimes also 240p, 720p).
     *  2. Video-only adaptive streams paired with the best matching audio-only
     *     adaptive stream. Most modern qualities (480p+) live here.
     *
     * Codec compatibility:
     *  - H.264 (`avc1`) video + AAC (`mp4a`) audio → MP4 transmux (no re-encode).
     *  - VP9 / AV1 video + Opus audio → transcode via Media3 Transformer.
     *
     * Output is sorted highest quality first, deduped per resolution (we keep the
     * cheapest-to-process variant — passthrough beats transmux beats transcode).
     */
    suspend fun getAvailableQualities(videoId: String): List<DownloadQualityOption> = withContext(Dispatchers.IO) {
        try {
            // Three-tier resolver chain. Each tier is consulted only if the previous one
            // returned no usable streams. Order is by cost — fastest/cheapest first.
            //   Tier 1: InnerTube  (~ms; direct API call, structured codec info)
            //   Tier 2: NewPipe    (~seconds; handles YouTube API churn)
            //   Tier 3: yt-dlp     (~5-10s; Python subprocess, most robust extractor)
            val streams = resolveStreams(videoId)
            Log.d(TAG, "Found ${streams.size} streams for quality options")

            val combinedProgressive = streams.filter {
                !it.isVideoOnly && it.height > 0 && it.format != "hls"
            }
            val videoOnly = streams.filter { it.isVideoOnly && it.height > 0 }
            val audioOnly = streams.filter { !it.isVideoOnly && it.height == 0 && it.bitrate > 0 }

            Log.d(TAG, "combined=${combinedProgressive.size} videoOnly=${videoOnly.size} audioOnly=${audioOnly.size}")

            val bestAac = audioOnly.filter { it.audioCodec == "mp4a" }.maxByOrNull { it.bitrate }
            val bestOpus = audioOnly.filter { it.audioCodec == "opus" }.maxByOrNull { it.bitrate }
            val anyAudio = bestAac ?: bestOpus ?: audioOnly.maxByOrNull { it.bitrate }

            val options = mutableListOf<DownloadQualityOption>()

            // 1. Progressive streams (passthrough — no mux/transcode).
            combinedProgressive.forEach { stream ->
                options += DownloadQualityOption(
                    quality = "${stream.height}p",
                    format = stream.format,
                    url = stream.url,
                    sizeBytes = stream.contentLength.takeIf { it > 0 } ?: probeContentLength(stream.url),
                    isVideoOnly = false,
                    videoStream = stream,
                    audioStream = null,
                    requiresTransmux = false,
                    requiresTranscode = false
                )
            }

            // 2. Video-only streams paired with the matching audio.
            videoOnly.forEach { vStream ->
                val matchedAudio: StreamUrl?
                val transcode: Boolean
                when (vStream.videoCodec) {
                    "avc1" -> {
                        // H.264 — prefer AAC for MP4-clean transmux.
                        matchedAudio = bestAac ?: anyAudio
                        transcode = (matchedAudio?.audioCodec != "mp4a")
                    }
                    "vp9", "av01" -> {
                        // VP9/AV1 — must transcode to land in .mp4 with sane compatibility.
                        matchedAudio = bestOpus ?: anyAudio
                        transcode = true
                    }
                    else -> {
                        matchedAudio = anyAudio
                        transcode = true
                    }
                }
                if (matchedAudio == null) return@forEach

                val videoBytes = if (vStream.contentLength > 0) vStream.contentLength else probeContentLength(vStream.url)
                val audioBytes = if (matchedAudio.contentLength > 0) matchedAudio.contentLength else probeContentLength(matchedAudio.url)
                val totalBytes = if (videoBytes > 0 && audioBytes > 0) videoBytes + audioBytes else -1L

                options += DownloadQualityOption(
                    quality = "${vStream.height}p",
                    format = "mp4",
                    url = vStream.url,
                    sizeBytes = totalBytes,
                    isVideoOnly = false,
                    videoStream = vStream,
                    audioStream = matchedAudio,
                    requiresTransmux = !transcode,
                    requiresTranscode = transcode
                )
            }

            // Dedupe per resolution: prefer passthrough > transmux > transcode.
            val processingCost: (DownloadQualityOption) -> Int = { opt ->
                when {
                    opt.requiresTranscode -> 2
                    opt.requiresTransmux -> 1
                    else -> 0
                }
            }
            val deduped = options
                .groupBy { it.qualityValue }
                .mapValues { (_, group) -> group.minByOrNull(processingCost)!! }
                .values
                .sortedByDescending { it.qualityValue }
                .toList()

            Log.d(TAG, "Final qualities: ${deduped.map { "${it.quality} ${it.processingNote ?: "passthrough"} ${it.sizeFormatted}" }}")
            deduped
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get quality options for $videoId", e)
            emptyList()
        }
    }

    /**
     * Resolve a video's streams through the three-tier fallback chain. Each tier runs
     * only if the previous one returned an empty list; a thrown exception is treated as
     * an empty result so we always fall through.
     */
    private suspend fun resolveStreams(videoId: String): List<StreamUrl> {
        val tier1 = runCatching { youTubeService.getStreamUrls(videoId) }.getOrDefault(emptyList())
        if (tier1.isNotEmpty()) {
            Log.d(TAG, "Resolver: InnerTube (${tier1.size} streams)")
            telemetry.trackQualitiesLoaded(DownloadTelemetry.TIER_INNERTUBE, tier1.size)
            return tier1
        }

        Log.w(TAG, "InnerTube returned no streams for $videoId, trying NewPipe...")
        val tier2 = runCatching { newPipeExtractor.getAllStreams(videoId) }.getOrDefault(emptyList())
        if (tier2.isNotEmpty()) {
            Log.d(TAG, "Resolver: NewPipe (${tier2.size} streams)")
            telemetry.trackQualitiesLoaded(DownloadTelemetry.TIER_NEWPIPE, tier2.size)
            return tier2
        }

        Log.w(TAG, "NewPipe returned no streams for $videoId, trying yt-dlp...")
        val tier3 = runCatching { ytDlpResolver.resolve(videoId) }.getOrDefault(emptyList())
        if (tier3.isNotEmpty()) {
            Log.d(TAG, "Resolver: yt-dlp (${tier3.size} streams)")
            telemetry.trackQualitiesLoaded(DownloadTelemetry.TIER_YTDLP, tier3.size)
            return tier3
        }

        Log.e(TAG, "All resolvers failed for $videoId")
        telemetry.trackQualitiesLoaded(DownloadTelemetry.TIER_NONE, 0)
        telemetry.recordNonFatal("all_resolvers_returned_empty", null)
        return emptyList()
    }

    /**
     * Fallback when InnerTube doesn't include contentLength. Synchronous HEAD on the IO
     * dispatcher we're already running on.
     */
    private fun probeContentLength(url: String): Long {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "com.google.android.youtube/21.03.36 (Linux; U; Android 14) gzip")
                .build()
            httpClient.newCall(request).execute().use { resp ->
                resp.header("Content-Length")?.toLongOrNull() ?: -1L
            }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD probe failed for $url: ${e.message}")
            -1L
        }
    }

    /**
     * Start downloading a video with a specific stream URL (progressive / passthrough path).
     * @param requireWifiOnly If true, only download on unmetered (WiFi) networks
     */
    fun downloadVideoWithUrl(videoId: String, streamUrl: String, quality: String, requireWifiOnly: Boolean = true): UUID {
        // Use UNMETERED (WiFi) if WiFi only, otherwise any CONNECTED network
        val networkType = if (requireWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_VIDEO_ID to videoId,
                    DownloadWorker.KEY_QUALITY to quality,
                    DownloadWorker.KEY_STREAM_URL to streamUrl,
                    DownloadWorker.KEY_MODE to DownloadWorker.MODE_PASSTHROUGH
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag("download_$videoId")
            .build()

        workManager.enqueueUniqueWork(
            "download_$videoId",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )

        return downloadRequest.id
    }

    /**
     * Start a download for the selected quality option. Picks the right pipeline based on
     * option flags: passthrough, transmux, or transcode.
     */
    fun downloadVideoWithOption(videoId: String, option: DownloadQualityOption, requireWifiOnly: Boolean = true): UUID {
        val networkType = if (requireWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val mode = when {
            option.requiresTranscode -> DownloadWorker.MODE_TRANSCODE
            option.requiresTransmux -> DownloadWorker.MODE_TRANSMUX
            else -> DownloadWorker.MODE_PASSTHROUGH
        }

        val data = workDataOf(
            DownloadWorker.KEY_VIDEO_ID to videoId,
            DownloadWorker.KEY_QUALITY to option.quality,
            DownloadWorker.KEY_MODE to mode,
            DownloadWorker.KEY_VIDEO_URL to (option.videoStream?.url ?: option.url),
            DownloadWorker.KEY_AUDIO_URL to option.audioStream?.url,
            DownloadWorker.KEY_VIDEO_CODEC to option.videoStream?.videoCodec,
            DownloadWorker.KEY_AUDIO_CODEC to option.audioStream?.audioCodec,
            DownloadWorker.KEY_VIDEO_CONTAINER to (option.videoStream?.format ?: option.format),
            DownloadWorker.KEY_AUDIO_CONTAINER to option.audioStream?.format
        )

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag("download_$videoId")
            .build()

        workManager.enqueueUniqueWork(
            "download_$videoId",
            ExistingWorkPolicy.KEEP,
            downloadRequest
        )

        return downloadRequest.id
    }
}
