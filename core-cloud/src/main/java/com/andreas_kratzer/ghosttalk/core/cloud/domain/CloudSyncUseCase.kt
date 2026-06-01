package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger
) {
    private val TAG = "CloudSyncUseCase"
    private val FOLDER_NAME = "GhosTTalk_Sync"
    private val TTS_CACHE_FILE_NAME = "tts_cache.zip"

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
        
        val customFolderId = settingsRepository.googleDriveFolderId
        var folderId: String?
        
        if (customFolderId != null) {
            logger.d(TAG, "Step 1: Using custom folder ID: $customFolderId")
            folderId = customFolderId
        } else {
            logger.d(TAG, "Step 1: Finding or creating default folder '$FOLDER_NAME'...")
            try {
                folderId = helper.findFolder(FOLDER_NAME)
            } catch (e: Exception) {
                logger.e(TAG, "Exception during findFolder. Sync aborted.", e)
                return@withContext false
            }
            
            if (folderId == null) {
                logger.d(TAG, "Folder not found, creating folder: $FOLDER_NAME")
                folderId = try {
                    helper.createFolder(FOLDER_NAME)
                } catch (e: Exception) {
                    logger.e(TAG, "Exception during createFolder", e)
                    null
                }
            }
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

        val remoteLastModified = try {
            remoteFile?.modifiedTime?.value ?: 0L
        } catch (e: Exception) {
            logger.e(TAG, "Error accessing remote modifiedTime", e)
            0L
        }
        logger.d(TAG, "Remote modifiedTime: $remoteLastModified")

        val localLastModified = book.updatedAt // Use database timestamp, not file system
        logger.d(TAG, "Local updatedAt: $localLastModified")

        val bookModeStr = settingsRepository.syncModeBook
        val isBookSyncEnabled = bookModeStr != "OFF"
        val bookMode = try {
            SyncMode.valueOf(bookModeStr)
        } catch (_: Exception) {
            syncMode
        }

        var success = true

        if (isBookSyncEnabled) {
            val needsExport = remoteFile == null || when (bookMode) {
                SyncMode.RESTORE_ONLY -> false
                SyncMode.BACKUP_ONLY -> localLastModified > remoteLastModified + 2000
                SyncMode.TWO_WAY -> localLastModified > remoteLastModified + 2000
            }

            // Local Export (only needed for comparison or upload)
            val tempFile = File(context.cacheDir, currentFileName)
            if (needsExport) {
                logger.d(TAG, "Step 3: Exporting local book data...")
                if (mimeType == "application/zip") {
                    tempFile.outputStream().use { os ->
                        importExportManager.exportBookToZip(bookId, os, includeTtsCache = false) { p, s -> 
                            onProgress(p * 0.3f, s) // ZIP export is 0-30%
                        }
                    }
                } else {
                    val localJson = importExportManager.exportBookToJson(bookId)
                    tempFile.writeText(localJson)
                }
            } else {
                logger.d(TAG, "Step 3: Skipping local export (not needed).")
            }

            if (remoteFile == null) {
                logger.d(TAG, "Step 4a: Remote file does not exist.")
                if (bookMode == SyncMode.RESTORE_ONLY) {
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
            } else {
                logger.d(TAG, "Step 4b: Remote file exists. Comparing timestamps...")
                when (bookMode) {
                    SyncMode.BACKUP_ONLY -> {
                        if (localLastModified <= remoteLastModified + 2000) {
                            logger.d(TAG, "BACKUP_ONLY: Remote version is already up-to-date. Skipping backup.")
                            syncLogProvider.addLogEntry("BACKUP_ONLY: Keine lokalen Änderungen vorhanden", bookId, book.name)
                            success = true
                        } else {
                            if (remoteZipFile != null) {
                                syncLogProvider.addLogEntry("BACKUP_ONLY: Cloud-Sicherung aktualisiert", bookId, book.name)
                            } else {
                                syncLogProvider.addLogEntry("BACKUP_ONLY: Cloud-Sicherung neu erstellt", bookId, book.name)
                            }
                            var uploadedFileId: String?
                            val updateSuccess = if (remoteZipFile != null) {
                                uploadedFileId = remoteZipFile.id
                                helper.updateFile(remoteZipFile.id, tempFile, "application/zip", book.name) { p ->
                                    onProgress(0.3f + p * 0.7f, "Uploading to Drive...")
                                }
                            } else {
                                val newId = helper.uploadFile(folderId, tempFile, "application/zip", book.name) { p ->
                                    onProgress(0.3f + p * 0.7f, "Uploading to Drive...")
                                }
                                uploadedFileId = newId
                                newId != null
                            }
                            logger.d(TAG, "Update/Migration result: $updateSuccess")
                            if (updateSuccess) {
                                if (uploadedFileId != null) {
                                    val metadata = helper.getFileMetadata(uploadedFileId)
                                    val driveTime = metadata?.modifiedTime?.value ?: 0L
                                    if (driveTime > 0L) {
                                        bookRepository.updateLastModified(bookId, driveTime)
                                    }
                                }
                            } else {
                                syncLogProvider.addLogEntry("BACKUP_ONLY: Sicherung fehlgeschlagen", bookId, book.name, isError = true)
                            }
                            success = updateSuccess
                        }
                    }
                    SyncMode.RESTORE_ONLY -> {
                        val inSync = Math.abs(localLastModified - remoteLastModified) <= 2000
                        if (inSync) {
                            logger.d(TAG, "RESTORE_ONLY: Local and remote versions are synchronized. Skipping restore download.")
                            syncLogProvider.addLogEntry("RESTORE_ONLY: Lokal bereits aktuell", bookId, book.name)
                            success = true
                        } else {
                            logger.d(TAG, "RESTORE_ONLY mode. Downloading and importing...")
                            success = downloadAndImport(helper, remoteFile.id, remoteFile.name, book, remoteLastModified, onProgress)
                            if (!success) {
                                syncLogProvider.addLogEntry("RESTORE_ONLY: Wiederherstellung fehlgeschlagen", bookId, book.name, isError = true)
                            }
                        }
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
            
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } else {
            logger.d(TAG, "Book sync mode is OFF. Skipping book synchronization.")
            syncLogProvider.addLogEntry("Buch-Synchronisierung deaktiviert (Aus)", bookId, book.name)
        }
        
        // Sync TTS cache separately after book sync if enabled
        val ttsModeStr = settingsRepository.syncModeTts
        if (success && ttsModeStr != "OFF") {
            val ttsMode = try {
                SyncMode.valueOf(ttsModeStr)
            } catch (_: Exception) {
                SyncMode.TWO_WAY
            }
            try {
                syncTtsCache(drive, folderId, ttsMode)
            } catch (e: Exception) {
                logger.e(TAG, "TTS cache sync failed (non-fatal)", e)
            }
        } else if (ttsModeStr == "OFF") {
            logger.d(TAG, "TTS cache sync mode is OFF. Skipping TTS cache sync.")
        }
        
        logger.d(TAG, "Sync process finished with status: $success")
        success
    }

    suspend fun getAvailableBackups(drive: Drive, folderId: String? = null): List<RemoteBackupInfo> = withContext(Dispatchers.IO) {
        logger.d(TAG, "Fetching available backups (folderId: $folderId)...")
        val helper = DriveServiceHelper(drive)
        val targetFolderId = folderId ?: settingsRepository.googleDriveFolderId ?: helper.findFolder(FOLDER_NAME) ?: return@withContext emptyList()

        val files = helper.listFiles(targetFolderId)
logger.d(TAG, "Found ${files.size} total files in sync folder.")

files.filter { it.name != TTS_CACHE_FILE_NAME }.mapNotNull { file ->
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
        
        if (result.isSuccess) {
            syncLogProvider.addLogEntry("Cloud-Import erfolgreich: $fileName", null, null)
            // Also restore TTS cache from separate file if available
            try {
                restoreTtsCacheIfAvailable(drive)
            } catch (e: Exception) {
                logger.e(TAG, "TTS cache restore after cloud import failed (non-fatal)", e)
            }
        } else {
            syncLogProvider.addLogEntry("Cloud-Import fehlgeschlagen: ${result.exceptionOrNull()?.message}", null, null, isError = true)
        }
        
        result
    } else {
        logger.e(TAG, "Failed to download remote file $fileId")
        syncLogProvider.addLogEntry("Download für Cloud-Import fehlgeschlagen", null, null, isError = true)
        Result.failure<String>(Exception("Download der Cloud-Datei fehlgeschlagen."))
    }
} catch (e: Exception) {
    logger.e(TAG, "Error in importCloudBackup", e)
    syncLogProvider.addLogEntry("Fehler beim Cloud-Import: ${e.message}", null, null, isError = true)
    tempFile.delete()
    Result.failure<String>(e)
}
}

    private suspend fun syncTtsCache(
        drive: Drive,
        folderId: String,
        syncMode: SyncMode
    ) = withContext(Dispatchers.IO) {
        val helper = DriveServiceHelper(drive)
        
        val remoteFile: DriveFile? = try {
            helper.listFiles(folderId).find { it.name == TTS_CACHE_FILE_NAME }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to find TTS cache on Drive", e)
            null
        }
        
        val localLastModified = importExportManager.getTtsCacheLastModified()
        val remoteLastModified = remoteFile?.modifiedTime?.value ?: 0L
        
        val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
        val lastSyncedLocalTime = prefs.getLong("tts_cache_last_synced_local_time", 0L)
        val lastSyncedRemoteTime = prefs.getLong("tts_cache_last_synced_remote_time", 0L)
        
        logger.d(TAG, "TTS cache sync: local=$localLastModified, remote=$remoteLastModified, lastSyncedLocal=$lastSyncedLocalTime, lastSyncedRemote=$lastSyncedRemoteTime")
        
        // No local cache and no remote cache -> nothing to do
        if (localLastModified == 0L && remoteFile == null) {
            logger.d(TAG, "No TTS cache to sync.")
            return@withContext
        }
        
        val hasLocalChanged = localLastModified > lastSyncedLocalTime + 2000 && localLastModified > 0L
        val hasRemoteChanged = remoteFile != null && remoteLastModified > lastSyncedRemoteTime + 2000
        
        val shouldUpload = when (syncMode) {
            SyncMode.RESTORE_ONLY -> false
            SyncMode.BACKUP_ONLY -> hasLocalChanged || remoteFile == null
            SyncMode.TWO_WAY -> hasLocalChanged && !hasRemoteChanged
        }
        
        val shouldDownload = when (syncMode) {
            SyncMode.BACKUP_ONLY -> false
            SyncMode.RESTORE_ONLY -> hasRemoteChanged
            SyncMode.TWO_WAY -> hasRemoteChanged
        }
        
        if (shouldUpload) {
            logger.d(TAG, "Uploading TTS cache...")
            val tempFile = File(context.cacheDir, TTS_CACHE_FILE_NAME)
            try {
                tempFile.outputStream().use { os ->
                    importExportManager.exportTtsCacheToZip(os) { _, _ -> }
                }
                if (tempFile.length() > 0) {
                    val fileId = if (remoteFile != null) {
                        val updateSuccess = helper.updateFile(remoteFile.id, tempFile, "application/zip") { _ -> }
                        if (updateSuccess) remoteFile.id else null
                    } else {
                        helper.uploadFile(folderId, tempFile, "application/zip") { _ -> }
                    }
                    
                    if (fileId != null) {
                        val newMetadata = helper.getFileMetadata(fileId)
                        val newRemoteTime = newMetadata?.modifiedTime?.value ?: 0L
                        prefs.edit {
                            putLong("tts_cache_last_synced_local_time", localLastModified)
                            putLong("tts_cache_last_synced_remote_time", newRemoteTime)
                        }
                        syncLogProvider.addLogEntry("TTS-Cache in die Cloud hochgeladen", null, null)
                    }
                }
            } finally {
                tempFile.delete()
            }
        } else if (shouldDownload) {
            logger.d(TAG, "Downloading TTS cache...")
            val tempFile = File(context.cacheDir, "download_$TTS_CACHE_FILE_NAME")
            try {
                if (helper.downloadFile(remoteFile!!.id, tempFile) { _ -> }) {
                    tempFile.inputStream().use { inputStream ->
                        importExportManager.importTtsCacheFromZip(inputStream) { _, _ -> }
                    }
                    val newLocalLastModified = importExportManager.getTtsCacheLastModified()
                    prefs.edit {
                        putLong("tts_cache_last_synced_local_time", newLocalLastModified)
                        putLong("tts_cache_last_synced_remote_time", remoteLastModified)
                    }
                    syncLogProvider.addLogEntry("TTS-Cache aus der Cloud wiederhergestellt", null, null)
                }
            } finally {
                tempFile.delete()
            }
        } else {
            logger.d(TAG, "TTS cache is in sync.")
            // Make sure the last synced values are aligned if they weren't yet
            if (lastSyncedLocalTime == 0L || lastSyncedRemoteTime == 0L) {
                prefs.edit {
                    putLong("tts_cache_last_synced_local_time", localLastModified)
                    putLong("tts_cache_last_synced_remote_time", remoteLastModified)
                }
            }
        }
    }

    private suspend fun restoreTtsCacheIfAvailable(
        drive: Drive
    ) {
        val helper = DriveServiceHelper(drive)
        val folderId = settingsRepository.googleDriveFolderId ?: helper.findFolder(FOLDER_NAME) ?: return
        val remoteFile = try {
            helper.listFiles(folderId).find { it.name == TTS_CACHE_FILE_NAME }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to find TTS cache for restore", e)
            null
        } ?: return
        
        logger.d(TAG, "Found separate TTS cache on Drive, restoring...")
        val tempFile = File(context.cacheDir, "download_$TTS_CACHE_FILE_NAME")
        try {
            if (helper.downloadFile(remoteFile.id, tempFile) { _ -> }) {
                tempFile.inputStream().use { inputStream ->
                    importExportManager.importTtsCacheFromZip(inputStream) { _, _ -> }
                }
                val newLocalLastModified = importExportManager.getTtsCacheLastModified()
                val remoteLastModified = remoteFile.modifiedTime?.value ?: 0L
                val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
                prefs.edit {
                    putLong("tts_cache_last_synced_local_time", newLocalLastModified)
                    putLong("tts_cache_last_synced_remote_time", remoteLastModified)
                }
                syncLogProvider.addLogEntry("TTS-Cache aus der Cloud wiederhergestellt", null, null)
            }
        } finally {
            tempFile.delete()
        }
    }
}
