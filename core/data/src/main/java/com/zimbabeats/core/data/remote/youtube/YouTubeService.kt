package com.zimbabeats.core.data.remote.youtube

import android.util.Log
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONObject

/**
 * YouTube service for scraping videos without API
 * Uses Ktor HTTP client and Jsoup for HTML parsing
 * Uses Innertube API for real stream URL extraction
 */
class YouTubeService(private val httpClient: HttpClient) {

    private val innertubeClient = InnertubeClient(httpClient)

    companion object {
        private const val TAG = "YouTubeService"
        private const val YOUTUBE_SEARCH_URL = "https://www.youtube.com/results"
        private const val YOUTUBE_VIDEO_URL = "https://www.youtube.com/watch"
        private const val YOUTUBE_TRENDING_URL = "https://www.youtube.com/feed/trending"
    }

    /**
     * Enable or disable kid-safe mode for searches
     * When enabled, uses YouTube Kids API for safer content
     */
    fun setKidSafeMode(enabled: Boolean) {
        innertubeClient.kidSafeModeEnabled = enabled
        Log.d(TAG, "Kid-safe mode set to: $enabled")
    }

    /**
     * Check if kid-safe mode is enabled
     */
    fun isKidSafeModeEnabled(): Boolean = innertubeClient.kidSafeModeEnabled

    /**
     * Search for videos on YouTube using Innertube API
     */
    suspend fun searchVideos(query: String, maxResults: Int = 50): List<YouTubeVideo> {
        return try {
            Log.d(TAG, "Starting search for query: '$query', maxResults: $maxResults")
            val results = innertubeClient.searchVideos(query)
            Log.d(TAG, "Innertube returned ${results.size} results for: '$query'")

            val limitedResults = results.take(maxResults)
            Log.d(TAG, "Returning ${limitedResults.size} real YouTube results")
            limitedResults
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for: $query", e)
            e.printStackTrace()
            // Return empty list on error - no mock data
            emptyList()
        }
    }

    /**
     * Get trending videos using Innertube API
     */
    suspend fun getTrendingVideos(maxResults: Int = 20): List<YouTubeVideo> {
        return try {
            Log.d(TAG, "Fetching trending videos via Innertube API")
            val results = innertubeClient.getTrendingVideos()
            Log.d(TAG, "Innertube trending returned ${results.size} videos")
            results.take(maxResults)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch trending videos", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get detailed video information
     */
    suspend fun getVideoInfo(videoId: String): YouTubeVideo? =
        withContext(Dispatchers.IO) {
            try {
                val response: String = httpClient.get(YOUTUBE_VIDEO_URL) {
                    parameter("v", videoId)
                }.bodyAsText()

                parseVideoInfo(response, videoId)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Get stream URLs for a video using Innertube API
     */
    suspend fun getStreamUrls(videoId: String): List<StreamUrl> {
        return innertubeClient.getStreamUrls(videoId)
    }

    /**
     * Get search suggestions/autocomplete as user types
     * This helps with typos by suggesting correct spellings
     */
    suspend fun getSearchSuggestions(query: String): List<String> {
        return try {
            innertubeClient.getSearchSuggestions(query)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get suggestions for: $query", e)
            emptyList()
        }
    }

    /**
     * Search with spelling correction support
     */
    suspend fun searchVideosWithCorrection(query: String, maxResults: Int = 50): SearchResultWithCorrection {
        return try {
            Log.d(TAG, "Searching with correction for: '$query'")
            val result = innertubeClient.searchVideosWithCorrection(query)
            Log.d(TAG, "Got ${result.videos.size} results, correction: ${result.correctedQuery}")
            SearchResultWithCorrection(
                videos = result.videos.take(maxResults),
                correctedQuery = result.correctedQuery,
                originalQuery = result.originalQuery
            )
        } catch (e: Exception) {
            Log.e(TAG, "Search with correction failed for: $query", e)
            SearchResultWithCorrection(emptyList(), null, query)
        }
    }

    private fun parseSearchResults(html: String): List<YouTubeVideo> {
        try {
            // Extract ytInitialData JSON from HTML
            val ytInitialDataRegex = """var ytInitialData = (\{.*?\});""".toRegex()
            val match = ytInitialDataRegex.find(html) ?: return emptyList()
            val jsonData = match.groupValues[1]

            val videos = mutableListOf<YouTubeVideo>()

            // Parse JSON and extract video data
            val json = JSONObject(jsonData)
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            contents?.let { contentsArray ->
                for (i in 0 until contentsArray.length()) {
                    val item = contentsArray.optJSONObject(i)
                        ?.optJSONObject("itemSectionRenderer")
                        ?.optJSONArray("contents")

                    item?.let { itemArray ->
                        for (j in 0 until itemArray.length()) {
                            val videoRenderer = itemArray.optJSONObject(j)
                                ?.optJSONObject("videoRenderer")

                            videoRenderer?.let { video ->
                                val videoId = video.optString("videoId")
                                val title = video.optJSONObject("title")
                                    ?.optJSONArray("runs")
                                    ?.optJSONObject(0)
                                    ?.optString("text") ?: "Unknown"

                                val channelName = video.optJSONObject("ownerText")
                                    ?.optJSONArray("runs")
                                    ?.optJSONObject(0)
                                    ?.optString("text") ?: "Unknown"

                                val thumbnail = video.optJSONObject("thumbnail")
                                    ?.optJSONArray("thumbnails")
                                    ?.optJSONObject(0)
                                    ?.optString("url") ?: ""

                                if (videoId.isNotEmpty()) {
                                    videos.add(
                                        YouTubeVideo(
                                            id = videoId,
                                            title = title,
                                            description = null,
                                            thumbnailUrl = thumbnail,
                                            channelName = channelName,
                                            channelId = "",
                                            duration = 0,
                                            viewCount = 0,
                                            publishedAt = System.currentTimeMillis(),
                                            url = "https://www.youtube.com/watch?v=$videoId",
                                            category = null,
                                            ageLimit = 0
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            return videos
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun parseVideoInfo(html: String, videoId: String): YouTubeVideo {
        try {
            val document = Jsoup.parse(html)
            val title = document.select("meta[name=title]").attr("content")
            val description = document.select("meta[name=description]").attr("content")
            val thumbnail = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"

            return YouTubeVideo(
                id = videoId,
                title = title.ifEmpty { "Unknown Video" },
                description = description.ifEmpty { null },
                thumbnailUrl = thumbnail,
                channelName = "Unknown Channel",
                channelId = "",
                duration = 0,
                viewCount = 0,
                publishedAt = System.currentTimeMillis(),
                url = "https://www.youtube.com/watch?v=$videoId",
                category = null,
                ageLimit = 0
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return YouTubeVideo(
                id = videoId,
                title = "Video $videoId",
                description = null,
                thumbnailUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                channelName = "Unknown",
                channelId = "",
                duration = 0,
                viewCount = 0,
                publishedAt = System.currentTimeMillis(),
                url = "https://www.youtube.com/watch?v=$videoId",
                category = null,
                ageLimit = 0
            )
        }
    }

}

/**
 * YouTube video data class
 */
data class YouTubeVideo(
    val id: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String,
    val channelName: String,
    val channelId: String,
    val duration: Long,
    val viewCount: Long,
    val publishedAt: Long,
    val url: String,
    val category: String? = null,
    val ageLimit: Int = 0,
    val isFamilySafe: Boolean = true,
    val isMadeForKids: Boolean = false
)

/**
 * Stream URL with quality info
 */
data class StreamUrl(
    val url: String,
    val quality: String,
    val format: String,
    val isVideoOnly: Boolean
)

/**
 * Search result with spelling correction info
 */
data class SearchResultWithCorrection(
    val videos: List<YouTubeVideo>,
    val correctedQuery: String?,
    val originalQuery: String
)

/**
 * YouTube service exception
 */
class YouTubeException(message: String, cause: Throwable? = null) : Exception(message, cause)
