package com.zimbabeats.media.auto

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zimbabeats.core.domain.model.music.MusicPlaylist
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.MusicRepository
import kotlinx.coroutines.flow.first

/**
 * Provides content hierarchy for Android Auto media browsing.
 * Exposes Favorites, Playlists, and Recently Played tracks.
 *
 * Auto-browsed MediaItems carry a zimba:// URI that is resolved lazily by
 * the PlaybackService's ResolvingDataSource. This keeps the Auto path
 * fully independent from the in-app MusicPlaybackManager flow.
 */
class AutoContentProvider(
    private val musicRepository: MusicRepository
) {
    companion object {
        const val ROOT_ID = "ROOT"
        const val FAVORITES_ID = "FAVORITES"
        const val PLAYLISTS_ID = "PLAYLISTS"
        const val RECENT_ID = "RECENT"
        const val PLAYLIST_PREFIX = "PLAYLIST_"

        const val AUTO_URI_SCHEME = "zimba"
        const val EXTRA_PARENT_ID = "zimba.parentId"
    }

    /**
     * Get the root browsable items for Android Auto.
     */
    fun getRootChildren(): List<MediaItem> {
        return listOf(
            createBrowsableItem(
                id = FAVORITES_ID,
                title = "Favorites",
                subtitle = "Your favorite tracks"
            ),
            createBrowsableItem(
                id = PLAYLISTS_ID,
                title = "Playlists",
                subtitle = "Your playlists"
            ),
            createBrowsableItem(
                id = RECENT_ID,
                title = "Recently Played",
                subtitle = "Continue listening"
            )
        )
    }

    /**
     * Get favorite tracks as MediaItems.
     */
    suspend fun getFavorites(): List<MediaItem> {
        return musicRepository.getFavoriteTracks().first()
            .map { track -> trackToMediaItem(track, FAVORITES_ID) }
    }

    /**
     * Get recently played tracks as MediaItems.
     */
    suspend fun getRecent(): List<MediaItem> {
        return musicRepository.getRecentlyPlayed(50).first()
            .map { track -> trackToMediaItem(track, RECENT_ID) }
    }

    /**
     * Get user playlists as browsable MediaItems.
     */
    suspend fun getPlaylists(): List<MediaItem> {
        return musicRepository.getMusicPlaylists().first()
            .map { playlist -> playlistToMediaItem(playlist) }
    }

    /**
     * Get tracks from a specific playlist.
     */
    suspend fun getPlaylistTracks(playlistId: Long): List<MediaItem> {
        val parentId = "$PLAYLIST_PREFIX$playlistId"
        return musicRepository.getPlaylistTracks(playlistId).first()
            .map { track -> trackToMediaItem(track, parentId) }
    }

    /**
     * Re-fetch the full child list for a given parent node, used to build the
     * sibling queue when Android Auto picks a single track.
     */
    suspend fun getChildrenForParent(parentId: String): List<MediaItem> = when {
        parentId == FAVORITES_ID -> getFavorites()
        parentId == RECENT_ID -> getRecent()
        parentId.startsWith(PLAYLIST_PREFIX) -> {
            val id = parentId.removePrefix(PLAYLIST_PREFIX).toLongOrNull()
            if (id != null) getPlaylistTracks(id) else emptyList()
        }
        else -> emptyList()
    }

    /**
     * Convert a Track to a playable MediaItem for Android Auto.
     *
     * The URI uses the zimba:// scheme so the PlaybackService's
     * ResolvingDataSource can fetch the real HTTPS stream on demand.
     */
    private fun trackToMediaItem(track: Track, parentId: String): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_PARENT_ID, parentId)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumName)
            .setArtworkUri(Uri.parse(track.thumbnailUrl))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse("$AUTO_URI_SCHEME://track/${track.id}"))
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Convert a MusicPlaylist to a browsable MediaItem.
     */
    private fun playlistToMediaItem(playlist: MusicPlaylist): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(playlist.name)
            .setSubtitle("${playlist.trackCount} tracks")
            .apply {
                playlist.thumbnailUrl?.let { setArtworkUri(Uri.parse(it)) }
            }
            .setIsPlayable(false)
            .setIsBrowsable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId("$PLAYLIST_PREFIX${playlist.id}")
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Create a browsable category item (Favorites, Playlists, Recent).
     */
    private fun createBrowsableItem(
        id: String,
        title: String,
        subtitle: String
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsPlayable(false)
            .setIsBrowsable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }
}
