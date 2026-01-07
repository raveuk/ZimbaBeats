package com.zimbabeats.download

import android.content.Context
import android.util.Log
import androidx.work.*
import com.zimbabeats.core.data.remote.youtube.YouTubeService
import com.zimbabeats.core.domain.model.DownloadStatus
import com.zimbabeats.core.domain.repository.DownloadRepository
import com.zimbabeats.worker.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Data class for download quality option with size info
 */
data class DownloadQualityOption(
    val quality: String,
    val format: String,
    val url: String,
    val sizeBytes: Long,
    val isVideoOnly: Boolean = false
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
    private val youTubeService: YouTubeService
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
                .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
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
     * Get all available download quality options with sizes
     * Returns list sorted by quality (highest first)
     */
    suspend fun getAvailableQualities(videoId: String): List<DownloadQualityOption> = withContext(Dispatchers.IO) {
        try {
            val streams = youTubeService.getStreamUrls(videoId)
            Log.d(TAG, "Found ${streams.size} streams for quality options")

            // Get combined streams (video+audio) only - sorted by quality
            val combinedStreams = streams
                .filter { !it.isVideoOnly && !it.quality.contains("kbps") && it.format != "hls" }
                .sortedByDescending { it.quality.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 }

            Log.d(TAG, "Combined streams available: ${combinedStreams.map { "${it.quality} (${it.format})" }}")

            // Fetch sizes for each stream (in parallel would be better but keep it simple)
            val qualityOptions = mutableListOf<DownloadQualityOption>()

            for (stream in combinedStreams) {
                try {
                    val request = Request.Builder()
                        .url(stream.url)
                        .head()
                        .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    response.close()

                    qualityOptions.add(
                        DownloadQualityOption(
                            quality = stream.quality,
                            format = stream.format,
                            url = stream.url,
                            sizeBytes = contentLength,
                            isVideoOnly = stream.isVideoOnly
                        )
                    )
                    Log.d(TAG, "Quality option: ${stream.quality} - ${contentLength} bytes")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get size for ${stream.quality}", e)
                    // Add without size info
                    qualityOptions.add(
                        DownloadQualityOption(
                            quality = stream.quality,
                            format = stream.format,
                            url = stream.url,
                            sizeBytes = -1L,
                            isVideoOnly = stream.isVideoOnly
                        )
                    )
                }
            }

            qualityOptions
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get quality options for $videoId", e)
            emptyList()
        }
    }

    /**
     * Start downloading a video with a specific stream URL
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
                    DownloadWorker.KEY_STREAM_URL to streamUrl
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
}
