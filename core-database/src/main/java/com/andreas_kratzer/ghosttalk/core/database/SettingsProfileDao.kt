package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsProfileDao {
    @Query("SELECT * FROM settings_profiles WHERE isDeleted = 0 ORDER BY CASE WHEN id = 'profile-default' THEN 0 ELSE 1 END ASC, name ASC")
    fun getAllProfilesFlow(): Flow<List<SettingsProfileEntity>>

    @Query("SELECT * FROM settings_profiles WHERE isDeleted = 0 ORDER BY CASE WHEN id = 'profile-default' THEN 0 ELSE 1 END ASC, name ASC")
    suspend fun getAllProfiles(): List<SettingsProfileEntity>

    @Query("SELECT * FROM settings_profiles WHERE id = :id AND isDeleted = 0")
    suspend fun getProfileById(id: String): SettingsProfileEntity?

    @Query("SELECT * FROM settings_profiles WHERE id = :id AND isDeleted = 0")
    fun getProfileByIdFlow(id: String): Flow<SettingsProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: SettingsProfileEntity)

    @Update
    suspend fun updateProfile(profile: SettingsProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: SettingsProfileEntity)

    @Query("UPDATE settings_profiles SET profileVersionSequence = profileVersionSequence + 1, updatedAt = :timestamp WHERE id = :profileId")
    suspend fun incrementVersionSequence(profileId: String, timestamp: Long = System.currentTimeMillis())
}
