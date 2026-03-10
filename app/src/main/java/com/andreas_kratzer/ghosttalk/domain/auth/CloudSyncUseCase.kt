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
    private val bookRepository: com.andreas_kratzer.ghosttalk.data.BookRepository,
    private val importExportManager: PageImportExportManager,
    private val logger: Logger
) {
    private val TAG = "CloudSyncUseCase"
    private val FOLDER_NAME = "GhosTTalk_Sync"

    suspend fun syncBook(
        drive: Drive,
        bookId: String,
        syncMode: SyncMode
    ): Boolean = withContext(Dispatchers.IO) {
        val fileName = "book_$bookId.json"
        logger.d(TAG, "Syncing book $bookId (mode: $syncMode, file: $fileName)")
                
        
        val book = bookRepository.getBookById(bookId)
        if (book == null) {
            logger.e(TAG, "Book $bookId not found in database. Sync aborted.")
            return@withContext false
        }

        // Automatic Sync is handled by CloudSyncWorker. 
        // Manual sync (and the worker if enabled) should always be able to proceed in this UseCase.

        val helper = DriveServiceHelper(drive)
        
        logger.d(TAG, "Step 1: Finding or creating folder '$FOLDER_NAME'...")
        var folderId = try {
            helper.findFolder(FOLDER_NAME)
        } catch (e: Exception) {
            logger.e(TAG, "Exception during findFolder", e)
            null
        }
        
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

        val remoteFile = try {
            val driveFiles = helper.listFiles(folderId)
            driveFiles.find { it.name == fileName }
        } catch (e: Exception) {
            logger.e(TAG, "Exception during remote file location", e)
            null
        }
        
        logger.d(TAG, "Remote file found: ${remoteFile != null} (id: ${remoteFile?.id}, name: ${remoteFile?.name})")

        // Local Export (only needed for comparison or upload)
        logger.d(TAG, "Step 3: Exporting local book data for comparison/upload...")
        val localJson = importExportManager.exportBookToJson(bookId)
        val tempFile = File(context.cacheDir, fileName).apply {
            writeText(localJson)
        }
        val localLastModified = book.updatedAt // Use database timestamp, not file system
        logger.d(TAG, "Local updatedAt: $localLastModified")

        var success = true

        if (remoteFile == null) {
            logger.d(TAG, "Step 4a: Remote file does not exist.")
            if (syncMode == SyncMode.RESTORE_ONLY) {
                logger.w(TAG, "RESTORE_ONLY mode but no remote file found. Cannot restore.")
                success = false
            } else {
                logger.d(TAG, "Uploading for the first time...")
                val newFileId = helper.uploadFile(folderId, tempFile, "application/json")
                logger.d(TAG, "Upload result id: $newFileId")
                success = newFileId != null
            }
        }
 else {
            logger.d(TAG, "Step 4b: Remote file exists. Comparing timestamps...")
            val remoteLastModified = try {
                remoteFile.modifiedTime?.value ?: 0L
            } catch (e: Exception) {
                logger.e(TAG, "Error accessing remote modifiedTime", e)
                0L
            }
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
                        logger.d(TAG, "Local version is newer. Updating remote file...")
                        success = helper.updateFile(remoteFile.id, tempFile, "application/json")
                    } else if (remoteLastModified > localLastModified + 2000) {
                        logger.d(TAG, "Remote version is newer. Downloading and importing...")
                        success = downloadAndImport(helper, remoteFile.id, fileName, bookId, remoteLastModified, tempFile)
                    } else {
                        logger.d(TAG, "Local and remote versions are synchronized.")
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
        logger.d(TAG, "Fetching available backups...")
        val helper = DriveServiceHelper(drive)
        val folderId = helper.findFolder(FOLDER_NAME) ?: return@withContext emptyList()
        
        val files = helper.listFiles(folderId)
        logger.d(TAG, "Found ${files.size} total files in sync folder.")
        
        files.mapNotNull { file ->
            try {
                logger.d(TAG, "Processing metadata for file: ${file.name} (${file.id})")
                val downloadFile = File(context.cacheDir, "metadata_${file.name}")
                if (helper.downloadFile(file.id, downloadFile)) {
                    val json = downloadFile.readText()
                    val bookName = importExportManager.extractBookNameFromJson(json) ?: "Unbenanntes Buch"
                    downloadFile.delete()
                    RemoteBackupInfo(
                        fileId = file.id,
                        fileName = file.name,
                        bookName = bookName,
                        lastModified = file.modifiedTime?.value ?: 0L
                    )
                } else {
                    logger.w(TAG, "Failed to download metadata for file ${file.id}")
                    null
                }
            } catch (e: Exception) {
                logger.e(TAG, "Error processing backup info for file ${file.id}", e)
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
        logger.d(TAG, "downloadAndImport: Starting for $remoteFileId")
        val downloadFile = File(context.cacheDir, "download_$fileName")
        return if (helper.downloadFile(remoteFileId, downloadFile)) {
            logger.d(TAG, "Download successful. File size: ${downloadFile.length()}. Importing JSON...")
            val remoteJson = try {
                downloadFile.readText()
            } catch (e: Exception) {
                logger.e(TAG, "Failed to read downloaded JSON file", e)
                return false
            }
            
            logger.d(TAG, "JSON content read (length: ${remoteJson.length}). Calling importExportManager...")
            val result = importExportManager.importFromJson(remoteJson, bookId, restoreSyncSettings = false)
            
            if (result.isSuccess) {
                logger.d(TAG, "Import successful. Updating local timestamp to $remoteLastModified")
                tempFile.setLastModified(remoteLastModified)
                true
            } else {
                logger.e(TAG, "Import failed: ${result.exceptionOrNull()?.message}")
                false
            }
        } else {
            logger.e(TAG, "Failed to download remote file.")
            false
        }
    }
    
    suspend fun importCloudBackup(drive: Drive, fileId: String, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        logger.d(TAG, "importCloudBackup: Starting for $fileName (ID: $fileId)")
        val helper = DriveServiceHelper(drive)
        val tempFile = File(context.cacheDir, "import_cloud_$fileId.json")
        
        try {
            if (helper.downloadFile(fileId, tempFile)) {
                val json = tempFile.readText()
                val result = importExportManager.importCloudBackup(json, fileName)
                tempFile.delete()
                result
            } else {
                logger.e(TAG, "Failed to download remote file $fileId")
                Result.failure(Exception("Download der Cloud-Datei fehlgeschlagen."))
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error in importCloudBackup", e)
            tempFile.delete()
            Result.failure(e)
        }
    }
}
