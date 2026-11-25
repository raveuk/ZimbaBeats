package com.zimbabeats.shared.network

import com.zimbabeats.shared.model.SharedStreamUrl
import com.zimbabeats.shared.model.SharedVideo
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Multiplatform YouTube InnerTube client that runs on both Android (OkHttp) and iOS (Darwin).
 */
class SharedInnertubeClient(
    private val httpClient: HttpClient = createDefaultHttpClient()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val INNERTUBE_SEARCH_URL = "https://www.youtube.com/youtubei/v1/search"
        private const val INNERTUBE_PLAYER_URL = "https://www.youtube.com/youtubei/v1/player"
        private const val SEARCH_CLIENT_NAME = "WEB"
        private const val SEARCH_CLIENT_VERSION = "2.20250222.10.00"
        private const val IOS_CLIENT_NAME = "IOS"
        private const val IOS_CLIENT_VERSION = "19.45.4"

        fun createDefaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    suspend fun searchKidSafeVideos(query: String): List<SharedVideo> {
        return try {
            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", SEARCH_CLIENT_NAME)
                        put("clientVersion", SEARCH_CLIENT_VERSION)
                        put("hl", "en")
                        put("gl", "US")
                    }
                    putJsonObject("user") {
                        put("enableSafetyMode", true)
                    }
                }
                put("query", query)
            }

            val responseText = httpClient.post(INNERTUBE_SEARCH_URL) {
                contentType(ContentType.Application.Json)
                header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15")
                setBody(requestBody.toString())
            }.bodyAsText()

            parseSearchResponse(responseText)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun resolveStreamUrls(videoId: String): List<SharedStreamUrl> {
        return try {
            val requestBody = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", IOS_CLIENT_NAME)
                        put("clientVersion", IOS_CLIENT_VERSION)
                        put("deviceMake", "Apple")
                        put("deviceModel", "iPhone16,2")
                        put("osName", "iPhone")
                        put("osVersion", "17.5.1.21F90")
                        put("hl", "en")
                        put("gl", "US")
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val responseText = httpClient.post(INNERTUBE_PLAYER_URL) {
                contentType(ContentType.Application.Json)
                header("User-Agent", "com.google.ios.youtube/$IOS_CLIENT_VERSION (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)")
                header("X-YouTube-Client-Name", "5")
                header("X-YouTube-Client-Version", IOS_CLIENT_VERSION)
                setBody(requestBody.toString())
            }.bodyAsText()

            parsePlayerStreams(responseText)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseSearchResponse(responseText: String): List<SharedVideo> {
        val results = mutableListOf<SharedVideo>()
        val root = json.parseToJsonElement(responseText).jsonObject
        val rendererObjects = mutableListOf<JsonObject>()
        collectVideoRenderers(root, rendererObjects)

        for (renderer in rendererObjects) {
            val videoId = renderer["videoId"]?.jsonPrimitive?.content ?: continue
            val title = extractText(renderer["title"]) ?: continue
            val channelName = extractText(renderer["ownerText"])
                ?: extractText(renderer["longBylineText"])
                ?: "YouTube Kids"
            val duration = extractText(renderer["lengthText"]) ?: ""
            val views = extractText(renderer["shortViewCountText"]) ?: ""
            val thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            results.add(
                SharedVideo(
                    id = videoId,
                    title = title,
                    channelName = channelName,
                    thumbnailUrl = thumbnailUrl,
                    durationText = duration,
                    viewCountText = views
                )
            )
        }
        return results.distinctBy { it.id }
    }

    private fun collectVideoRenderers(element: JsonElement, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                element["videoRenderer"]?.let {
                    if (it is JsonObject) out.add(it)
                }
                for ((_, value) in element) {
                    collectVideoRenderers(value, out)
                }
            }
            is JsonArray -> {
                for (item in element) {
                    collectVideoRenderers(item, out)
                }
            }
            else -> {}
        }
    }

    private fun extractText(element: JsonElement?): String? {
        if (element !is JsonObject) return null
        element["simpleText"]?.jsonPrimitive?.content?.let { return it }
        val runs = element["runs"] as? JsonArray ?: return null
        return runs.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }.joinToString("")
    }

    private fun parsePlayerStreams(responseText: String): List<SharedStreamUrl> {
        val streams = mutableListOf<SharedStreamUrl>()
        val root = json.parseToJsonElement(responseText).jsonObject
        val streamingData = root["streamingData"]?.jsonObject ?: return emptyList()

        // HLS manifest URL (ideal for iOS AVPlayer)
        streamingData["hlsManifestUrl"]?.jsonPrimitive?.content?.let { hlsUrl ->
            streams.add(
                SharedStreamUrl(
                    url = hlsUrl,
                    mimeType = "application/x-mpegURL",
                    qualityLabel = "Auto (HLS)",
                    isAudioOnly = false
                )
            )
        }

        val formats = streamingData["formats"] as? JsonArray ?: JsonArray(emptyList())
        for (item in formats) {
            val formatObj = item as? JsonObject ?: continue
            val url = formatObj["url"]?.jsonPrimitive?.content ?: continue
            val mimeType = formatObj["mimeType"]?.jsonPrimitive?.content ?: "video/mp4"
            val qualityLabel = formatObj["qualityLabel"]?.jsonPrimitive?.content ?: "360p"
            streams.add(
                SharedStreamUrl(
                    url = url,
                    mimeType = mimeType,
                    qualityLabel = qualityLabel,
                    isAudioOnly = mimeType.startsWith("audio/")
                )
            )
        }
        return streams
    }
}
