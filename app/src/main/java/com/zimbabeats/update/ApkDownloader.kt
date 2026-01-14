package com.zimbabeats.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import java.io.File

/**
 * In-app APK downloader that bypasses browser restrictions.
 * Uses Android's DownloadManager for reliable background downloads.
 */
class ApkDownloader(private val context: Context) {

    companion object {
        private const val TAG = "ApkDownloader"
        private const val APK_FILE_NAME = "ZimbaBeats-update.apk"
        private const val MIME_TYPE_APK = "application/vnd.android.package-archive"
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var currentDownloadId: Long = -1

    /**
     * Download APK from URL and return progress updates
     */
    fun downloadApk(url: String, version: String): Flow<DownloadState> = callbackFlow {
        try {
            // Delete old APK if exists
            deleteOldApk()

            val fileName = "ZimbaBeats-$version.apk"
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("ZimbaBeats Update")
                setDescription("Downloading version $version")
                setMimeType(MIME_TYPE_APK)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

                // Allow download over mobile and wifi
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
                )

                // Required for Android 9+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setRequiresCharging(false)
                    setRequiresDeviceIdle(false)
                }
            }

            currentDownloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download started with ID: $currentDownloadId")

            trySend(DownloadState.Downloading(0))

            // Register receiver for download completion
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == currentDownloadId) {
                        val query = DownloadManager.Query().setFilterById(currentDownloadId)
                        val cursor = downloadManager.query(query)

                        if (cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = cursor.getInt(statusIndex)

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                    val localUri = cursor.getString(uriIndex)
                                    Log.d(TAG, "Download complete: $localUri")
                                    trySend(DownloadState.Completed(localUri))
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                    val reason = cursor.getInt(reasonIndex)
                                    Log.e(TAG, "Download failed with reason: $reason")
                                    trySend(DownloadState.Failed("Download failed (error: $reason)"))
                                }
                            }
                        }
                        cursor.close()
                    }
                }
            }

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            // Poll for progress updates
            while (true) {
                val query = DownloadManager.Query().setFilterById(currentDownloadId)
                val cursor = downloadManager.query(query)

                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)

                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                        val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                        val bytesTotal = cursor.getLong(bytesTotalIndex)

                        val progress = if (bytesTotal > 0) {
                            ((bytesDownloaded * 100) / bytesTotal).toInt()
                        } else {
                            0
                        }

                        trySend(DownloadState.Downloading(progress))
                    } else if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        cursor.close()
                        break
                    }
                }
                cursor.close()
                delay(500) // Poll every 500ms
            }

            awaitClose {
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    // Receiver might already be unregistered
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            trySend(DownloadState.Failed(e.message ?: "Unknown error"))
            close()
        }
    }

    /**
     * Install APK from local URI
     */
    fun installApk(localUri: String) {
        try {
            val file = File(Uri.parse(localUri).path ?: return)

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
     * Install APK from Downloads folder by version
     */
    fun installApkFromDownloads(version: String) {
        try {
            val fileName = "ZimbaBeats-$version.apk"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            if (!file.exists()) {
                Log.e(TAG, "APK file not found: ${file.absolutePath}")
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
            Log.e(TAG, "Failed to install APK from downloads", e)
        }
    }

    /**
     * Cancel current download
     */
    fun cancelDownload() {
        if (currentDownloadId != -1L) {
            downloadManager.remove(currentDownloadId)
            currentDownloadId = -1
            Log.d(TAG, "Download cancelled")
        }
    }

    /**
     * Delete old APK files
     */
    private fun deleteOldApk() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.listFiles()?.filter {
                it.name.startsWith("ZimbaBeats") && it.name.endsWith(".apk")
            }?.forEach {
                it.delete()
                Log.d(TAG, "Deleted old APK: ${it.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete old APK", e)
        }
    }
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
