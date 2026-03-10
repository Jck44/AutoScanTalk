package com.andreas_kratzer.ghosttalk.domain.auth

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.pages.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class SyncMode {
    TWO_WAY,
    BACKUP_ONLY,
    RESTORE_ONLY
}

data class RemoteBackupInfo(
    val fileId: String,
    val fileName: String,
    val bookName: String,
    val lastModified: Long
)

class CloudSyncUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val importExportManager: PageImportExportManager,
    private val logger: Logger
) {
    private val TAG = "CloudSyncUseCase"
    private val FOLDER_NAME = "GhosTTalk_Sync"

    suspend fun syncBook(
        drive: Drive,
        bookId: String,
        syncMode: SyncMode = SyncMode.TWO_WAY,
        fileIdOverride: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        logger.d(TAG, "Starting sync for book: $bookId with mode: $syncMode (override: $fileIdOverride)")
        
        // Manual sync (BACKUP_ONLY or RESTORE_ONLY) is allowed even if Auto-Sync is disabled
        val isManual = syncMode == SyncMode.BACKUP_ONLY || syncMode == SyncMode.RESTORE_ONLY
        if (!settingsRepository.isCloudSyncEnabled && !isManual) {
            logger.w(TAG, "Cloud sync is disabled in settings and not manual. Skipping.")
            return@withContext false
        }

        val helper = DriveServiceHelper(drive)
        var folderId = helper.findFolder(FOLDER_NAME)
        logger.d(TAG, "findFolder result: $folderId")
        if (folderId == null) {
            logger.d(TAG, "Folder not found, creating folder: $FOLDER_NAME")
            folderId = helper.createFolder(FOLDER_NAME)
            logger.d(TAG, "createFolder result: $folderId")
        }

        if (folderId == null) {
            logger.e(TAG, "Failed to find or create folder. Sync aborted.")
            return@withContext false
        }

        val fileName = "book_$bookId.json"
        
        val remoteFile = if (fileIdOverride != null) {
            // Use override
            val driveFiles = helper.listFiles(folderId)
            driveFiles.find { it.id == fileIdOverride }
        } else {
            logger.d(TAG, "Listing files for folderId: $folderId")
            val driveFiles = helper.listFiles(folderId)
            driveFiles.find { it.name == fileName }
        }
        
        logger.d(TAG, "Remote file found: ${remoteFile != null} (id: ${remoteFile?.id})")

        // Local Export
        logger.d(TAG, "Exporting local book data to JSON")
        val localJson = importExportManager.exportBookToJson(bookId)
        val tempFile = File(context.cacheDir, fileName).apply {
            writeText(localJson)
        }
        val localLastModified = tempFile.lastModified()
        logger.d(TAG, "Local file size: ${tempFile.length()} bytes, lastModified: $localLastModified")

        var success = true

        if (remoteFile == null) {
            // Upload for the first time
            if (syncMode == SyncMode.RESTORE_ONLY) {
                logger.w(TAG, "RESTORE_ONLY mode but no remote file found. Cannot restore.")
                success = false
            } else {
                logger.d(TAG, "No remote file found. Uploading for the first time...")
                val newFileId = helper.uploadFile(folderId, tempFile, "application/json")
                logger.d(TAG, "Upload result id: $newFileId")
                success = newFileId != null
            }
        } else {
            // Compare and act based on SyncMode
            val remoteLastModified = remoteFile.modifiedTime.value // Long from RFC 3339
            logger.d(TAG, "Remote modifiedTime: $remoteLastModified")

            when (syncMode) {
                SyncMode.BACKUP_ONLY -> {
                    logger.d(TAG, "BACKUP_ONLY mode. Overwriting remote file...")
                    success = helper.updateFile(remoteFile.id, tempFile, "application/json")
                    logger.d(TAG, "Update result: $success")
                }
                SyncMode.RESTORE_ONLY -> {
                    logger.d(TAG, "RESTORE_ONLY mode. Downloading and importing...")
                    success = downloadAndImport(helper, remoteFile.id, fileName, bookId, remoteLastModified, tempFile)
                }
                SyncMode.TWO_WAY -> {
                    if (localLastModified > remoteLastModified + 2000) { // 2s Grace period
                        // Local is newer
                        logger.d(TAG, "Local version is newer. Updating remote file...")
                        success = helper.updateFile(remoteFile.id, tempFile, "application/json")
                        logger.d(TAG, "Update result: $success")
                    } else if (remoteLastModified > localLastModified + 2000) {
                        // Remote is newer
                        logger.d(TAG, "Remote version is newer. Downloading and importing...")
                        success = downloadAndImport(helper, remoteFile.id, fileName, bookId, remoteLastModified, tempFile)
                    } else {
                        logger.d(TAG, "Local and remote versions are synchronized (within grace period).")
                        success = true
                    }
                }
            }
        }
        
        tempFile.delete()
        logger.d(TAG, "Sync process finished with status: $success")
        success
    }

    suspend fun getAvailableBackups(drive: Drive): List<RemoteBackupInfo> = withContext(Dispatchers.IO) {
        val helper = DriveServiceHelper(drive)
        val folderId = helper.findFolder(FOLDER_NAME) ?: return@withContext emptyList()
        
        val files = helper.listFiles(folderId)
        files.mapNotNull { file ->
            val downloadFile = File(context.cacheDir, "metadata_${file.name}")
            if (helper.downloadFile(file.id, downloadFile)) {
                val json = downloadFile.readText()
                val bookName = importExportManager.extractBookNameFromJson(json) ?: "Unbenanntes Buch"
                downloadFile.delete()
                RemoteBackupInfo(
                    fileId = file.id,
                    fileName = file.name,
                    bookName = bookName,
                    lastModified = file.modifiedTime.value
                )
            } else {
                null
            }
        }.sortedByDescending { it.lastModified }
    }

    private suspend fun downloadAndImport(
        helper: DriveServiceHelper,
        remoteFileId: String,
        fileName: String,
        bookId: String,
        remoteLastModified: Long,
        tempFile: File
    ): Boolean {
        val downloadFile = File(context.cacheDir, "download_$fileName")
        return if (helper.downloadFile(remoteFileId, downloadFile)) {
            logger.d(TAG, "Download successful. Importing JSON...")
            val remoteJson = downloadFile.readText()
            // Manual restore or sync should NOT override the local sync activation state
            importExportManager.importFromJson(remoteJson, bookId, restoreSyncSettings = false)
            // Update local timestamp to match remote to avoid loop
            tempFile.setLastModified(remoteLastModified)
            logger.d(TAG, "Import completed.")
            true
        } else {
            logger.e(TAG, "Failed to download remote file.")
            false
        }
    }
}
