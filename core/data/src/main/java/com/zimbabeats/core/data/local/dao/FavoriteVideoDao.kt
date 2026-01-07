package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.FavoriteVideoEntity
import com.zimbabeats.core.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteVideoDao {
    @Query("""
        SELECT v.* FROM videos v
        INNER JOIN favorite_videos fv ON v.id = fv.videoId
        ORDER BY fv.addedAt DESC
    """)
    fun getAllFavoriteVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM favorite_videos WHERE videoId = :videoId")
    suspend fun getFavoriteVideo(videoId: String): FavoriteVideoEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_videos WHERE videoId = :videoId)")
    fun isFavorite(videoId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteVideoEntity)

    @Query("DELETE FROM favorite_videos WHERE videoId = :videoId")
    suspend fun deleteFavorite(videoId: String)

    @Query("SELECT COUNT(*) FROM favorite_videos")
    fun getFavoriteCount(): Flow<Int>
}
