package com.zimbabeats.core.data.repository

import com.zimbabeats.core.data.local.database.ZimbaBeatsDatabase
import com.zimbabeats.core.data.local.entity.PlaylistEntity
import com.zimbabeats.core.data.local.entity.PlaylistTrackEntity
import com.zimbabeats.core.data.local.entity.PlaylistVideoEntity
import com.zimbabeats.core.data.mapper.toDomain
import com.zimbabeats.core.data.mapper.toEntity
import com.zimbabeats.core.domain.model.Playlist
import com.zimbabeats.core.domain.model.PlaylistColor
import com.zimbabeats.core.domain.model.Video
import com.zimbabeats.core.domain.model.music.Track
import com.zimbabeats.core.domain.repository.PlaylistRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PlaylistRepositoryImpl(
    private val database: ZimbaBeatsDatabase
) : PlaylistRepository {

    private val playlistDao = database.playlistDao()
    private val playlistVideoDao = database.playlistVideoDao()
    private val playlistTrackDao = database.playlistTrackDao()
    private val trackDao = database.trackDao()

    init {
        // Verify all DAOs are properly initialized
        try {
            playlistDao
            playlistVideoDao
            playlistTrackDao  // Will throw if not injected
            trackDao
            android.util.Log.d("PlaylistRepo", "All DAOs initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("PlaylistRepo", "DAO initialization failed", e)
            throw IllegalStateException("PlaylistRepository DAOs not properly configured", e)
        }
    }

    override fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAllPlaylists().map { playlists ->
            playlists.map { it.toDomain() }
        }

    override fun getPlaylistById(playlistId: Long): Flow<Playlist?> =
        combine(
            playlistDao.getPlaylistById(playlistId),
            playlistVideoDao.getVideosInPlaylist(playlistId),
            playlistTrackDao.getTracksInPlaylist(playlistId)
        ) { playlist, videos, tracks ->
            playlist?.toDomain(
                videos = videos.map { it.toDomain() },
                tracks = tracks.map { it.toDomain() }
            )
        }

    override fun getFavoritePlaylists(): Flow<List<Playlist>> =
        playlistDao.getFavoritePlaylists().map { playlists ->
            playlists.map { it.toDomain() }
        }

    override suspend fun createPlaylist(name: String, description: String?, color: PlaylistColor): Resource<Long> = try {
        val playlist = PlaylistEntity(
            name = name,
            description = description,
            thumbnailUrl = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            videoCount = 0,
            isFavorite = false,
            color = color.hex
        )
        val id = playlistDao.insertPlaylist(playlist)
        Resource.success(id)
    } catch (e: Exception) {
        Resource.error("Failed to create playlist: ${e.message}", e)
    }

    override suspend fun updatePlaylist(playlist: Playlist): Resource<Unit> = try {
        playlistDao.updatePlaylist(playlist.toEntity())
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to update playlist: ${e.message}", e)
    }

    override suspend fun deletePlaylist(playlistId: Long): Resource<Unit> = try {
        playlistVideoDao.deleteAllVideosFromPlaylist(playlistId)
        playlistTrackDao.deleteAllTracksFromPlaylist(playlistId)
        playlistDao.deletePlaylistById(playlistId)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to delete playlist: ${e.message}", e)
    }

    override suspend fun updateFavoriteStatus(playlistId: Long, isFavorite: Boolean): Resource<Unit> = try {
        playlistDao.updateFavoriteStatus(playlistId, isFavorite)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to update favorite status: ${e.message}", e)
    }

    override fun getVideosInPlaylist(playlistId: Long): Flow<List<Video>> =
        playlistVideoDao.getVideosInPlaylist(playlistId).map { videos ->
            videos.map { it.toDomain() }
        }

    override suspend fun addVideoToPlaylist(playlistId: Long, videoId: String): Resource<Unit> = try {
        val maxPosition = playlistVideoDao.getMaxPosition(playlistId) ?: -1
        val playlistVideo = PlaylistVideoEntity(
            playlistId = playlistId,
            videoId = videoId,
            position = maxPosition + 1,
            addedAt = System.currentTimeMillis()
        )
        playlistVideoDao.insertPlaylistVideo(playlistVideo)

        // Update video count
        val currentCount = playlistVideoDao.getVideoCountInPlaylistSync(playlistId)
        playlistDao.updateVideoCount(playlistId, currentCount)

        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to add video to playlist: ${e.message}", e)
    }

    override suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String): Resource<Unit> = try {
        playlistVideoDao.deletePlaylistVideo(playlistId, videoId)

        // Update video count
        val currentCount = playlistVideoDao.getVideoCountInPlaylistSync(playlistId)
        playlistDao.updateVideoCount(playlistId, currentCount)

        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to remove video from playlist: ${e.message}", e)
    }

    override suspend fun moveVideoInPlaylist(playlistId: Long, videoId: String, newPosition: Int): Resource<Unit> = try {
        playlistVideoDao.updateVideoPosition(playlistId, videoId, newPosition)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to move video in playlist: ${e.message}", e)
    }

    override suspend fun clearPlaylist(playlistId: Long): Resource<Unit> = try {
        playlistVideoDao.deleteAllVideosFromPlaylist(playlistId)
        playlistTrackDao.deleteAllTracksFromPlaylist(playlistId)
        playlistDao.updateVideoCount(playlistId, 0)
        playlistDao.updateTrackCount(playlistId, 0)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to clear playlist: ${e.message}", e)
    }

    // ==================== Track (Music) Methods ====================

    override fun getTracksInPlaylist(playlistId: Long): Flow<List<Track>> =
        playlistTrackDao.getTracksInPlaylist(playlistId).map { tracks ->
            tracks.map { it.toDomain() }
        }

    override suspend fun addTrackToPlaylist(playlistId: Long, track: Track): Resource<Unit> = try {
        // Ensure track is in the database first
        trackDao.insertTrack(track.toEntity())

        val maxPosition = playlistTrackDao.getMaxPosition(playlistId) ?: -1
        val playlistTrack = PlaylistTrackEntity(
            playlistId = playlistId,
            trackId = track.id,
            position = maxPosition + 1,
            addedAt = System.currentTimeMillis()
        )
        playlistTrackDao.insertPlaylistTrack(playlistTrack)

        // Update track count
        val currentCount = playlistTrackDao.getTrackCountInPlaylistSync(playlistId)
        playlistDao.updateTrackCount(playlistId, currentCount)

        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to add track to playlist: ${e.message}", e)
    }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Resource<Unit> = try {
        playlistTrackDao.deletePlaylistTrack(playlistId, trackId)

        // Update track count
        val currentCount = playlistTrackDao.getTrackCountInPlaylistSync(playlistId)
        playlistDao.updateTrackCount(playlistId, currentCount)

        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to remove track from playlist: ${e.message}", e)
    }

    override suspend fun moveTrackInPlaylist(playlistId: Long, trackId: String, newPosition: Int): Resource<Unit> = try {
        playlistTrackDao.updateTrackPosition(playlistId, trackId, newPosition)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to move track in playlist: ${e.message}", e)
    }

    override suspend fun isTrackInPlaylist(playlistId: Long, trackId: String): Boolean =
        playlistTrackDao.isTrackInPlaylist(playlistId, trackId)

    // ==================== Sharing Methods ====================

    override fun getSharedPlaylists(): Flow<List<Playlist>> =
        playlistDao.getSharedPlaylists().map { playlists ->
            playlists.map { it.toDomain() }
        }

    override fun getImportedPlaylists(): Flow<List<Playlist>> =
        playlistDao.getImportedPlaylists().map { playlists ->
            playlists.map { it.toDomain() }
        }

    override suspend fun updateShareCode(playlistId: Long, shareCode: String?, sharedAt: Long?): Resource<Unit> = try {
        playlistDao.updateShareCode(playlistId, shareCode, sharedAt)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to update share code: ${e.message}", e)
    }

    override suspend fun createImportedPlaylist(
        name: String,
        description: String?,
        color: PlaylistColor,
        importedFrom: String
    ): Resource<Long> = try {
        val now = System.currentTimeMillis()
        val playlist = PlaylistEntity(
            name = name,
            description = description,
            thumbnailUrl = null,
            createdAt = now,
            updatedAt = now,
            videoCount = 0,
            trackCount = 0,
            isFavorite = false,
            color = color.hex,
            isImported = true,
            importedFrom = importedFrom,
            importedAt = now
        )
        val id = playlistDao.insertPlaylist(playlist)
        Resource.success(id)
    } catch (e: Exception) {
        Resource.error("Failed to create imported playlist: ${e.message}", e)
    }

    override suspend fun markAsImported(playlistId: Long, importedFrom: String): Resource<Unit> = try {
        playlistDao.markAsImported(playlistId, importedFrom)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to mark playlist as imported: ${e.message}", e)
    }
}
