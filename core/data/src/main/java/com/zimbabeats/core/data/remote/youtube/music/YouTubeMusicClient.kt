package com.zimbabeats.core.data.remote.youtube.music

import android.util.Log
import com.zimbabeats.core.domain.model.music.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * YouTube Music Innertube API client
 * Uses the WEB_REMIX client (YouTube Music web client) for music-specific content
 */
class YouTubeMusicClient(private val httpClient: HttpClient) {

    companion object {
        private const val TAG = "YouTubeMusicClient"
        private const val INNERTUBE_BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val INNERTUBE_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

        // WEB_REMIX client for YouTube Music
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20231204.01.00"

        // Browse IDs for YouTube Music sections
        private const val BROWSE_ID_HOME = "FEmusic_home"
        private const val BROWSE_ID_EXPLORE = "FEmusic_explore"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Build the context object for YouTube Music API requests
     */
    private fun buildContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", CLIENT_NAME)
            put("clientVersion", CLIENT_VERSION)
            put("hl", "en")
            put("gl", "US")
            put("platform", "DESKTOP")
            put("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }
    }

    /**
     * Search for music content (tracks, albums, artists, playlists)
     * Fetches all available results using pagination
     */
    suspend fun searchMusic(
        query: String,
        filter: MusicSearchFilter = MusicSearchFilter.ALL,
        maxResults: Int = 50 // Reasonable limit to prevent excessive API calls
    ): List<MusicSearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching music for: $query with filter: $filter (max: $maxResults)")

            val allResults = mutableListOf<MusicSearchResult>()

            // Initial search request
            val requestBody = buildJsonObject {
                put("context", buildContext())
                put("query", query)
                filter.param?.let { put("params", it) }
            }

            val response: String = httpClient.post("$INNERTUBE_BASE_URL/search?key=$INNERTUBE_KEY") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                setBody(requestBody.toString())
            }.bodyAsText()

            Log.d(TAG, "Search response length: ${response.length}")

            val jsonElement = json.parseToJsonElement(response)
            var parsed = MusicResponseParser.parseSearchResultsWithContinuation(jsonElement.jsonObject)
            allResults.addAll(parsed.results)

            Log.d(TAG, "Initial search: ${parsed.results.size} results, continuation: ${parsed.continuationToken != null}")

            // Fetch more pages if continuation token exists and we haven't hit the limit
            var continuationToken = parsed.continuationToken
            var pageCount = 1

            while (continuationToken != null && allResults.size < maxResults && pageCount < 10) {
                pageCount++
                Log.d(TAG, "Fetching continuation page $pageCount...")

                val continuationBody = buildJsonObject {
                    put("context", buildContext())
                    put("continuation", continuationToken)
                }

                val continuationResponse: String = httpClient.post("$INNERTUBE_BASE_URL/search?key=$INNERTUBE_KEY") {
                    contentType(ContentType.Application.Json)
                    header("Origin", "https://music.youtube.com")
                    header("Referer", "https://music.youtube.com/")
                    setBody(continuationBody.toString())
                }.bodyAsText()

                val continuationJson = json.parseToJsonElement(continuationResponse)
                parsed = MusicResponseParser.parseContinuationResults(continuationJson.jsonObject)
                allResults.addAll(parsed.results)
                continuationToken = parsed.continuationToken

                Log.d(TAG, "Page $pageCount: ${parsed.results.size} results, total: ${allResults.size}, more: ${continuationToken != null}")
            }

            Log.d(TAG, "Search complete: ${allResults.size} total results in $pageCount pages")
            allResults

        } catch (e: Exception) {
            Log.e(TAG, "Failed to search music: $query", e)
            emptyList()
        }
    }

    /**
     * Get the YouTube Music home page with recommendations
     */
    suspend fun getMusicHome(): List<MusicBrowseSection> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching music home page")

            val requestBody = buildJsonObject {
                put("context", buildContext())
                put("browseId", BROWSE_ID_HOME)
            }

            val response: String = httpClient.post("$INNERTUBE_BASE_URL/browse?key=$INNERTUBE_KEY") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                setBody(requestBody.toString())
            }.bodyAsText()

            Log.d(TAG, "Home response length: ${response.length}")

            val jsonElement = json.parseToJsonElement(response)
            MusicResponseParser.parseBrowseSections(jsonElement.jsonObject)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch music home", e)
            emptyList()
        }
    }

    /**
     * Get artist details
     */
    suspend fun getArtist(artistId: String): Artist? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching artist: $artistId")

            val requestBody = buildJsonObject {
                put("context", buildContext())
                put("browseId", artistId)
            }

            val response: String = httpClient.post("$INNERTUBE_BASE_URL/browse?key=$INNERTUBE_KEY") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                setBody(requestBody.toString())
            }.bodyAsText()

            val jsonElement = json.parseToJsonElement(response)
            MusicResponseParser.parseArtistPage(jsonElement.jsonObject, artistId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch artist: $artistId", e)
            null
        }
    }

    /**
     * Get artist's tracks
     */
    suspend fun getArtistTracks(artistId: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching artist tracks: $artistId")

            val requestBody = buildJsonObject {
                put("context", buildContext())
                put("browseId", artistId)
            }

            val response: String = httpClient.post("$INNERTUBE_BASE_URL/browse?key=$INNERTUBE_KEY") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                setBody(requestBody.toString())
            }.bodyAsText()

            val jsonElement = json.parseToJsonElement(response)
            MusicResponseParser.parseArtistTracks(jsonElement.jsonObject)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch artist tracks: $artistId", e)
            emptyList()
        }
    }

    /**
     * Get album details with tracks
     */
    suspend fun getAlbum(albumId: String): Album? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== Fetching album: $albumId ===")

            val requestBody = buildJsonObject {
                put("context", buildContext())
                put("browseId", albumId)
            }

            Log.d(TAG, "Album request body: ${requestBody.toString().take(500)}")

            val response: String = httpClient.post("$INNERTUBE_BASE_URL/browse?key=$INNERTUBE_KEY") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                setBody(requestBody.toString())
            }.bodyAsText()

            Log.d(TAG, "Album response length: ${response.length}")
            Log.d(TAG, "Album response preview: ${response.take(1000)}")

            val jsonElement = json.parseToJsonElement(response)
            val result = MusicResponseParser.parseAlbumPage(jsonElement.jsonObject, albumId)

            Log.d(TAG, "Album parse result: title=${result?.title}, tracks=${result?.tracks?.size ?: 0}")

            result

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch album: $albumId", e)
            null
        }
    }

    /**
     * Get YouTube Music playlist details with tracks
     */
    suspend fun getYouTubeMusicPlaylist(playlistId: String): MusicResponseParser.YouTubeMusicPlaylistWithTracks? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== Fetching YouTube Music playlist: $playlistId ===")

            val requestBody = buildJsonObject {
                put("context", buildContext())
                put("browseId", playlistId)
            }

            val response: String = httpClient.post("$INNERTUBE_BASE_URL/browse?key=$INNERTUBE_KEY") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                setBody(requestBody.toString())
            }.bodyAsText()

            Log.d(TAG, "Playlist response length: ${response.length}")

            val jsonElement = json.parseToJsonElement(response)
            MusicResponseParser.parseYouTubeMusicPlaylistPage(jsonElement.jsonObject, playlistId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch YouTube Music playlist: $playlistId", e)
            null
        }
    }

    /**
     * Result containing both stream URL and track info
     */
    data class PlayerResult(
        val streamUrl: String,
        val track: Track
    )

    /**
     * Get audio stream URL and track details for a video/track
     * Tries multiple sources in order of reliability
     */
    suspend fun getPlayerData(videoId: String): PlayerResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Getting player data for: $videoId ===")

        // Try YouTube innertube API FIRST (fastest and most reliable)
        try {
            Log.d(TAG, "Trying YouTube innertube...")
            val innertubeResult = getPlayerDataFromInnertube(videoId)
            if (innertubeResult != null) {
                Log.d(TAG, "SUCCESS: Got player data from innertube: ${innertubeResult.track.title}")
                return@withContext innertubeResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Innertube API failed: ${e.message}")
        }

        // Fallback to Invidious API (often slow/unavailable)
        try {
            Log.d(TAG, "Trying Invidious API as fallback...")
            val invidiousResult = getPlayerDataFromPiped(videoId)
            if (invidiousResult != null) {
                Log.d(TAG, "SUCCESS: Got player data from Invidious: ${invidiousResult.track.title}")
                return@withContext invidiousResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Invidious API failed: ${e.message}")
        }

        // Last resort: Piped API
        try {
            Log.d(TAG, "Trying Piped API as last resort...")
            val pipedResult = getPlayerDataFromPipedApi(videoId)
            if (pipedResult != null) {
                Log.d(TAG, "SUCCESS: Got player data from Piped: ${pipedResult.track.title}")
                return@withContext pipedResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Piped API failed: ${e.message}")
        }

        Log.e(TAG, "=== All sources failed for: $videoId ===")
        null
    }

    /**
     * Get player data from Piped API instances
     */
    private suspend fun getPlayerDataFromPipedApi(videoId: String): PlayerResult? {
        // Use full URLs to avoid string interpolation issues
        val pipedUrls = listOf(
            "https://pipedapi.kavin.rocks/streams/$videoId",
            "https://pipedapi.adminforge.de/streams/$videoId",
            "https://watchapi.whatever.social/streams/$videoId",
            "https://pipedapi.in.projectsegfau.lt/streams/$videoId"
        )

        for (url in pipedUrls) {
            try {
                Log.d(TAG, "Trying Piped: $url")

                val response: String = httpClient.get(url) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    header("Accept", "application/json")
                }.bodyAsText()

                // Check if response is HTML (error page)
                if (response.trimStart().startsWith("<")) {
                    Log.w(TAG, "Piped returned HTML, skipping: $url")
                    continue
                }

                val jsonObject = json.parseToJsonElement(response).jsonObject

                // Check for error
                if (jsonObject.containsKey("error")) {
                    Log.w(TAG, "Piped error: ${jsonObject["error"]}")
                    continue
                }

                // Get track info
                val title = jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                val uploader = jsonObject["uploader"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                val uploaderId = jsonObject["uploaderUrl"]?.jsonPrimitive?.contentOrNull?.substringAfterLast("/")
                val duration = jsonObject["duration"]?.jsonPrimitive?.longOrNull ?: 0L
                val thumbnailUrl = jsonObject["thumbnailUrl"]?.jsonPrimitive?.contentOrNull
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                val track = Track(
                    id = videoId,
                    title = title,
                    artistName = uploader,
                    artistId = uploaderId,
                    albumName = null,
                    albumId = null,
                    thumbnailUrl = thumbnailUrl,
                    duration = duration * 1000
                )

                // Get audio streams
                val audioStreams = jsonObject["audioStreams"]?.jsonArray
                val bestAudio = audioStreams
                    ?.filter { it.jsonObject["mimeType"]?.jsonPrimitive?.contentOrNull?.contains("audio") == true }
                    ?.maxByOrNull { it.jsonObject["bitrate"]?.jsonPrimitive?.intOrNull ?: 0 }

                val streamUrl = bestAudio?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                if (streamUrl != null) {
                    Log.d(TAG, "Found audio stream from Piped: $url")
                    return PlayerResult(streamUrl, track)
                }

            } catch (e: Exception) {
                Log.w(TAG, "Piped URL failed $url: ${e.message}")
                continue
            }
        }
        return null
    }

    /**
     * Get player data from Invidious API (YouTube frontend)
     */
    private suspend fun getPlayerDataFromPiped(videoId: String): PlayerResult? {
        // Try multiple Invidious instances for reliability
        // Using more reliable instances from https://api.invidious.io/
        val invidiousInstances = listOf(
            "https://yewtu.be",
            "https://inv.tux.pizza",
            "https://invidious.protokolla.fi",
            "https://invidious.lunar.icu",
            "https://invidious.privacydev.net"
        )

        for (instance in invidiousInstances) {
            try {
                val url = "$instance/api/v1/videos/$videoId"
                Log.d(TAG, "Trying Invidious: $url")

                val response: String = httpClient.get(url) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    header("Accept", "application/json")
                }.bodyAsText()

                // Check if response starts with HTML (error page)
                if (response.trimStart().startsWith("<")) {
                    Log.w(TAG, "Invidious returned HTML, skipping: $instance")
                    continue
                }

                val jsonObject = json.parseToJsonElement(response).jsonObject

                // Check for error
                if (jsonObject.containsKey("error")) {
                    Log.w(TAG, "Invidious error: ${jsonObject["error"]}")
                    continue
                }

                // Get track info
                val title = jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                val author = jsonObject["author"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                val authorId = jsonObject["authorId"]?.jsonPrimitive?.contentOrNull
                val lengthSeconds = jsonObject["lengthSeconds"]?.jsonPrimitive?.longOrNull ?: 0L

                // Get thumbnail
                val videoThumbnails = jsonObject["videoThumbnails"]?.jsonArray
                val thumbnailUrl = videoThumbnails?.firstOrNull {
                    it.jsonObject["quality"]?.jsonPrimitive?.contentOrNull == "medium"
                }?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                    ?: videoThumbnails?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                val track = Track(
                    id = videoId,
                    title = title,
                    artistName = author,
                    artistId = authorId,
                    albumName = null,
                    albumId = null,
                    thumbnailUrl = thumbnailUrl,
                    duration = lengthSeconds * 1000
                )

                // Get adaptive formats (audio streams)
                val adaptiveFormats = jsonObject["adaptiveFormats"]?.jsonArray
                val audioStream = adaptiveFormats
                    ?.filter {
                        val type = it.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: ""
                        type.startsWith("audio/")
                    }
                    ?.maxByOrNull {
                        it.jsonObject["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                    }

                val streamUrl = audioStream?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                if (streamUrl != null) {
                    Log.d(TAG, "Found audio stream from Invidious: $instance")
                    return PlayerResult(streamUrl, track)
                }

                // Fallback to format streams
                val formatStreams = jsonObject["formatStreams"]?.jsonArray
                val videoStream = formatStreams?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                if (videoStream != null) {
                    Log.d(TAG, "Found video stream from Invidious (fallback)")
                    return PlayerResult(videoStream, track)
                }

            } catch (e: Exception) {
                Log.w(TAG, "Invidious instance $instance failed: ${e.message}")
                continue
            }
        }
        return null
    }

    /**
     * Get player data from YouTube innertube API (fallback)
     * Tries multiple client types for best compatibility
     */
    private suspend fun getPlayerDataFromInnertube(videoId: String): PlayerResult? {
        // Try ANDROID_MUSIC client first - most compatible with Android playback
        val androidMusicResult = tryAndroidMusicClient(videoId)
        if (androidMusicResult != null) return androidMusicResult

        // Try Android client
        val androidResult = tryInnertubeClient(videoId, "ANDROID", "19.44.38", null)
        if (androidResult != null) return androidResult

        // Try TV embed client as last resort (usually less restrictive)
        val tvResult = tryTvEmbedClient(videoId)
        if (tvResult != null) return tvResult

        // Try iOS client (least compatible on Android devices)
        val iosResult = tryInnertubeClient(videoId, "IOS", "19.45.4", "18.1.0.22D68")
        if (iosResult != null) return iosResult

        return null
    }

    /**
     * Try ANDROID_MUSIC client - specifically for YouTube Music on Android
     */
    private suspend fun tryAndroidMusicClient(videoId: String): PlayerResult? {
        try {
            Log.d(TAG, "Trying ANDROID_MUSIC client for: $videoId")

            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "ANDROID_MUSIC")
                        put("clientVersion", "6.42.52")
                        put("androidSdkVersion", 34)
                        put("hl", "en")
                        put("gl", "US")
                        put("osName", "Android")
                        put("osVersion", "14")
                        put("platform", "MOBILE")
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val response: String = httpClient.post("https://music.youtube.com/youtubei/v1/player?key=$INNERTUBE_KEY") {
                contentType(ContentType.Application.Json)
                header("User-Agent", "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14) gzip")
                header("Origin", "https://music.youtube.com")
                header("X-YouTube-Client-Name", "21")
                header("X-YouTube-Client-Version", "6.42.52")
                setBody(requestBody.toString())
            }.bodyAsText()

            return parseInnertubeResponse(response, videoId, "ANDROID_MUSIC")
        } catch (e: Exception) {
            Log.w(TAG, "ANDROID_MUSIC client failed: ${e.message}")
            return null
        }
    }

    private suspend fun tryInnertubeClient(
        videoId: String,
        clientName: String,
        clientVersion: String,
        osVersion: String?
    ): PlayerResult? {
        try {
            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", clientName)
                        put("clientVersion", clientVersion)
                        put("hl", "en")
                        put("gl", "US")
                        if (osVersion != null) {
                            put("osVersion", osVersion)
                            put("deviceModel", "iPhone16,2")
                        }
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val userAgent = if (clientName == "IOS") {
                "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
            } else {
                "com.google.android.youtube/19.44.38 (Linux; U; Android 14) gzip"
            }

            val response: String = httpClient.post("https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8") {
                contentType(ContentType.Application.Json)
                header("User-Agent", userAgent)
                header("Origin", "https://www.youtube.com")
                header("X-YouTube-Client-Name", if (clientName == "IOS") "5" else "3")
                header("X-YouTube-Client-Version", clientVersion)
                setBody(requestBody.toString())
            }.bodyAsText()

            return parseInnertubeResponse(response, videoId, clientName)
        } catch (e: Exception) {
            Log.w(TAG, "$clientName client failed: ${e.message}")
            return null
        }
    }

    private suspend fun tryTvEmbedClient(videoId: String): PlayerResult? {
        try {
            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                        put("clientVersion", "2.0")
                        put("hl", "en")
                        put("gl", "US")
                    }
                    putJsonObject("thirdParty") {
                        put("embedUrl", "https://www.youtube.com")
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val response: String = httpClient.post("https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8") {
                contentType(ContentType.Application.Json)
                header("User-Agent", "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version")
                header("Origin", "https://www.youtube.com")
                header("Referer", "https://www.youtube.com/")
                setBody(requestBody.toString())
            }.bodyAsText()

            return parseInnertubeResponse(response, videoId, "TV")
        } catch (e: Exception) {
            Log.w(TAG, "TV client failed: ${e.message}")
            return null
        }
    }

    private fun parseInnertubeResponse(response: String, videoId: String, clientName: String): PlayerResult? {
        val jsonElement = json.parseToJsonElement(response)
        val jsonObject = jsonElement.jsonObject

        // Check playability
        val playabilityStatus = jsonObject["playabilityStatus"]?.jsonObject
        val status = playabilityStatus?.get("status")?.jsonPrimitive?.contentOrNull
        val reason = playabilityStatus?.get("reason")?.jsonPrimitive?.contentOrNull

        if (status != "OK") {
            Log.w(TAG, "$clientName client playability: $status - $reason")
            return null
        }

        Log.d(TAG, "$clientName client: playability OK")

        // Get video details for track info
        val videoDetails = jsonObject["videoDetails"]?.jsonObject
        val title = videoDetails?.get("title")?.jsonPrimitive?.contentOrNull ?: "Unknown"
        val author = videoDetails?.get("author")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
        val channelId = videoDetails?.get("channelId")?.jsonPrimitive?.contentOrNull
        val lengthSeconds = videoDetails?.get("lengthSeconds")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val thumbnails = videoDetails?.get("thumbnail")?.jsonObject
            ?.get("thumbnails")?.jsonArray
        val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

        val track = Track(
            id = videoId,
            title = title,
            artistName = author,
            artistId = channelId,
            albumName = null,
            albumId = null,
            thumbnailUrl = thumbnailUrl,
            duration = lengthSeconds * 1000 // Convert to ms
        )

        Log.d(TAG, "Got track info: ${track.title} by ${track.artistName}")

        // Get streaming data
        val streamingData = jsonObject["streamingData"]?.jsonObject
            ?: return null

        // PREFER direct audio streams over HLS (HLS segments often get 403 errors)
        val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray
        Log.d(TAG, "adaptiveFormats count: ${adaptiveFormats?.size ?: 0}")

        val audioStreams = adaptiveFormats?.filter { format ->
            val mimeType = format.jsonObject["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
            val hasUrl = format.jsonObject["url"] != null
            Log.d(TAG, "Format: $mimeType, hasUrl: $hasUrl")
            mimeType.startsWith("audio/") && hasUrl
        }?.sortedByDescending { format ->
            format.jsonObject["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
        }

        Log.d(TAG, "Audio streams with direct URL: ${audioStreams?.size ?: 0}")

        // First try direct audio URL from adaptive formats
        val directAudioUrl = audioStreams?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        if (directAudioUrl != null) {
            Log.d(TAG, "Found direct audio stream URL (preferred): ${directAudioUrl.take(100)}...")
            return PlayerResult(directAudioUrl, track)
        }

        // Try regular formats (combined audio+video) as fallback
        val formats = streamingData["formats"]?.jsonArray
        val combinedStreamUrl = formats?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        if (combinedStreamUrl != null) {
            Log.d(TAG, "Found combined stream URL (fallback)")
            return PlayerResult(combinedStreamUrl, track)
        }

        // Last resort: HLS manifest (often fails with 403 on segments)
        streamingData["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull?.let {
            Log.d(TAG, "Found HLS manifest URL (last resort - may fail)")
            return PlayerResult(it, track)
        }

        Log.w(TAG, "No playable stream URL found for $clientName client")
        return null
    }

    /**
     * Get audio stream URL for a track (legacy method)
     */
    suspend fun getAudioStreamUrl(videoId: String): String? {
        return getPlayerData(videoId)?.streamUrl
    }

    /**
     * Get related tracks (radio/autoplay queue)
     * Uses the radio playlist ID format: RDAMVM + videoId
     */
    suspend fun getRadio(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching radio for: $videoId")

            // Use radio playlist ID format to get more tracks
            val radioPlaylistId = "RDAMVM$videoId"

            val requestBody = buildJsonObject {
                put("context", buildContext())
                put("videoId", videoId)
                put("playlistId", radioPlaylistId)
                put("isAudioOnly", true)
                put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
            }

            val response: String = httpClient.post("$INNERTUBE_BASE_URL/next?key=$INNERTUBE_KEY") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                setBody(requestBody.toString())
            }.bodyAsText()

            val jsonElement = json.parseToJsonElement(response)
            val tracks = MusicResponseParser.parseRadioTracks(jsonElement.jsonObject)
            Log.d(TAG, "Radio queue parsed: ${tracks.size} tracks")
            tracks

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get radio for: $videoId", e)
            emptyList()
        }
    }

    /**
     * Get search suggestions using simple YouTube suggest API
     * This is more reliable than the complex Innertube endpoint
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            Log.d(TAG, "Getting music suggestions for: $query")

            // Use YouTube's simple suggest API (same as video, works reliably)
            val response: String = httpClient.get("https://suggestqueries-clients6.youtube.com/complete/search") {
                parameter("client", "youtube")
                parameter("ds", "yt")
                parameter("q", query)
            }.bodyAsText()

            // Parse JSONP response: window.google.ac.h(["query",[["suggestion1","",[]],...],...])
            val jsonStart = response.indexOf("[[")
            val jsonEnd = response.lastIndexOf("]]") + 2

            if (jsonStart > 0 && jsonEnd > jsonStart) {
                val jsonArrayStr = response.substring(jsonStart, jsonEnd)
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

                Log.d(TAG, "Found ${suggestions.size} music suggestions for: $query")
                suggestions.take(10)
            } else {
                Log.d(TAG, "Could not parse music suggestions response")
                emptyList()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get music suggestions for: $query", e)
            emptyList()
        }
    }

    /**
     * Get lyrics for a track using LRCLIB API (same as MarelikayBeats)
     * LRCLIB provides both synced (LRC) and plain lyrics
     */
    suspend fun getLyrics(trackId: String): Lyrics? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching lyrics for: $trackId")

            // First get track info to search LRCLIB
            val trackInfo = getTrackInfo(trackId)
            if (trackInfo == null) {
                Log.d(TAG, "Could not get track info for lyrics: $trackId")
                return@withContext null
            }

            val trackName = trackInfo.first
            val artistName = trackInfo.second
            val durationSeconds = trackInfo.third

            Log.d(TAG, "Searching LRCLIB for: $trackName by $artistName")

            // Try LRCLIB API (used by MarelikayBeats)
            val lrcLibResult = getLyricsFromLrcLib(trackName, artistName, durationSeconds)
            if (lrcLibResult != null) {
                return@withContext lrcLibResult.copy(trackId = trackId)
            }

            // Fallback to search endpoint if exact match fails
            val searchResult = searchLyricsFromLrcLib(trackName, artistName)
            if (searchResult != null) {
                return@withContext searchResult.copy(trackId = trackId)
            }

            Log.d(TAG, "No lyrics found for: $trackName by $artistName")
            null

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get lyrics for: $trackId", e)
            null
        }
    }

    /**
     * Get track info (title, artist, duration) for lyrics search
     */
    private suspend fun getTrackInfo(trackId: String): Triple<String, String, Long>? {
        return try {
            // Try to get from local cache first via player data
            val requestBody = buildJsonObject {
                put("context", buildContext())
                put("videoId", trackId)
            }

            val response: String = httpClient.post("$INNERTUBE_BASE_URL/player?key=$INNERTUBE_KEY") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                setBody(requestBody.toString())
            }.bodyAsText()

            val jsonObject = json.parseToJsonElement(response).jsonObject
            val videoDetails = jsonObject["videoDetails"]?.jsonObject

            val title = videoDetails?.get("title")?.jsonPrimitive?.contentOrNull ?: return null
            val author = videoDetails["author"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
            val lengthSeconds = videoDetails["lengthSeconds"]?.jsonPrimitive?.longOrNull ?: 0L

            // Clean up the title (remove common suffixes like "Official Video", etc.)
            val cleanTitle = title
                .replace(Regex("\\s*\\(Official.*?\\)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\[Official.*?\\]", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*Official.*?Video", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\(Lyric.*?\\)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\[Lyric.*?\\]", RegexOption.IGNORE_CASE), "")
                .trim()

            Triple(cleanTitle, author, lengthSeconds)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get track info: ${e.message}")
            null
        }
    }

    /**
     * Get lyrics from LRCLIB using exact match (by track name, artist, duration)
     */
    private suspend fun getLyricsFromLrcLib(
        trackName: String,
        artistName: String,
        durationSeconds: Long
    ): Lyrics? {
        return try {
            val url = buildString {
                append("https://lrclib.net/api/get?")
                append("track_name=${java.net.URLEncoder.encode(trackName, "UTF-8")}")
                append("&artist_name=${java.net.URLEncoder.encode(artistName, "UTF-8")}")
                if (durationSeconds > 0) {
                    append("&duration=$durationSeconds")
                }
            }

            Log.d(TAG, "LRCLIB get: $url")

            val response: String = httpClient.get(url) {
                header("User-Agent", "MarelikayBeats/1.0 (https://github.com/raveuk/MarielikayBeat)")
            }.bodyAsText()

            if (response.contains("\"statusCode\"") || response.contains("\"error\"")) {
                Log.d(TAG, "LRCLIB get returned error")
                return null
            }

            parseLrcLibResponse(response)

        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB get failed: ${e.message}")
            null
        }
    }

    /**
     * Search lyrics from LRCLIB (less strict matching)
     */
    private suspend fun searchLyricsFromLrcLib(
        trackName: String,
        artistName: String
    ): Lyrics? {
        return try {
            val query = "$trackName $artistName"
            val url = "https://lrclib.net/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"

            Log.d(TAG, "LRCLIB search: $url")

            val response: String = httpClient.get(url) {
                header("User-Agent", "MarelikayBeats/1.0 (https://github.com/raveuk/MarielikayBeat)")
            }.bodyAsText()

            val jsonArray = json.parseToJsonElement(response).jsonArray
            if (jsonArray.isEmpty()) {
                Log.d(TAG, "LRCLIB search returned no results")
                return null
            }

            // Get best match (first result)
            val bestMatch = jsonArray.firstOrNull()?.jsonObject
            if (bestMatch != null) {
                parseLrcLibResult(bestMatch)
            } else {
                null
            }

        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB search failed: ${e.message}")
            null
        }
    }

    /**
     * Parse LRCLIB API response (single result)
     */
    private fun parseLrcLibResponse(response: String): Lyrics? {
        return try {
            val jsonObject = json.parseToJsonElement(response).jsonObject
            parseLrcLibResult(jsonObject)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse LRCLIB response: ${e.message}")
            null
        }
    }

    /**
     * Parse a single LRCLIB result object
     */
    private fun parseLrcLibResult(jsonObject: JsonObject): Lyrics? {
        val syncedLyrics = jsonObject["syncedLyrics"]?.jsonPrimitive?.contentOrNull
        val plainLyrics = jsonObject["plainLyrics"]?.jsonPrimitive?.contentOrNull

        if (syncedLyrics.isNullOrBlank() && plainLyrics.isNullOrBlank()) {
            return null
        }

        // Prefer synced lyrics if available
        val (lines, isSynced) = if (!syncedLyrics.isNullOrBlank()) {
            parseSyncedLyrics(syncedLyrics) to true
        } else {
            parsePlainLyrics(plainLyrics!!) to false
        }

        if (lines.isEmpty()) {
            return null
        }

        Log.d(TAG, "LRCLIB: Found ${lines.size} lyrics lines (synced: $isSynced)")

        return Lyrics(
            trackId = "",  // Will be set by caller
            lines = lines,
            source = "LRCLIB",
            isSynced = isSynced
        )
    }

    /**
     * Parse LRC format synced lyrics
     * Format: [mm:ss.xx] Lyrics text
     */
    private fun parseSyncedLyrics(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)$")

        lrc.lines().forEach { line ->
            val match = regex.find(line.trim())
            if (match != null) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0
                val seconds = match.groupValues[2].toLongOrNull() ?: 0
                val millis = match.groupValues[3].let {
                    val value = it.toLongOrNull() ?: 0
                    if (it.length == 2) value * 10 else value  // Handle both .xx and .xxx
                }
                val text = match.groupValues[4].trim()

                if (text.isNotEmpty()) {
                    val startTimeMs = (minutes * 60 + seconds) * 1000 + millis
                    lines.add(LyricLine(text = text, startTimeMs = startTimeMs))
                }
            }
        }

        // Calculate end times
        for (i in 0 until lines.size - 1) {
            lines[i] = lines[i].copy(endTimeMs = lines[i + 1].startTimeMs)
        }
        if (lines.isNotEmpty()) {
            lines[lines.size - 1] = lines.last().copy(endTimeMs = lines.last().startTimeMs + 5000)
        }

        return lines
    }

    /**
     * Parse plain text lyrics (no timestamps)
     */
    private fun parsePlainLyrics(text: String): List<LyricLine> {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { LyricLine(text = it) }
    }
}
