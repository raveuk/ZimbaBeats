package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloaded_videos",
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
data class DownloadedVideoEntity(
    @PrimaryKey val videoId: String,
    val filePath: String,                     // Local file path
    val fileSize: Long,                       // In bytes
    val quality: String,                      // 360p, 480p, 720p
    val downloadedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,             // Optional expiration
    val thumbnailPath: String?                // Cached thumbnail
)
