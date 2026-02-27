package com.zimbabeats.core.data.remote.youtube.music

import android.util.Log
import com.zimbabeats.core.data.remote.youtube.NewPipeStreamExtractor
import com.zimbabeats.core.domain.model.music.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.security.MessageDigest

/**
 * YouTube Music Innertube API client
 * Uses the WEB_REMIX client (YouTube Music web client) for music-specific content
 * Supports authenticated requests with YouTube cookies for better stream access
 *
 * Stream extraction priority:
 * 1. yt-dlp (handles "n" parameter decryption locally)
 * 2. Authenticated Innertube (if logged in)
 * 3. Piped API
 * 4. Invidious API
 * 5. Unauthenticated Innertube
 */
class YouTubeMusicClient(private val httpClient: HttpClient) {

    /**
     * NewPipe extractor for reliable stream extraction.
     * Handles "n" parameter decryption that YouTube requires.
     */
    var newPipeExtractor: NewPipeStreamExtractor? = null

    companion object {
        private const val TAG = "YouTubeMusicClient"
        private const val INNERTUBE_BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val INNERTUBE_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

        // Firebase Cloud Function for stream extraction (handles n-parameter decryption)
        private const val FIREBASE_FUNCTION_URL = "https://us-central1-zimba-beats.cloudfunctions.net/getAudioStream"

        // WEB_REMIX client for YouTube Music
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20260121.03.00"

        // Browse IDs for YouTube Music sections
        private const val BROWSE_ID_HOME = "FEmusic_home"
        private const val BROWSE_ID_EXPLORE = "FEmusic_explore"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * YouTube cookies for authenticated requests
     * Set from AppPreferences when user is logged in
     */
    var cookie: String = ""
        set(value) {
            field = value
            _sapisid = extractSapisid(value)
            Log.d(TAG, "Cookie updated, SAPISID present: ${_sapisid != null}")
        }

    private var _sapisid: String? = null

    /**
     * Check if authenticated requests are available
     */
    val isAuthenticated: Boolean
        get() = _sapisid != null

    /**
     * Extract SAPISID from cookie string for authentication header
     */
    private fun extractSapisid(cookieString: String): String? {
        if (cookieString.isEmpty()) return null

        return cookieString.split(";")
            .map { it.trim() }
            .find { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.split("=")
            ?.getOrNull(1)
    }

    /**
     * Generate SAPISIDHASH for YouTube Music API authentication
     * Format: SAPISIDHASH {timestamp}_{sha1(timestamp + " " + SAPISID + " " + origin)}
     */
    private fun generateSapisidHash(origin: String = "https://music.youtube.com"): String? {
        val sapisid = _sapisid ?: return null
        val timestamp = System.currentTimeMillis() / 1000
        val data = "$timestamp $sapisid $origin"

        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(data.toByteArray())
            val hash = digest.joinToString("") { "%02x".format(it) }
            "SAPISIDHASH ${timestamp}_$hash"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate SAPISIDHASH", e)
            null
        }
    }

    /**
     * Apply authentication headers to request builder if logged in
     */
    private fun HttpRequestBuilder.applyAuthHeaders(origin: String = "https://music.youtube.com") {
        if (cookie.isNotEmpty()) {
            header("Cookie", cookie)
            generateSapisidHash(origin)?.let {
                header("Authorization", it)
            }
        }
    }

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
     *
     * Priority order:
     * 1. NewPipe Extractor (handles "n" parameter decryption locally - most reliable)
     * 2. iOS client (fast fallback - bypasses many restrictions)
     * 3. Authenticated Innertube (if logged in)
     * 4. Piped API
     * 5. Invidious API
     * 6. Other Innertube clients (Android, TV) - last resort
     */
    suspend fun getPlayerData(videoId: String): PlayerResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== Getting player data for: $videoId (authenticated: $isAuthenticated) ===")

        // PRIORITY 1: NewPipe Extractor (most reliable - handles n-parameter decryption locally)
        newPipeExtractor?.let { extractor ->
            try {
                Log.d(TAG, "Trying NewPipe Extractor first (handles n-parameter decryption)...")
                val result = extractor.extractAudioStream(videoId)
                if (result != null) {
                    val (streamUrl, track) = extractor.toPlayerResult(result, videoId)
                    Log.d(TAG, "=== NewPipe SUCCESS ===")
                    Log.d(TAG, "  Requested videoId: $videoId")
                    Log.d(TAG, "  Returned track.id: ${track.id}")
                    Log.d(TAG, "  Returned track.title: ${track.title}")
                    Log.d(TAG, "  Returned track.artist: ${track.artistName}")
                    Log.d(TAG, "  Stream URL prefix: ${streamUrl.take(80)}...")
                    return@withContext PlayerResult(streamUrl, track)
                }
            } catch (e: Exception) {
                Log.w(TAG, "NewPipe extraction failed: ${e.message}")
            }
        } ?: Log.d(TAG, "NewPipe extractor not available, skipping...")

        // PRIORITY 2: Try iOS client directly - fast and reliable for music tracks
        // iOS client bypasses many restrictions that cause NewPipe to fail
        // Using InnerTune's working versions: 19.29.1 with iOS 17.5.1
        try {
            Log.d(TAG, "Trying iOS client (fast fallback)...")
            val iosResult = tryInnertubeClient(videoId, "IOS", "19.29.1", "17.5.1.21F90")
            if (iosResult != null) {
                Log.d(TAG, "SUCCESS: Got player data from iOS client: ${iosResult.track.title}")
                return@withContext iosResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "iOS client failed: ${e.message}")
        }

        // PRIORITY 3: If logged in, try authenticated Innertube
        if (isAuthenticated) {
            try {
                Log.d(TAG, "Trying AUTHENTICATED innertube...")
                val authResult = getPlayerDataFromAuthenticatedInnertube(videoId)
                if (authResult != null) {
                    Log.d(TAG, "SUCCESS: Got player data from authenticated innertube: ${authResult.track.title}")
                    return@withContext authResult
                }
            } catch (e: Exception) {
                Log.w(TAG, "Authenticated innertube failed: ${e.message}")
            }
        }

        // PRIORITY 4: Try Piped API - it handles "n" parameter decryption
        try {
            Log.d(TAG, "Trying Piped API...")
            val pipedResult = getPlayerDataFromPipedApi(videoId)
            if (pipedResult != null) {
                Log.d(TAG, "SUCCESS: Got player data from Piped: ${pipedResult.track.title}")
                return@withContext pipedResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Piped API failed: ${e.message}")
        }

        // PRIORITY 5: Fallback to Invidious API
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

        // PRIORITY 6: Last resort - other innertube clients (Android, TV)
        try {
            Log.d(TAG, "Trying other innertube clients as last resort...")
            val innertubeResult = getPlayerDataFromInnertube(videoId)
            if (innertubeResult != null) {
                Log.d(TAG, "SUCCESS: Got player data from innertube: ${innertubeResult.track.title}")
                return@withContext innertubeResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Innertube API failed: ${e.message}")
        }

        Log.e(TAG, "=== All sources failed for: $videoId ===")
        null
    }

    /**
     * Get player data from Firebase Cloud Function.
     * This is the most reliable method as it handles the "n" parameter decryption server-side.
     */
    private suspend fun getPlayerDataFromFirebase(videoId: String): PlayerResult? {
        try {
            Log.d(TAG, "Calling Firebase function for: $videoId")

            val response: String = httpClient.get("$FIREBASE_FUNCTION_URL?videoId=$videoId") {
                header("Accept", "application/json")
            }.bodyAsText()

            val jsonResponse = json.parseToJsonElement(response).jsonObject

            val success = jsonResponse["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!success) {
                val error = jsonResponse["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                Log.w(TAG, "Firebase returned error: $error")
                return null
            }

            val audioUrl = jsonResponse["audioUrl"]?.jsonPrimitive?.contentOrNull
            val title = jsonResponse["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Title"
            val author = jsonResponse["author"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val durationSeconds = jsonResponse["duration"]?.jsonPrimitive?.intOrNull ?: 0
            val thumbnail = jsonResponse["thumbnail"]?.jsonPrimitive?.contentOrNull

            if (audioUrl.isNullOrEmpty()) {
                Log.w(TAG, "Firebase returned no audio URL")
                return null
            }

            Log.d(TAG, "Firebase success: $title by $author")

            val track = Track(
                id = videoId,
                title = title,
                artistName = author,
                artistId = null,
                albumName = null,
                albumId = null,
                thumbnailUrl = thumbnail ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                duration = durationSeconds * 1000L, // Convert to milliseconds
                isExplicit = false
            )

            return PlayerResult(
                streamUrl = audioUrl,
                track = track
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firebase request failed: ${e.message}", e)
            return null
        }
    }

    /**
     * Get player data using AUTHENTICATED Innertube API
     * This uses the user's cookies for authenticated requests which can bypass
     * some YouTube restrictions and potentially avoid the "n" parameter issue
     */
    private suspend fun getPlayerDataFromAuthenticatedInnertube(videoId: String): PlayerResult? {
        try {
            Log.d(TAG, "Making authenticated player request for: $videoId")

            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", CLIENT_VERSION)
                        put("hl", "en")
                        put("gl", "US")
                        put("platform", "DESKTOP")
                        put("userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val response: String = httpClient.post("$INNERTUBE_BASE_URL/player?key=$INNERTUBE_KEY") {
                contentType(ContentType.Application.Json)
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("X-YouTube-Client-Name", "67")  // WEB_REMIX client number
                header("X-YouTube-Client-Version", CLIENT_VERSION)
                applyAuthHeaders()
                setBody(requestBody.toString())
            }.bodyAsText()

            return parseInnertubeResponse(response, videoId, "WEB_REMIX_AUTH")
        } catch (e: Exception) {
            Log.w(TAG, "Authenticated WEB_REMIX failed: ${e.message}")
            return null
        }
    }

    /**
     * Get player data from Piped API instances
     * Piped handles "n" parameter decryption which is required for YouTube streams
     */
    private suspend fun getPlayerDataFromPipedApi(videoId: String): PlayerResult? {
        // Multiple Piped instances for reliability (updated Feb 2026)
        // These handle the "n" parameter decryption that YouTube requires
        // Source: https://github.com/TeamPiped/documentation/blob/main/content/docs/public-instances/index.md
        val pipedUrls = listOf(
            "https://api.piped.private.coffee/streams/$videoId",    // 100% uptime
            "https://pipedapi.kavin.rocks/streams/$videoId",
            "https://pipedapi.leptons.xyz/streams/$videoId",
            "https://pipedapi.nosebs.ru/streams/$videoId",
            "https://pipedapi-libre.kavin.rocks/streams/$videoId",
            "https://piped-api.privacy.com.de/streams/$videoId",
            "https://pipedapi.adminforge.de/streams/$videoId",
            "https://api.piped.yt/streams/$videoId",
            "https://pipedapi.drgns.space/streams/$videoId",
            "https://pipedapi.owo.si/streams/$videoId",
            "https://pipedapi.ducks.party/streams/$videoId",
            "https://piped-api.codespace.cz/streams/$videoId",
            "https://pipedapi.reallyaweso.me/streams/$videoId",
            "https://pipedapi.darkness.services/streams/$videoId",
            "https://pipedapi.orangenet.cc/streams/$videoId"
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
     * Priority order based on SimpMusic's MediaServiceCore:
     * 1. iOS client (19.29.1) - most reliable for music
     * 2. ANDROID client (20.10.38) - SimpMusic's primary fallback
     * 3. TV embed client
     * 4. WEB_SAFARI for HLS streams
     */
    private suspend fun getPlayerDataFromInnertube(videoId: String): PlayerResult? {
        // Try iOS client first - using SimpMusic's working versions
        Log.d(TAG, "Trying iOS client (SimpMusic config): $videoId")
        val iosResult = tryInnertubeClient(videoId, "IOS", "19.29.1", "17.5.1.21F90")
        if (iosResult != null) {
            Log.d(TAG, "iOS client succeeded!")
            return iosResult
        }

        // Try ANDROID client - SimpMusic uses this as primary fallback (NOT ANDROID_MUSIC)
        Log.d(TAG, "iOS failed, trying ANDROID client (20.10.38)")
        val androidResult = tryAndroidClient(videoId)
        if (androidResult != null) {
            Log.d(TAG, "ANDROID client succeeded!")
            return androidResult
        }

        // Try TV embed client
        Log.d(TAG, "ANDROID failed, trying TV embed client")
        val tvResult = tryTvEmbedClient(videoId)
        if (tvResult != null) {
            Log.d(TAG, "TV client succeeded!")
            return tvResult
        }

        // Try WEB_SAFARI for HLS streams - doesn't require n-param decryption
        Log.d(TAG, "TV failed, trying WEB_SAFARI client (HLS)")
        val safariResult = tryWebSafariClient(videoId)
        if (safariResult != null) {
            Log.d(TAG, "WEB_SAFARI client succeeded with HLS stream!")
            return safariResult
        }

        Log.e(TAG, "All innertube clients failed for: $videoId")
        return null
    }

    /**
     * Try ANDROID client - SimpMusic's primary player client
     * Version 20.10.38, Android 11, SDK 30
     */
    private suspend fun tryAndroidClient(videoId: String): PlayerResult? {
        try {
            Log.d(TAG, "Trying ANDROID client for: $videoId")

            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "ANDROID")
                        put("clientVersion", "20.10.38")
                        put("androidSdkVersion", 30)
                        put("hl", "en")
                        put("gl", "US")
                        put("osName", "Android")
                        put("osVersion", "11")
                        put("platform", "MOBILE")
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val response: String = httpClient.post("https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8") {
                contentType(ContentType.Application.Json)
                header("User-Agent", "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip")
                header("Origin", "https://www.youtube.com")
                header("X-YouTube-Client-Name", "3")
                header("X-YouTube-Client-Version", "20.10.38")
                setBody(requestBody.toString())
            }.bodyAsText()

            return parseInnertubeResponse(response, videoId, "ANDROID")
        } catch (e: Exception) {
            Log.w(TAG, "ANDROID client failed: ${e.message}")
            return null
        }
    }

    /**
     * Try WEB_SAFARI client - returns HLS m3u8 streams that DON'T require "n" parameter decryption
     * This is based on yt-dlp's finding that web_safari m3u8 formats bypass throttling
     */
    private suspend fun tryWebSafariClient(videoId: String): PlayerResult? {
        try {
            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20260131.00.00")
                        put("hl", "en")
                        put("gl", "US")
                        put("userAgent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15")
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val response: String = httpClient.post("https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8") {
                contentType(ContentType.Application.Json)
                header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15")
                header("Origin", "https://www.youtube.com")
                header("Referer", "https://www.youtube.com/")
                setBody(requestBody.toString())
            }.bodyAsText()

            // Parse specifically looking for HLS manifest (that's what we want from Safari)
            return parseWebSafariResponse(response, videoId)
        } catch (e: Exception) {
            Log.w(TAG, "WEB_SAFARI client failed: ${e.message}")
            return null
        }
    }

    /**
     * Parse WEB_SAFARI response - prioritize HLS manifest over adaptive formats
     * HLS streams from Safari don't need "n" parameter decryption
     */
    private fun parseWebSafariResponse(response: String, videoId: String): PlayerResult? {
        val jsonElement = json.parseToJsonElement(response)
        val jsonObject = jsonElement.jsonObject

        // Check playability
        val playabilityStatus = jsonObject["playabilityStatus"]?.jsonObject
        val status = playabilityStatus?.get("status")?.jsonPrimitive?.contentOrNull
        val reason = playabilityStatus?.get("reason")?.jsonPrimitive?.contentOrNull

        if (status != "OK") {
            Log.w(TAG, "WEB_SAFARI playability: $status - $reason")
            return null
        }

        // Get video details
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
            duration = lengthSeconds * 1000
        )

        // Get streaming data
        val streamingData = jsonObject["streamingData"]?.jsonObject
            ?: return null

        // PRIORITIZE HLS manifest - this is the key to bypassing "n" parameter!
        // HLS streams from WEB client don't require JavaScript decryption
        streamingData["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull?.let { hlsUrl ->
            Log.d(TAG, "WEB_SAFARI: Found HLS manifest (no n-param needed): ${hlsUrl.take(80)}...")
            return PlayerResult(hlsUrl, track)
        }

        Log.w(TAG, "WEB_SAFARI: No HLS manifest found")
        return null
    }

    /**
     * Try ANDROID_MUSIC client - specifically for YouTube Music on Android
     * Using InnerTune's working version: 5.01
     */
    private suspend fun tryAndroidMusicClient(videoId: String): PlayerResult? {
        try {
            Log.d(TAG, "Trying ANDROID_MUSIC client for: $videoId")

            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "ANDROID_MUSIC")
                        put("clientVersion", "5.01")
                        put("androidSdkVersion", 30)
                        put("hl", "en")
                        put("gl", "US")
                        put("osName", "Android")
                        put("osVersion", "11")
                        put("platform", "MOBILE")
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            // Use InnerTune's API key for ANDROID_MUSIC
            val androidMusicApiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"

            val response: String = httpClient.post("https://music.youtube.com/youtubei/v1/player?key=$androidMusicApiKey") {
                contentType(ContentType.Application.Json)
                header("User-Agent", "com.google.android.apps.youtube.music/5.01 (Linux; U; Android 11) gzip")
                header("Origin", "https://music.youtube.com")
                header("X-YouTube-Client-Name", "21")
                header("X-YouTube-Client-Version", "5.01")
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
                            // Use iPhone 14 Pro Max (iPhone15,3) which matches InnerTune
                            put("deviceModel", "iPhone15,3")
                        }
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            // For iOS: match InnerTune's User-Agent format with iOS 17.5.1
            val userAgent = if (clientName == "IOS") {
                "com.google.ios.youtube/$clientVersion (iPhone15,3; U; CPU iOS 17_5_1 like Mac OS X;)"
            } else {
                "com.google.android.youtube/$clientVersion (Linux; U; Android 11) gzip"
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

        // Get all adaptive formats (audio streams)
        val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray
        Log.d(TAG, "adaptiveFormats count: ${adaptiveFormats?.size ?: 0}")

        // Filter to audio formats and sort by bitrate
        val audioFormats = adaptiveFormats?.filter { format ->
            val mimeType = format.jsonObject["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
            mimeType.startsWith("audio/")
        }?.sortedByDescending { format ->
            format.jsonObject["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
        }

        Log.d(TAG, "Audio formats found: ${audioFormats?.size ?: 0}")

        // Try to get a decoded stream URL using NewPipe's n-parameter decoder
        for (format in audioFormats ?: emptyList()) {
            val formatObj = format.jsonObject
            val url = formatObj["url"]?.jsonPrimitive?.contentOrNull
            val signatureCipher = formatObj["signatureCipher"]?.jsonPrimitive?.contentOrNull

            if (url == null && signatureCipher == null) continue

            // If we have NewPipe extractor, use it to decode the n-parameter
            // This is the key to avoiding 403 errors from YouTube throttling
            val decodedUrl = newPipeExtractor?.decodeStreamUrl(url, signatureCipher, videoId)
            if (decodedUrl != null) {
                Log.d(TAG, "NewPipe decoded URL successfully for $clientName")
                return PlayerResult(decodedUrl, track)
            }

            // If NewPipe decoding fails but we have a direct URL, try it anyway
            if (url != null) {
                Log.d(TAG, "Using direct URL (may get 403): ${url.take(80)}...")
                return PlayerResult(url, track)
            }
        }

        // Try regular formats (combined audio+video) as fallback
        val formats = streamingData["formats"]?.jsonArray
        for (format in formats ?: emptyList()) {
            val formatObj = format.jsonObject
            val url = formatObj["url"]?.jsonPrimitive?.contentOrNull
            val signatureCipher = formatObj["signatureCipher"]?.jsonPrimitive?.contentOrNull

            if (url == null && signatureCipher == null) continue

            val decodedUrl = newPipeExtractor?.decodeStreamUrl(url, signatureCipher, videoId)
            if (decodedUrl != null) {
                Log.d(TAG, "NewPipe decoded combined stream URL")
                return PlayerResult(decodedUrl, track)
            }

            if (url != null) {
                Log.d(TAG, "Using combined stream URL (fallback)")
                return PlayerResult(url, track)
            }
        }

        // Last resort: HLS manifest (doesn't need n-parameter decryption)
        streamingData["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull?.let {
            Log.d(TAG, "Found HLS manifest URL (last resort)")
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
