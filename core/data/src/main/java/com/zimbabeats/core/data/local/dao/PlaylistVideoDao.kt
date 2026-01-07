package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.PlaylistVideoEntity
import com.zimbabeats.core.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistVideoDao {
    @Query("""
        SELECT v.* FROM videos v
        INNER JOIN playlist_videos pv ON v.id = pv.videoId
        WHERE pv.playlistId = :playlistId
        ORDER BY pv.position ASC
    """)
    fun getVideosInPlaylist(playlistId: Long): Flow<List<VideoEntity>>

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getPlaylistVideos(playlistId: Long): Flow<List<PlaylistVideoEntity>>

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun getPlaylistVideo(playlistId: Long, videoId: String): PlaylistVideoEntity?

    @Query("SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId")
    fun getVideoCountInPlaylist(playlistId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun getVideoCountInPlaylistSync(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistVideo(playlistVideo: PlaylistVideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistVideos(playlistVideos: List<PlaylistVideoEntity>)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deletePlaylistVideo(playlistId: Long, videoId: String)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun deleteAllVideosFromPlaylist(playlistId: Long)

    @Query("UPDATE playlist_videos SET position = :newPosition WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun updateVideoPosition(playlistId: Long, videoId: String, newPosition: Int)

    @Query("SELECT MAX(position) FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?
}
