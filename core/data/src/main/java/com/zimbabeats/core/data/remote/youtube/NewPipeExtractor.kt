package com.zimbabeats.core.data.remote.youtube

import android.util.Log
import com.zimbabeats.core.domain.model.music.Track
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * YouTube stream extractor using NewPipe Extractor library.
 *
 * This handles the critical "n" parameter decryption that YouTube uses
 * to throttle/block stream access. NewPipe Extractor has built-in
 * JavaScript interpreter to properly decode these URLs.
 *
 * Based on how SimpMusic handles stream extraction - uses YoutubeJavaScriptPlayerManager
 * to decode the n-parameter on stream URLs.
 */
class NewPipeStreamExtractor {

    companion object {
        private const val TAG = "NewPipeExtractor"
        private var isInitialized = false

        // User agent for web requests
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    /**
     * Result containing stream URL and track info
     */
    data class ExtractionResult(
        val audioUrl: String,
        val title: String,
        val artist: String,
        val duration: Long, // in seconds
        val thumbnailUrl: String
    )

    /**
     * Result containing video stream URL (video+audio combined or video with separate audio)
     */
    data class VideoExtractionResult(
        val videoUrl: String,
        val audioUrl: String?, // For video-only streams that need separate audio
        val title: String,
        val uploaderName: String,
        val duration: Long, // in seconds
        val thumbnailUrl: String,
        val quality: String
    )

    /**
     * Initialize NewPipe Extractor (should be called once at app startup)
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "NewPipe Extractor already initialized")
            return@withContext true
        }

        try {
            Log.d(TAG, "Initializing NewPipe Extractor...")
            NewPipe.init(OkHttpDownloader())
            isInitialized = true
            Log.d(TAG, "NewPipe Extractor initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NewPipe Extractor", e)
            false
        }
    }

    /**
     * Decode a stream URL using NewPipe's YoutubeJavaScriptPlayerManager.
     * This handles both signature cipher decryption and n-parameter decryption.
     *
     * Based on SimpMusic's NewPipeUtils.getStreamUrl() method.
     */
    fun decodeStreamUrl(url: String?, signatureCipher: String?, videoId: String): String? {
        try {
            Log.d(TAG, "Decoding stream URL for $videoId: ${url?.take(50) ?: signatureCipher?.take(50)}...")

            val decodedUrl = url ?: signatureCipher?.let { cipher ->
                // Parse signature cipher
                val params = parseQueryString(cipher)
                val obfuscatedSignature = params["s"] ?: run {
                    Log.e(TAG, "Could not parse cipher signature")
                    return null
                }
                val signatureParam = params["sp"] ?: run {
                    Log.e(TAG, "Could not parse cipher signature parameter")
                    return null
                }
                val cipherUrl = params["url"]?.let { URLBuilder(it) } ?: run {
                    Log.e(TAG, "Could not parse cipher url")
                    return null
                }

                // Deobfuscate signature using NewPipe's JavaScript interpreter
                cipherUrl.parameters[signatureParam] = YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                    videoId,
                    obfuscatedSignature
                )
                cipherUrl.toString()
            } ?: run {
                Log.e(TAG, "No URL or signature cipher provided")
                return null
            }

            // Decode the n-parameter (throttling parameter) - this is the critical step!
            val finalUrl = YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId,
                decodedUrl
            )

            Log.d(TAG, "Successfully decoded URL for $videoId")
            return finalUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode stream URL: ${e.message}", e)
            return null
        }
    }

    /**
     * Get signature timestamp needed for player requests.
     * SimpMusic uses this to ensure the signature is valid.
     */
    fun getSignatureTimestamp(videoId: String): Int? {
        return try {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get signature timestamp: ${e.message}")
            null
        }
    }

    /**
     * Extract audio stream URL for a YouTube video ID.
     * Uses StreamInfo.getInfo() which handles decryption internally.
     */
    suspend fun extractAudioStream(videoId: String): ExtractionResult? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "NewPipe Extractor not initialized, attempting initialization...")
            if (!initialize()) {
                Log.e(TAG, "Failed to initialize NewPipe Extractor")
                return@withContext null
            }
        }

        try {
            Log.d(TAG, "Extracting audio stream for: $videoId")

            // Use YouTube Music URL for better audio quality
            val url = "https://www.youtube.com/watch?v=$videoId"
            val service = NewPipe.getService(ServiceList.YouTube.serviceId) as YoutubeService
            val streamInfo = StreamInfo.getInfo(service, url)

            // Get audio streams
            val audioStreams = streamInfo.audioStreams
            if (audioStreams.isNullOrEmpty()) {
                Log.e(TAG, "No audio streams found for: $videoId")
                return@withContext null
            }

            // Find best audio stream (highest bitrate)
            val bestAudio = audioStreams
                .filter { !it.content.isNullOrEmpty() }
                .maxByOrNull { it.averageBitrate }

            val audioUrl = bestAudio?.content
            if (audioUrl.isNullOrEmpty()) {
                Log.e(TAG, "No valid audio URL found for: $videoId")
                return@withContext null
            }

            Log.d(TAG, "NewPipe extracted: ${streamInfo.name} (${streamInfo.duration}s)")
            Log.d(TAG, "Audio URL: ${audioUrl.take(80)}...")
            Log.d(TAG, "Audio bitrate: ${bestAudio.averageBitrate}kbps, format: ${bestAudio.format?.name}")

            // Get thumbnail URL from thumbnails list
            val thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url
                ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

            ExtractionResult(
                audioUrl = audioUrl,
                title = streamInfo.name ?: "Unknown Title",
                artist = streamInfo.uploaderName ?: "Unknown Artist",
                duration = streamInfo.duration,
                thumbnailUrl = thumbnailUrl
            )

        } catch (e: Exception) {
            Log.e(TAG, "NewPipe extraction failed for $videoId: ${e.message}", e)
            null
        }
    }

    /**
     * Extract VIDEO stream URL for a YouTube video ID.
     * Prioritizes combined video+audio streams (mp4/webm), falls back to video-only + audio.
     * Uses StreamInfo.getInfo() which handles decryption internally.
     */
    suspend fun extractVideoStream(videoId: String): VideoExtractionResult? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "NewPipe Extractor not initialized, attempting initialization...")
            if (!initialize()) {
                Log.e(TAG, "Failed to initialize NewPipe Extractor")
                return@withContext null
            }
        }

        try {
            Log.d(TAG, "Extracting video stream for: $videoId")

            val url = "https://www.youtube.com/watch?v=$videoId"
            val service = NewPipe.getService(ServiceList.YouTube.serviceId) as YoutubeService
            val streamInfo = StreamInfo.getInfo(service, url)

            // PRIORITY 1: Get combined video+audio streams (preferred for ExoPlayer)
            val videoStreams = streamInfo.videoStreams
            Log.d(TAG, "Found ${videoStreams?.size ?: 0} video streams (combined)")

            // Find best combined stream (highest resolution, prefer mp4)
            val bestCombined = videoStreams
                ?.filter { !it.content.isNullOrEmpty() }
                ?.sortedWith(
                    compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> {
                        it.resolution?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0
                    }.thenByDescending {
                        if (it.format?.name?.contains("MPEG", ignoreCase = true) == true) 1 else 0
                    }
                )
                ?.firstOrNull()

            if (bestCombined != null && !bestCombined.content.isNullOrEmpty()) {
                val resolution = bestCombined.resolution ?: "unknown"
                Log.d(TAG, "Using combined stream: $resolution - ${bestCombined.format?.name}")
                Log.d(TAG, "Video URL: ${bestCombined.content.take(80)}...")

                val thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url
                    ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

                return@withContext VideoExtractionResult(
                    videoUrl = bestCombined.content,
                    audioUrl = null, // Combined stream, no separate audio needed
                    title = streamInfo.name ?: "Unknown Title",
                    uploaderName = streamInfo.uploaderName ?: "Unknown",
                    duration = streamInfo.duration,
                    thumbnailUrl = thumbnailUrl,
                    quality = resolution
                )
            }

            // PRIORITY 2: Video-only stream + separate audio stream
            val videoOnlyStreams = streamInfo.videoOnlyStreams
            val audioStreams = streamInfo.audioStreams

            Log.d(TAG, "No combined streams, trying video-only (${videoOnlyStreams?.size ?: 0}) + audio (${audioStreams?.size ?: 0})")

            val bestVideoOnly = videoOnlyStreams
                ?.filter { !it.content.isNullOrEmpty() }
                ?.sortedByDescending { it.resolution?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0 }
                ?.firstOrNull()

            val bestAudio = audioStreams
                ?.filter { !it.content.isNullOrEmpty() }
                ?.maxByOrNull { it.averageBitrate }

            if (bestVideoOnly != null && !bestVideoOnly.content.isNullOrEmpty()) {
                val resolution = bestVideoOnly.resolution ?: "unknown"
                Log.d(TAG, "Using video-only stream: $resolution with separate audio")

                val thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url
                    ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

                return@withContext VideoExtractionResult(
                    videoUrl = bestVideoOnly.content,
                    audioUrl = bestAudio?.content, // May need separate audio track
                    title = streamInfo.name ?: "Unknown Title",
                    uploaderName = streamInfo.uploaderName ?: "Unknown",
                    duration = streamInfo.duration,
                    thumbnailUrl = thumbnailUrl,
                    quality = resolution
                )
            }

            Log.e(TAG, "No video streams found for: $videoId")
            null

        } catch (e: Exception) {
            Log.e(TAG, "NewPipe video extraction failed for $videoId: ${e.message}", e)
            null
        }
    }

    /**
     * Get all audio streams with itag -> URL mapping.
     * This matches SimpMusic's newPipePlayer() return format.
     */
    suspend fun getAudioStreams(videoId: String): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            if (!initialize()) {
                return@withContext emptyList()
            }
        }

        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(NewPipe.getService(0), url)

            val streams = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            streams.mapNotNull { stream ->
                val itag = stream.itagItem?.id ?: return@mapNotNull null
                val content = stream.content ?: return@mapNotNull null
                itag to content
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get audio streams: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Convert extraction result to Track and stream URL
     */
    fun toPlayerResult(result: ExtractionResult, videoId: String): Pair<String, Track> {
        val track = Track(
            id = videoId,
            title = result.title,
            artistName = result.artist,
            artistId = null,
            albumName = null,
            albumId = null,
            thumbnailUrl = result.thumbnailUrl,
            duration = result.duration * 1000, // Convert to milliseconds
            isExplicit = false
        )
        return result.audioUrl to track
    }

    /**
     * OkHttp-based Downloader for NewPipe Extractor.
     * Based on SimpMusic's NewPipeDownloaderImpl.
     */
    private class OkHttpDownloader : Downloader() {

        private val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        override fun execute(request: Request): Response {
            val httpMethod = request.httpMethod()
            val url = request.url()
            val headers = request.headers()
            val dataToSend = request.dataToSend()

            val requestBuilder = okhttp3.Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)

            // Handle request body - POST/PUT require a body even if empty
            val body = dataToSend?.toRequestBody()
            requestBuilder.method(httpMethod, body)

            // Add all headers from the request
            headers.forEach { (headerName, headerValueList) ->
                if (headerValueList.size > 1) {
                    requestBuilder.removeHeader(headerName)
                    headerValueList.forEach { headerValue ->
                        requestBuilder.addHeader(headerName, headerValue)
                    }
                } else if (headerValueList.size == 1) {
                    requestBuilder.header(headerName, headerValueList[0])
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()

            // Handle rate limiting
            if (response.code == 429) {
                response.close()
                throw IOException("Rate limited (429) - reCaptcha challenge required for: $url")
            }

            val responseBody = response.body?.string()
            val latestUrl = response.request.url.toString()

            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                latestUrl
            )
        }
    }
}
