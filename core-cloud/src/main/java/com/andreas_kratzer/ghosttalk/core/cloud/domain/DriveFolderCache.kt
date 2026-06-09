package com.andreas_kratzer.ghosttalk.core.cloud.domain

import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive

class DriveFolderCache(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {
    private val TAG = "DriveFolderCache"
    private val FOLDER_NAME = "GhosTTalk_Sync"
    private val FOLDER_TTL_MS = 1 * 60 * 60 * 1000L // 1 hour TTL

    companion object {
        @Volatile
        private var cachedParentFolderId: String? = null
        @Volatile
        private var cachedProfilesFolderId: String? = null
        @Volatile
        private var cachedLogsFolderId: String? = null

        fun clearCache(settingsRepository: SettingsRepository? = null, clearSettings: Boolean = false) {
            cachedParentFolderId = null
            cachedProfilesFolderId = null
            cachedLogsFolderId = null
            if (clearSettings && settingsRepository != null) {
                settingsRepository.googleDriveProfilesFolderId = null
                settingsRepository.googleDriveLogsFolderId = null
                settingsRepository.lastFolderValidationTime = 0L
            }
        }
    }

    fun clear(clearSettings: Boolean = false) {
        clearCache(settingsRepository, clearSettings)
    }

    suspend fun resolveParentFolderId(drive: Drive): String {
        val helper = DriveServiceHelper(drive)
        val memId = cachedParentFolderId
        if (memId != null) return memId

        val cachedFolderId = settingsRepository.googleDriveFolderId
        if (cachedFolderId != null) {
            if (isTtlValid()) {
                logger.d(TAG, "resolveParentFolderId: Using persistent cache for folder ID $cachedFolderId (TTL valid)")
                cachedParentFolderId = cachedFolderId
                return cachedFolderId
            } else {
                try {
                    val meta = helper.getFileMetadata(cachedFolderId)
                    if (meta != null && meta.name == FOLDER_NAME) {
                        cachedParentFolderId = cachedFolderId
                        settingsRepository.lastFolderValidationTime = System.currentTimeMillis()
                        return cachedFolderId
                    } else {
                        logger.w(TAG, "Cached parent folder ID $cachedFolderId is invalid. Invalidating.")
                        clear(clearSettings = true)
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "Error validating parent folder ID $cachedFolderId. Exception: ${e.message}")
                    clear(clearSettings = true)
                }
            }
        }

        val resolved = helper.findFolder(FOLDER_NAME) ?: helper.createFolder(FOLDER_NAME) ?: throw IllegalStateException("No valid folder ID found for Drive API.")
        cachedParentFolderId = resolved
        settingsRepository.googleDriveFolderId = resolved
        settingsRepository.lastFolderValidationTime = System.currentTimeMillis()
        return resolved
    }

    suspend fun resolveProfilesFolderId(drive: Drive): String {
        val memId = cachedProfilesFolderId
        if (memId != null) return memId

        val helper = DriveServiceHelper(drive)
        val cachedId = settingsRepository.googleDriveProfilesFolderId
        if (cachedId != null) {
            if (isTtlValid()) {
                logger.d(TAG, "resolveProfilesFolderId: Using persistent cache for profiles folder ID $cachedId (TTL valid)")
                cachedProfilesFolderId = cachedId
                return cachedId
            } else {
                try {
                    val meta = helper.getFileMetadata(cachedId)
                    if (meta != null && meta.name == "Profiles") {
                        cachedProfilesFolderId = cachedId
                        settingsRepository.lastFolderValidationTime = System.currentTimeMillis()
                        return cachedId
                    } else {
                        logger.w(TAG, "Cached Profiles folder ID $cachedId is invalid. Invalidating.")
                        settingsRepository.googleDriveProfilesFolderId = null
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "Error validating Profiles folder ID $cachedId. Exception: ${e.message}")
                    settingsRepository.googleDriveProfilesFolderId = null
                }
            }
        }

        val parentId = resolveParentFolderId(drive)
        val resolved = helper.findFolder("Profiles", parentId) ?: helper.createFolder("Profiles", parentId) ?: parentId
        cachedProfilesFolderId = resolved
        settingsRepository.googleDriveProfilesFolderId = resolved
        settingsRepository.lastFolderValidationTime = System.currentTimeMillis()
        return resolved
    }

    suspend fun resolveLogsFolderId(drive: Drive): String {
        val memId = cachedLogsFolderId
        if (memId != null) return memId

        val helper = DriveServiceHelper(drive)
        val cachedId = settingsRepository.googleDriveLogsFolderId
        if (cachedId != null) {
            if (isTtlValid()) {
                logger.d(TAG, "resolveLogsFolderId: Using persistent cache for logs folder ID $cachedId (TTL valid)")
                cachedLogsFolderId = cachedId
                return cachedId
            } else {
                try {
                    val meta = helper.getFileMetadata(cachedId)
                    if (meta != null && meta.name == "Logs") {
                        cachedLogsFolderId = cachedId
                        settingsRepository.lastFolderValidationTime = System.currentTimeMillis()
                        return cachedId
                    } else {
                        logger.w(TAG, "Cached Logs folder ID $cachedId is invalid. Invalidating.")
                        settingsRepository.googleDriveLogsFolderId = null
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "Error validating Logs folder ID $cachedId. Exception: ${e.message}")
                    settingsRepository.googleDriveLogsFolderId = null
                }
            }
        }

        val parentId = resolveParentFolderId(drive)
        val resolved = helper.findFolder("Logs", parentId) ?: helper.createFolder("Logs", parentId) ?: parentId
        cachedLogsFolderId = resolved
        settingsRepository.googleDriveLogsFolderId = resolved
        settingsRepository.lastFolderValidationTime = System.currentTimeMillis()
        return resolved
    }

    private fun isTtlValid(): Boolean {
        val now = System.currentTimeMillis()
        val lastValidation = settingsRepository.lastFolderValidationTime
        return (now - lastValidation) in 0..FOLDER_TTL_MS
    }
}
