package com.zimbabeats.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_usage")
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionStart: Long,
    val sessionEnd: Long? = null,
    val totalMinutes: Int = 0,
    val videosWatched: Int = 0,
    val date: String                          // YYYY-MM-DD format
)
