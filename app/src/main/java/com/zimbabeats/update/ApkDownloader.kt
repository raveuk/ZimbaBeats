package com.zimbabeats.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app APK downloader that uses private cache directory.
 * No storage permissions required - works on all Android versions.
 */
class ApkDownloader(private val context: Context) {

    companion object {
        private const val TAG = "ApkDownloader"
        private const val MIME_TYPE_APK = "application/vnd.android.package-archive"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000
    }

    // Use app's private cache directory - no permissions needed
    private val apkCacheDir: File
        get() = File(context.cacheDir, "apk_updates").also { it.mkdirs() }

    private var currentDownloadFile: File? = null

    /**
     * Download APK from URL and return progress updates.
     * Downloads to app's private cache directory - no permissions required.
     */
    fun downloadApk(url: String, version: String): Flow<DownloadState> = flow {
        try {
            // Delete old APKs first
            deleteOldApk()

            val fileName = "ZimbaBeats-$version.apk"
            val outputFile = File(apkCacheDir, fileName)
            currentDownloadFile = outputFile

            Log.d(TAG, "Starting download to: ${outputFile.absolutePath}")
            emit(DownloadState.Downloading(0))

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", MIME_TYPE_APK)

            // Follow redirects (GitHub releases use redirects)
            connection.instanceFollowRedirects = true

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadState.Failed("Server returned HTTP $responseCode"))
                return@flow
            }

            val fileLength = connection.contentLength.toLong()
            Log.d(TAG, "File size: $fileLength bytes")

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesDownloaded = 0L
                    var bytesRead: Int
                    var lastProgress = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        // Calculate and emit progress
                        val progress = if (fileLength > 0) {
                            ((bytesDownloaded * 100) / fileLength).toInt()
                        } else {
                            // If content length unknown, show indeterminate-ish progress
                            ((bytesDownloaded / 1024) % 100).toInt()
                        }

                        // Only emit when progress changes to avoid flooding
                        if (progress != lastProgress) {
                            emit(DownloadState.Downloading(progress))
                            lastProgress = progress
                        }
                    }
                }
            }

            connection.disconnect()

            // Verify file was downloaded
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Download complete: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                emit(DownloadState.Downloading(100))
                emit(DownloadState.Completed(outputFile.absolutePath))
            } else {
                emit(DownloadState.Failed("Download failed - file is empty"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            emit(DownloadState.Failed(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Install APK from the cached file
     */
    fun installApk(filePath: String) {
        try {
            val file = File(filePath)

            if (!file.exists()) {
                Log.e(TAG, "APK file not found: $filePath")
                return
            }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_TYPE_APK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
            Log.d(TAG, "Install intent started for: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK", e)
        }
    }

    /**
     * Install APK by version (looks in cache directory)
     */
    fun installApkFromDownloads(version: String) {
        val fileName = "ZimbaBeats-$version.apk"
        val file = File(apkCacheDir, fileName)

        if (file.exists()) {
            installApk(file.absolutePath)
        } else {
            Log.e(TAG, "APK file not found: ${file.absolutePath}")
        }
    }

    /**
     * Cancel current download (best effort - closes connection on next read)
     */
    fun cancelDownload() {
        currentDownloadFile?.let { file ->
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Cancelled download, deleted partial file")
            }
        }
        currentDownloadFile = null
    }

    /**
     * Delete old APK files from cache
     */
    private fun deleteOldApk() {
        try {
            apkCacheDir.listFiles()?.filter {
                it.name.endsWith(".apk")
            }?.forEach {
                it.delete()
                Log.d(TAG, "Deleted old APK: ${it.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete old APK", e)
        }
    }

    /**
     * Get the cache directory path (for FileProvider configuration)
     */
    fun getCacheDir(): File = apkCacheDir
}

/**
 * Download state for UI updates
 */
sealed class DownloadState {
    data class Downloading(val progress: Int) : DownloadState()
    data class Completed(val localUri: String) : DownloadState()
    data class Failed(val error: String) : DownloadState()
    object Idle : DownloadState()
}
