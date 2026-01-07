package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,              // YouTube video ID
    val title: String,
    val description: String?,
    val thumbnailUrl: String,
    val channelName: String,
    val channelId: String,
    val duration: Long,                       // In seconds
    val viewCount: Long,
    val publishedAt: Long,                    // Timestamp
    val isKidFriendly: Boolean = true,
    val ageRating: String = "ALL",           // ALL, 3+, 7+, 12+
    val category: String?,                    // Education, Entertainment, etc.
    val addedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)
