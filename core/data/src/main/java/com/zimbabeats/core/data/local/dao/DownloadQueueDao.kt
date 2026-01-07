package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {
    @Query("SELECT * FROM download_queue ORDER BY queuedAt ASC")
    fun getAllQueueItems(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue WHERE status = :status ORDER BY queuedAt ASC")
    fun getQueueItemsByStatus(status: String): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue WHERE videoId = :videoId")
    fun getQueueItem(videoId: String): Flow<DownloadQueueEntity?>

    @Query("SELECT * FROM download_queue WHERE videoId = :videoId")
    suspend fun getQueueItemSync(videoId: String): DownloadQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(queueItem: DownloadQueueEntity)

    @Update
    suspend fun updateQueueItem(queueItem: DownloadQueueEntity)

    @Query("UPDATE download_queue SET status = :status WHERE videoId = :videoId")
    suspend fun updateStatus(videoId: String, status: String)

    @Query("UPDATE download_queue SET progress = :progress WHERE videoId = :videoId")
    suspend fun updateProgress(videoId: String, progress: Int)

    @Query("UPDATE download_queue SET status = :status, error = :error WHERE videoId = :videoId")
    suspend fun updateStatusWithError(videoId: String, status: String, error: String)

    @Delete
    suspend fun deleteQueueItem(queueItem: DownloadQueueEntity)

    @Query("DELETE FROM download_queue WHERE videoId = :videoId")
    suspend fun deleteQueueItemByVideoId(videoId: String)

    @Query("DELETE FROM download_queue WHERE status = 'COMPLETED'")
    suspend fun deleteCompletedItems()

    @Query("SELECT COUNT(*) FROM download_queue WHERE status = :status")
    fun getCountByStatus(status: String): Flow<Int>
}
