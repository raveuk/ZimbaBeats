package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_progress",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("videoId")]
)
data class VideoProgressEntity(
    @PrimaryKey val videoId: String,
    val currentPosition: Long,                // Current playback position in ms
    val duration: Long,                       // Total duration in ms
    val lastUpdated: Long = System.currentTimeMillis()
)
