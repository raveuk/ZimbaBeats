package com.zimbabeats.core.domain.model.music

/**
 * Represents a music track (song)
 */
data class Track(
    val id: String,                    // YouTube video ID
    val title: String,
    val artistName: String,
    val artistId: String?,
    val albumName: String?,
    val albumId: String?,
    val thumbnailUrl: String,
    val duration: Long,                // In milliseconds
    val isExplicit: Boolean = false,
    val playCount: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

/**
 * Represents an artist
 */
data class Artist(
    val id: String,                    // YouTube Music artist browseId
    val name: String,
    val thumbnailUrl: String?,
    val subscriberCount: String?,
    val description: String? = null
)

/**
 * Represents an album
 */
data class Album(
    val id: String,                    // YouTube Music album browseId
    val title: String,
    val artistName: String,
    val artistId: String?,
    val thumbnailUrl: String,
    val year: Int?,
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList()
)

/**
 * User-created music playlist (separate from video playlists)
 */
data class MusicPlaylist(
    val id: Long = 0,
    val name: String,
    val description: String?,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val trackCount: Int = 0,
    val totalDuration: Long = 0,
    val tracks: List<Track> = emptyList()
)

/**
 * Search result types for music search
 */
sealed class MusicSearchResult {
    data class TrackResult(val track: Track) : MusicSearchResult()
    data class AlbumResult(val album: Album) : MusicSearchResult()
    data class ArtistResult(val artist: Artist) : MusicSearchResult()
    data class PlaylistResult(val playlist: YouTubeMusicPlaylist) : MusicSearchResult()
}

/**
 * YouTube Music playlist (from YouTube, not user-created)
 */
data class YouTubeMusicPlaylist(
    val id: String,                    // YouTube Music playlist browseId
    val title: String,
    val author: String?,
    val thumbnailUrl: String,
    val trackCount: Int = 0
)

/**
 * Browse section for music home page
 */
data class MusicBrowseSection(
    val title: String,
    val browseId: String? = null,      // For "See All" navigation
    val items: List<MusicBrowseItem>
)

/**
 * Items that can appear in browse sections
 */
sealed class MusicBrowseItem {
    data class TrackItem(val track: Track) : MusicBrowseItem()
    data class AlbumItem(val album: Album) : MusicBrowseItem()
    data class ArtistItem(val artist: Artist) : MusicBrowseItem()
    data class PlaylistItem(val playlist: YouTubeMusicPlaylist) : MusicBrowseItem()
}

/**
 * Filter options for music search
 */
enum class MusicSearchFilter(val param: String?) {
    ALL(null),
    SONGS("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"),
    ALBUMS("EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D"),
    ARTISTS("EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"),
    PLAYLISTS("EgWKAQIoAWoKEAkQBRAKEAMQBA%3D%3D")
}

/**
 * Listening history entry
 */
data class ListeningHistoryEntry(
    val track: Track,
    val listenedAt: Long,
    val listenDuration: Long
)

/**
 * Lyrics for a track
 */
data class Lyrics(
    val trackId: String,
    val lines: List<LyricLine>,
    val source: String? = null,  // e.g., "Genius", "YouTube Music"
    val isSynced: Boolean = false  // Whether lyrics have timestamps
)

/**
 * A single line of lyrics
 */
data class LyricLine(
    val text: String,
    val startTimeMs: Long = 0L,  // Timestamp for synced lyrics (0 if not synced)
    val endTimeMs: Long = 0L
)
