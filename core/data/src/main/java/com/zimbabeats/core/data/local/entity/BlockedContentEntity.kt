package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_content")
data class BlockedContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,                    // Video or channel ID
    val contentType: String,                  // VIDEO, CHANNEL
    val reason: String?,                      // Optional reason
    val blockedAt: Long = System.currentTimeMillis(),
    val blockedBy: Long?                      // ProfileId who blocked it
)
