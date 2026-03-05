package com.andreas_kratzer.ghosttalk.domain.auth

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.pages.PageImportExportManager
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

import com.andreas_kratzer.ghosttalk.core.util.Logger

import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

enum class SyncMode {
    TWO_WAY,
    BACKUP_ONLY,
    RESTORE_ONLY
}

class CloudSyncUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val importExportManager: PageImportExportManager,
    private val logger: Logger
) {
    private val TAG = "CloudSyncUseCase"
    private val FOLDER_NAME = "GhosTTalk_Sync"

    suspend fun syncBook(drive: Drive, bookId: String, syncMode: SyncMode = SyncMode.TWO_WAY) = withContext(Dispatchers.IO) {
        logger.d(TAG, "Starting sync for book: $bookId with mode: $syncMode")
        if (!settingsRepository.isCloudSyncEnabled) {
            logger.w(TAG, "Cloud sync is disabled in settings. Skipping.")
            return@withContext
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
            return@withContext
        }

        val fileName = "book_$bookId.json"
        logger.d(TAG, "Listing files for folderId: $folderId")
        val driveFiles = helper.listFiles(folderId)
        val remoteFile = driveFiles.find { it.name == fileName }
        logger.d(TAG, "Remote file found: ${remoteFile != null} (id: ${remoteFile?.id})")

        // Local Export
        logger.d(TAG, "Exporting local book data to JSON")
        val localJson = importExportManager.exportBookToJson(bookId)
        val tempFile = File(context.cacheDir, fileName).apply {
            writeText(localJson)
        }
        val localLastModified = tempFile.lastModified()
        logger.d(TAG, "Local file size: ${tempFile.length()} bytes, lastModified: $localLastModified")

        if (remoteFile == null) {
            // Upload for the first time
            if (syncMode == SyncMode.RESTORE_ONLY) {
                logger.w(TAG, "RESTORE_ONLY mode but no remote file found. Cannot restore.")
            } else {
                logger.d(TAG, "No remote file found. Uploading for the first time...")
                val newFileId = helper.uploadFile(folderId, tempFile, "application/json")
                logger.d(TAG, "Upload result id: $newFileId")
            }
        } else {
            // Compare and act based on SyncMode
            val remoteLastModified = remoteFile.modifiedTime.value // Long from RFC 3339
            logger.d(TAG, "Remote modifiedTime: $remoteLastModified")

            when (syncMode) {
                SyncMode.BACKUP_ONLY -> {
                    logger.d(TAG, "BACKUP_ONLY mode. Overwriting remote file...")
                    val success = helper.updateFile(remoteFile.id, tempFile, "application/json")
                    logger.d(TAG, "Update result: $success")
                }
                SyncMode.RESTORE_ONLY -> {
                    logger.d(TAG, "RESTORE_ONLY mode. Downloading and importing...")
                    downloadAndImport(helper, remoteFile.id, fileName, bookId, remoteLastModified, tempFile)
                }
                SyncMode.TWO_WAY -> {
                    if (localLastModified > remoteLastModified + 2000) { // 2s Grace period
                        // Local is newer
                        logger.d(TAG, "Local version is newer. Updating remote file...")
                        val success = helper.updateFile(remoteFile.id, tempFile, "application/json")
                        logger.d(TAG, "Update result: $success")
                    } else if (remoteLastModified > localLastModified + 2000) {
                        // Remote is newer
                        logger.d(TAG, "Remote version is newer. Downloading and importing...")
                        downloadAndImport(helper, remoteFile.id, fileName, bookId, remoteLastModified, tempFile)
                    } else {
                        logger.d(TAG, "Local and remote versions are synchronized (within grace period).")
                    }
                }
            }
        }
        
        tempFile.delete()
        logger.d(TAG, "Sync process finished.")
    }

    private suspend fun downloadAndImport(
        helper: DriveServiceHelper,
        remoteFileId: String,
        fileName: String,
        bookId: String,
        remoteLastModified: Long,
        tempFile: File
    ) {
        val downloadFile = File(context.cacheDir, "download_$fileName")
        if (helper.downloadFile(remoteFileId, downloadFile)) {
            logger.d(TAG, "Download successful. Importing JSON...")
            val remoteJson = downloadFile.readText()
            importExportManager.importBookFromJson(remoteJson, bookId)
            // Update local timestamp to match remote to avoid loop
            tempFile.setLastModified(remoteLastModified)
            logger.d(TAG, "Import completed.")
        } else {
            logger.e(TAG, "Failed to download remote file.")
        }
    }
}
