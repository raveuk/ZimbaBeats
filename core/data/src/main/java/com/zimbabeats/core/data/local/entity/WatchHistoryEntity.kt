package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_history",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lastWatchedAt")]
)
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val lastWatchedAt: Long = System.currentTimeMillis(),
    val watchDuration: Long,                  // How long watched in seconds
    val completionPercentage: Float,          // 0.0 to 1.0
    val profileId: Long? = null,              // Optional: if multiple profiles
    val watchCount: Int = 1                   // Number of times video has been watched
)
