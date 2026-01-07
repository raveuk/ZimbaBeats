package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.VideoProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoProgressDao {
    @Query("SELECT * FROM video_progress WHERE videoId = :videoId")
    fun getProgress(videoId: String): Flow<VideoProgressEntity?>

    @Query("SELECT * FROM video_progress WHERE videoId = :videoId")
    suspend fun getProgressSync(videoId: String): VideoProgressEntity?

    @Query("SELECT * FROM video_progress")
    fun getAllProgress(): Flow<List<VideoProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: VideoProgressEntity)

    @Query("UPDATE video_progress SET currentPosition = :position, lastUpdated = :timestamp WHERE videoId = :videoId")
    suspend fun updateProgress(videoId: String, position: Long, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteProgress(progress: VideoProgressEntity)

    @Query("DELETE FROM video_progress WHERE videoId = :videoId")
    suspend fun deleteProgressForVideo(videoId: String)

    @Query("DELETE FROM video_progress")
    suspend fun deleteAllProgress()
}
