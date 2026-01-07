package com.zimbabeats.core.data.remote.youtube.music

import android.util.Log
import com.zimbabeats.core.domain.model.music.*
import kotlinx.serialization.json.*

/**
 * Parser for YouTube Music API responses
 * Handles the unique response structures from the WEB_REMIX client
 */
object MusicResponseParser {

    private const val TAG = "MusicResponseParser"

    /**
     * Data class to hold search results with continuation token
     */
    data class SearchResultsWithContinuation(
        val results: List<MusicSearchResult>,
        val continuationToken: String?
    )

    /**
     * Parse search results from YouTube Music search response
     */
    fun parseSearchResults(response: JsonObject): List<MusicSearchResult> {
        return parseSearchResultsWithContinuation(response).results
    }

    /**
     * Parse search results with continuation token for pagination
     */
    fun parseSearchResultsWithContinuation(response: JsonObject): SearchResultsWithContinuation {
        val results = mutableListOf<MusicSearchResult>()
        var continuationToken: String? = null

        try {
            Log.d(TAG, "parseSearchResults - top level keys: ${response.keys.joinToString()}")

            // Navigate to search results
            val sectionListRenderer = response["contents"]?.jsonObject
                ?.get("tabbedSearchResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject

            val contents = sectionListRenderer?.get("contents")?.jsonArray

            Log.d(TAG, "Found ${contents?.size ?: 0} content sections")

            contents?.forEachIndexed { index, section ->
                val shelfRenderer = section.jsonObject["musicShelfRenderer"]?.jsonObject
                val cardRenderer = section.jsonObject["musicCardShelfRenderer"]?.jsonObject

                Log.d(TAG, "Section $index: musicShelfRenderer=${shelfRenderer != null}, musicCardShelfRenderer=${cardRenderer != null}")

                // Parse items from musicShelfRenderer
                shelfRenderer?.get("contents")?.jsonArray?.forEach { item ->
                    parseSearchResultItem(item.jsonObject)?.let { results.add(it) }
                }

                // Get continuation token from musicShelfRenderer
                if (shelfRenderer != null && continuationToken == null) {
                    continuationToken = shelfRenderer["continuations"]?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("nextContinuationData")?.jsonObject
                        ?.get("continuation")?.jsonPrimitive?.contentOrNull
                    if (continuationToken != null) {
                        Log.d(TAG, "Found continuation token: ${continuationToken?.take(50)}...")
                    }
                }

                // Also try musicCardShelfRenderer for top result
                cardRenderer?.let {
                    parseCardShelfItem(it)?.let { result -> results.add(0, result) }
                }
            }

            Log.d(TAG, "Parsed ${results.size} total results, has continuation: ${continuationToken != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search results", e)
        }

        return SearchResultsWithContinuation(results, continuationToken)
    }

    /**
     * Parse continuation search results (different structure than initial search)
     */
    fun parseContinuationResults(response: JsonObject): SearchResultsWithContinuation {
        val results = mutableListOf<MusicSearchResult>()
        var continuationToken: String? = null

        try {
            // Continuation responses have a different structure
            val continuationContents = response["continuationContents"]?.jsonObject
                ?.get("musicShelfContinuation")?.jsonObject

            if (continuationContents != null) {
                // Parse items
                continuationContents["contents"]?.jsonArray?.forEach { item ->
                    parseSearchResultItem(item.jsonObject)?.let { results.add(it) }
                }

                // Get next continuation token
                continuationToken = continuationContents["continuations"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("nextContinuationData")?.jsonObject
                    ?.get("continuation")?.jsonPrimitive?.contentOrNull

                Log.d(TAG, "Parsed ${results.size} continuation results, has more: ${continuationToken != null}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing continuation results", e)
        }

        return SearchResultsWithContinuation(results, continuationToken)
    }

    /**
     * Parse the top result card (musicCardShelfRenderer)
     */
    private fun parseCardShelfItem(cardRenderer: JsonObject): MusicSearchResult? {
        return try {
            val title = cardRenderer["title"]?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull

            val subtitle = cardRenderer["subtitle"]?.jsonObject
                ?.get("runs")?.jsonArray

            val thumbnails = cardRenderer["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

            // Check navigation to determine type
            val onTap = cardRenderer["onTap"]?.jsonObject
            val watchEndpoint = onTap?.get("watchEndpoint")?.jsonObject
            val browseEndpoint = onTap?.get("browseEndpoint")?.jsonObject

            when {
                watchEndpoint != null -> {
                    val videoId = watchEndpoint["videoId"]?.jsonPrimitive?.contentOrNull ?: return null
                    val artistName = subtitle?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

                    Log.d(TAG, "Parsed top result track: $title (id=$videoId)")
                    MusicSearchResult.TrackResult(
                        Track(
                            id = videoId,
                            title = title ?: "Unknown",
                            artistName = artistName,
                            artistId = null,
                            albumName = null,
                            albumId = null,
                            thumbnailUrl = thumbnailUrl,
                            duration = 0
                        )
                    )
                }
                browseEndpoint != null -> {
                    val browseId = browseEndpoint["browseId"]?.jsonPrimitive?.contentOrNull ?: return null
                    val pageType = browseEndpoint["browseEndpointContextSupportedConfigs"]?.jsonObject
                        ?.get("browseEndpointContextMusicConfig")?.jsonObject
                        ?.get("pageType")?.jsonPrimitive?.contentOrNull

                    when {
                        pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") -> {
                            Log.d(TAG, "Parsed top result artist: $title")
                            MusicSearchResult.ArtistResult(
                                Artist(
                                    id = browseId,
                                    name = title ?: "Unknown Artist",
                                    thumbnailUrl = thumbnailUrl,
                                    subscriberCount = null
                                )
                            )
                        }
                        pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb") -> {
                            val artistName = subtitle?.firstOrNull()?.jsonObject
                                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                            Log.d(TAG, "Parsed top result album: $title")
                            MusicSearchResult.AlbumResult(
                                Album(
                                    id = browseId,
                                    title = title ?: "Unknown Album",
                                    artistName = artistName,
                                    artistId = null,
                                    thumbnailUrl = thumbnailUrl,
                                    year = null
                                )
                            )
                        }
                        else -> null
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing card shelf item", e)
            null
        }
    }

    private fun parseSearchResultItem(item: JsonObject): MusicSearchResult? {
        return try {
            val renderer = item["musicResponsiveListItemRenderer"]?.jsonObject
                ?: return null

            val flexColumns = renderer["flexColumns"]?.jsonArray
            val navigationEndpoint = renderer["navigationEndpoint"]?.jsonObject

            // Log all renderer keys for debugging
            Log.d(TAG, "Renderer keys: ${renderer.keys.joinToString()}")

            // Determine type from navigation endpoint
            val watchEndpoint = navigationEndpoint?.get("watchEndpoint")?.jsonObject
            val browseEndpoint = navigationEndpoint?.get("browseEndpoint")?.jsonObject

            // Also check for watchPlaylistEndpoint which is used for songs in search
            val watchPlaylistEndpoint = navigationEndpoint?.get("watchPlaylistEndpoint")?.jsonObject

            // Also check for playNavigationEndpoint in the overlay (common for tracks in search)
            val overlayWatchEndpoint = renderer["overlay"]?.jsonObject
                ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicPlayButtonRenderer")?.jsonObject
                ?.get("playNavigationEndpoint")?.jsonObject
                ?.get("watchEndpoint")?.jsonObject

            // Check for playlistItemData which contains videoId for tracks
            val playlistItemData = renderer["playlistItemData"]?.jsonObject
            val hasVideoId = playlistItemData?.get("videoId") != null

            // Check browseEndpoint details first to detect albums/artists/playlists
            val browseId = browseEndpoint?.get("browseId")?.jsonPrimitive?.contentOrNull ?: ""
            val pageType = browseEndpoint?.get("browseEndpointContextSupportedConfigs")?.jsonObject
                ?.get("browseEndpointContextMusicConfig")?.jsonObject
                ?.get("pageType")?.jsonPrimitive?.contentOrNull

            // Determine if this is an album, artist, or playlist based on browseId/pageType
            val isAlbum = pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb")
            val isArtist = pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC")
            val isPlaylist = pageType == "MUSIC_PAGE_TYPE_PLAYLIST"

            val hasPlayableContent = watchEndpoint != null || watchPlaylistEndpoint != null || overlayWatchEndpoint != null || hasVideoId

            Log.d(TAG, "Parsing item: browseId=$browseId, pageType=$pageType, isAlbum=$isAlbum, isArtist=$isArtist, hasPlayableContent=$hasPlayableContent")

            when {
                // Check for albums FIRST (they may have play overlays but should be treated as albums)
                isAlbum -> {
                    Log.d(TAG, "  Parsing as album")
                    parseAlbumFromRenderer(renderer, browseId)?.let { MusicSearchResult.AlbumResult(it) }
                }
                // Check for artists
                isArtist -> {
                    Log.d(TAG, "  Parsing as artist")
                    parseArtistFromRenderer(renderer, browseId)?.let { MusicSearchResult.ArtistResult(it) }
                }
                // Check for playlists
                isPlaylist -> {
                    Log.d(TAG, "  Parsing as playlist")
                    parsePlaylistFromRenderer(renderer, browseId)?.let { MusicSearchResult.PlaylistResult(it) }
                }
                // Fall back to track if it has playable content
                hasPlayableContent -> {
                    val track = parseTrackFromRenderer(renderer)
                    if (track != null) {
                        Log.d(TAG, "  Parsed track: ${track.title} (id=${track.id})")
                        MusicSearchResult.TrackResult(track)
                    } else {
                        Log.d(TAG, "  Failed to parse track from renderer")
                        null
                    }
                }
                else -> {
                    Log.d(TAG, "  Unknown item type, skipping")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search result item", e)
            null
        }
    }

    private fun parseTrackFromRenderer(renderer: JsonObject): Track? {
        return try {
            val flexColumns = renderer["flexColumns"]?.jsonArray ?: run {
                Log.d(TAG, "No flexColumns found in track renderer")
                return null
            }
            val thumbnails = renderer["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            Log.d(TAG, "parseTrackFromRenderer - flexColumns count: ${flexColumns.size}")

            // Try multiple paths to find the video ID
            val videoId = renderer["playlistItemData"]?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull
                // From overlay play button
                ?: renderer["overlay"]?.jsonObject
                    ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("musicPlayButtonRenderer")?.jsonObject
                    ?.get("playNavigationEndpoint")?.jsonObject
                    ?.get("watchEndpoint")?.jsonObject
                    ?.get("videoId")?.jsonPrimitive?.contentOrNull
                // From navigation endpoint watchEndpoint
                ?: renderer["navigationEndpoint"]?.jsonObject
                    ?.get("watchEndpoint")?.jsonObject
                    ?.get("videoId")?.jsonPrimitive?.contentOrNull
                // From flexColumns runs navigation
                ?: flexColumns?.getOrNull(0)?.jsonObject
                    ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                    ?.get("text")?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("navigationEndpoint")?.jsonObject
                    ?.get("watchEndpoint")?.jsonObject
                    ?.get("videoId")?.jsonPrimitive?.contentOrNull
                ?: run {
                    Log.d(TAG, "No videoId found in track renderer, keys: ${renderer.keys}")
                    return null
                }

            val title = flexColumns.getOrNull(0)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

            val artistInfo = flexColumns.getOrNull(1)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray

            // Debug log all artist info runs
            val artistRuns = artistInfo?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            Log.d(TAG, "parseTrackFromRenderer - title: $title, artistRuns: $artistRuns")

            val artistName = artistInfo?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

            val artistId = artistInfo?.firstOrNull()?.jsonObject
                ?.get("navigationEndpoint")?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull

            // Album name is typically after " • " separator
            val albumInfo = artistInfo?.find {
                val browseId = it.jsonObject["navigationEndpoint"]?.jsonObject
                    ?.get("browseEndpoint")?.jsonObject
                    ?.get("browseId")?.jsonPrimitive?.contentOrNull
                browseId?.startsWith("MPREb") == true
            }

            val albumName = albumInfo?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            val albumId = albumInfo?.jsonObject?.get("navigationEndpoint")?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

            // Parse duration from fixedColumns (if available - not always present in search results)
            val duration = renderer["fixedColumns"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("musicResponsiveListItemFixedColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
                ?.let { parseDuration(it) } ?: 0L

            val isExplicit = renderer["badges"]?.jsonArray?.any {
                it.jsonObject["musicInlineBadgeRenderer"]?.jsonObject
                    ?.get("icon")?.jsonObject
                    ?.get("iconType")?.jsonPrimitive?.contentOrNull == "MUSIC_EXPLICIT_BADGE"
            } == true

            Log.d(TAG, "Parsed track: $title by $artistName (id=$videoId)")
            Track(
                id = videoId,
                title = title,
                artistName = artistName,
                artistId = artistId,
                albumName = albumName,
                albumId = albumId,
                thumbnailUrl = thumbnailUrl,
                duration = duration,
                isExplicit = isExplicit
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing track from renderer", e)
            null
        }
    }

    private fun parseAlbumFromRenderer(renderer: JsonObject, browseId: String): Album? {
        return try {
            val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null
            val thumbnails = renderer["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val title = flexColumns.getOrNull(0)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Album"

            val artistInfo = flexColumns.getOrNull(1)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray

            val artistName = artistInfo?.firstOrNull {
                it.jsonObject["navigationEndpoint"]?.jsonObject
                    ?.get("browseEndpoint")?.jsonObject
                    ?.get("browseId")?.jsonPrimitive?.contentOrNull?.startsWith("UC") == true
            }?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: artistInfo?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: "Unknown Artist"

            val artistId = artistInfo?.firstOrNull()?.jsonObject
                ?.get("navigationEndpoint")?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull

            // Parse year from subtitle
            val yearStr = artistInfo?.find {
                val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                text?.matches(Regex("\\d{4}")) == true
            }?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

            Album(
                id = browseId,
                title = title,
                artistName = artistName,
                artistId = artistId,
                thumbnailUrl = thumbnailUrl,
                year = yearStr?.toIntOrNull()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing album from renderer", e)
            null
        }
    }

    private fun parseArtistFromRenderer(renderer: JsonObject, browseId: String): Artist? {
        return try {
            val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null
            val thumbnails = renderer["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val name = flexColumns.getOrNull(0)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

            val subscriberCount = flexColumns.getOrNull(1)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull

            Artist(
                id = browseId,
                name = name,
                thumbnailUrl = thumbnailUrl,
                subscriberCount = subscriberCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing artist from renderer", e)
            null
        }
    }

    private fun parsePlaylistFromRenderer(renderer: JsonObject, browseId: String): YouTubeMusicPlaylist? {
        return try {
            val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null
            val thumbnails = renderer["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val title = flexColumns.getOrNull(0)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Playlist"

            val subtitle = flexColumns.getOrNull(1)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray

            val author = subtitle?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

            YouTubeMusicPlaylist(
                id = browseId,
                title = title,
                author = author,
                thumbnailUrl = thumbnailUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing playlist from renderer", e)
            null
        }
    }

    /**
     * Parse browse sections from YouTube Music home/explore page
     */
    fun parseBrowseSections(response: JsonObject): List<MusicBrowseSection> {
        val sections = mutableListOf<MusicBrowseSection>()

        try {
            val contents = response["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

            contents?.forEach { section ->
                parseBrowseSection(section.jsonObject)?.let { sections.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing browse sections", e)
        }

        return sections
    }

    private fun parseBrowseSection(section: JsonObject): MusicBrowseSection? {
        return try {
            val shelfRenderer = section["musicCarouselShelfRenderer"]?.jsonObject
                ?: section["musicShelfRenderer"]?.jsonObject
                ?: return null

            val header = shelfRenderer["header"]?.jsonObject
                ?.get("musicCarouselShelfBasicHeaderRenderer")?.jsonObject
                ?: shelfRenderer["header"]?.jsonObject
                    ?.get("musicShelfBasicHeaderRenderer")?.jsonObject

            val title = header?.get("title")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Section"

            val browseId = header?.get("moreContentButton")?.jsonObject
                ?.get("buttonRenderer")?.jsonObject
                ?.get("navigationEndpoint")?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull

            val contents = shelfRenderer["contents"]?.jsonArray
            val items = mutableListOf<MusicBrowseItem>()

            Log.d(TAG, "parseBrowseSection - section '$title' has ${contents?.size ?: 0} content items")

            contents?.forEachIndexed { index, item ->
                val parsedItem = parseBrowseItem(item.jsonObject)
                if (parsedItem != null) {
                    items.add(parsedItem)
                    when (parsedItem) {
                        is MusicBrowseItem.TrackItem -> Log.d(TAG, "  [$index] Track: ${parsedItem.track.title}")
                        is MusicBrowseItem.AlbumItem -> Log.d(TAG, "  [$index] Album: ${parsedItem.album.title}")
                        is MusicBrowseItem.ArtistItem -> Log.d(TAG, "  [$index] Artist: ${parsedItem.artist.name}")
                        is MusicBrowseItem.PlaylistItem -> Log.d(TAG, "  [$index] Playlist: ${parsedItem.playlist.title}")
                    }
                } else {
                    // Log what we failed to parse
                    val itemKeys = item.jsonObject.keys.joinToString()
                    Log.d(TAG, "  [$index] FAILED to parse item, keys: $itemKeys")
                }
            }

            Log.d(TAG, "parseBrowseSection - section '$title' parsed ${items.size} items")

            if (items.isNotEmpty()) {
                MusicBrowseSection(
                    title = title,
                    browseId = browseId,
                    items = items
                )
            } else {
                Log.w(TAG, "parseBrowseSection - section '$title' has 0 items, skipping")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing browse section", e)
            null
        }
    }

    private fun parseBrowseItem(item: JsonObject): MusicBrowseItem? {
        return try {
            // Try different renderer types
            val twoRowRenderer = item["musicTwoRowItemRenderer"]?.jsonObject
            val responsiveRenderer = item["musicResponsiveListItemRenderer"]?.jsonObject

            when {
                twoRowRenderer != null -> {
                    val navigationEndpoint = twoRowRenderer["navigationEndpoint"]?.jsonObject
                    val watchEndpoint = navigationEndpoint?.get("watchEndpoint")?.jsonObject
                    val browseEndpoint = navigationEndpoint?.get("browseEndpoint")?.jsonObject

                    when {
                        watchEndpoint != null -> {
                            parseTrackFromTwoRowRenderer(twoRowRenderer)?.let { MusicBrowseItem.TrackItem(it) }
                        }
                        browseEndpoint != null -> {
                            val browseId = browseEndpoint["browseId"]?.jsonPrimitive?.contentOrNull ?: ""
                            val pageType = browseEndpoint["browseEndpointContextSupportedConfigs"]?.jsonObject
                                ?.get("browseEndpointContextMusicConfig")?.jsonObject
                                ?.get("pageType")?.jsonPrimitive?.contentOrNull

                            when {
                                pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb") -> {
                                    parseAlbumFromTwoRowRenderer(twoRowRenderer, browseId)?.let { MusicBrowseItem.AlbumItem(it) }
                                }
                                pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") -> {
                                    parseArtistFromTwoRowRenderer(twoRowRenderer, browseId)?.let { MusicBrowseItem.ArtistItem(it) }
                                }
                                pageType == "MUSIC_PAGE_TYPE_PLAYLIST" -> {
                                    parsePlaylistFromTwoRowRenderer(twoRowRenderer, browseId)?.let { MusicBrowseItem.PlaylistItem(it) }
                                }
                                else -> null
                            }
                        }
                        else -> null
                    }
                }
                responsiveRenderer != null -> {
                    parseTrackFromRenderer(responsiveRenderer)?.let { MusicBrowseItem.TrackItem(it) }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing browse item", e)
            null
        }
    }

    private fun parseTrackFromTwoRowRenderer(renderer: JsonObject): Track? {
        return try {
            val videoId = renderer["navigationEndpoint"]?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull ?: return null

            val title = renderer["title"]?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

            val subtitle = renderer["subtitle"]?.jsonObject
                ?.get("runs")?.jsonArray

            // Log all subtitle runs to debug
            val subtitleTexts = subtitle?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            Log.d(TAG, "parseTrackFromTwoRowRenderer - title: $title, subtitleRuns: $subtitleTexts")

            // Extract artist - first run that isn't a separator or duration
            val artistName = subtitle?.firstOrNull { run ->
                val text = run.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
                text.isNotBlank() && text != "•" && text != " • " && !text.matches(Regex("\\d+:\\d+"))
            }?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

            // Try to find artist ID
            val artistId = subtitle?.firstOrNull { run ->
                run.jsonObject["navigationEndpoint"]?.jsonObject
                    ?.get("browseEndpoint")?.jsonObject
                    ?.get("browseId")?.jsonPrimitive?.contentOrNull?.startsWith("UC") == true
            }?.jsonObject?.get("navigationEndpoint")?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull

            val thumbnails = renderer["thumbnailRenderer"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

            Log.d(TAG, "parseTrackFromTwoRowRenderer - parsed: $title by $artistName (artistId: $artistId)")

            Track(
                id = videoId,
                title = title,
                artistName = artistName,
                artistId = artistId,
                albumName = null,
                albumId = null,
                thumbnailUrl = thumbnailUrl,
                duration = 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing track from two row renderer", e)
            null
        }
    }

    private fun parseAlbumFromTwoRowRenderer(renderer: JsonObject, browseId: String): Album? {
        return try {
            val title = renderer["title"]?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Album"

            val subtitle = renderer["subtitle"]?.jsonObject
                ?.get("runs")?.jsonArray

            val artistName = subtitle?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

            val thumbnails = renderer["thumbnailRenderer"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

            Album(
                id = browseId,
                title = title,
                artistName = artistName,
                artistId = null,
                thumbnailUrl = thumbnailUrl,
                year = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing album from two row renderer", e)
            null
        }
    }

    private fun parseArtistFromTwoRowRenderer(renderer: JsonObject, browseId: String): Artist? {
        return try {
            val name = renderer["title"]?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

            val subscriberCount = renderer["subtitle"]?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull

            val thumbnails = renderer["thumbnailRenderer"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull

            Artist(
                id = browseId,
                name = name,
                thumbnailUrl = thumbnailUrl,
                subscriberCount = subscriberCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing artist from two row renderer", e)
            null
        }
    }

    private fun parsePlaylistFromTwoRowRenderer(renderer: JsonObject, browseId: String): YouTubeMusicPlaylist? {
        return try {
            val title = renderer["title"]?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Playlist"

            val subtitleRuns = renderer["subtitle"]?.jsonObject
                ?.get("runs")?.jsonArray

            // Log subtitle for debugging
            val subtitleTexts = subtitleRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            Log.d(TAG, "parsePlaylistFromTwoRowRenderer - title: $title, subtitleRuns: $subtitleTexts")

            // First run is usually the author/source
            val author = subtitleRuns?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull

            // Try to find track count from subtitle (e.g., "50 tracks" or "100 songs")
            val trackCountStr = subtitleTexts?.find { text ->
                text.contains("track", ignoreCase = true) || text.contains("song", ignoreCase = true)
            }
            val trackCount = trackCountStr?.let {
                Regex("(\\d+)").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
            } ?: 0

            val thumbnails = renderer["thumbnailRenderer"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

            Log.d(TAG, "parsePlaylistFromTwoRowRenderer - parsed: $title, browseId: $browseId, author: $author, trackCount: $trackCount, thumbnail: ${thumbnailUrl.take(50)}...")

            YouTubeMusicPlaylist(
                id = browseId,
                title = title,
                author = author,
                thumbnailUrl = thumbnailUrl,
                trackCount = trackCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing playlist from two row renderer", e)
            null
        }
    }

    /**
     * Parse artist page details
     */
    fun parseArtistPage(response: JsonObject, artistId: String): Artist? {
        return try {
            val header = response["header"]?.jsonObject
                ?.get("musicImmersiveHeaderRenderer")?.jsonObject
                ?: response["header"]?.jsonObject
                    ?.get("musicVisualHeaderRenderer")?.jsonObject

            val name = header?.get("title")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

            val description = header?.get("description")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull

            val subscriberCount = header?.get("subscriptionButton")?.jsonObject
                ?.get("subscribeButtonRenderer")?.jsonObject
                ?.get("subscriberCountText")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull

            val thumbnails = header?.get("thumbnail")?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull

            Artist(
                id = artistId,
                name = name,
                thumbnailUrl = thumbnailUrl,
                subscriberCount = subscriberCount,
                description = description
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing artist page", e)
            null
        }
    }

    /**
     * Parse tracks from artist page
     */
    fun parseArtistTracks(response: JsonObject): List<Track> {
        val tracks = mutableListOf<Track>()

        try {
            val contents = response["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

            contents?.forEach { section ->
                val shelfContents = section.jsonObject["musicShelfRenderer"]?.jsonObject
                    ?.get("contents")?.jsonArray

                shelfContents?.forEach { item ->
                    parseTrackFromRenderer(item.jsonObject)?.let { tracks.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing artist tracks", e)
        }

        return tracks
    }

    /**
     * Parse album page with tracks
     */
    fun parseAlbumPage(response: JsonObject, albumId: String): Album? {
        return try {
            Log.d(TAG, "parseAlbumPage - parsing album: $albumId")
            Log.d(TAG, "parseAlbumPage - top level keys: ${response.keys.joinToString()}")

            // Try multiple header paths
            var header = response["header"]?.jsonObject
                ?.get("musicDetailHeaderRenderer")?.jsonObject

            // Alternative: Header might be in twoColumnBrowseResultsRenderer tabs
            if (header == null) {
                val contentsObj = response["contents"]?.jsonObject
                val twoColumnRenderer = contentsObj?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                val tabs = twoColumnRenderer?.get("tabs")?.jsonArray
                val tabContent = tabs?.firstOrNull()?.jsonObject
                    ?.get("tabRenderer")?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("sectionListRenderer")?.jsonObject
                    ?.get("contents")?.jsonArray

                // Look for musicResponsiveHeaderRenderer in tab content
                val headerSection = tabContent?.firstOrNull()?.jsonObject
                header = headerSection?.get("musicResponsiveHeaderRenderer")?.jsonObject
                Log.d(TAG, "parseAlbumPage - alternative header from tabs: ${header != null}")
            }

            // Another alternative: immersiveHeaderRenderer
            if (header == null) {
                header = response["header"]?.jsonObject
                    ?.get("musicImmersiveHeaderRenderer")?.jsonObject
                Log.d(TAG, "parseAlbumPage - immersiveHeader: ${header != null}")
            }

            Log.d(TAG, "parseAlbumPage - header found: ${header != null}")
            if (header != null) {
                Log.d(TAG, "parseAlbumPage - header keys: ${header.keys.joinToString()}")
            }

            // Parse title - try different structures
            val title = header?.get("title")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
                // Alternative: straplineTextOne for musicResponsiveHeaderRenderer
                ?: header?.get("straplineTextOne")?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull
                ?: "Unknown Album"

            // Parse subtitle/artist - try different structures
            val subtitle = header?.get("subtitle")?.jsonObject
                ?.get("runs")?.jsonArray
                // Alternative: straplineThumbnail for artist info
                ?: header?.get("straplineTextOne")?.jsonObject
                    ?.get("runs")?.jsonArray

            var artistName = subtitle?.find {
                it.jsonObject["navigationEndpoint"]?.jsonObject
                    ?.get("browseEndpoint")?.jsonObject
                    ?.get("browseId")?.jsonPrimitive?.contentOrNull?.startsWith("UC") == true
            }?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?: subtitle?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull

            // Alternative: secondSubtitle for musicResponsiveHeaderRenderer
            if (artistName == null || artistName == "Unknown Artist") {
                artistName = header?.get("secondSubtitle")?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: "Unknown Artist"
            }

            val artistId = subtitle?.firstOrNull()?.jsonObject
                ?.get("navigationEndpoint")?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull

            // Try to find year from subtitle
            val yearStr = subtitle?.find {
                it.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.matches(Regex("\\d{4}")) == true
            }?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                // Alternative: in secondSubtitle
                ?: header?.get("secondSubtitle")?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.find { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.matches(Regex("\\d{4}")) == true }
                    ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull

            // Parse thumbnail - try different structures
            var thumbnails = header?.get("thumbnail")?.jsonObject
                ?.get("croppedSquareThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            // Alternative: musicThumbnailRenderer
            if (thumbnails == null) {
                thumbnails = header?.get("thumbnail")?.jsonObject
                    ?.get("musicThumbnailRenderer")?.jsonObject
                    ?.get("thumbnail")?.jsonObject
                    ?.get("thumbnails")?.jsonArray
            }

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

            Log.d(TAG, "parseAlbumPage - title: $title, artist: $artistName, year: $yearStr")

            // Parse tracks
            val tracks = mutableListOf<Track>()
            val contents = response["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

            Log.d(TAG, "parseAlbumPage - found ${contents?.size ?: 0} content sections")

            // Try alternative paths if contents is null
            val actualContents = contents ?: run {
                Log.d(TAG, "parseAlbumPage - DEBUG: contents is null, checking alternative paths...")
                val contentsObj = response["contents"]?.jsonObject
                Log.d(TAG, "parseAlbumPage - contents keys: ${contentsObj?.keys?.joinToString() ?: "null"}")

                // Try twoColumnBrowseResultsRenderer path (common for album pages)
                val twoColumnRenderer = contentsObj?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                if (twoColumnRenderer != null) {
                    Log.d(TAG, "parseAlbumPage - twoColumnRenderer keys: ${twoColumnRenderer.keys.joinToString()}")
                    val secondaryContents = twoColumnRenderer["secondaryContents"]?.jsonObject
                        ?.get("sectionListRenderer")?.jsonObject
                        ?.get("contents")?.jsonArray
                    Log.d(TAG, "parseAlbumPage - secondaryContents found: ${secondaryContents?.size ?: 0}")
                    if (secondaryContents != null) {
                        return@run secondaryContents
                    }
                }

                // Try singleColumnBrowseResultsRenderer without tabs
                val singleColumnRenderer = contentsObj?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                if (singleColumnRenderer != null) {
                    Log.d(TAG, "parseAlbumPage - singleColumnRenderer keys: ${singleColumnRenderer.keys.joinToString()}")
                }

                null
            }

            Log.d(TAG, "parseAlbumPage - actual contents found: ${actualContents?.size ?: 0}")

            actualContents?.forEachIndexed { sectionIndex, section ->
                val shelfContents = section.jsonObject["musicShelfRenderer"]?.jsonObject
                    ?.get("contents")?.jsonArray

                Log.d(TAG, "parseAlbumPage - section $sectionIndex has ${shelfContents?.size ?: 0} items")

                shelfContents?.forEachIndexed { itemIndex, item ->
                    Log.d(TAG, "parseAlbumPage - item $itemIndex keys: ${item.jsonObject.keys.joinToString()}")

                    // Try standard parsing first
                    var track = parseTrackFromRenderer(item.jsonObject)

                    // If that fails, try parsing album track directly
                    if (track == null) {
                        track = parseAlbumTrack(item.jsonObject, albumId, artistName, thumbnailUrl)
                    }

                    if (track != null) {
                        Log.d(TAG, "parseAlbumPage - parsed track: ${track.title} (id=${track.id})")
                        tracks.add(track)
                    } else {
                        Log.d(TAG, "parseAlbumPage - failed to parse track at index $itemIndex")
                    }
                }
            }

            Log.d(TAG, "parseAlbumPage - total tracks parsed: ${tracks.size}")

            Album(
                id = albumId,
                title = title,
                artistName = artistName,
                artistId = artistId,
                thumbnailUrl = thumbnailUrl,
                year = yearStr?.toIntOrNull(),
                trackCount = tracks.size,
                tracks = tracks
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing album page", e)
            null
        }
    }

    /**
     * Data class for YouTube Music playlist with tracks
     */
    data class YouTubeMusicPlaylistWithTracks(
        val id: String,
        val title: String,
        val author: String?,
        val thumbnailUrl: String,
        val trackCount: Int,
        val tracks: List<Track>
    )

    /**
     * Parse YouTube Music playlist page with tracks
     */
    fun parseYouTubeMusicPlaylistPage(response: JsonObject, playlistId: String): YouTubeMusicPlaylistWithTracks? {
        return try {
            Log.d(TAG, "parseYouTubeMusicPlaylistPage - parsing playlist: $playlistId")
            Log.d(TAG, "parseYouTubeMusicPlaylistPage - top level keys: ${response.keys.joinToString()}")

            val headerObj = response["header"]?.jsonObject
            Log.d(TAG, "parseYouTubeMusicPlaylistPage - header keys: ${headerObj?.keys?.joinToString() ?: "null"}")

            // Try to find header info
            var header = headerObj?.get("musicDetailHeaderRenderer")?.jsonObject

            // Alternative: musicEditablePlaylistDetailHeaderRenderer (for editable playlists)
            if (header == null) {
                header = headerObj?.get("musicEditablePlaylistDetailHeaderRenderer")?.jsonObject
                    ?.get("header")?.jsonObject
                    ?.get("musicDetailHeaderRenderer")?.jsonObject
                Log.d(TAG, "parseYouTubeMusicPlaylistPage - tried editablePlaylist header: ${header != null}")
            }

            // Alternative: musicImmersiveHeaderRenderer
            if (header == null) {
                header = headerObj?.get("musicImmersiveHeaderRenderer")?.jsonObject
                Log.d(TAG, "parseYouTubeMusicPlaylistPage - tried immersive header: ${header != null}")
            }

            // Alternative: musicVisualHeaderRenderer
            if (header == null) {
                header = headerObj?.get("musicVisualHeaderRenderer")?.jsonObject
                Log.d(TAG, "parseYouTubeMusicPlaylistPage - tried visual header: ${header != null}")
            }

            // Alternative: musicResponsiveHeaderRenderer (in twoColumnBrowseResultsRenderer)
            if (header == null) {
                val contents = response["contents"]?.jsonObject
                val twoColumnRenderer = contents?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                val tabs = twoColumnRenderer?.get("tabs")?.jsonArray
                val tabContent = tabs?.firstOrNull()?.jsonObject
                    ?.get("tabRenderer")?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("sectionListRenderer")?.jsonObject
                    ?.get("contents")?.jsonArray
                header = tabContent?.firstOrNull()?.jsonObject
                    ?.get("musicResponsiveHeaderRenderer")?.jsonObject
                Log.d(TAG, "parseYouTubeMusicPlaylistPage - tried responsive header from tabs: ${header != null}")
            }

            Log.d(TAG, "parseYouTubeMusicPlaylistPage - header found: ${header != null}")
            if (header != null) {
                Log.d(TAG, "parseYouTubeMusicPlaylistPage - header keys: ${header.keys.joinToString()}")
            }

            // Parse title - try multiple structures
            var title = header?.get("title")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull

            // Alternative: straplineTextOne for musicResponsiveHeaderRenderer
            if (title == null) {
                title = header?.get("straplineTextOne")?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull
            }

            title = title ?: "Unknown Playlist"

            // Parse author/subtitle
            val subtitle = header?.get("subtitle")?.jsonObject
                ?.get("runs")?.jsonArray
                ?: header?.get("secondSubtitle")?.jsonObject
                    ?.get("runs")?.jsonArray
                ?: header?.get("straplineThumbnail")?.jsonObject
                    ?.get("runs")?.jsonArray

            val author = subtitle?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull

            // Parse thumbnail - try multiple structures
            var thumbnails = header?.get("thumbnail")?.jsonObject
                ?.get("croppedSquareThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            if (thumbnails == null) {
                thumbnails = header?.get("thumbnail")?.jsonObject
                    ?.get("musicThumbnailRenderer")?.jsonObject
                    ?.get("thumbnail")?.jsonObject
                    ?.get("thumbnails")?.jsonArray
            }

            // Try foregroundThumbnail for immersive header
            if (thumbnails == null) {
                thumbnails = header?.get("foregroundThumbnail")?.jsonObject
                    ?.get("musicThumbnailRenderer")?.jsonObject
                    ?.get("thumbnail")?.jsonObject
                    ?.get("thumbnails")?.jsonArray
            }

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

            Log.d(TAG, "parseYouTubeMusicPlaylistPage - title: $title, author: $author, thumbnail: ${thumbnailUrl.take(50)}...")

            // Parse tracks
            val tracks = mutableListOf<Track>()

            // Navigate to contents - try multiple paths
            var contents = response["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

            // Alternative: twoColumnBrowseResultsRenderer
            if (contents == null) {
                contents = response["contents"]?.jsonObject
                    ?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                    ?.get("secondaryContents")?.jsonObject
                    ?.get("sectionListRenderer")?.jsonObject
                    ?.get("contents")?.jsonArray
            }

            Log.d(TAG, "parseYouTubeMusicPlaylistPage - found ${contents?.size ?: 0} content sections")

            contents?.forEach { section ->
                val shelfContents = section.jsonObject["musicPlaylistShelfRenderer"]?.jsonObject
                    ?.get("contents")?.jsonArray
                    ?: section.jsonObject["musicShelfRenderer"]?.jsonObject
                        ?.get("contents")?.jsonArray

                Log.d(TAG, "parseYouTubeMusicPlaylistPage - shelf has ${shelfContents?.size ?: 0} items")

                shelfContents?.forEach { item ->
                    val track = parseTrackFromRenderer(item.jsonObject)
                        ?: parseAlbumTrack(item.jsonObject, playlistId, author ?: "", thumbnailUrl)

                    if (track != null) {
                        Log.d(TAG, "parseYouTubeMusicPlaylistPage - parsed track: ${track.title} by ${track.artistName}")
                        tracks.add(track)
                    } else {
                        Log.d(TAG, "parseYouTubeMusicPlaylistPage - failed to parse track, keys: ${item.jsonObject.keys.joinToString()}")
                    }
                }
            }

            Log.d(TAG, "parseYouTubeMusicPlaylistPage - total tracks parsed: ${tracks.size}")

            YouTubeMusicPlaylistWithTracks(
                id = playlistId,
                title = title,
                author = author,
                thumbnailUrl = thumbnailUrl,
                trackCount = tracks.size,
                tracks = tracks
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing YouTube Music playlist page", e)
            null
        }
    }

    /**
     * Parse album track - specifically for tracks in album pages which may have different structure
     */
    private fun parseAlbumTrack(item: JsonObject, albumId: String, albumArtist: String, albumThumbnail: String): Track? {
        return try {
            val renderer = item["musicResponsiveListItemRenderer"]?.jsonObject
                ?: return null

            val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null

            // Get video ID from various possible locations
            val videoId = renderer["playlistItemData"]?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull
                ?: renderer["overlay"]?.jsonObject
                    ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("musicPlayButtonRenderer")?.jsonObject
                    ?.get("playNavigationEndpoint")?.jsonObject
                    ?.get("watchEndpoint")?.jsonObject
                    ?.get("videoId")?.jsonPrimitive?.contentOrNull
                ?: flexColumns.getOrNull(0)?.jsonObject
                    ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                    ?.get("text")?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("navigationEndpoint")?.jsonObject
                    ?.get("watchEndpoint")?.jsonObject
                    ?.get("videoId")?.jsonPrimitive?.contentOrNull

            if (videoId == null) {
                Log.d(TAG, "parseAlbumTrack - no videoId found, renderer keys: ${renderer.keys.joinToString()}")
                return null
            }

            // Get title
            val title = flexColumns.getOrNull(0)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

            // Get artist (might be different from album artist for featuring artists)
            val artistInfo = flexColumns.getOrNull(1)?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray

            val artistName = artistInfo?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull ?: albumArtist

            // Try to get thumbnail from track, fall back to album thumbnail
            val thumbnails = renderer["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray

            val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull ?: albumThumbnail

            // Parse duration from fixedColumns
            val duration = renderer["fixedColumns"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("musicResponsiveListItemFixedColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.contentOrNull
                ?.let { parseDuration(it) } ?: 0L

            Track(
                id = videoId,
                title = title,
                artistName = artistName,
                artistId = null,
                albumName = null,  // Will be set by context
                albumId = albumId,
                thumbnailUrl = thumbnailUrl,
                duration = duration
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing album track", e)
            null
        }
    }

    /**
     * Parse radio/autoplay tracks
     */
    fun parseRadioTracks(response: JsonObject): List<Track> {
        val tracks = mutableListOf<Track>()

        try {
            Log.d(TAG, "Parsing radio tracks...")

            // Try the standard path
            var playlist = response["contents"]?.jsonObject
                ?.get("singleColumnMusicWatchNextResultsRenderer")?.jsonObject
                ?.get("tabbedRenderer")?.jsonObject
                ?.get("watchNextTabbedResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicQueueRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("playlistPanelRenderer")?.jsonObject
                ?.get("contents")?.jsonArray

            // If standard path fails, try alternative path
            if (playlist == null) {
                Log.d(TAG, "Standard radio path failed, trying alternative...")
                playlist = response["contents"]?.jsonObject
                    ?.get("singleColumnMusicWatchNextResultsRenderer")?.jsonObject
                    ?.get("tabbedRenderer")?.jsonObject
                    ?.get("watchNextTabbedResultsRenderer")?.jsonObject
                    ?.get("tabs")?.jsonArray
                    ?.find { tab ->
                        tab.jsonObject["tabRenderer"]?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("musicQueueRenderer") != null
                    }?.jsonObject
                    ?.get("tabRenderer")?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("musicQueueRenderer")?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("playlistPanelRenderer")?.jsonObject
                    ?.get("contents")?.jsonArray
            }

            Log.d(TAG, "Radio playlist items found: ${playlist?.size ?: 0}")

            playlist?.forEach { item ->
                val renderer = item.jsonObject["playlistPanelVideoRenderer"]?.jsonObject
                    ?: return@forEach

                val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull ?: return@forEach

                val title = renderer["title"]?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown"

                val artistInfo = renderer["longBylineText"]?.jsonObject
                    ?.get("runs")?.jsonArray

                val artistName = artistInfo?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"

                val thumbnails = renderer["thumbnail"]?.jsonObject
                    ?.get("thumbnails")?.jsonArray

                val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject
                    ?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

                val durationStr = renderer["lengthText"]?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.contentOrNull

                tracks.add(
                    Track(
                        id = videoId,
                        title = title,
                        artistName = artistName,
                        artistId = null,
                        albumName = null,
                        albumId = null,
                        thumbnailUrl = thumbnailUrl,
                        duration = durationStr?.let { parseDuration(it) } ?: 0
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing radio tracks", e)
        }

        return tracks
    }

    /**
     * Parse search suggestions
     */
    fun parseSearchSuggestions(response: JsonObject): List<String> {
        val suggestions = mutableListOf<String>()

        try {
            val contents = response["contents"]?.jsonArray

            contents?.forEach { item ->
                val suggestion = item.jsonObject["searchSuggestionRenderer"]?.jsonObject
                    ?.get("suggestion")?.jsonObject
                    ?.get("runs")?.jsonArray
                    ?.joinToString("") {
                        it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
                    }

                suggestion?.let { suggestions.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search suggestions", e)
        }

        return suggestions
    }

    /**
     * Parse duration string (e.g., "3:45") to milliseconds
     */
    private fun parseDuration(durationStr: String): Long {
        return try {
            val parts = durationStr.split(":")
            when (parts.size) {
                2 -> {
                    val minutes = parts[0].toLongOrNull() ?: 0
                    val seconds = parts[1].toLongOrNull() ?: 0
                    (minutes * 60 + seconds) * 1000
                }
                3 -> {
                    val hours = parts[0].toLongOrNull() ?: 0
                    val minutes = parts[1].toLongOrNull() ?: 0
                    val seconds = parts[2].toLongOrNull() ?: 0
                    (hours * 3600 + minutes * 60 + seconds) * 1000
                }
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }
}
