package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY lastAccessedAt DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :videoId")
    fun getVideoById(videoId: String): Flow<VideoEntity?>

    @Query("SELECT * FROM videos WHERE isKidFriendly = 1 ORDER BY lastAccessedAt DESC LIMIT :limit")
    fun getKidFriendlyVideos(limit: Int): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE category = :category ORDER BY publishedAt DESC")
    fun getVideosByCategory(category: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE channelId = :channelId ORDER BY publishedAt DESC")
    fun getVideosByChannel(channelId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%' OR channelName LIKE '%' || :query || '%'")
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("UPDATE videos SET lastAccessedAt = :timestamp WHERE id = :videoId")
    suspend fun updateLastAccessed(videoId: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteVideo(video: VideoEntity)

    @Query("DELETE FROM videos WHERE id = :videoId")
    suspend fun deleteVideoById(videoId: String)

    @Query("DELETE FROM videos")
    suspend fun deleteAllVideos()

    @Query("SELECT COUNT(*) FROM videos")
    fun getVideoCount(): Flow<Int>

    // Clean up dummy/mock videos from old development builds
    @Query("DELETE FROM videos WHERE id LIKE 'search_%' OR id = 'dQw4w9WgXcQ' OR id = 'jNQXAC9IVRw' OR id = 'kJQP7kiw5Fk' OR id = '3fm8jGTrqHo' OR id = 'ZbZSe6N_BXs'")
    suspend fun deleteDummyVideos()
}
