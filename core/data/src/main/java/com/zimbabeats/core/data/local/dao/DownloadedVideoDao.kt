package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.DownloadedVideoEntity
import com.zimbabeats.core.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedVideoDao {
    @Query("""
        SELECT v.* FROM videos v
        INNER JOIN downloaded_videos dv ON v.id = dv.videoId
        ORDER BY dv.downloadedAt DESC
    """)
    fun getAllDownloadedVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM downloaded_videos")
    fun getAllDownloads(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE videoId = :videoId")
    fun getDownloadedVideo(videoId: String): Flow<DownloadedVideoEntity?>

    @Query("SELECT * FROM downloaded_videos WHERE videoId = :videoId")
    suspend fun getDownloadedVideoSync(videoId: String): DownloadedVideoEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_videos WHERE videoId = :videoId)")
    fun isVideoDownloaded(videoId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadedVideoEntity)

    @Delete
    suspend fun deleteDownload(download: DownloadedVideoEntity)

    @Query("DELETE FROM downloaded_videos WHERE videoId = :videoId")
    suspend fun deleteDownloadByVideoId(videoId: String)

    @Query("DELETE FROM downloaded_videos WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredDownloads(currentTime: Long = System.currentTimeMillis())

    @Query("SELECT SUM(fileSize) FROM downloaded_videos")
    fun getTotalDownloadSize(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM downloaded_videos")
    fun getDownloadCount(): Flow<Int>
}
