package com.zimbabeats.core.data.repository.music

import android.util.Log
import com.zimbabeats.core.data.local.dao.music.*
import com.zimbabeats.core.data.local.entity.music.*
import com.zimbabeats.core.data.mapper.toDomain
import com.zimbabeats.core.data.mapper.toDomainList
import com.zimbabeats.core.data.mapper.toEntity
import com.zimbabeats.core.data.mapper.toEntityList
import com.zimbabeats.core.data.remote.youtube.music.YouTubeMusicClient
import com.zimbabeats.core.domain.model.music.*
import com.zimbabeats.core.domain.repository.MusicRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class MusicRepositoryImpl(
    private val musicClient: YouTubeMusicClient,
    private val trackDao: TrackDao,
    private val musicPlaylistDao: MusicPlaylistDao,
    private val favoriteTrackDao: FavoriteTrackDao,
    private val listeningHistoryDao: MusicListeningHistoryDao
) : MusicRepository {

    companion object {
        private const val TAG = "MusicRepositoryImpl"
    }

    // ==================== Search ====================

    override suspend fun searchMusic(
        query: String,
        filter: MusicSearchFilter
    ): Resource<List<MusicSearchResult>> {
        return try {
            val results = musicClient.searchMusic(query, filter)

            // Cache tracks to database
            val tracks = results.filterIsInstance<MusicSearchResult.TrackResult>()
                .map { it.track }
            if (tracks.isNotEmpty()) {
                trackDao.insertTracks(tracks.toEntityList())
            }

            Resource.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for: $query", e)
            Resource.error("Search failed: ${e.message}", e)
        }
    }

    override suspend fun getSearchSuggestions(query: String): Resource<List<String>> {
        return try {
            val suggestions = musicClient.getSearchSuggestions(query)
            Resource.success(suggestions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get search suggestions", e)
            Resource.error("Failed to get suggestions: ${e.message}", e)
        }
    }

    // ==================== Browse ====================

    override suspend fun getMusicHome(): Resource<List<MusicBrowseSection>> {
        return try {
            val sections = musicClient.getMusicHome()

            // Cache tracks from sections
            val tracks = sections.flatMap { section ->
                section.items.filterIsInstance<MusicBrowseItem.TrackItem>().map { it.track }
            }
            if (tracks.isNotEmpty()) {
                trackDao.insertTracks(tracks.toEntityList())
            }

            Resource.success(sections)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch music home", e)
            Resource.error("Failed to load music home: ${e.message}", e)
        }
    }

    override suspend fun getArtist(artistId: String): Resource<Artist> {
        return try {
            val artist = musicClient.getArtist(artistId)
            if (artist != null) {
                Resource.success(artist)
            } else {
                Resource.error("Artist not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch artist: $artistId", e)
            Resource.error("Failed to load artist: ${e.message}", e)
        }
    }

    override suspend fun getArtistTracks(artistId: String): Resource<List<Track>> {
        return try {
            val tracks = musicClient.getArtistTracks(artistId)

            // Cache tracks
            if (tracks.isNotEmpty()) {
                trackDao.insertTracks(tracks.toEntityList())
            }

            Resource.success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch artist tracks: $artistId", e)
            Resource.error("Failed to load artist tracks: ${e.message}", e)
        }
    }

    override suspend fun getAlbum(albumId: String): Resource<Album> {
        return try {
            val album = musicClient.getAlbum(albumId)
            if (album != null) {
                // Cache album tracks
                if (album.tracks.isNotEmpty()) {
                    trackDao.insertTracks(album.tracks.toEntityList())
                }
                Resource.success(album)
            } else {
                Resource.error("Album not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch album: $albumId", e)
            Resource.error("Failed to load album: ${e.message}", e)
        }
    }

    override suspend fun getYouTubeMusicPlaylist(playlistId: String): Resource<MusicRepository.YouTubeMusicPlaylistData> {
        return try {
            val playlist = musicClient.getYouTubeMusicPlaylist(playlistId)
            if (playlist != null) {
                // Cache playlist tracks
                if (playlist.tracks.isNotEmpty()) {
                    trackDao.insertTracks(playlist.tracks.toEntityList())
                }
                Resource.success(
                    MusicRepository.YouTubeMusicPlaylistData(
                        id = playlist.id,
                        title = playlist.title,
                        author = playlist.author,
                        thumbnailUrl = playlist.thumbnailUrl,
                        trackCount = playlist.trackCount,
                        tracks = playlist.tracks
                    )
                )
            } else {
                Resource.error("Playlist not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch YouTube Music playlist: $playlistId", e)
            Resource.error("Failed to load playlist: ${e.message}", e)
        }
    }

    // ==================== Playback ====================

    override suspend fun getTrack(trackId: String): Track? {
        return try {
            trackDao.getTrack(trackId)?.toDomain()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get track: $trackId", e)
            null
        }
    }

    override suspend fun getAudioStreamUrl(trackId: String): Resource<String> {
        return try {
            val url = musicClient.getAudioStreamUrl(trackId)
            if (url != null) {
                Resource.success(url)
            } else {
                Resource.error("Failed to get audio stream")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get audio stream for: $trackId", e)
            Resource.error("Failed to get audio stream: ${e.message}", e)
        }
    }

    override suspend fun getPlayerData(trackId: String): Resource<MusicRepository.PlayerData> {
        return try {
            val result = musicClient.getPlayerData(trackId)
            if (result != null) {
                // Cache the track
                trackDao.insertTrack(result.track.toEntity())
                Log.d(TAG, "Cached track: ${result.track.title}")
                Resource.success(MusicRepository.PlayerData(result.streamUrl, result.track))
            } else {
                Resource.error("Failed to get player data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get player data for: $trackId", e)
            Resource.error("Failed to get player data: ${e.message}", e)
        }
    }

    override suspend fun getRadio(trackId: String): Resource<List<Track>> {
        return try {
            val tracks = musicClient.getRadio(trackId)

            // Cache tracks
            if (tracks.isNotEmpty()) {
                trackDao.insertTracks(tracks.toEntityList())
            }

            Resource.success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get radio for: $trackId", e)
            Resource.error("Failed to get radio: ${e.message}", e)
        }
    }

    override suspend fun getLyrics(trackId: String): Resource<Lyrics?> {
        return try {
            val lyrics = musicClient.getLyrics(trackId)
            Resource.success(lyrics)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get lyrics for: $trackId", e)
            Resource.error("Failed to get lyrics: ${e.message}", e)
        }
    }

    override suspend fun recordListen(trackId: String, listenDuration: Long) {
        try {
            // Update track play stats
            trackDao.updatePlayStats(trackId)

            // Add to listening history
            listeningHistoryDao.recordListen(
                MusicListeningHistoryEntity(
                    trackId = trackId,
                    listenDuration = listenDuration
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record listen for: $trackId", e)
        }
    }

    // ==================== Library - Favorites ====================

    override fun getFavoriteTracks(): Flow<List<Track>> {
        return favoriteTrackDao.getFavoriteTracks().map { entities ->
            Log.d(TAG, "getFavoriteTracks: Received ${entities.size} favorite track entities from DAO")
            entities.forEach { entity ->
                Log.d(TAG, "  - Favorite track: ${entity.id} - ${entity.title}")
            }
            entities.map { it.toDomain(isFavorite = true) }
        }
    }

    override suspend fun toggleFavorite(track: Track): Resource<Boolean> {
        return try {
            Log.d(TAG, "toggleFavorite called for: ${track.id} - ${track.title}")

            // Ensure track is in database
            val entity = track.toEntity()
            Log.d(TAG, "Inserting track entity: $entity")
            trackDao.insertTrack(entity)
            Log.d(TAG, "Track inserted successfully")

            // Toggle favorite
            val isNowFavorite = favoriteTrackDao.toggleFavorite(track.id)
            Log.d(TAG, "Favorite toggled, isNowFavorite: $isNowFavorite")
            Resource.success(isNowFavorite)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle favorite for: ${track.id}", e)
            Resource.error("Failed to update favorite: ${e.message}", e)
        }
    }

    override fun isFavorite(trackId: String): Flow<Boolean> {
        return favoriteTrackDao.isFavorite(trackId)
    }

    // ==================== Library - History ====================

    override fun getRecentlyPlayed(limit: Int): Flow<List<Track>> {
        return combine(
            trackDao.getRecentlyPlayed(limit),
            favoriteTrackDao.getFavoriteTracks()
        ) { tracks, favorites ->
            val favoriteIds = favorites.map { it.id }.toSet()
            tracks.toDomainList(favoriteIds)
        }
    }

    override fun getMostPlayed(limit: Int): Flow<List<Track>> {
        return combine(
            trackDao.getMostPlayed(limit),
            favoriteTrackDao.getFavoriteTracks()
        ) { tracks, favorites ->
            val favoriteIds = favorites.map { it.id }.toSet()
            tracks.toDomainList(favoriteIds)
        }
    }

    override fun getListeningHistory(limit: Int): Flow<List<Track>> {
        return combine(
            listeningHistoryDao.getListeningHistory(limit),
            favoriteTrackDao.getFavoriteTracks()
        ) { tracks, favorites ->
            val favoriteIds = favorites.map { it.id }.toSet()
            tracks.toDomainList(favoriteIds)
        }
    }

    override suspend fun clearListeningHistory() {
        try {
            listeningHistoryDao.clearAllHistory()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear listening history", e)
        }
    }

    // ==================== Library - Playlists ====================

    override fun getMusicPlaylists(): Flow<List<MusicPlaylist>> {
        return musicPlaylistDao.getAllPlaylists().map { entities ->
            entities.map { entity ->
                val trackCount = musicPlaylistDao.getPlaylistTrackCount(entity.id)
                entity.toDomain().copy(trackCount = trackCount)
            }
        }
    }

    override suspend fun getPlaylist(playlistId: Long): Resource<MusicPlaylist> {
        return try {
            val playlistEntity = musicPlaylistDao.getPlaylist(playlistId)
            if (playlistEntity != null) {
                val trackEntities = musicPlaylistDao.getPlaylistTracksSync(playlistId)
                val favoriteIds = favoriteTrackDao.getFavoriteTracksSync().map { it.id }.toSet()
                val tracks = trackEntities.toDomainList(favoriteIds)
                Resource.success(playlistEntity.toDomain(tracks))
            } else {
                Resource.error("Playlist not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get playlist: $playlistId", e)
            Resource.error("Failed to load playlist: ${e.message}", e)
        }
    }

    override fun getPlaylistTracks(playlistId: Long): Flow<List<Track>> {
        return combine(
            musicPlaylistDao.getPlaylistTracks(playlistId),
            favoriteTrackDao.getFavoriteTracks()
        ) { tracks, favorites ->
            val favoriteIds = favorites.map { it.id }.toSet()
            tracks.toDomainList(favoriteIds)
        }
    }

    override suspend fun createMusicPlaylist(name: String, description: String?): Resource<Long> {
        return try {
            val playlist = MusicPlaylistEntity(
                name = name,
                description = description
            )
            val id = musicPlaylistDao.createPlaylist(playlist)
            Resource.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create playlist", e)
            Resource.error("Failed to create playlist: ${e.message}", e)
        }
    }

    override suspend fun updateMusicPlaylist(playlist: MusicPlaylist): Resource<Unit> {
        return try {
            musicPlaylistDao.updatePlaylist(playlist.toEntity())
            Resource.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update playlist: ${playlist.id}", e)
            Resource.error("Failed to update playlist: ${e.message}", e)
        }
    }

    override suspend fun deleteMusicPlaylist(playlistId: Long): Resource<Unit> {
        return try {
            musicPlaylistDao.deletePlaylistById(playlistId)
            Resource.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete playlist: $playlistId", e)
            Resource.error("Failed to delete playlist: ${e.message}", e)
        }
    }

    override suspend fun addTrackToPlaylist(playlistId: Long, track: Track): Resource<Unit> {
        return try {
            // Ensure track is in database
            trackDao.insertTrack(track.toEntity())

            // Add to playlist
            musicPlaylistDao.addTrackToPlaylistWithUpdate(playlistId, track.id)
            Resource.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add track to playlist: $playlistId", e)
            Resource.error("Failed to add track: ${e.message}", e)
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Resource<Unit> {
        return try {
            musicPlaylistDao.removeTrackFromPlaylist(playlistId, trackId)
            musicPlaylistDao.updatePlaylistTimestamp(playlistId)
            Resource.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove track from playlist: $playlistId", e)
            Resource.error("Failed to remove track: ${e.message}", e)
        }
    }

    override suspend fun isTrackInPlaylist(playlistId: Long, trackId: String): Boolean {
        return try {
            musicPlaylistDao.isTrackInPlaylist(playlistId, trackId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check track in playlist", e)
            false
        }
    }
}
