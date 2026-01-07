package com.zimbabeats.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.zimbabeats.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles checking for app updates via GitHub Releases API.
 */
class UpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_API_BASE = "https://api.github.com/repos"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 15000
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Check for updates from GitHub releases.
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val repo = BuildConfig.GITHUB_REPO
            val url = URL("$GITHUB_API_BASE/$repo/releases/latest")

            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ZimbaBeats-Android")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "GitHub API returned $responseCode")
                    return@withContext UpdateResult.Error("Server returned error: $responseCode")
                }

                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val release = json.decodeFromString<GitHubRelease>(responseBody)

                val latestVersion = release.tagName.removePrefix("v").removePrefix("V")
                val currentVersion = getCurrentVersion()

                Log.d(TAG, "Current: $currentVersion, Latest: $latestVersion")

                if (isNewerVersion(currentVersion, latestVersion)) {
                    UpdateResult.UpdateAvailable(
                        version = latestVersion,
                        url = release.htmlUrl,
                        notes = release.body ?: "No release notes available.",
                        releaseName = release.name ?: "Version $latestVersion"
                    )
                } else {
                    UpdateResult.NoUpdate
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            UpdateResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    /**
     * Get the current app version from BuildConfig.
     */
    fun getCurrentVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    /**
     * Compare version strings to determine if latest is newer than current.
     * Supports semantic versioning (e.g., 1.0.0, 1.1.0, 2.0.0)
     */
    fun isNewerVersion(current: String, latest: String): Boolean {
        try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(currentParts.size, latestParts.size)

            for (i in 0 until maxLength) {
                val currentPart = currentParts.getOrElse(i) { 0 }
                val latestPart = latestParts.getOrElse(i) { 0 }

                when {
                    latestPart > currentPart -> return true
                    latestPart < currentPart -> return false
                }
            }
            return false // Versions are equal
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: $current vs $latest", e)
            return false
        }
    }

    /**
     * Open the download page in a browser.
     */
    fun openDownloadPage(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open download page", e)
        }
    }

    /**
     * Open the Buy Me a Coffee page.
     */
    fun openBuyMeCoffee() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.BUY_COFFEE_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Buy Me a Coffee page", e)
        }
    }
}

/**
 * Result of an update check.
 */
sealed class UpdateResult {
    data class UpdateAvailable(
        val version: String,
        val url: String,
        val notes: String,
        val releaseName: String
    ) : UpdateResult()

    object NoUpdate : UpdateResult()

    data class Error(val message: String) : UpdateResult()
}

/**
 * GitHub Release API response model.
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("body")
    val body: String? = null,

    @SerialName("html_url")
    val htmlUrl: String,

    @SerialName("published_at")
    val publishedAt: String? = null
)
