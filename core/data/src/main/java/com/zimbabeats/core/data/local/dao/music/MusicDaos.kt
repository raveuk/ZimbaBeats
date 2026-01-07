package com.zimbabeats.core.data.local.dao.music

import androidx.room.*
import com.zimbabeats.core.data.local.entity.music.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for music tracks
 */
@Dao
interface TrackDao {

    @Query("SELECT * FROM music_tracks WHERE id = :trackId")
    suspend fun getTrack(trackId: String): TrackEntity?

    @Query("SELECT * FROM music_tracks WHERE id IN (:trackIds)")
    suspend fun getTracks(trackIds: List<String>): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE music_tracks SET lastPlayedAt = :timestamp, playCount = playCount + 1 WHERE id = :trackId")
    suspend fun updatePlayStats(trackId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM music_tracks ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM music_tracks ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM music_tracks WHERE artistId = :artistId ORDER BY title ASC")
    fun getTracksByArtist(artistId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM music_tracks WHERE albumId = :albumId ORDER BY title ASC")
    fun getTracksByAlbum(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM music_tracks WHERE title LIKE '%' || :query || '%' OR artistName LIKE '%' || :query || '%' OR albumName LIKE '%' || :query || '%' ORDER BY playCount DESC LIMIT :limit")
    suspend fun searchTracks(query: String, limit: Int = 50): List<TrackEntity>

    @Delete
    suspend fun deleteTrack(track: TrackEntity)

    @Query("DELETE FROM music_tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)
}

/**
 * DAO for music playlists
 */
@Dao
interface MusicPlaylistDao {

    @Query("SELECT * FROM music_playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<MusicPlaylistEntity>>

    @Query("SELECT * FROM music_playlists WHERE id = :playlistId")
    suspend fun getPlaylist(playlistId: Long): MusicPlaylistEntity?

    @Query("SELECT * FROM music_playlists WHERE id = :playlistId")
    fun getPlaylistFlow(playlistId: Long): Flow<MusicPlaylistEntity?>

    @Insert
    suspend fun createPlaylist(playlist: MusicPlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: MusicPlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: MusicPlaylistEntity)

    @Query("DELETE FROM music_playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    // Playlist tracks operations

    @Query("""
        SELECT t.* FROM music_tracks t
        INNER JOIN music_playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
    """)
    fun getPlaylistTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM music_tracks t
        INNER JOIN music_playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
    """)
    suspend fun getPlaylistTracksSync(playlistId: Long): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(playlistTrack: MusicPlaylistTrackEntity)

    @Query("DELETE FROM music_playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("SELECT COUNT(*) FROM music_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getPlaylistTrackCount(playlistId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM music_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getNextPosition(playlistId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM music_playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId)")
    suspend fun isTrackInPlaylist(playlistId: Long, trackId: String): Boolean

    @Query("UPDATE music_playlists SET updatedAt = :timestamp WHERE id = :playlistId")
    suspend fun updatePlaylistTimestamp(playlistId: Long, timestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun addTrackToPlaylistWithUpdate(playlistId: Long, trackId: String) {
        val position = getNextPosition(playlistId)
        addTrackToPlaylist(MusicPlaylistTrackEntity(playlistId, trackId, position = position))
        updatePlaylistTimestamp(playlistId)
    }
}

/**
 * DAO for favorite tracks
 */
@Dao
interface FavoriteTrackDao {

    @Query("""
        SELECT t.* FROM music_tracks t
        INNER JOIN favorite_tracks f ON t.id = f.trackId
        ORDER BY f.addedAt DESC
    """)
    fun getFavoriteTracks(): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM music_tracks t
        INNER JOIN favorite_tracks f ON t.id = f.trackId
        ORDER BY f.addedAt DESC
    """)
    suspend fun getFavoriteTracksSync(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE trackId = :trackId)")
    fun isFavorite(trackId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE trackId = :trackId)")
    suspend fun isFavoriteSync(trackId: String): Boolean

    @Query("SELECT COUNT(*) FROM favorite_tracks")
    fun getFavoriteCount(): Flow<Int>

    @Transaction
    suspend fun toggleFavorite(trackId: String): Boolean {
        val isFav = isFavoriteSync(trackId)
        if (isFav) {
            removeFavorite(trackId)
        } else {
            addFavorite(FavoriteTrackEntity(trackId))
        }
        return !isFav
    }
}

/**
 * DAO for music listening history
 */
@Dao
interface MusicListeningHistoryDao {

    @Query("""
        SELECT DISTINCT t.* FROM music_tracks t
        INNER JOIN music_listening_history h ON t.id = h.trackId
        ORDER BY h.listenedAt DESC
        LIMIT :limit
    """)
    fun getListeningHistory(limit: Int = 100): Flow<List<TrackEntity>>

    @Insert
    suspend fun recordListen(history: MusicListeningHistoryEntity)

    @Query("DELETE FROM music_listening_history WHERE listenedAt < :before")
    suspend fun clearOldHistory(before: Long)

    @Query("DELETE FROM music_listening_history")
    suspend fun clearAllHistory()

    @Query("SELECT SUM(listenDuration) FROM music_listening_history WHERE trackId = :trackId")
    suspend fun getTotalListenTime(trackId: String): Long?

    @Query("SELECT COUNT(*) FROM music_listening_history WHERE trackId = :trackId")
    suspend fun getListenCount(trackId: String): Int
}
