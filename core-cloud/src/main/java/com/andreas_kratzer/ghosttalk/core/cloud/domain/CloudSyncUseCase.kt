package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import com.google.api.services.drive.model.File as DriveFile

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
    private val bookRepository: com.andreas_kratzer.ghosttalk.core.data.BookRepository,
    private val importExportManager: PageImportExportManager,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger
) {
    private val TAG = "CloudSyncUseCase"
    private val FOLDER_NAME = "GhosTTalk_Sync"

    suspend fun syncBook(
        drive: Drive,
        bookId: String,
        syncMode: SyncMode,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val zipFileName = "book_$bookId.zip"
        val jsonFileName = "book_$bookId.json"
        
        logger.d(TAG, "Syncing book $bookId (mode: $syncMode)")
                
        
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

        val remoteZipFile: DriveFile? = try {
            val driveFiles = helper.listFiles(folderId)
            driveFiles.find { it.name == zipFileName }
        } catch (e: Exception) {
            logger.e(TAG, "Exception during remote ZIP file location", e)
            null
        }

        val remoteJsonFile: DriveFile? = if (remoteZipFile == null) try {
            val driveFiles = helper.listFiles(folderId)
            driveFiles.find { it.name == jsonFileName }
        } catch (e: Exception) {
            logger.e(TAG, "Exception during remote JSON file location", e)
            null
        } else null
        
        val remoteFile = remoteZipFile ?: remoteJsonFile
        val currentFileName = remoteZipFile?.name ?: zipFileName // Use ZIP for new uploads
        val mimeType = if (remoteZipFile != null || remoteJsonFile == null) "application/zip" else "application/json"

        logger.d(TAG, "Remote file found: ${remoteFile != null} (id: ${remoteFile?.id}, name: ${remoteFile?.name})")

        // Local Export (only needed for comparison or upload)
        logger.d(TAG, "Step 3: Exporting local book data...")
        val tempFile = File(context.cacheDir, currentFileName)
        
        if (remoteFile == null || syncMode != SyncMode.RESTORE_ONLY) {
             if (mimeType == "application/zip") {
                tempFile.outputStream().use { os ->
                    importExportManager.exportBookToZip(bookId, os) { p, s -> 
                        onProgress(p * 0.3f, s) // ZIP export is 0-30%
                    }
                }
            } else {
                val localJson = importExportManager.exportBookToJson(bookId)
                tempFile.writeText(localJson)
            }
        }
        val localLastModified = book.updatedAt // Use database timestamp, not file system
        logger.d(TAG, "Local updatedAt: $localLastModified")

        var success: Boolean

        if (remoteFile == null) {
            logger.d(TAG, "Step 4a: Remote file does not exist.")
            if (syncMode == SyncMode.RESTORE_ONLY) {
                logger.w(TAG, "RESTORE_ONLY mode but no remote file found. Cannot restore.")
                success = false
            } else {
                logger.d(TAG, "Uploading for the first time...")
                val newFileId = helper.uploadFile(folderId, tempFile, mimeType, book.name) { p ->
                    onProgress(0.3f + p * 0.7f, "Uploading to Drive...") // Upload is 30-100%
                }
                if (newFileId != null) {
                    syncLogProvider.addLogEntry("Erster Upload in die Cloud (ZIP)", bookId, book.name, isError = false)
                    val metadata = helper.getFileMetadata(newFileId)
                    val driveTime = metadata?.modifiedTime?.value
                    if (driveTime != null) {
                        bookRepository.updateLastModified(bookId, driveTime)
                    }
                    success = true
                } else {
                    syncLogProvider.addLogEntry("Upload fehlgeschlagen", bookId, book.name, isError = true)
                    success = false
                }
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
                    logger.d(TAG, "BACKUP_ONLY mode. Overwriting/Migrating to remote ZIP...")
                    success = if (remoteZipFile != null) {
                        helper.updateFile(remoteZipFile.id, tempFile, "application/zip", book.name) { p ->
                            onProgress(0.3f + p * 0.7f, "Uploading to Drive...")
                        }
                    } else {
                        helper.uploadFile(folderId, tempFile, "application/zip", book.name) { p ->
                            onProgress(0.3f + p * 0.7f, "Uploading to Drive...")
                        } != null
                    }
                    logger.d(TAG, "Update/Migration result: $success")
                }
                SyncMode.RESTORE_ONLY -> {
                    logger.d(TAG, "RESTORE_ONLY mode. Downloading and importing...")
                    success = downloadAndImport(helper, remoteFile.id, remoteFile.name, book, remoteLastModified, onProgress)
                }
                SyncMode.TWO_WAY -> {
                    if (localLastModified > remoteLastModified + 2000) { // 2s Grace period
                        logger.d(TAG, "Local version is newer. Updating remote file...")
                        val updateSuccess = if (remoteZipFile != null) {
                            helper.updateFile(remoteZipFile.id, tempFile, "application/zip", book.name) { p ->
                                onProgress(0.3f + p * 0.7f, "Uploading to Drive...")
                            }
                        } else {
                            // Migrate JSON to ZIP
                            helper.uploadFile(folderId, tempFile, "application/zip", book.name) { p ->
                                onProgress(0.3f + p * 0.7f, "Uploading to Drive...")
                            } != null
                        }
                        if (updateSuccess) {
                            syncLogProvider.addLogEntry("Lokale Version war neuer -> Cloud aktualisiert (ZIP)", bookId, book.name)
                            val metadata = helper.getFileMetadata(remoteFile.id)
                            val driveTime = metadata?.modifiedTime?.value ?: 0L
                            if (driveTime > 0L) {
                                bookRepository.updateLastModified(bookId, driveTime)
                            }
                        } else {
                            syncLogProvider.addLogEntry("Update der Cloud-Datei fehlgeschlagen", bookId, book.name, isError = true)
                        }
                        success = updateSuccess
                    } else if (remoteLastModified > localLastModified + 2000) {
                        logger.d(TAG, "Remote version is newer. Downloading and importing...")
                        success = downloadAndImport(helper, remoteFile.id, remoteFile.name, book, remoteLastModified, onProgress)
                    } else {
                        logger.d(TAG, "Local and remote versions are synchronized.")
                        syncLogProvider.addLogEntry("Lokal und Cloud sind synchron", bookId, book.name)
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
                val bookName = file.description ?: if (file.name.endsWith(".json")) {
                    logger.d(TAG, "Processing metadata for legacy JSON file: ${file.name}")
                    val downloadFile = File(context.cacheDir, "metadata_${file.name}")
                    if (helper.downloadFile(file.id, downloadFile)) {
                        val json = downloadFile.readText()
                        val name = importExportManager.extractBookNameFromJson(json) ?: "Unbenanntes Buch"
                        downloadFile.delete()
                        name
                    } else null
                } else {
                    file.name // Fallback to filename for ZIPs without description
                }
                
                if (bookName != null) {
                    RemoteBackupInfo(
                        fileId = file.id,
                        fileName = file.name,
                        bookName = bookName,
                        lastModified = file.modifiedTime?.value ?: 0L
                    )
                } else null
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
        book: com.andreas_kratzer.ghosttalk.core.model.Book,
        remoteLastModified: Long,
        onProgress: (Float, String) -> Unit
    ): Boolean {
        logger.d(TAG, "downloadAndImport: Starting for $remoteFileId ($fileName)")
        val downloadFile = File(context.cacheDir, "download_$fileName")
        return if (helper.downloadFile(remoteFileId, downloadFile) { p -> 
            onProgress(p * 0.7f, "Downloading from Drive...") // Download is 0-70%
        }) {
            logger.d(TAG, "Download successful. File size: ${downloadFile.length()}.")
            
            val result = if (fileName.endsWith(".zip")) {
                downloadFile.inputStream().use { inputStream ->
                    importExportManager.importFromZip(
                        inputStream = inputStream,
                        bookId = book.id,
                        regenerateIds = false,
                        restoreSyncSettings = false
                    ) { p, s -> 
                        onProgress(0.7f + p * 0.3f, s) // ZIP import is 70-100%
                    }
                }
            } else {
                val remoteJson = try {
                    downloadFile.readText()
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to read downloaded JSON file", e)
                    return false
                }
                importExportManager.importFromJson(remoteJson, book.id, restoreSyncSettings = false)
            }
            
            downloadFile.delete()

            if (result.isSuccess) {
                logger.d(TAG, "Import successful. Updating local timestamp to $remoteLastModified")
                syncLogProvider.addLogEntry("Cloud-Version war neuer -> Lokal aktualisiert (${if (fileName.endsWith(".zip")) "ZIP" else "JSON"})", book.id, book.name)
                bookRepository.updateLastModified(book.id, remoteLastModified)
                true
            } else {
                logger.e(TAG, "Import failed: ${result.exceptionOrNull()?.message}")
                syncLogProvider.addLogEntry("Import der Cloud-Datei fehlgeschlagen: ${result.exceptionOrNull()?.message}", book.id, book.name, isError = true)
                false
            }
        } else {
            logger.e(TAG, "Failed to download remote file.")
            false
        }
    }
    
    suspend fun importCloudBackup(
        drive: Drive, 
        fileId: String, 
        fileName: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        logger.d(TAG, "importCloudBackup: Starting for $fileName (ID: $fileId)")
        val helper = DriveServiceHelper(drive)
        val tempFile = File(context.cacheDir, "import_cloud_$fileId${if (fileName.endsWith(".zip")) ".zip" else ".json"}")
        
        try {
            if (helper.downloadFile(fileId, tempFile) { p -> 
                onProgress(p * 0.7f, "Downloading from Drive...")
            }) {
                val result: Result<String> = if (fileName.endsWith(".zip")) {
                    tempFile.inputStream().use { inputStream ->
                        importExportManager.importCloudBackupFromZip(
                            inputStream = inputStream,
                            cloudFileId = fileId
                        ) { p, s -> 
                            onProgress(0.7f + p * 0.3f, s)
                        }
                    }
                } else {
                    val json = tempFile.readText()
                    importExportManager.importCloudBackup(json, fileId)
                }
                tempFile.delete()
                result
            } else {
                logger.e(TAG, "Failed to download remote file $fileId")
                Result.failure<String>(Exception("Download der Cloud-Datei fehlgeschlagen."))
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error in importCloudBackup", e)
            tempFile.delete()
            Result.failure<String>(e)
        }
    }
}
