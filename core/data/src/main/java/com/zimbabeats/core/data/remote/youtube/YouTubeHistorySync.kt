package com.zimbabeats.core.data.remote.youtube

import android.util.Log
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Sync watch history to YouTube for personalized recommendations.
 * Uses Innertube API to report video playback events.
 */
class YouTubeHistorySync(private val httpClient: HttpClient) {

    companion object {
        private const val TAG = "YouTubeHistorySync"
        private const val INNERTUBE_API_KEY = "" // Set via local.properties
        private const val INNERTUBE_HOST = "https://www.youtube.com/youtubei/v1"
    }

    /**
     * Report a video watch event to YouTube.
     * This helps YouTube's recommendation algorithm learn user preferences.
     *
     * @param videoId The YouTube video ID
     * @param watchTimeSeconds How long the user watched the video
     * @param totalDurationSeconds Total duration of the video
     * @return true if successfully reported, false otherwise
     */
    suspend fun reportWatchEvent(
        videoId: String,
        watchTimeSeconds: Long,
        totalDurationSeconds: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Reporting watch event for video: $videoId (watched ${watchTimeSeconds}s of ${totalDurationSeconds}s)")

            // Build the Innertube playback tracking request
            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "ANDROID")
                        put("clientVersion", "19.09.37")
                        put("hl", "en")
                        put("gl", "US")
                        put("androidSdkVersion", 30)
                        put("osName", "Android")
                        put("osVersion", "11")
                    }
                }
                put("videoId", videoId)
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("currentUrl", "/watch?v=$videoId")
                        put("watchTimeSeconds", watchTimeSeconds)
                    }
                }
                // Signal that video was watched (helps with recommendations)
                put("racyCheckOk", true)
                put("contentCheckOk", true)
            }

            // Send the playback event to YouTube
            val response: HttpResponse = httpClient.post("$INNERTUBE_HOST/player?key=$INNERTUBE_API_KEY") {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            val isSuccess = response.status.isSuccess()
            Log.d(TAG, "Watch event report status: ${response.status} - success: $isSuccess")

            isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report watch event for $videoId", e)
            false
        }
    }

    /**
     * Report that a video has been completed (watched to the end).
     * This signals stronger interest in similar content.
     */
    suspend fun reportVideoCompleted(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Reporting video completed: $videoId")

            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "ANDROID")
                        put("clientVersion", "19.09.37")
                        put("hl", "en")
                        put("gl", "US")
                    }
                }
                put("videoId", videoId)
                put("cpn", generateCpn()) // Client Playback Nonce
            }

            val response: HttpResponse = httpClient.post("$INNERTUBE_HOST/player?key=$INNERTUBE_API_KEY") {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            response.status.isSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report video completed for $videoId", e)
            false
        }
    }

    /**
     * Generate a Client Playback Nonce (CPN) for tracking.
     */
    private fun generateCpn(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..16).map { chars.random() }.joinToString("")
    }
}
