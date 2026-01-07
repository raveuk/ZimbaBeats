package com.zimbabeats.download

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class VideoDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "VideoDownloadWorker"
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_VIDEO_URL = "video_url"
        const val KEY_VIDEO_TITLE = "video_title"
        const val KEY_CHANNEL_NAME = "channel_name"
        const val KEY_DURATION = "duration"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_PROGRESS = "progress"
        const val KEY_FILE_PATH = "file_path"

        fun createWorkRequest(
            videoId: String,
            videoUrl: String,
            title: String,
            channelName: String,
            duration: Long,
            thumbnailUrl: String,
            requireWifiOnly: Boolean = true
        ): OneTimeWorkRequest {
            val inputData = workDataOf(
                KEY_VIDEO_ID to videoId,
                KEY_VIDEO_URL to videoUrl,
                KEY_VIDEO_TITLE to title,
                KEY_CHANNEL_NAME to channelName,
                KEY_DURATION to duration,
                KEY_THUMBNAIL_URL to thumbnailUrl
            )

            // Use UNMETERED (WiFi) if WiFi only, otherwise any CONNECTED network
            val networkType = if (requireWifiOnly) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }

            return OneTimeWorkRequestBuilder<VideoDownloadWorker>()
                .setInputData(inputData)
                .addTag("download_$videoId")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType)
                        .build()
                )
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return@withContext Result.failure()
        val videoUrl = inputData.getString(KEY_VIDEO_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_VIDEO_TITLE) ?: "Unknown"

        Log.d(TAG, "Starting download for: $title")

        try {
            // Create download directory
            val downloadDir = File(applicationContext.filesDir, "downloads")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            // Create output file
            val outputFile = File(downloadDir, "$videoId.mp4")

            // Check if already downloaded
            if (outputFile.exists()) {
                Log.d(TAG, "Video already downloaded: $videoId")
                return@withContext Result.success(
                    workDataOf(KEY_FILE_PATH to outputFile.absolutePath)
                )
            }

            // Download file
            val url = URL(videoUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Origin", "https://www.youtube.com")
                setRequestProperty("Referer", "https://www.youtube.com/")
                connectTimeout = 30000
                readTimeout = 30000
            }

            val contentLength = connection.contentLength.toLong()
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Report progress
                        if (contentLength > 0) {
                            val progress = ((downloadedBytes * 100) / contentLength).toInt()
                            setProgress(workDataOf(KEY_PROGRESS to progress))
                        }
                    }
                }
            }

            Log.d(TAG, "Download completed: $title")
            Result.success(workDataOf(KEY_FILE_PATH to outputFile.absolutePath))

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $title", e)
            Result.failure()
        }
    }
}
