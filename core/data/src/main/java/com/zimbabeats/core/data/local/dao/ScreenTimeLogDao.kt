package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.ScreenTimeLogEntity
import com.zimbabeats.core.data.local.model.DailySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenTimeLogDao {
    @Query("SELECT * FROM screen_time_logs ORDER BY date DESC, startTime DESC")
    fun getAllLogs(): Flow<List<ScreenTimeLogEntity>>

    @Query("SELECT * FROM screen_time_logs WHERE date = :date ORDER BY startTime ASC")
    fun getLogsForDate(date: String): Flow<List<ScreenTimeLogEntity>>

    @Query("SELECT * FROM screen_time_logs WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getLogsForDateRange(startDate: String, endDate: String): Flow<List<ScreenTimeLogEntity>>

    @Query("SELECT SUM(durationMinutes) FROM screen_time_logs WHERE date = :date")
    fun getTotalMinutesForDate(date: String): Flow<Int?>

    @Query("SELECT SUM(durationMinutes) FROM screen_time_logs WHERE date = :date")
    suspend fun getTotalMinutesForDateSync(date: String): Int?

    @Query("SELECT SUM(durationMinutes) FROM screen_time_logs WHERE date >= :startDate AND date <= :endDate")
    fun getTotalMinutesForDateRange(startDate: String, endDate: String): Flow<Int?>

    @Query("SELECT date, SUM(durationMinutes) as total FROM screen_time_logs GROUP BY date ORDER BY date DESC LIMIT :limit")
    fun getDailySummary(limit: Int = 30): Flow<List<DailySummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ScreenTimeLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<ScreenTimeLogEntity>)

    @Delete
    suspend fun deleteLog(log: ScreenTimeLogEntity)

    @Query("DELETE FROM screen_time_logs WHERE date < :date")
    suspend fun deleteLogsBefore(date: String)

    @Query("DELETE FROM screen_time_logs")
    suspend fun deleteAllLogs()
}
