package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.VocalProfileRepository
import com.andreas_kratzer.ghosttalk.core.database.VocalProfileDao
import com.andreas_kratzer.ghosttalk.core.database.toEntity
import com.andreas_kratzer.ghosttalk.core.model.VocalProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VocalProfileRepositoryImpl @Inject constructor(
    private val vocalProfileDao: VocalProfileDao
) : VocalProfileRepository {

    override fun getAllProfilesFlow(): Flow<List<VocalProfile>> {
        return vocalProfileDao.getAllProfilesFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getActiveProfilesFlow(): Flow<List<VocalProfile>> {
        return vocalProfileDao.getActiveProfilesFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getProfileById(id: String): VocalProfile? {
        return vocalProfileDao.getProfileById(id)?.toDomain()
    }

    override suspend fun saveProfile(profile: VocalProfile) {
        vocalProfileDao.insertProfile(profile.toEntity())
    }

    override suspend fun deleteProfile(profile: VocalProfile) {
        vocalProfileDao.deleteProfile(profile.toEntity())
    }

    override suspend fun deleteProfileById(id: String) {
        vocalProfileDao.deleteProfileById(id)
    }

    override suspend fun clearAllProfiles() {
        vocalProfileDao.clearAllProfiles()
    }
}
