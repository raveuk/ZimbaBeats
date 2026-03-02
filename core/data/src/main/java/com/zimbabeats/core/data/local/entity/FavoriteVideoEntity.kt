package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for storing video favorites.
 *
 * NOTE: This entity stores the complete video metadata (denormalized).
 * This is intentional because:
 * 1. The videos table is a cache that gets cleared on app startup
 * 2. Favorites should persist independently of the cache
 * 3. Users expect their favorites to survive across app restarts
 *
 * By storing all video data here, favorites are self-contained and
 * don't depend on the video cache at all.
 */
@Entity(
    tableName = "favorite_videos",
    indices = [Index("videoId")]
)
data class FavoriteVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String,
    val channelName: String,
    val channelId: String,
    val duration: Long,              // In seconds
    val viewCount: Long,
    val publishedAt: Long,           // Timestamp
    val isKidFriendly: Boolean = true,
    val ageRating: String = "ALL",
    val category: String?,
    val addedAt: Long = System.currentTimeMillis()
)
