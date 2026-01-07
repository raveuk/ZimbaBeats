package com.zimbabeats.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zimbabeats.core.data.remote.youtube.YouTubeService
import com.zimbabeats.core.domain.model.DownloadStatus
import com.zimbabeats.core.domain.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Worker for downloading videos for offline playback
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val downloadRepository: DownloadRepository by inject()
    private val youTubeService: YouTubeService by inject()

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_QUALITY = "quality"
        const val KEY_STREAM_URL = "stream_url"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val quality = inputData.getString(KEY_QUALITY) ?: "medium"
        val providedStreamUrl = inputData.getString(KEY_STREAM_URL)

        Log.d(TAG, "Starting download for video: $videoId, quality: $quality, hasProvidedUrl: ${providedStreamUrl != null}")

        try {
            // Use provided URL or find best stream
            val streamUrl: String
            val streamQuality: String

            if (providedStreamUrl != null) {
                // Use the provided stream URL directly (user selected quality)
                streamUrl = providedStreamUrl
                streamQuality = quality
                Log.d(TAG, "Using provided stream URL for quality: $quality")
            } else {
                // Find best stream (fallback for legacy calls)
                val streams = youTubeService.getStreamUrls(videoId)
                Log.d(TAG, "Found ${streams.size} streams for $videoId")

                // Log available streams for debugging
                val combinedStreams = streams.filter { !it.isVideoOnly && !it.quality.contains("kbps") }
                val videoOnlyStreams = streams.filter { it.isVideoOnly }
                val audioOnlyStreams = streams.filter { it.quality.contains("kbps") }

                Log.d(TAG, "Combined streams: ${combinedStreams.size}, Video-only: ${videoOnlyStreams.size}, Audio-only: ${audioOnlyStreams.size}")
                combinedStreams.forEach { Log.d(TAG, "  Combined: ${it.quality} - ${it.format}") }

                // Priority 1: Highest quality combined stream
                var stream = combinedStreams
                    .sortedByDescending { it.quality.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 }
                    .firstOrNull()

                // Priority 2: Try lower quality combined streams (360p, 240p often have audio)
                if (stream == null) {
                    stream = combinedStreams.firstOrNull { it.quality.contains("360") || it.quality.contains("240") }
                }

                if (stream == null) {
                    Log.e(TAG, "No combined stream found for $videoId - only adaptive formats available")
                    downloadRepository.markDownloadFailed(videoId, "No downloadable stream found (video has only adaptive formats)")
                    return@withContext Result.failure(
                        workDataOf(KEY_ERROR to "No downloadable stream found. This video only has adaptive formats which require special handling.")
                    )
                }

                streamUrl = stream.url
                streamQuality = stream.quality
                Log.d(TAG, "Selected stream: ${stream.quality} - ${stream.format} - URL: ${stream.url.take(100)}...")
            }

            // Download the video
            val downloadResult = downloadVideo(videoId, streamUrl)

            if (downloadResult != null) {
                val (videoFile, fileSize) = downloadResult
                Log.d(TAG, "Download completed: $videoId, size: $fileSize bytes")

                // Update database with completion
                downloadRepository.completeDownload(
                    videoId = videoId,
                    filePath = videoFile.absolutePath,
                    fileSize = fileSize,
                    thumbnailPath = null
                )

                Result.success(workDataOf(KEY_VIDEO_ID to videoId))
            } else {
                Log.e(TAG, "Download returned null for $videoId")
                downloadRepository.markDownloadFailed(videoId, "Download failed - connection error")
                Result.failure(workDataOf(KEY_ERROR to "Download failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download exception for $videoId", e)
            downloadRepository.markDownloadFailed(videoId, e.message ?: "Unknown error")
            Result.failure(workDataOf(KEY_ERROR to e.message))
        }
    }

    private suspend fun downloadVideo(videoId: String, url: String): Pair<File, Long>? {
        return try {
            val downloadsDir = File(applicationContext.filesDir, "downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val videoFile = File(downloadsDir, "$videoId.mp4")

            // Use OkHttp with extended timeouts for large files
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.MINUTES)  // Extended for large files
                .writeTimeout(10, TimeUnit.MINUTES)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
                .header("Accept", "*/*")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return null
            }

            val body = response.body ?: return null
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(videoFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Update progress
                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes.toFloat() / totalBytes) * 100).toInt()
                        } else 0

                        setProgressAsync(workDataOf(
                            KEY_VIDEO_ID to videoId,
                            KEY_PROGRESS to progress
                        ))

                        // Update progress in database every 5%
                        if (progress % 5 == 0) {
                            downloadRepository.updateDownloadProgress(videoId, progress)
                        }
                    }
                }
            }

            Pair(videoFile, downloadedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
