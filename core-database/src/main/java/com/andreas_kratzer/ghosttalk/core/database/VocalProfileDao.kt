package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VocalProfileDao {
    @Query("SELECT * FROM vocal_profiles")
    fun getAllProfilesFlow(): Flow<List<VocalProfileEntity>>

    @Query("SELECT * FROM vocal_profiles WHERE isActive = 1")
    fun getActiveProfilesFlow(): Flow<List<VocalProfileEntity>>

    @Query("SELECT * FROM vocal_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): VocalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: VocalProfileEntity)

    @Update
    suspend fun updateProfile(profile: VocalProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: VocalProfileEntity)

    @Query("DELETE FROM vocal_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)

    @Query("DELETE FROM vocal_profiles")
    suspend fun clearAllProfiles()
}

