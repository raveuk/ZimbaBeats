package com.zimbabeats.core.domain.repository

import com.zimbabeats.core.domain.model.music.*
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for music-related operations
 */
interface MusicRepository {

    // ==================== Search ====================

    /**
     * Search for music content
     */
    suspend fun searchMusic(
        query: String,
        filter: MusicSearchFilter = MusicSearchFilter.ALL
    ): Resource<List<MusicSearchResult>>

    /**
     * Get search suggestions for a query
     */
    suspend fun getSearchSuggestions(query: String): Resource<List<String>>

    // ==================== Browse ====================

    /**
     * Get the music home page with sections (trending, recommended, etc.)
     */
    suspend fun getMusicHome(): Resource<List<MusicBrowseSection>>

    /**
     * Get artist details
     */
    suspend fun getArtist(artistId: String): Resource<Artist>

    /**
     * Get tracks by an artist
     */
    suspend fun getArtistTracks(artistId: String): Resource<List<Track>>

    /**
     * Get album details with tracks
     */
    suspend fun getAlbum(albumId: String): Resource<Album>

    /**
     * Data class for YouTube Music playlist with tracks
     */
    data class YouTubeMusicPlaylistData(
        val id: String,
        val title: String,
        val author: String?,
        val thumbnailUrl: String,
        val trackCount: Int,
        val tracks: List<Track>
    )

    /**
     * Get YouTube Music playlist details with tracks
     */
    suspend fun getYouTubeMusicPlaylist(playlistId: String): Resource<YouTubeMusicPlaylistData>

    // ==================== Playback ====================

    /**
     * Get a track by ID from local cache
     */
    suspend fun getTrack(trackId: String): Track?

    /**
     * Get audio stream URL for a track
     */
    suspend fun getAudioStreamUrl(trackId: String): Resource<String>

    /**
     * Result containing both stream URL and track info
     */
    data class PlayerData(
        val streamUrl: String,
        val track: Track
    )

    /**
     * Get audio stream URL and track details for a track
     */
    suspend fun getPlayerData(trackId: String): Resource<PlayerData>

    /**
     * Get radio/autoplay queue for a track
     */
    suspend fun getRadio(trackId: String): Resource<List<Track>>

    /**
     * Get lyrics for a track
     * @return Lyrics if available, null if not found
     */
    suspend fun getLyrics(trackId: String): Resource<Lyrics?>

    /**
     * Record that a track was played
     */
    suspend fun recordListen(trackId: String, listenDuration: Long)

    // ==================== Library - Favorites ====================

    /**
     * Get all favorite tracks
     */
    fun getFavoriteTracks(): Flow<List<Track>>

    /**
     * Toggle favorite status for a track
     * @return true if track is now a favorite, false if removed
     */
    suspend fun toggleFavorite(track: Track): Resource<Boolean>

    /**
     * Check if a track is a favorite
     */
    fun isFavorite(trackId: String): Flow<Boolean>

    // ==================== Library - History ====================

    /**
     * Get recently played tracks
     */
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<Track>>

    /**
     * Get most played tracks
     */
    fun getMostPlayed(limit: Int = 50): Flow<List<Track>>

    /**
     * Get listening history
     */
    fun getListeningHistory(limit: Int = 100): Flow<List<Track>>

    /**
     * Clear listening history
     */
    suspend fun clearListeningHistory()

    // ==================== Library - Playlists ====================

    /**
     * Get all user-created music playlists
     */
    fun getMusicPlaylists(): Flow<List<MusicPlaylist>>

    /**
     * Get a specific playlist with tracks
     */
    suspend fun getPlaylist(playlistId: Long): Resource<MusicPlaylist>

    /**
     * Get playlist tracks as a Flow
     */
    fun getPlaylistTracks(playlistId: Long): Flow<List<Track>>

    /**
     * Create a new music playlist
     * @return the ID of the created playlist
     */
    suspend fun createMusicPlaylist(name: String, description: String? = null): Resource<Long>

    /**
     * Update a music playlist
     */
    suspend fun updateMusicPlaylist(playlist: MusicPlaylist): Resource<Unit>

    /**
     * Delete a music playlist
     */
    suspend fun deleteMusicPlaylist(playlistId: Long): Resource<Unit>

    /**
     * Add a track to a playlist
     */
    suspend fun addTrackToPlaylist(playlistId: Long, track: Track): Resource<Unit>

    /**
     * Remove a track from a playlist
     */
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Resource<Unit>

    /**
     * Check if a track is in a playlist
     */
    suspend fun isTrackInPlaylist(playlistId: Long, trackId: String): Boolean
}
