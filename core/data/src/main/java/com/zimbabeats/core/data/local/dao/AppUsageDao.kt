package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.AppUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usage ORDER BY sessionStart DESC")
    fun getAllUsage(): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usage WHERE date = :date ORDER BY sessionStart DESC")
    fun getUsageForDate(date: String): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usage WHERE date >= :startDate AND date <= :endDate ORDER BY sessionStart DESC")
    fun getUsageForDateRange(startDate: String, endDate: String): Flow<List<AppUsageEntity>>

    @Query("SELECT SUM(totalMinutes) FROM app_usage WHERE date = :date")
    fun getTotalMinutesForDate(date: String): Flow<Int?>

    @Query("SELECT SUM(videosWatched) FROM app_usage WHERE date = :date")
    fun getTotalVideosForDate(date: String): Flow<Int?>

    @Query("SELECT SUM(totalMinutes) FROM app_usage WHERE date >= :startDate AND date <= :endDate")
    fun getTotalMinutesForDateRange(startDate: String, endDate: String): Flow<Int?>

    @Query("SELECT * FROM app_usage WHERE sessionEnd IS NULL ORDER BY sessionStart DESC LIMIT 1")
    fun getActiveSession(): Flow<AppUsageEntity?>

    @Query("SELECT * FROM app_usage WHERE sessionEnd IS NULL ORDER BY sessionStart DESC LIMIT 1")
    suspend fun getActiveSessionSync(): AppUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: AppUsageEntity): Long

    @Update
    suspend fun updateUsage(usage: AppUsageEntity)

    @Query("UPDATE app_usage SET sessionEnd = :endTime, totalMinutes = :totalMinutes WHERE id = :id")
    suspend fun endSession(id: Long, endTime: Long, totalMinutes: Int)

    @Delete
    suspend fun deleteUsage(usage: AppUsageEntity)

    @Query("DELETE FROM app_usage WHERE date < :date")
    suspend fun deleteUsageBefore(date: String)

    @Query("DELETE FROM app_usage")
    suspend fun deleteAllUsage()
}
