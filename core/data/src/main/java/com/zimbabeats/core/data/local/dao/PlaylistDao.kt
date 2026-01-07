package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistById(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoritePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET videoCount = :count, updatedAt = :timestamp WHERE id = :playlistId")
    suspend fun updateVideoCount(playlistId: Long, count: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET trackCount = :count, updatedAt = :timestamp WHERE id = :playlistId")
    suspend fun updateTrackCount(playlistId: Long, count: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET isFavorite = :isFavorite WHERE id = :playlistId")
    suspend fun updateFavoriteStatus(playlistId: Long, isFavorite: Boolean)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlists")
    fun getPlaylistCount(): Flow<Int>
}
