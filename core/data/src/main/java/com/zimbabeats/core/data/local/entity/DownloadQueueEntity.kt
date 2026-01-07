package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_queue",
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
data class DownloadQueueEntity(
    @PrimaryKey val videoId: String,
    val status: String,                       // PENDING, DOWNLOADING, PAUSED, FAILED, COMPLETED
    val progress: Float = 0f,                 // 0.0 to 1.0
    val quality: String,
    val queuedAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val error: String? = null
)
