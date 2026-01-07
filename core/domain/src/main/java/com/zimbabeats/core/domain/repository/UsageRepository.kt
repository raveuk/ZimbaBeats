package com.zimbabeats.core.domain.repository

import com.zimbabeats.core.domain.model.AppUsage
import com.zimbabeats.core.domain.model.ScreenTimeLog
import com.zimbabeats.core.domain.model.UsageStatistics
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    // Screen time logs
    fun getScreenTimeLogs(limit: Int = 100): Flow<List<ScreenTimeLog>>
    fun getScreenTimeForDate(date: String): Flow<List<ScreenTimeLog>>
    fun getTotalMinutesForDate(date: String): Flow<Int>
    fun getTotalMinutesForDateRange(startDate: String, endDate: String): Flow<Int>
    suspend fun logScreenTime(videoId: String?, startTime: Long, endTime: Long, durationMinutes: Int): Resource<Unit>
    suspend fun clearScreenTimeLogs(): Resource<Unit>

    // App usage
    fun getAllAppUsage(): Flow<List<AppUsage>>
    fun getAppUsageForDate(date: String): Flow<List<AppUsage>>
    fun getActiveSession(): Flow<AppUsage?>
    suspend fun startSession(): Resource<Long>
    suspend fun endSession(sessionId: Long, videosWatched: Int): Resource<Unit>
    suspend fun clearAppUsage(): Resource<Unit>

    // Statistics
    suspend fun getUsageStatistics(): Resource<UsageStatistics>
    suspend fun getRemainingScreenTime(maxMinutes: Int): Resource<Int>
}
