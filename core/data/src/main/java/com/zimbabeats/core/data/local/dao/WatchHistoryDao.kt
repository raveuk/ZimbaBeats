package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.WatchHistoryEntity
import com.zimbabeats.core.data.local.entity.VideoEntity
import com.zimbabeats.core.data.local.model.VideoWithProgressTuple
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("""
        SELECT v.* FROM videos v
        INNER JOIN watch_history wh ON v.id = wh.videoId
        ORDER BY wh.lastWatchedAt DESC
        LIMIT :limit
    """)
    fun getRecentlyWatched(limit: Int = 50): Flow<List<VideoEntity>>

    /**
     * Get recently watched videos with their playback progress.
     * Each video appears only once (videoId is PK in watch_history).
     * Includes resume position from video_progress table.
     */
    @Query("""
        SELECT
            v.id, v.title, v.description, v.thumbnailUrl, v.channelName, v.channelId,
            v.duration, v.viewCount, v.publishedAt, v.isKidFriendly, v.ageRating,
            v.category, v.addedAt, v.lastAccessedAt,
            vp.currentPosition, vp.duration as progressDuration, vp.lastUpdated as progressLastUpdated
        FROM videos v
        INNER JOIN watch_history wh ON v.id = wh.videoId
        LEFT JOIN video_progress vp ON v.id = vp.videoId
        ORDER BY wh.lastWatchedAt DESC
        LIMIT :limit
    """)
    fun getRecentlyWatchedWithProgress(limit: Int = 50): Flow<List<VideoWithProgressTuple>>

    @Query("SELECT * FROM watch_history WHERE videoId = :videoId")
    fun getHistoryForVideo(videoId: String): Flow<WatchHistoryEntity?>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId ORDER BY lastWatchedAt DESC")
    fun getHistoryForProfile(profileId: Long?): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun getAllHistory(limit: Int = 100): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun deleteHistoryForVideo(videoId: String)

    @Query("DELETE FROM watch_history WHERE lastWatchedAt < :timestamp")
    suspend fun deleteHistoryBefore(timestamp: Long)

    @Query("DELETE FROM watch_history")
    suspend fun deleteAllHistory()

    @Query("SELECT COUNT(*) FROM watch_history")
    fun getUniqueVideoCount(): Flow<Int>

    @Query("""
        SELECT SUM(watchDuration) FROM watch_history
        WHERE lastWatchedAt >= :startTimestamp AND lastWatchedAt <= :endTimestamp
    """)
    suspend fun getTotalWatchTime(startTimestamp: Long, endTimestamp: Long): Long?

    /**
     * Get most watched videos ordered by watch count (descending).
     * Similar to Music's "Most Played" feature.
     */
    @Query("""
        SELECT
            v.id, v.title, v.description, v.thumbnailUrl, v.channelName, v.channelId,
            v.duration, v.viewCount, v.publishedAt, v.isKidFriendly, v.ageRating,
            v.category, v.addedAt, v.lastAccessedAt,
            vp.currentPosition, vp.duration as progressDuration, vp.lastUpdated as progressLastUpdated
        FROM videos v
        INNER JOIN watch_history wh ON v.id = wh.videoId
        LEFT JOIN video_progress vp ON v.id = vp.videoId
        ORDER BY wh.watchCount DESC, wh.lastWatchedAt DESC
        LIMIT :limit
    """)
    fun getMostWatched(limit: Int = 10): Flow<List<VideoWithProgressTuple>>

    @Query("SELECT watchCount FROM watch_history WHERE videoId = :videoId")
    suspend fun getWatchCount(videoId: String): Int?

    @Query("UPDATE watch_history SET watchCount = watchCount + 1, lastWatchedAt = :timestamp WHERE videoId = :videoId")
    suspend fun incrementWatchCount(videoId: String, timestamp: Long = System.currentTimeMillis())
}
