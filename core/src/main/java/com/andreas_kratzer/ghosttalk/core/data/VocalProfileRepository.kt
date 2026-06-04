package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.VocalProfile
import kotlinx.coroutines.flow.Flow

interface VocalProfileRepository {
    fun getAllProfilesFlow(): Flow<List<VocalProfile>>
    fun getActiveProfilesFlow(): Flow<List<VocalProfile>>
    suspend fun getProfileById(id: String): VocalProfile?
    suspend fun saveProfile(profile: VocalProfile)
    suspend fun deleteProfile(profile: VocalProfile)
    suspend fun deleteProfileById(id: String)
    suspend fun clearAllProfiles()
}
