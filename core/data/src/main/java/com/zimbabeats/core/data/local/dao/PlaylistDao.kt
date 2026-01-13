package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM video_playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM video_playlists WHERE id = :playlistId")
    fun getPlaylistById(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM video_playlists WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoritePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE video_playlists SET videoCount = :count, updatedAt = :timestamp WHERE id = :playlistId")
    suspend fun updateVideoCount(playlistId: Long, count: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE video_playlists SET trackCount = :count, updatedAt = :timestamp WHERE id = :playlistId")
    suspend fun updateTrackCount(playlistId: Long, count: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE video_playlists SET isFavorite = :isFavorite, updatedAt = :timestamp WHERE id = :playlistId")
    suspend fun updateFavoriteStatus(playlistId: Long, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM video_playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("SELECT COUNT(*) FROM video_playlists")
    fun getPlaylistCount(): Flow<Int>

    // Sharing queries
    @Query("UPDATE video_playlists SET shareCode = :shareCode, sharedAt = :sharedAt WHERE id = :playlistId")
    suspend fun updateShareCode(playlistId: Long, shareCode: String?, sharedAt: Long?)

    @Query("SELECT * FROM video_playlists WHERE shareCode IS NOT NULL ORDER BY sharedAt DESC")
    fun getSharedPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM video_playlists WHERE isImported = 1 ORDER BY importedAt DESC")
    fun getImportedPlaylists(): Flow<List<PlaylistEntity>>

    @Query("UPDATE video_playlists SET isImported = 1, importedFrom = :importedFrom, importedAt = :importedAt WHERE id = :playlistId")
    suspend fun markAsImported(playlistId: Long, importedFrom: String, importedAt: Long = System.currentTimeMillis())
}
