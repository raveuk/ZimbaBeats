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
 * YouTube Innertube API client for extracting stream URLs
 * Based on the internal API YouTube uses
 */
class InnertubeClient(private val httpClient: HttpClient) {

    companion object {
        private const val TAG = "InnertubeClient"
        private const val INNERTUBE_PLAYER_URL = "https://www.youtube.com/youtubei/v1/player"
        private const val INNERTUBE_SEARCH_URL = "https://www.youtube.com/youtubei/v1/search"
        private const val SUGGEST_URL = "https://suggestqueries-clients6.youtube.com/complete/search"
        private const val INNERTUBE_KEY = "" // Set via local.properties

        // Use ANDROID client for stream URLs (more reliable for direct playback)
        private const val PLAYER_CLIENT_NAME = "ANDROID"
        private const val PLAYER_CLIENT_VERSION = "19.09.37"
        private const val ANDROID_SDK_VERSION = 30
        private const val ANDROID_OS_VERSION = "11"

        // Use WEB client for search (WEB_KIDS no longer works - returns 400 errors)
        // Safety mode is controlled via user.enableSafetyMode in context
        private const val SEARCH_CLIENT_NAME = "WEB"
        private const val SEARCH_CLIENT_VERSION = "2.20250222.10.00" // Updated version
    }

    // Flag to enable kid-safe mode
    var kidSafeModeEnabled: Boolean = false

    /**
     * Extract stream URLs for a video using Innertube API
     */
    suspend fun getStreamUrls(videoId: String): List<StreamUrl> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching stream URLs for video: $videoId")

            // Build Innertube request payload using ANDROID client
            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", PLAYER_CLIENT_NAME)
                        put("clientVersion", PLAYER_CLIENT_VERSION)
                        put("androidSdkVersion", ANDROID_SDK_VERSION)
                        put("osVersion", ANDROID_OS_VERSION)
                        put("osName", "Android")
                        put("hl", "en")
                        put("gl", "US")
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            // Make POST request to Innertube API with Android headers
            // Don't include empty key parameter - Innertube works without it
            val playerUrl = if (INNERTUBE_KEY.isNotEmpty()) {
                "$INNERTUBE_PLAYER_URL?key=$INNERTUBE_KEY"
            } else {
                INNERTUBE_PLAYER_URL
            }

            val response: String = httpClient.post(playerUrl) {
                contentType(ContentType.Application.Json)
                header("User-Agent", "com.google.android.youtube/$PLAYER_CLIENT_VERSION (Linux; U; Android $ANDROID_OS_VERSION) gzip")
                header("X-YouTube-Client-Name", "3") // Android client
                header("X-YouTube-Client-Version", PLAYER_CLIENT_VERSION)
                setBody(requestBody.toString())
            }.bodyAsText()

            Log.d(TAG, "API Response length: ${response.length}")

            // Parse JSON response
            val json = Json { ignoreUnknownKeys = true }
            val jsonElement = json.parseToJsonElement(response)
            val jsonObject = jsonElement.jsonObject

            Log.d(TAG, "Response JSON keys: ${jsonObject.keys}")
            Log.d(TAG, "Response preview: ${response.take(1000)}")

            // Check playability status
            val playabilityStatus = jsonObject["playabilityStatus"]?.jsonObject
            val status = playabilityStatus?.get("status")?.jsonPrimitive?.contentOrNull
            val reason = playabilityStatus?.get("reason")?.jsonPrimitive?.contentOrNull
            Log.d(TAG, "Playability status: $status, reason: $reason")

            val streamUrls = mutableListOf<StreamUrl>()

            // Extract streamingData
            val streamingData = jsonObject["streamingData"]?.jsonObject
            Log.d(TAG, "streamingData found: ${streamingData != null}")

            streamingData?.let { data ->
                Log.d(TAG, "streamingData keys: ${data.keys}")

                // Get HLS manifest URL (adaptive streaming - preferred)
                data["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull?.let { hlsUrl ->
                    streamUrls.add(
                        StreamUrl(
                            url = hlsUrl,
                            quality = "adaptive",
                            format = "hls",
                            isVideoOnly = false
                        )
                    )
                    Log.d(TAG, "Found HLS manifest URL")
                }

                // Get regular formats (video + audio combined) - ALL qualities
                val formats = data["formats"]?.jsonArray
                Log.d(TAG, "formats array size: ${formats?.size ?: 0}")
                formats?.forEach { format ->
                    val formatObj = format.jsonObject
                    val url = formatObj["url"]?.jsonPrimitive?.contentOrNull

                    if (url != null) {
                        val quality = formatObj["qualityLabel"]?.jsonPrimitive?.contentOrNull
                            ?: formatObj["quality"]?.jsonPrimitive?.contentOrNull
                            ?: "unknown"
                        val mimeType = formatObj["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                        val audioChannels = formatObj["audioChannels"]?.jsonPrimitive?.intOrNull
                        val hasAudio = audioChannels != null && audioChannels > 0

                        streamUrls.add(
                            StreamUrl(
                                url = url,
                                quality = quality,
                                format = extractFormatFromMime(mimeType),
                                isVideoOnly = !hasAudio
                            )
                        )
                        Log.d(TAG, "Added video+audio format: $quality, hasAudio: $hasAudio")
                    }
                }

                // Get adaptive formats - HIGH QUALITY video-only streams
                val adaptiveFormats = data["adaptiveFormats"]?.jsonArray
                Log.d(TAG, "adaptiveFormats array size: ${adaptiveFormats?.size ?: 0}")

                // Extract high quality VIDEO streams (video-only)
                adaptiveFormats
                    ?.filter { format ->
                        val mimeType = format.jsonObject["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                        mimeType.startsWith("video/")
                    }
                    ?.sortedByDescending { format ->
                        // Sort by quality: get height (e.g., 1080, 720, 480, etc.)
                        format.jsonObject["height"]?.jsonPrimitive?.intOrNull ?: 0
                    }
                    ?.forEach { videoFormat ->
                        val formatObj = videoFormat.jsonObject
                        val url = formatObj["url"]?.jsonPrimitive?.contentOrNull

                        if (url != null) {
                            val height = formatObj["height"]?.jsonPrimitive?.intOrNull ?: 0
                            val quality = formatObj["qualityLabel"]?.jsonPrimitive?.contentOrNull ?: "${height}p"
                            val mimeType = formatObj["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                            val fps = formatObj["fps"]?.jsonPrimitive?.intOrNull ?: 30

                            streamUrls.add(
                                StreamUrl(
                                    url = url,
                                    quality = "$quality (video-only)",
                                    format = extractFormatFromMime(mimeType),
                                    isVideoOnly = true
                                )
                            )
                            Log.d(TAG, "Added video-only stream: $quality @ ${fps}fps")
                        }
                    }

                // Extract high quality AUDIO streams
                adaptiveFormats
                    ?.filter { format ->
                        val mimeType = format.jsonObject["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                        mimeType.startsWith("audio/")
                    }
                    ?.sortedByDescending { format ->
                        format.jsonObject["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                    }
                    ?.take(3) // Get top 3 audio streams
                    ?.forEach { audioFormat ->
                        val formatObj = audioFormat.jsonObject
                        val url = formatObj["url"]?.jsonPrimitive?.contentOrNull

                        if (url != null) {
                            val bitrate = formatObj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                            val mimeType = formatObj["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
                            val audioQuality = formatObj["audioQuality"]?.jsonPrimitive?.contentOrNull ?: "unknown"

                            streamUrls.add(
                                StreamUrl(
                                    url = url,
                                    quality = "${bitrate / 1000}kbps ($audioQuality)",
                                    format = extractFormatFromMime(mimeType),
                                    isVideoOnly = false
                                )
                            )
                            Log.d(TAG, "Added audio stream: ${bitrate / 1000}kbps, quality: $audioQuality")
                        }
                    }

                Log.d(TAG, "Total streams found (video+audio): ${streamUrls.size}")
            }

            Log.d(TAG, "Extracted ${streamUrls.size} stream URLs for $videoId")
            streamUrls

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract stream URLs for $videoId", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Search for videos using Innertube API
     * Uses WEB client with enableSafetyMode in user context for content filtering
     * CloudContentFilter provides additional age-specific filtering
     */
    suspend fun searchVideos(query: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for: $query (safetyMode: $kidSafeModeEnabled)")

            // Build Innertube search request with user context for safety mode
            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", SEARCH_CLIENT_NAME)
                        put("clientVersion", SEARCH_CLIENT_VERSION)
                        put("hl", "en")
                        put("gl", "US")
                    }
                    // Add user context with safety mode settings
                    putJsonObject("user") {
                        put("enableSafetyMode", kidSafeModeEnabled)
                        put("lockedSafetyMode", false)
                    }
                }
                put("query", query)
            }

            // Make POST request to Innertube search API
            // Don't include empty key parameter - Innertube works without it
            val searchUrl = if (INNERTUBE_KEY.isNotEmpty()) {
                "$INNERTUBE_SEARCH_URL?key=$INNERTUBE_KEY"
            } else {
                INNERTUBE_SEARCH_URL
            }

            Log.d(TAG, "Making search request to: $searchUrl")
            Log.d(TAG, "Request body: ${requestBody.toString()}")

            val response: HttpResponse = httpClient.post(searchUrl) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            Log.d(TAG, "Response status: ${response.status}")
            val responseBody: String = response.bodyAsText()
            Log.d(TAG, "API Response length: ${responseBody.length} characters")
            if (responseBody.length < 2000) {
                Log.d(TAG, "API Response full: $responseBody")
            } else {
                Log.d(TAG, "API Response preview: ${responseBody.take(1000)}")
            }

            // Parse JSON response
            val json = Json { ignoreUnknownKeys = true }
            val jsonElement = json.parseToJsonElement(responseBody)
            val jsonObject = jsonElement.jsonObject

            Log.d(TAG, "Parsed JSON successfully, keys: ${jsonObject.keys}")

            val videos = mutableListOf<YouTubeVideo>()

            // Navigate to search results
            val contents = jsonObject["contents"]?.jsonObject
                ?.get("twoColumnSearchResultsRenderer")?.jsonObject
                ?.get("primaryContents")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

            Log.d(TAG, "Found contents array: ${contents != null}, size: ${contents?.size ?: 0}")

            contents?.forEach { section ->
                val itemSection = section.jsonObject["itemSectionRenderer"]?.jsonObject
                val sectionContents = itemSection?.get("contents")?.jsonArray

                sectionContents?.forEach { item ->
                    val videoRenderer = item.jsonObject["videoRenderer"]?.jsonObject
                    Log.d(TAG, "Item keys: ${item.jsonObject.keys}, hasVideoRenderer: ${videoRenderer != null}")

                    videoRenderer?.let { video ->
                        val videoId = video["videoId"]?.jsonPrimitive?.contentOrNull ?: return@let

                        val title = video["title"]?.jsonObject
                            ?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

                        val ownerTextRuns = video["ownerText"]?.jsonObject
                            ?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject

                        val channelName = ownerTextRuns
                            ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

                        // Extract channelId from navigationEndpoint.browseEndpoint.browseId
                        val channelId = ownerTextRuns
                            ?.get("navigationEndpoint")?.jsonObject
                            ?.get("browseEndpoint")?.jsonObject
                            ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: ""

                        val thumbnail = video["thumbnail"]?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject // Get highest quality
                            ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

                        val viewCountText = video["viewCountText"]?.jsonObject
                            ?.get("simpleText")?.jsonPrimitive?.contentOrNull ?: "0"

                        val publishedTimeText = video["publishedTimeText"]?.jsonObject
                            ?.get("simpleText")?.jsonPrimitive?.contentOrNull

                        videos.add(
                            YouTubeVideo(
                                id = videoId,
                                title = title,
                                description = null,
                                thumbnailUrl = thumbnail,
                                channelName = channelName,
                                channelId = channelId,
                                duration = 0,
                                viewCount = parseViewCount(viewCountText),
                                publishedAt = System.currentTimeMillis(),
                                url = "https://www.youtube.com/watch?v=$videoId",
                                category = null,
                                ageLimit = 0
                            )
                        )
                    }
                }
            }

            Log.d(TAG, "Found ${videos.size} videos for query: $query")
            videos

        } catch (e: Exception) {
            Log.e(TAG, "Failed to search for: $query", e)
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseViewCount(viewCountText: String): Long {
        return try {
            viewCountText
                .replace(" views", "")
                .replace(",", "")
                .replace("K", "000")
                .replace("M", "000000")
                .replace("B", "000000000")
                .toLongOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun extractFormatFromMime(mimeType: String): String {
        return when {
            mimeType.contains("mp4") -> "mp4"
            mimeType.contains("webm") -> "webm"
            mimeType.contains("m4a") -> "m4a"
            mimeType.contains("opus") -> "opus"
            else -> "unknown"
        }
    }

    /**
     * Get search suggestions/autocomplete for a query
     * Uses YouTube's suggest API which handles typos and provides corrections
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            Log.d(TAG, "Getting suggestions for: $query")

            // YouTube suggest API returns JSONP, we need to parse it
            val response: String = httpClient.get(SUGGEST_URL) {
                parameter("client", "youtube")
                parameter("ds", "yt")
                parameter("q", query)
            }.bodyAsText()

            Log.d(TAG, "Suggestions response length: ${response.length}")

            // Parse JSONP response: window.google.ac.h(["query",[["suggestion1","",[]],["suggestion2","",[]]],...])
            // Extract the JSON array from the JSONP wrapper
            val jsonStart = response.indexOf("[[")
            val jsonEnd = response.lastIndexOf("]]") + 2

            if (jsonStart > 0 && jsonEnd > jsonStart) {
                val jsonArrayStr = response.substring(jsonStart, jsonEnd)
                val json = Json { ignoreUnknownKeys = true }
                val jsonArray = json.parseToJsonElement(jsonArrayStr).jsonArray

                val suggestions = mutableListOf<String>()
                jsonArray.forEach { item ->
                    if (item is JsonArray && item.size > 0) {
                        val suggestion = item[0].jsonPrimitive.contentOrNull
                        if (!suggestion.isNullOrBlank()) {
                            suggestions.add(suggestion)
                        }
                    }
                }

                Log.d(TAG, "Found ${suggestions.size} suggestions for: $query")
                suggestions.take(10) // Return top 10 suggestions
            } else {
                Log.d(TAG, "Could not parse suggestions response")
                emptyList()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get suggestions for: $query", e)
            emptyList()
        }
    }

    /**
     * Search result with optional spelling correction
     */
    data class SearchResultWithCorrection(
        val videos: List<YouTubeVideo>,
        val correctedQuery: String? = null,
        val originalQuery: String
    )

    /**
     * Search for videos with spelling correction support
     * Uses WEB client with enableSafetyMode in user context for content filtering
     * CloudContentFilter provides additional age-specific filtering
     */
    suspend fun searchVideosWithCorrection(query: String): SearchResultWithCorrection = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching with correction for: $query (safetyMode: $kidSafeModeEnabled)")

            // Build Innertube search request with user context for safety mode
            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", SEARCH_CLIENT_NAME)
                        put("clientVersion", SEARCH_CLIENT_VERSION)
                        put("hl", "en")
                        put("gl", "US")
                    }
                    // Add user context with safety mode settings
                    putJsonObject("user") {
                        put("enableSafetyMode", kidSafeModeEnabled)
                        put("lockedSafetyMode", false)
                    }
                }
                put("query", query)
            }

            // Don't include empty key parameter - Innertube works without it
            val searchUrl = if (INNERTUBE_KEY.isNotEmpty()) {
                "$INNERTUBE_SEARCH_URL?key=$INNERTUBE_KEY"
            } else {
                INNERTUBE_SEARCH_URL
            }

            val response: String = httpClient.post(searchUrl) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }.bodyAsText()

            val json = Json { ignoreUnknownKeys = true }
            val jsonElement = json.parseToJsonElement(response)
            val jsonObject = jsonElement.jsonObject

            val videos = mutableListOf<YouTubeVideo>()
            var correctedQuery: String? = null

            // Check for "showingResultsFor" (spelling correction)
            val contents = jsonObject["contents"]?.jsonObject
                ?.get("twoColumnSearchResultsRenderer")?.jsonObject
                ?.get("primaryContents")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

            contents?.forEach { section ->
                val itemSection = section.jsonObject["itemSectionRenderer"]?.jsonObject
                val sectionContents = itemSection?.get("contents")?.jsonArray

                sectionContents?.forEach { item ->
                    // Check for spelling correction
                    val showingResultsFor = item.jsonObject["showingResultsForRenderer"]?.jsonObject
                    showingResultsFor?.let { correction ->
                        val correctedQueryObj = correction["correctedQuery"]?.jsonObject
                        correctedQuery = correctedQueryObj?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.contentOrNull

                        Log.d(TAG, "Found spelling correction: $correctedQuery")
                    }

                    // Also check for "didYouMeanRenderer"
                    val didYouMean = item.jsonObject["didYouMeanRenderer"]?.jsonObject
                    didYouMean?.let { suggestion ->
                        val correctedQueryObj = suggestion["correctedQuery"]?.jsonObject
                        if (correctedQuery == null) {
                            correctedQuery = correctedQueryObj?.get("runs")?.jsonArray
                                ?.firstOrNull()?.jsonObject
                                ?.get("text")?.jsonPrimitive?.contentOrNull

                            Log.d(TAG, "Found 'did you mean': $correctedQuery")
                        }
                    }

                    // Parse video results
                    val videoRenderer = item.jsonObject["videoRenderer"]?.jsonObject
                    videoRenderer?.let { video ->
                        val videoId = video["videoId"]?.jsonPrimitive?.contentOrNull ?: return@let

                        val title = video["title"]?.jsonObject
                            ?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

                        val ownerTextRuns2 = video["ownerText"]?.jsonObject
                            ?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject

                        val channelName = ownerTextRuns2
                            ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

                        // Extract channelId from navigationEndpoint.browseEndpoint.browseId
                        val channelId = ownerTextRuns2
                            ?.get("navigationEndpoint")?.jsonObject
                            ?.get("browseEndpoint")?.jsonObject
                            ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: ""

                        val thumbnail = video["thumbnail"]?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject
                            ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

                        val viewCountText = video["viewCountText"]?.jsonObject
                            ?.get("simpleText")?.jsonPrimitive?.contentOrNull ?: "0"

                        videos.add(
                            YouTubeVideo(
                                id = videoId,
                                title = title,
                                description = null,
                                thumbnailUrl = thumbnail,
                                channelName = channelName,
                                channelId = channelId,
                                duration = 0,
                                viewCount = parseViewCount(viewCountText),
                                publishedAt = System.currentTimeMillis(),
                                url = "https://www.youtube.com/watch?v=$videoId",
                                category = null,
                                ageLimit = 0
                            )
                        )
                    }
                }
            }

            Log.d(TAG, "Found ${videos.size} videos, correction: $correctedQuery")
            SearchResultWithCorrection(videos, correctedQuery, query)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to search with correction for: $query", e)
            SearchResultWithCorrection(emptyList(), null, query)
        }
    }

    /**
     * Get trending/popular videos by searching for popular content
     * YouTube's browse API for trending requires special authentication,
     * so we use search with popular queries instead
     */
    suspend fun getTrendingVideos(): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching popular videos via search")

            // Search for popular kid-friendly content
            val popularQueries = listOf(
                "kids songs 2024",
                "nursery rhymes",
                "cartoon for kids"
            )

            val allVideos = mutableListOf<YouTubeVideo>()

            for (query in popularQueries) {
                try {
                    val results = searchVideos(query)
                    Log.d(TAG, "Popular search '$query': ${results.size} videos")
                    allVideos.addAll(results.take(10))
                    if (allVideos.size >= 20) break
                } catch (e: Exception) {
                    Log.e(TAG, "Failed search for '$query': ${e.message}")
                }
            }

            // Remove duplicates
            val uniqueVideos = allVideos.distinctBy { it.id }
            Log.d(TAG, "Found ${uniqueVideos.size} unique popular videos")
            uniqueVideos

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch trending videos", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Parse a video from various renderer types
     */
    private fun parseVideoFromRenderer(item: JsonObject): YouTubeVideo? {
        // Try videoRenderer
        val videoRenderer = item["videoRenderer"]?.jsonObject
            ?: item["gridVideoRenderer"]?.jsonObject
            ?: item["compactVideoRenderer"]?.jsonObject

        return videoRenderer?.let { video ->
            val videoId = video["videoId"]?.jsonPrimitive?.contentOrNull ?: return null

            val title = video["title"]?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
                ?: video["title"]?.jsonObject
                    ?.get("simpleText")?.jsonPrimitive?.contentOrNull
                ?: "Unknown"

            // Try ownerText first, then shortBylineText for channel info
            val ownerTextRuns = video["ownerText"]?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?: video["shortBylineText"]?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject

            val channelName = ownerTextRuns
                ?.get("text")?.jsonPrimitive?.contentOrNull
                ?: "Unknown"

            // Extract channelId from navigationEndpoint.browseEndpoint.browseId
            val channelId = ownerTextRuns
                ?.get("navigationEndpoint")?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: ""

            val thumbnail = video["thumbnail"]?.jsonObject
                ?.get("thumbnails")?.jsonArray
                ?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

            val viewCountText = video["viewCountText"]?.jsonObject
                ?.get("simpleText")?.jsonPrimitive?.contentOrNull
                ?: video["shortViewCountText"]?.jsonObject
                    ?.get("simpleText")?.jsonPrimitive?.contentOrNull
                ?: "0"

            YouTubeVideo(
                id = videoId,
                title = title,
                description = null,
                thumbnailUrl = thumbnail,
                channelName = channelName,
                channelId = channelId,
                duration = 0,
                viewCount = parseViewCount(viewCountText),
                publishedAt = System.currentTimeMillis(),
                url = "https://www.youtube.com/watch?v=$videoId",
                category = null,
                ageLimit = 0
            )
        }
    }
}
