package com.zimbabeats.core.data.local.dao

import androidx.room.*
import com.zimbabeats.core.data.local.entity.ParentalProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParentalProfileDao {
    @Query("SELECT * FROM parental_profiles ORDER BY createdAt ASC")
    fun getAllProfiles(): Flow<List<ParentalProfileEntity>>

    @Query("SELECT * FROM parental_profiles WHERE id = :profileId")
    fun getProfileById(profileId: Long): Flow<ParentalProfileEntity?>

    @Query("SELECT * FROM parental_profiles WHERE id = :profileId")
    suspend fun getProfileByIdSync(profileId: Long): ParentalProfileEntity?

    @Query("SELECT * FROM parental_profiles WHERE isDefault = 1 LIMIT 1")
    fun getDefaultProfile(): Flow<ParentalProfileEntity?>

    @Query("SELECT * FROM parental_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfileSync(): ParentalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ParentalProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ParentalProfileEntity)

    @Query("UPDATE parental_profiles SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE parental_profiles SET isDefault = 1 WHERE id = :profileId")
    suspend fun setAsDefault(profileId: Long)

    @Delete
    suspend fun deleteProfile(profile: ParentalProfileEntity)

    @Query("DELETE FROM parental_profiles WHERE id = :profileId")
    suspend fun deleteProfileById(profileId: Long)

    @Query("SELECT COUNT(*) FROM parental_profiles")
    fun getProfileCount(): Flow<Int>
}
