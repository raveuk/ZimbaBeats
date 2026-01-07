package com.zimbabeats.core.data.repository

import com.zimbabeats.core.data.local.database.ZimbaBeatsDatabase
import com.zimbabeats.core.data.local.entity.AppUsageEntity
import com.zimbabeats.core.data.local.entity.ScreenTimeLogEntity
import com.zimbabeats.core.domain.model.AppUsage
import com.zimbabeats.core.domain.model.ScreenTimeLog
import com.zimbabeats.core.domain.model.UsageStatistics
import com.zimbabeats.core.domain.repository.UsageRepository
import com.zimbabeats.core.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

class UsageRepositoryImpl(
    private val database: ZimbaBeatsDatabase
) : UsageRepository {

    private val screenTimeLogDao = database.screenTimeLogDao()
    private val appUsageDao = database.appUsageDao()
    private val watchHistoryDao = database.watchHistoryDao()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun getScreenTimeLogs(limit: Int): Flow<List<ScreenTimeLog>> =
        screenTimeLogDao.getAllLogs().map { logs ->
            logs.take(limit).map { it.toDomain() }
        }

    override fun getScreenTimeForDate(date: String): Flow<List<ScreenTimeLog>> =
        screenTimeLogDao.getLogsForDate(date).map { logs ->
            logs.map { it.toDomain() }
        }

    override fun getTotalMinutesForDate(date: String): Flow<Int> =
        screenTimeLogDao.getTotalMinutesForDate(date).map { it ?: 0 }

    override fun getTotalMinutesForDateRange(startDate: String, endDate: String): Flow<Int> =
        screenTimeLogDao.getTotalMinutesForDateRange(startDate, endDate).map { it ?: 0 }

    override suspend fun logScreenTime(
        videoId: String?,
        startTime: Long,
        endTime: Long,
        durationMinutes: Int
    ): Resource<Unit> = try {
        val date = dateFormat.format(Date(startTime))
        val log = ScreenTimeLogEntity(
            date = date,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationMinutes,
            videoId = videoId
        )
        screenTimeLogDao.insertLog(log)
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to log screen time: ${e.message}", e)
    }

    override suspend fun clearScreenTimeLogs(): Resource<Unit> = try {
        screenTimeLogDao.deleteAllLogs()
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to clear screen time logs: ${e.message}", e)
    }

    override fun getAllAppUsage(): Flow<List<AppUsage>> =
        appUsageDao.getAllUsage().map { usage ->
            usage.map { it.toDomain() }
        }

    override fun getAppUsageForDate(date: String): Flow<List<AppUsage>> =
        appUsageDao.getUsageForDate(date).map { usage ->
            usage.map { it.toDomain() }
        }

    override fun getActiveSession(): Flow<AppUsage?> =
        appUsageDao.getActiveSession().map { it?.toDomain() }

    override suspend fun startSession(): Resource<Long> = try {
        val date = dateFormat.format(Date())
        val usage = AppUsageEntity(
            sessionStart = System.currentTimeMillis(),
            sessionEnd = null,
            totalMinutes = 0,
            videosWatched = 0,
            date = date
        )
        val id = appUsageDao.insertUsage(usage)
        Resource.success(id)
    } catch (e: Exception) {
        Resource.error("Failed to start session: ${e.message}", e)
    }

    override suspend fun endSession(sessionId: Long, videosWatched: Int): Resource<Unit> = try {
        val session = appUsageDao.getActiveSessionSync()
        if (session != null && session.id == sessionId) {
            val endTime = System.currentTimeMillis()
            val totalMinutes = ((endTime - session.sessionStart) / 60000).toInt()
            appUsageDao.endSession(sessionId, endTime, totalMinutes)
        }
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to end session: ${e.message}", e)
    }

    override suspend fun clearAppUsage(): Resource<Unit> = try {
        appUsageDao.deleteAllUsage()
        Resource.success(Unit)
    } catch (e: Exception) {
        Resource.error("Failed to clear app usage: ${e.message}", e)
    }

    override suspend fun getUsageStatistics(): Resource<UsageStatistics> = try {
        val today = dateFormat.format(Date())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekStart = dateFormat.format(calendar.time)
        calendar.time = Date()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val monthStart = dateFormat.format(calendar.time)

        val todayMinutes = screenTimeLogDao.getTotalMinutesForDateSync(today) ?: 0
        val weekMinutes = getTotalMinutesForDateRange(weekStart, today).first()
        val monthMinutes = getTotalMinutesForDateRange(monthStart, today).first()

        val todayVideos = appUsageDao.getUsageForDate(today).first().sumOf { it.videosWatched }
        val weekVideos = appUsageDao.getUsageForDateRange(weekStart, today).first().sumOf { it.videosWatched }

        // Calculate average daily minutes for the month
        val averageDailyMinutes = monthMinutes / 30

        // Most watched category (placeholder - would need more complex query)
        val mostWatchedCategory = null

        val statistics = UsageStatistics(
            todayMinutes = todayMinutes,
            weekMinutes = weekMinutes,
            monthMinutes = monthMinutes,
            todayVideos = todayVideos,
            weekVideos = weekVideos,
            mostWatchedCategory = mostWatchedCategory,
            averageDailyMinutes = averageDailyMinutes,
            screenTimeRemaining = null
        )

        Resource.success(statistics)
    } catch (e: Exception) {
        Resource.error("Failed to get usage statistics: ${e.message}", e)
    }

    override suspend fun getRemainingScreenTime(maxMinutes: Int): Resource<Int> = try {
        val today = dateFormat.format(Date())
        val usedMinutes = screenTimeLogDao.getTotalMinutesForDateSync(today) ?: 0
        val remaining = maxOf(0, maxMinutes - usedMinutes)
        Resource.success(remaining)
    } catch (e: Exception) {
        Resource.error("Failed to get remaining screen time: ${e.message}", e)
    }
}

private fun ScreenTimeLogEntity.toDomain() = ScreenTimeLog(
    id = id,
    date = date,
    startTime = startTime,
    endTime = endTime,
    durationMinutes = durationMinutes,
    videoId = videoId
)

private fun AppUsageEntity.toDomain() = AppUsage(
    id = id,
    sessionStart = sessionStart,
    sessionEnd = sessionEnd,
    totalMinutes = totalMinutes,
    videosWatched = videosWatched,
    date = date
)
