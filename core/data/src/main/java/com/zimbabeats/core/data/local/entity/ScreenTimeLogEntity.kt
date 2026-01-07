package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "screen_time_logs",
    indices = [Index("date")]
)
data class ScreenTimeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                         // YYYY-MM-DD format
    val startTime: Long,                      // Timestamp
    val endTime: Long,                        // Timestamp
    val durationMinutes: Int,
    val videoId: String?                      // Optional: video being watched
)
