package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.BlockedContentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedContentDao {
    @Query("SELECT * FROM blocked_content ORDER BY blockedAt DESC")
    fun getAllBlockedContent(): Flow<List<BlockedContentEntity>>

    @Query("SELECT * FROM blocked_content WHERE contentType = :type ORDER BY blockedAt DESC")
    fun getBlockedContentByType(type: String): Flow<List<BlockedContentEntity>>

    @Query("SELECT * FROM blocked_content WHERE contentId = :contentId")
    suspend fun getBlockedContent(contentId: String): BlockedContentEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_content WHERE contentId = :contentId)")
    fun isContentBlocked(contentId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_content WHERE contentId = :contentId)")
    suspend fun isContentBlockedSync(contentId: String): Boolean

    @Query("SELECT contentId FROM blocked_content WHERE contentType = 'CHANNEL'")
    fun getBlockedChannelIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedContent(content: BlockedContentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedContentList(content: List<BlockedContentEntity>)

    @Delete
    suspend fun deleteBlockedContent(content: BlockedContentEntity)

    @Query("DELETE FROM blocked_content WHERE contentId = :contentId")
    suspend fun deleteBlockedContentById(contentId: String)

    @Query("DELETE FROM blocked_content")
    suspend fun deleteAllBlockedContent()

    @Query("SELECT COUNT(*) FROM blocked_content WHERE contentType = :type")
    fun getBlockedCountByType(type: String): Flow<Int>
}
