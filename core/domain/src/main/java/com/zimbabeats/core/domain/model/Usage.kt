package com.zimbabeats.core.domain.model

data class ScreenTimeLog(
    val id: Long = 0,
    val date: String,  // YYYY-MM-DD
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Int,
    val videoId: String?
)

data class AppUsage(
    val id: Long = 0,
    val sessionStart: Long,
    val sessionEnd: Long? = null,
    val totalMinutes: Int = 0,
    val videosWatched: Int = 0,
    val date: String  // YYYY-MM-DD
)

data class UsageStatistics(
    val todayMinutes: Int,
    val weekMinutes: Int,
    val monthMinutes: Int,
    val todayVideos: Int,
    val weekVideos: Int,
    val mostWatchedCategory: VideoCategory?,
    val averageDailyMinutes: Int,
    val screenTimeRemaining: Int?  // null if no limit set
)
