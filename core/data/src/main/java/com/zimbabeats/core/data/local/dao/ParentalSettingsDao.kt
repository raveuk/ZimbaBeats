package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.ParentalSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParentalSettingsDao {
    @Query("SELECT * FROM parental_settings WHERE id = 1")
    fun getSettings(): Flow<ParentalSettingsEntity?>

    @Query("SELECT * FROM parental_settings WHERE id = 1")
    suspend fun getSettingsSync(): ParentalSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: ParentalSettingsEntity)

    @Update
    suspend fun updateSettings(settings: ParentalSettingsEntity)

    @Query("UPDATE parental_settings SET isEnabled = :isEnabled WHERE id = 1")
    suspend fun setEnabled(isEnabled: Boolean)

    @Query("UPDATE parental_settings SET maxScreenTimeMinutes = :minutes WHERE id = 1")
    suspend fun setMaxScreenTime(minutes: Int)

    @Query("UPDATE parental_settings SET allowSearch = :allow WHERE id = 1")
    suspend fun setAllowSearch(allow: Boolean)

    @Query("UPDATE parental_settings SET requirePinForSettings = :require WHERE id = 1")
    suspend fun setRequirePinForSettings(require: Boolean)

    @Query("UPDATE parental_settings SET requirePinForDownloads = :require WHERE id = 1")
    suspend fun setRequirePinForDownloads(require: Boolean)
}
