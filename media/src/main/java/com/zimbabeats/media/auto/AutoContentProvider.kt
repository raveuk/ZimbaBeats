package com.zimbabeats.media.auto

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zimbabeats.core.domain.model.music.MusicPlaylist
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.MusicRepository
import kotlinx.coroutines.flow.first

/**
 * Provides content hierarchy for Android Auto media browsing.
 * Exposes Favorites, Playlists, and Recently Played tracks.
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
            .map { track -> trackToMediaItem(track) }
    }

    /**
     * Get recently played tracks as MediaItems.
     */
    suspend fun getRecent(): List<MediaItem> {
        return musicRepository.getRecentlyPlayed(50).first()
            .map { track -> trackToMediaItem(track) }
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
        return musicRepository.getPlaylistTracks(playlistId).first()
            .map { track -> trackToMediaItem(track) }
    }

    /**
     * Convert a Track to a playable MediaItem for Android Auto.
     */
    private fun trackToMediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumName)
            .setArtworkUri(Uri.parse(track.thumbnailUrl))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
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
