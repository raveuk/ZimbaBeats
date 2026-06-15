package com.zimbabeats.core.data.remote.youtube

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Third-tier stream resolver — runs the bundled yt-dlp script via the
 * youtubedl-android library (Python interpreter shipped in the APK).
 *
 * Used as a fallback when both [InnertubeClient] and [NewPipeStreamExtractor]
 * fail. Slow (seconds per call — Python startup + network) but extremely robust
 * against YouTube's frequent API breakage because yt-dlp is the most actively
 * maintained extractor in the world.
 *
 * The library bundles a baseline yt-dlp script in the APK and extracts it to
 * `filesDir/youtubedl-android/` on first init. A future auto-update channel
 * (Piece 4 of the rewrite plan) can swap in a newer script remotely without
 * shipping an app release.
 */
class YtDlpStreamResolver {

    companion object {
        private const val TAG = "YtDlpResolver"
        private const val WATCH_URL_PREFIX = "https://www.youtube.com/watch?v="
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Resolve all available stream URLs for a video.
     *
     * yt-dlp's `-J` (dump full JSON) output contains a `formats[]` array with one entry
     * per available stream. Each entry carries `url`, `vcodec`, `acodec`, `height`,
     * `width`, `tbr`/`abr`, `filesize`, `ext`. We map this onto the same [StreamUrl]
     * shape that the other resolvers emit so [com.zimbabeats.download.DownloadManager]
     * can treat all three resolvers identically.
     *
     * Returns empty list on any failure — never throws. The caller falls through to
     * the next resolver (or surfaces "no qualities").
     */
    suspend fun resolve(videoId: String): List<StreamUrl> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resolving streams via yt-dlp for $videoId")
            val request = YoutubeDLRequest("$WATCH_URL_PREFIX$videoId").apply {
                addOption("-J")             // dump JSON metadata, don't download
                addOption("--no-warnings")
                addOption("--no-playlist")
            }
            val response = YoutubeDL.getInstance().execute(request)
            val raw = response.out
            if (raw.isNullOrBlank()) {
                Log.w(TAG, "yt-dlp returned empty output for $videoId")
                return@withContext emptyList()
            }
            parseFormats(raw)
        } catch (e: Throwable) {
            Log.w(TAG, "yt-dlp execute failed for $videoId: ${e.message}")
            emptyList()
        }
    }

    private fun parseFormats(jsonText: String): List<StreamUrl> {
        return try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val formats = root["formats"]?.jsonArray ?: return emptyList()

            val out = mutableListOf<StreamUrl>()
            formats.forEach { entry ->
                val obj = entry.jsonObject
                val url = obj["url"]?.jsonPrimitive?.content ?: return@forEach
                val vcodec = obj["vcodec"]?.jsonPrimitive?.content?.takeIf { it != "none" }
                val acodec = obj["acodec"]?.jsonPrimitive?.content?.takeIf { it != "none" }
                val height = obj["height"]?.jsonPrimitive?.intOrNull ?: 0
                val width = obj["width"]?.jsonPrimitive?.intOrNull ?: 0
                val ext = obj["ext"]?.jsonPrimitive?.content ?: "unknown"
                val filesize = obj["filesize"]?.jsonPrimitive?.longOrNull
                    ?: obj["filesize_approx"]?.jsonPrimitive?.longOrNull
                    ?: -1L
                val abr = obj["abr"]?.jsonPrimitive?.content?.toFloatOrNull()?.toInt() ?: 0
                val tbr = obj["tbr"]?.jsonPrimitive?.content?.toFloatOrNull()?.toInt() ?: 0

                // Skip entries with no streams (audio-only + video-only both absent).
                val isAudioOnly = vcodec == null && acodec != null
                val isVideoOnly = vcodec != null && acodec == null
                val isCombined = vcodec != null && acodec != null
                if (!isAudioOnly && !isVideoOnly && !isCombined) return@forEach

                val quality = when {
                    isAudioOnly -> "${if (abr > 0) abr else tbr}kbps"
                    isVideoOnly -> "${height}p (video-only)"
                    else -> "${height}p"
                }

                out += StreamUrl(
                    url = url,
                    quality = quality,
                    format = ext,
                    isVideoOnly = isVideoOnly,
                    videoCodec = vcodec?.let { normalizeCodec(it) },
                    audioCodec = acodec?.let { normalizeCodec(it) },
                    height = height,
                    bitrate = (if (isAudioOnly) abr else tbr).let { if (it > 0) it * 1000 else 0 },
                    contentLength = filesize
                )
            }
            Log.d(TAG, "yt-dlp parsed ${out.size} stream formats")
            out
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse yt-dlp JSON: ${e.message}")
            emptyList()
        }
    }

    /**
     * Reduce yt-dlp's verbose codec strings down to the short family names the rest of
     * the pipeline uses. yt-dlp emits things like `avc1.640028`, `vp09.00.41.08`,
     * `mp4a.40.2`, `opus`.
     */
    private fun normalizeCodec(raw: String): String = raw.substringBefore('.').lowercase().let {
        when {
            it.startsWith("avc") -> "avc1"
            it.startsWith("vp09") || it == "vp9" -> "vp9"
            it.startsWith("av01") -> "av01"
            it.startsWith("mp4a") -> "mp4a"
            it == "opus" -> "opus"
            else -> it
        }
    }
}
