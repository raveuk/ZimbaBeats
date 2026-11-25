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
        // Search pagination caps: stop after this many results or pages, whichever comes first.
        private const val MAX_SEARCH_RESULTS = 200
        private const val MAX_SEARCH_PAGES = 10
        private const val SUGGEST_URL = "https://suggestqueries-clients6.youtube.com/complete/search"
        private const val INNERTUBE_KEY = "" // Set via local.properties

        // Updated to match YouTubeClient.ANDROID (yt-dlp INNERTUBE_CLIENTS)
        private const val PLAYER_CLIENT_NAME = "ANDROID"
        private const val PLAYER_CLIENT_VERSION = "21.26.364"
        private const val ANDROID_SDK_VERSION = 30
        private const val ANDROID_OS_VERSION = "11"
        private const val ANDROID_USER_AGENT = "com.google.android.youtube/$PLAYER_CLIENT_VERSION (Linux; U; Android $ANDROID_OS_VERSION) gzip"

        private const val IOS_CLIENT_NAME = "IOS"
        private const val IOS_CLIENT_VERSION = "19.45.4"
        private const val IOS_USER_AGENT = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"

        // Use WEB client for search (WEB_KIDS no longer works - returns 400 errors)
        // Safety mode is controlled via user.enableSafetyMode in context
        // Updated to latest production version
        private const val SEARCH_CLIENT_NAME = "WEB"
        private const val SEARCH_CLIENT_VERSION = "2.20250312.04.00"
    }

    // Flag to enable kid-safe mode
    var kidSafeModeEnabled: Boolean = false

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fast targeted extraction of JSON objects assigned to `"$key": { ... }` inside a large
     * YouTube InnerTube response (~1.8 MB for WEB search).
     * Avoids parsing a 200,000-node JsonElement DOM for the 97% of the payload we don't use.
     */
    private fun extractJsonObjectsByKey(source: String, key: String): List<JsonObject> {
        val needle = "\"$key\":"
        val results = mutableListOf<JsonObject>()
        var searchFrom = 0
        val length = source.length

        while (searchFrom < length) {
            val keyIdx = source.indexOf(needle, searchFrom)
            if (keyIdx < 0) break

            var i = keyIdx + needle.length
            while (i < length && source[i].isWhitespace()) i++
            if (i >= length || source[i] != '{') {
                searchFrom = i
                continue
            }

            val objStart = i
            var depth = 0
            var inString = false
            var escaped = false

            while (i < length) {
                val c = source[i]
                if (escaped) {
                    escaped = false
                } else if (c == '\\' && inString) {
                    escaped = true
                } else if (c == '"') {
                    inString = !inString
                } else if (!inString) {
                    if (c == '{') {
                        depth++
                    } else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            val objSlice = source.substring(objStart, i + 1)
                            try {
                                results.add(json.parseToJsonElement(objSlice).jsonObject)
                            } catch (_: Exception) {
                            }
                            searchFrom = i + 1
                            break
                        }
                    }
                }
                i++
            }
            if (depth != 0) break
        }
        return results
    }

    private fun extractContinuationTokenFromRaw(source: String): String? =
        extractJsonObjectsByKey(source, "continuationItemRenderer").firstNotNullOfOrNull {
            it["continuationEndpoint"]?.jsonObject
                ?.get("continuationCommand")?.jsonObject
                ?.get("token")?.jsonPrimitive?.contentOrNull
        }

    /**
     * Extract stream URLs for a video using Innertube API.
     * Tries ANDROID (21.26.364, works for both VOD and live HLS) and falls back to IOS (19.45.4).
     */
    suspend fun getStreamUrls(videoId: String): List<StreamUrl> = withContext(Dispatchers.IO) {
        val androidStreams = fetchStreamUrlsWithClient(videoId, useIos = false)
        if (androidStreams.isNotEmpty()) return@withContext androidStreams

        Log.d(TAG, "ANDROID client returned 0 streams for $videoId, trying IOS fallback...")
        fetchStreamUrlsWithClient(videoId, useIos = true)
    }

    private suspend fun fetchStreamUrlsWithClient(videoId: String, useIos: Boolean): List<StreamUrl> {
        return try {
            val clientLabel = if (useIos) IOS_CLIENT_NAME else PLAYER_CLIENT_NAME
            Log.d(TAG, "Fetching stream URLs for video: $videoId (client=$clientLabel)")

            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        if (useIos) {
                            put("clientName", IOS_CLIENT_NAME)
                            put("clientVersion", IOS_CLIENT_VERSION)
                            put("deviceMake", "Apple")
                            put("deviceModel", "iPhone16,2")
                            put("osName", "iPhone")
                            put("osVersion", "17.5.1.21F90")
                            put("userAgent", IOS_USER_AGENT)
                            put("hl", "en")
                            put("gl", "US")
                        } else {
                            put("clientName", PLAYER_CLIENT_NAME)
                            put("clientVersion", PLAYER_CLIENT_VERSION)
                            put("androidSdkVersion", ANDROID_SDK_VERSION)
                            put("osVersion", ANDROID_OS_VERSION)
                            put("osName", "Android")
                            put("userAgent", ANDROID_USER_AGENT)
                            put("hl", "en")
                            put("gl", "US")
                        }
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val playerUrl = if (INNERTUBE_KEY.isNotEmpty()) {
                "$INNERTUBE_PLAYER_URL?key=$INNERTUBE_KEY"
            } else {
                INNERTUBE_PLAYER_URL
            }

            val response: String = httpClient.post(playerUrl) {
                contentType(ContentType.Application.Json)
                if (useIos) {
                    header("User-Agent", IOS_USER_AGENT)
                    header("X-YouTube-Client-Name", "5")
                    header("X-YouTube-Client-Version", IOS_CLIENT_VERSION)
                } else {
                    header("User-Agent", ANDROID_USER_AGENT)
                    header("X-YouTube-Client-Name", "3")
                    header("X-YouTube-Client-Version", PLAYER_CLIENT_VERSION)
                }
                setBody(requestBody.toString())
            }.bodyAsText()

            Log.d(TAG, "API Response length ($clientLabel): ${response.length}")

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
                        val height = formatObj["height"]?.jsonPrimitive?.intOrNull ?: 0
                        val bitrate = formatObj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                        val contentLength = formatObj["contentLength"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: -1L
                        val codec = extractCodecFromMime(mimeType)
                        // Combined streams carry both tracks; codec string typically includes both,
                        // but InnerTube only emits one codecs= attribute. Assume video codec for
                        // anything that isn't a pure audio mimeType.
                        val isAudioOnlyMime = mimeType.startsWith("audio/")

                        streamUrls.add(
                            StreamUrl(
                                url = url,
                                quality = quality,
                                format = extractFormatFromMime(mimeType),
                                isVideoOnly = !hasAudio,
                                videoCodec = if (isAudioOnlyMime) null else codec,
                                audioCodec = if (isAudioOnlyMime) codec else null,
                                height = height,
                                bitrate = bitrate,
                                contentLength = contentLength
                            )
                        )
                        Log.d(TAG, "Added video+audio format: $quality, hasAudio: $hasAudio, codec: $codec, size: $contentLength")
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
                            val bitrate = formatObj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                            val contentLength = formatObj["contentLength"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: -1L
                            val codec = extractCodecFromMime(mimeType)

                            streamUrls.add(
                                StreamUrl(
                                    url = url,
                                    quality = "$quality (video-only)",
                                    format = extractFormatFromMime(mimeType),
                                    isVideoOnly = true,
                                    videoCodec = codec,
                                    audioCodec = null,
                                    height = height,
                                    bitrate = bitrate,
                                    contentLength = contentLength
                                )
                            )
                            Log.d(TAG, "Added video-only stream: $quality @ ${fps}fps, codec: $codec, size: $contentLength")
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
                            val contentLength = formatObj["contentLength"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: -1L
                            val codec = extractCodecFromMime(mimeType)

                            streamUrls.add(
                                StreamUrl(
                                    url = url,
                                    quality = "${bitrate / 1000}kbps ($audioQuality)",
                                    format = extractFormatFromMime(mimeType),
                                    isVideoOnly = false,
                                    videoCodec = null,
                                    audioCodec = codec,
                                    height = 0,
                                    bitrate = bitrate,
                                    contentLength = contentLength
                                )
                            )
                            Log.d(TAG, "Added audio stream: ${bitrate / 1000}kbps, quality: $audioQuality, codec: $codec, size: $contentLength")
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

            val responseBody: String = response.bodyAsText()

            val videos = mutableListOf<YouTubeVideo>()
            val seenIds = HashSet<String>()
            extractJsonObjectsByKey(responseBody, "videoRenderer").forEach { video ->
                parseSearchVideoRenderer(video)?.let { parsed ->
                    if (seenIds.add(parsed.id)) {
                        videos.add(parsed)
                    }
                }
            }

            Log.d(TAG, "Found ${videos.size} videos for query: $query")
            videos

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search for: $query", e)
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

    /**
     * Parse a search `videoRenderer` JSON node into a [YouTubeVideo], or null if it has no id.
     * Shared by the initial search page and continuation pages.
     */
    private fun parseSearchVideoRenderer(video: JsonObject): YouTubeVideo? {
        val videoId = video["videoId"]?.jsonPrimitive?.contentOrNull ?: return null

        val title = video["title"]?.jsonObject
            ?.get("runs")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

        val ownerTextRuns = video["ownerText"]?.jsonObject
            ?.get("runs")?.jsonArray
            ?.firstOrNull()?.jsonObject

        val channelName = ownerTextRuns?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

        val channelId = ownerTextRuns
            ?.get("navigationEndpoint")?.jsonObject
            ?.get("browseEndpoint")?.jsonObject
            ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: ""

        val thumbnail = video["thumbnail"]?.jsonObject
            ?.get("thumbnails")?.jsonArray
            ?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

        val viewCountText = video["viewCountText"]?.jsonObject
            ?.get("simpleText")?.jsonPrimitive?.contentOrNull ?: "0"

        val publishedTimeText = video["publishedTimeText"]?.jsonObject
            ?.get("simpleText")?.jsonPrimitive?.contentOrNull

        val lengthText = video["lengthText"]?.jsonObject
            ?.get("simpleText")?.jsonPrimitive?.contentOrNull

        return YouTubeVideo(
            id = videoId,
            title = title,
            description = null,
            thumbnailUrl = thumbnail,
            channelName = channelName,
            channelId = channelId,
            duration = parseDurationSeconds(lengthText),
            viewCount = parseViewCount(viewCountText),
            publishedAt = parsePublishedTime(publishedTimeText),
            url = "https://www.youtube.com/watch?v=$videoId",
            category = null,
            ageLimit = 0
        )
    }

    /**
     * Parse a YouTube duration string like "12:34" or "1:02:15" into total seconds.
     */
    private fun parseDurationSeconds(lengthText: String?): Long {
        if (lengthText.isNullOrBlank()) return 0L
        val parts = lengthText.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    /**
     * Find the "load more" continuation token in a search results / continuation-items array.
     */
    private fun extractSearchContinuationToken(items: kotlinx.serialization.json.JsonArray?): String? =
        items?.firstNotNullOfOrNull {
            it.jsonObject["continuationItemRenderer"]?.jsonObject
                ?.get("continuationEndpoint")?.jsonObject
                ?.get("continuationCommand")?.jsonObject
                ?.get("token")?.jsonPrimitive?.contentOrNull
        }

    /**
     * Convert YouTube's relative "published" text (e.g. "3 days ago", "Streamed 2 weeks ago",
     * "1 year ago") into an approximate epoch-millis upload time so results can be sorted
     * newest-first. YouTube only provides this coarse relative value in search results, so the
     * timestamp is an approximation. Falls back to "now" when the text is missing/unparseable.
     */
    private fun parsePublishedTime(publishedTimeText: String?): Long {
        val now = System.currentTimeMillis()
        if (publishedTimeText.isNullOrBlank()) return now
        val match = Regex("(\\d+)\\s*(second|minute|hour|day|week|month|year)")
            .find(publishedTimeText.lowercase()) ?: return now
        val amount = match.groupValues[1].toLongOrNull() ?: return now
        val unitMs = when (match.groupValues[2]) {
            "second" -> 1_000L
            "minute" -> 60_000L
            "hour" -> 3_600_000L
            "day" -> 86_400_000L
            "week" -> 604_800_000L
            "month" -> 2_592_000_000L      // ~30 days
            "year" -> 31_536_000_000L      // ~365 days
            else -> return now
        }
        return now - amount * unitMs
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
     * Pull the codec family out of an InnerTube mimeType.
     * Example inputs:
     *   video/mp4; codecs="avc1.640028"     -> "avc1"
     *   video/webm; codecs="vp9"            -> "vp9"
     *   audio/mp4; codecs="mp4a.40.2"       -> "mp4a"
     *   audio/webm; codecs="opus"           -> "opus"
     * Returns null if no codecs= attribute is present.
     */
    private fun extractCodecFromMime(mimeType: String): String? {
        val match = Regex("""codecs="?([^".,]+)""").find(mimeType) ?: return null
        return match.groupValues[1].substringBefore('.').lowercase()
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
    suspend fun searchVideosWithCorrection(
        query: String,
        maxResults: Int = MAX_SEARCH_RESULTS
    ): SearchResultWithCorrection = withContext(Dispatchers.IO) {
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

            val videos = mutableListOf<YouTubeVideo>()
            val seenIds = HashSet<String>()
            var correctedQuery: String? = null

            extractJsonObjectsByKey(response, "showingResultsForRenderer").firstOrNull()?.let { correction ->
                correctedQuery = correction["correctedQuery"]?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull
            }
            if (correctedQuery == null) {
                extractJsonObjectsByKey(response, "didYouMeanRenderer").firstOrNull()?.let { suggestion ->
                    correctedQuery = suggestion["correctedQuery"]?.jsonObject
                        ?.get("runs")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.contentOrNull
                }
            }

            extractJsonObjectsByKey(response, "videoRenderer").forEach { video ->
                parseSearchVideoRenderer(video)?.let { parsed ->
                    if (seenIds.add(parsed.id)) {
                        videos.add(parsed)
                    }
                }
            }

            // Paginate: follow continuation tokens to load more results (YouTube returns ~20
            // per page). Bound both total results and maximum pages ((maxResults + 19) / 20)
            // so single-page Home queries (maxResults <= 20) never paginate across 7+ pages
            // when YouTube returns shelves/shorts on page 1.
            val targetResults = maxResults.coerceIn(1, MAX_SEARCH_RESULTS)
            val maxPages = ((targetResults + 19) / 20).coerceIn(1, MAX_SEARCH_PAGES)
            var continuationToken = if (maxPages > 1) extractContinuationTokenFromRaw(response) else null
            var pageCount = 1
            while (continuationToken != null && videos.size < targetResults && pageCount < maxPages) {
                pageCount++
                Log.d(TAG, "Fetching search continuation page $pageCount (maxPages=$maxPages)...")
                val continuationBody = buildJsonObject {
                    putJsonObject("context") {
                        putJsonObject("client") {
                            put("clientName", SEARCH_CLIENT_NAME)
                            put("clientVersion", SEARCH_CLIENT_VERSION)
                            put("hl", "en")
                            put("gl", "US")
                        }
                    }
                    put("continuation", continuationToken)
                }

                val continuationResponse: String = httpClient.post(searchUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(continuationBody.toString())
                }.bodyAsText()

                val beforeCount = videos.size
                extractJsonObjectsByKey(continuationResponse, "videoRenderer").forEach { video ->
                    parseSearchVideoRenderer(video)?.let { parsed ->
                        if (seenIds.add(parsed.id)) {
                            videos.add(parsed)
                        }
                    }
                }
                if (videos.size == beforeCount) break

                continuationToken = extractContinuationTokenFromRaw(continuationResponse)
            }

            Log.d(TAG, "Found ${videos.size} videos across $pageCount page(s), correction: $correctedQuery")
            SearchResultWithCorrection(videos, correctedQuery, query)

        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search with correction for: $query", e)
            SearchResultWithCorrection(emptyList(), null, query)
        }
    }

    /**
     * Get trending/popular videos by searching for popular content
     * Uses a single page request to keep startup fast and memory-light.
     */
    suspend fun getTrendingVideos(): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching popular videos via single-page search")
            val query = if (kidSafeModeEnabled) "kids songs nursery rhymes" else "trending videos today"
            val results = searchVideosWithCorrection(query, maxResults = 20).videos
            val uniqueVideos = results.distinctBy { it.id }
            Log.d(TAG, "Found ${uniqueVideos.size} unique popular videos")
            uniqueVideos
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch trending videos", e)
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

            val publishedTimeText = video["publishedTimeText"]?.jsonObject
                ?.get("simpleText")?.jsonPrimitive?.contentOrNull

            YouTubeVideo(
                id = videoId,
                title = title,
                description = null,
                thumbnailUrl = thumbnail,
                channelName = channelName,
                channelId = channelId,
                duration = 0,
                viewCount = parseViewCount(viewCountText),
                publishedAt = parsePublishedTime(publishedTimeText),
                url = "https://www.youtube.com/watch?v=$videoId",
                category = null,
                ageLimit = 0
            )
        }
    }
}
