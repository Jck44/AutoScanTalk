@file:Suppress("UseKtx", "REDUNDANT_ELVIS", "RedundantInitializer")
package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.net.Uri
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedTombstone
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
    private val bookRepository: com.andreas_kratzer.ghosttalk.core.data.BookRepository,
    private val importExportManager: PageImportExportManager,
    private val settingsRepository: SettingsRepository,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger
) {
    private val TAG = "CloudSyncUseCase"
    private val FOLDER_NAME = "GhosTTalk_Sync"
    private val TTS_CACHE_FILE_NAME = "tts_cache.zip"
    private val syncMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun getStorageProvider(drive: Drive?, folderId: String? = null): SyncStorageProvider {
        if (drive == null && folderId?.startsWith("content://") == true) {
            return DocumentFolderSyncStorageProvider(context, folderId)
        }
        val targetType = settingsRepository.syncTargetType
        if (targetType == "LOCAL_FOLDER_SAF" || drive == null) {
            val uri = settingsRepository.localFolderSafUri ?: folderId
            if (!uri.isNullOrEmpty()) {
                return DocumentFolderSyncStorageProvider(context, uri)
            }
            throw IllegalStateException("SAF mode enabled but no folder URI configured.")
        }
        val actualFolderId = folderId ?: settingsRepository.googleDriveFolderId ?: DriveServiceHelper(
            drive
        ).findFolder(FOLDER_NAME) ?: throw IllegalStateException("No valid folder ID found for Drive API.")
        return DriveApiSyncStorageProvider(drive, actualFolderId)
    }

    suspend fun syncBook(
        drive: Drive?,
        bookId: String,
        syncMode: SyncMode,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            logger.d(TAG, "Sync already in progress. Skipping execution to avoid race conditions.")
            return@withContext false
        }
        try {
            val zipFileName = "book_$bookId.zip"
            val jsonFileName = "book_$bookId.json"
            
            logger.d(TAG, "Syncing book $bookId (mode: $syncMode)")
            
            val book = bookRepository.getBookById(bookId)
            if (book == null) {
                logger.e(TAG, "Book $bookId not found in database. Sync aborted.")
                return@withContext false
            }

        var remoteSyncError: Exception? = null
        var success = false

        try {
            val storageProvider = getStorageProvider(drive)
            val remoteZipFile: RemoteSyncFile? = try {
                storageProvider.listFiles().find { it.name == zipFileName }
            } catch (e: Exception) {
                logger.e(TAG, "Exception during remote ZIP file location", e)
                null
            }

            val remoteJsonFile: RemoteSyncFile? = if (remoteZipFile == null) try {
                storageProvider.listFiles().find { it.name == jsonFileName }
            } catch (e: Exception) {
                logger.e(TAG, "Exception during remote JSON file location", e)
                null
            } else null
            
            val remoteFile = remoteZipFile ?: remoteJsonFile
            val currentFileName = remoteZipFile?.name ?: zipFileName // Use ZIP for new uploads
            val mimeType = if (remoteZipFile != null || remoteJsonFile == null) "application/zip" else "application/json"

            logger.d(TAG, "Remote file found: ${remoteFile != null} (id: ${remoteFile?.id}, name: ${remoteFile?.name})")

            val remoteLastModified = remoteFile?.modifiedTime ?: 0L
            logger.d(TAG, "Remote modifiedTime: $remoteLastModified")

            val localLastModified = book.updatedAt // Use database timestamp, not file system
            logger.d(TAG, "Local updatedAt: $localLastModified")

            // Resolve component-specific sync mode for book
            val bookModeStr = settingsRepository.syncModeBook
            val resolvedBookMode = if (syncMode == SyncMode.TWO_WAY) {
                if (bookModeStr == "OFF") null else {
                    try {
                        SyncMode.valueOf(bookModeStr)
                    } catch (_: Exception) {
                        SyncMode.TWO_WAY
                    }
                }
            } else {
                if (bookModeStr == "OFF") null else syncMode
            }

            val needsExport = if (resolvedBookMode == null) false else (remoteFile == null || when (resolvedBookMode) {
                SyncMode.RESTORE_ONLY -> false
                SyncMode.BACKUP_ONLY -> localLastModified > remoteLastModified + 2000
                SyncMode.TWO_WAY -> localLastModified > remoteLastModified + 2000
            })

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
                
                // Copy to local backup folder on internal storage
                saveToLocalBackupFolder(currentFileName, tempFile)
            } else {
                logger.d(TAG, "Step 3: Skipping local export (not needed).")
            }

            if (resolvedBookMode == null) {
                logger.d(TAG, "Book sync is OFF. Skipping book synchronization.")
                success = true
            } else if (remoteFile == null) {
                logger.d(TAG, "Step 4a: Remote file does not exist.")
                if (resolvedBookMode == SyncMode.RESTORE_ONLY) {
                    logger.w(TAG, "RESTORE_ONLY mode but no remote file found. Cannot restore.")
                    success = false
                } else {
                    logger.d(TAG, "Uploading for the first time...")
                    val newFileId = storageProvider.uploadFile(tempFile, mimeType, book.name) { p ->
                        onProgress(0.3f + p * 0.7f, "Uploading to Drive...") // Upload is 30-100%
                    }
                    if (newFileId != null) {
                        syncLogProvider.addLogEntry("Erster Upload in die Cloud (ZIP)", bookId, book.name, isError = false)
                        val metadata = storageProvider.getFileMetadata(newFileId)
                        val driveTime = metadata?.modifiedTime
                        if (driveTime != null) {
                            bookRepository.updateLastModified(bookId, driveTime)
                        }
                        success = true
                    } else {
                        syncLogProvider.addLogEntry("Upload fehlgeschlagen", bookId, book.name, isError = true)
                        success = false
                        // If SAF write failed, make sure we still have local backup
                        if (tempFile.exists()) {
                            saveToLocalBackupFolder(currentFileName, tempFile)
                        }
                    }
                }
            } else {
                logger.d(TAG, "Step 4b: Remote file exists. Comparing timestamps...")
                when (syncMode) {
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
                            val uploadedFileId: String?
                            val updateSuccess = if (remoteZipFile != null) {
                                uploadedFileId = remoteZipFile.id
                                storageProvider.updateFile(remoteZipFile.id, tempFile, "application/zip", book.name) { p ->
                                    onProgress(0.3f + p * 0.7f, "Uploading to Drive...")
                                }
                            } else {
                                val newId = storageProvider.uploadFile(tempFile, "application/zip", book.name) { p ->
                                    onProgress(0.3f + p * 0.7f, "Uploading to Drive...")
                                }
                                uploadedFileId = newId
                                newId != null
                            }
                            logger.d(TAG, "Update/Migration result: $updateSuccess")
                            if (updateSuccess) {
                                if (uploadedFileId != null) {
                                    val metadata = storageProvider.getFileMetadata(uploadedFileId)
                                    val driveTime = metadata?.modifiedTime ?: 0L
                                    if (driveTime > 0L) {
                                        bookRepository.updateLastModified(bookId, driveTime)
                                    }
                                }
                            } else {
                                syncLogProvider.addLogEntry("BACKUP_ONLY: Sicherung fehlgeschlagen", bookId, book.name, isError = true)
                                if (tempFile.exists()) {
                                    saveToLocalBackupFolder(currentFileName, tempFile)
                                }
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
                            success = downloadAndImport(storageProvider, remoteFile.id, remoteFile.name, book, remoteLastModified, onProgress)
                            if (!success) {
                                syncLogProvider.addLogEntry("RESTORE_ONLY: Wiederherstellung fehlgeschlagen", bookId, book.name, isError = true)
                            }
                        }
                    }
                    SyncMode.TWO_WAY -> {
                        logger.d(TAG, "TWO_WAY sync: Starting granular merge...")
                        val downloadFile = File(context.cacheDir, "download_$currentFileName")
                        val downloadSuccess = storageProvider.downloadFile(remoteFile.id, downloadFile) { _ -> }
                        if (!downloadSuccess) {
                            logger.e(TAG, "Failed to download remote file for merge.")
                            success = false
                        } else {
                            try {
                                if (remoteFile.name.endsWith(".zip")) {
                                    downloadFile.inputStream().use { inputStream ->
                                        java.util.zip.ZipInputStream(inputStream).use { zipIn ->
                                            val audioDir = File(context.filesDir, "audio_recordings")
                                            if (!audioDir.exists()) audioDir.mkdirs()
                                            var entry = zipIn.nextEntry
                                            while (entry != null) {
                                                if (entry.name.startsWith("audio_recordings/")) {
                                                    val fileName = entry.name.substringAfter("audio_recordings/")
                                                    if (fileName.isNotEmpty()) {
                                                        val targetFile = File(audioDir, fileName)
                                                        val shouldExtract = !targetFile.exists() || (entry.time > targetFile.lastModified())
                                                        if (shouldExtract) {
                                                            FileOutputStream(targetFile).use { out -> zipIn.copyTo(out) }
                                                            if (entry.time != -1L) {
                                                                targetFile.setLastModified(entry.time)
                                                            }
                                                        }
                                                    }
                                                }
                                                zipIn.closeEntry()
                                                entry = zipIn.nextEntry
                                            }
                                        }
                                    }
                                }

                                val remoteJson = readJsonFromFile(downloadFile)
                                val localJson = importExportManager.exportBookToJson(bookId)

                                val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
                                val remoteData = jsonParser.decodeFromString<ImportExportData>(remoteJson)
                                val localData = jsonParser.decodeFromString<ImportExportData>(localJson)

                                val mergedData = mergeBooks(localData, remoteData)
                                val mergedJson = jsonParser.encodeToString(mergedData)

                                val localNeedsUpdate = localJson != mergedJson
                                val remoteNeedsUpdate = remoteJson != mergedJson

                                var localUpdateSuccess = true
                                if (localNeedsUpdate) {
                                    logger.d(TAG, "Merged data differs from local database. Writing merged data...")
                                    val importResult = importExportManager.importFromJson(mergedJson, bookId, restoreSyncSettings = false)
                                    localUpdateSuccess = importResult.isSuccess
                                    if (!localUpdateSuccess) {
                                        logger.e(TAG, "Failed to import merged data locally: ${importResult.exceptionOrNull()?.message}")
                                    }
                                }

                                var remoteUpdateSuccess = true
                                var uploadedFileId = remoteZipFile?.id ?: remoteFile.id
                                if (remoteNeedsUpdate && localUpdateSuccess) {
                                    logger.d(TAG, "Merged data differs from remote file. Uploading merged data...")
                                    val mergedTempFile = File(context.cacheDir, "merged_$currentFileName")
                                    mergedTempFile.outputStream().use { os ->
                                        java.util.zip.ZipOutputStream(os).use { zip ->
                                            zip.putNextEntry(java.util.zip.ZipEntry("backup.json"))
                                            zip.write(mergedJson.toByteArray(Charsets.UTF_8))
                                            zip.closeEntry()

                                            val audioDir = File(context.filesDir, "audio_recordings")
                                            if (audioDir.exists() && audioDir.isDirectory) {
                                                audioDir.listFiles()?.filter { it.isFile && it.name.endsWith(".ogg") }?.forEach { file ->
                                                    zip.putNextEntry(java.util.zip.ZipEntry("audio_recordings/${file.name}"))
                                                    file.inputStream().use { input -> input.copyTo(zip) }
                                                    zip.closeEntry()
                                                }
                                            }
                                        }
                                    }
                                     val uploadDir = File(context.cacheDir, "upload_temp")
                                     if (!uploadDir.exists()) uploadDir.mkdirs()
                                     val finalUploadFile = File(uploadDir, currentFileName)
                                     if (finalUploadFile.exists()) finalUploadFile.delete()
                                     mergedTempFile.renameTo(finalUploadFile)

                                     remoteUpdateSuccess = if (remoteZipFile != null) {
                                         storageProvider.updateFile(remoteZipFile.id, finalUploadFile, "application/zip", book.name) { _ -> }
                                     } else {
                                         val newId = storageProvider.uploadFile(finalUploadFile, "application/zip", book.name) { _ -> }
                                         if (newId != null) {
                                             uploadedFileId = newId
                                             true
                                         } else {
                                             false
                                         }
                                     }
                                     finalUploadFile.delete()
                                     uploadDir.delete()
                                 }

                                if (localUpdateSuccess && remoteUpdateSuccess) {
                                    val metadata = storageProvider.getFileMetadata(uploadedFileId)
                                    val driveTime = metadata?.modifiedTime ?: 0L
                                    if (driveTime > 0L) {
                                        bookRepository.updateLastModified(bookId, driveTime)
                                    }
                                    syncLogProvider.addLogEntry("Zwei-Wege-Merge erfolgreich abgeschlossen", bookId, book.name)
                                    success = true
                                } else {
                                    success = false
                                }
                            } catch (e: Exception) {
                                logger.e(TAG, "Error during TWO_WAY merge sync", e)
                                success = false
                            } finally {
                                downloadFile.delete()
                            }
                        }
                    }
                }
            }

            if (tempFile.exists()) {
                tempFile.delete()
            }

            // Sync TTS cache separately after book sync if enabled
            val ttsModeStr = settingsRepository.syncModeTts
            if (success && ttsModeStr != "OFF") {
                val ttsMode = if (syncMode == SyncMode.TWO_WAY) {
                    try {
                        SyncMode.valueOf(ttsModeStr)
                    } catch (_: Exception) {
                        SyncMode.TWO_WAY
                    }
                } else {
                    syncMode
                }
                try {
                    syncTtsCache(storageProvider, ttsMode)
                } catch (e: Exception) {
                    logger.e(TAG, "TTS cache sync failed (non-fatal)", e)
                }
            } else if (ttsModeStr == "OFF") {
                logger.d(TAG, "TTS cache sync mode is OFF. Skipping TTS cache sync.")
            }

            // Sync statistics separately after book sync if enabled
            val statsModeStr = settingsRepository.syncModeStats
            logger.w(TAG, "[STATS-DEBUG] Gate check: success=$success, statsModeStr='$statsModeStr', bookId='$bookId'")
            if (success && statsModeStr != "OFF") {
                val statsMode = if (syncMode == SyncMode.TWO_WAY) {
                    try {
                        SyncMode.valueOf(statsModeStr)
                    } catch (_: Exception) {
                        SyncMode.BACKUP_ONLY
                    }
                } else {
                    syncMode
                }
                logger.w(TAG, "[STATS-DEBUG] Resolved statsMode=$statsMode (from statsModeStr='$statsModeStr', syncMode=$syncMode)")
                try {
                    syncStatistics(storageProvider, statsMode, bookId, book.name)
                } catch (e: Exception) {
                    logger.e(TAG, "Statistics sync failed (non-fatal)", e)
                }
            } else if (statsModeStr == "OFF") {
                logger.d(TAG, "Statistics sync mode is OFF. Skipping statistics sync.")
            } else if (!success) {
                logger.w(TAG, "[STATS-DEBUG] Book sync failed (success=false), skipping statistics sync!")
            }

        } catch (e: Exception) {
            logger.e(TAG, "Sync to remote storage provider failed. Performing local fallback...", e)
            remoteSyncError = e
        }

        // FALLBACK: If remote sync failed (e.g. SAF permission lost, network down),
        // we still export and update the local backup files in internal storage!
        if (remoteSyncError != null) {
            try {
                logger.d(TAG, "Running local fallback backup for book $bookId...")
                val fallbackTemp = File(context.cacheDir, zipFileName)
                fallbackTemp.outputStream().use { os ->
                    importExportManager.exportBookToZip(bookId, os, includeTtsCache = false) { _, _ -> }
                }
                saveToLocalBackupFolder(zipFileName, fallbackTemp)
                if (fallbackTemp.exists()) {
                    fallbackTemp.delete()
                }

                // Fallback for statistics if enabled
                val statsModeStr = settingsRepository.syncModeStats
                if (statsModeStr != "OFF") {
                    val statsFileName = "statistics_$bookId.zip"
                    val statsTemp = File(context.cacheDir, statsFileName)
                    try {
                        statsTemp.outputStream().use { os ->
                            importExportManager.exportStatisticsToZip(bookId, os)
                        }
                        saveToLocalBackupFolder(statsFileName, statsTemp)
                    } catch (ex: Exception) {
                        logger.e(TAG, "Fallback statistics export failed", ex)
                    } finally {
                        if (statsTemp.exists()) statsTemp.delete()
                    }
                }

                // Fallback for TTS cache if enabled
                val ttsModeStr = settingsRepository.syncModeTts
                if (ttsModeStr != "OFF") {
                    val ttsTemp = File(context.cacheDir, TTS_CACHE_FILE_NAME)
                    try {
                        ttsTemp.outputStream().use { os ->
                            importExportManager.exportTtsCacheToZip(os) { _, _ -> }
                        }
                        saveToLocalBackupFolder(TTS_CACHE_FILE_NAME, ttsTemp)
                    } catch (ex: Exception) {
                        logger.e(TAG, "Fallback TTS cache export failed", ex)
                    } finally {
                        if (ttsTemp.exists()) ttsTemp.delete()
                    }
                }

                syncLogProvider.addLogEntry(
                    "Cloud-Sync fehlgeschlagen (${remoteSyncError.message}). Lokales Backup erfolgreich aktualisiert.",
                    bookId,
                    book.name,
                    isError = true
                )
            } catch (fallbackEx: Exception) {
                logger.e(TAG, "Local fallback backup failed as well", fallbackEx)
                syncLogProvider.addLogEntry(
                    "Sync & Backup komplett fehlgeschlagen: ${fallbackEx.message}",
                    bookId,
                    book.name,
                    isError = true
                )
            }
        }

        logger.d(TAG, "Sync process finished with status: ${remoteSyncError == null && success}")
        remoteSyncError == null && success
        } finally {
            syncMutex.unlock()
        }
    }

    suspend fun getAvailableBackups(drive: Drive?, folderId: String? = null): List<RemoteBackupInfo> = withContext(Dispatchers.IO) {
        logger.d(TAG, "Fetching available backups (folderId: $folderId)...")
        val storageProvider = try {
            getStorageProvider(drive, folderId)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to instantiate storage provider for backups list", e)
            return@withContext emptyList()
        }

        val files = storageProvider.listFiles()
        logger.d(TAG, "Found ${files.size} total files in sync folder.")

        files.filter { it.name != TTS_CACHE_FILE_NAME && !it.name.startsWith("statistics_") }.mapNotNull { file ->
            try {
                val bookName = file.description ?: if (file.name.endsWith(".json")) {
                    logger.d(TAG, "Processing metadata for legacy JSON file: ${file.name}")
                    val downloadFile = File(context.cacheDir, "metadata_${file.name}")
                    if (storageProvider.downloadFile(file.id, downloadFile)) {
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
                        lastModified = file.modifiedTime
                    )
                } else null
            } catch (e: Exception) {
                logger.e(TAG, "Error processing backup info for file ${file.id}", e)
                null
            }
        }.sortedByDescending { it.lastModified }
    }

    private suspend fun downloadAndImport(
        storageProvider: SyncStorageProvider,
        remoteFileId: String,
        fileName: String,
        book: com.andreas_kratzer.ghosttalk.core.model.Book,
        remoteLastModified: Long,
        onProgress: (Float, String) -> Unit
    ): Boolean {
        logger.d(TAG, "downloadAndImport: Starting for $remoteFileId ($fileName)")
        val downloadFile = File(context.cacheDir, "download_$fileName")
        return if (storageProvider.downloadFile(remoteFileId, downloadFile) { p -> 
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
                saveToLocalBackupFolder(fileName, downloadFile)
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
        drive: Drive?, 
        fileId: String, 
        fileName: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {
        logger.d(TAG, "importCloudBackup: Starting for $fileName (ID: $fileId)")
        val isSafUri = fileId.startsWith("content://")
        
        val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}${if (fileName.endsWith(".zip")) ".zip" else ".json"}")

        try {
            val downloadSuccess = if (isSafUri) {
                // For SAF URIs, read directly via ContentResolver
                downloadSafFile(fileId, tempFile) { p ->
                    onProgress(p * 0.7f, "Datei wird heruntergeladen...")
                }
            } else {
                val storageProvider = try {
                    getStorageProvider(drive)
                } catch (e: Exception) {
                    return@withContext Result.failure<String>(e)
                }
                storageProvider.downloadFile(fileId, tempFile) { p ->
                    onProgress(p * 0.7f, "Datei wird heruntergeladen...")
                }
            }
            
            if (downloadSuccess) {
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
                    val bookId = result.getOrNull() ?: ""
                    syncLogProvider.addLogEntry("Cloud-Import erfolgreich: $fileName", bookId, null)
                    
                    // Align book's updatedAt with the remote file's modification time
                    // to prevent the sync logic from seeing the imported book as locally modified.
                    if (bookId.isNotEmpty()) {
                        try {
                            if (isSafUri) {
                                val docUri = Uri.parse(fileId)
                                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, docUri)
                                val remoteTime = doc?.lastModified() ?: 0L
                                if (remoteTime > 0L) {
                                    bookRepository.updateLastModified(bookId, remoteTime)
                                    logger.d(TAG, "Set book updatedAt to remote SAF time: $remoteTime")
                                }
                            } else {
                                try {
                                    val sp = getStorageProvider(drive)
                                    val metadata = sp.getFileMetadata(fileId)
                                    if (metadata != null && metadata.modifiedTime > 0L) {
                                        bookRepository.updateLastModified(bookId, metadata.modifiedTime)
                                        logger.d(TAG, "Set book updatedAt to remote Drive time: ${metadata.modifiedTime}")
                                    }
                                } catch (_: Exception) { /* non-fatal */ }
                            }
                        } catch (e: Exception) {
                            logger.e(TAG, "Failed to align book timestamp after import (non-fatal)", e)
                        }
                    }
                    // Also restore TTS cache from separate file if available
                    try {
                        val storageProvider = if (isSafUri) {
                            // Extract tree URI from document URI for folder-level access
                            val treeUri = extractTreeUriFromDocumentUri(fileId)
                            if (treeUri != null) DocumentFolderSyncStorageProvider(context, treeUri) else null
                        } else {
                            try { getStorageProvider(drive) } catch (_: Exception) { null }
                        }
                        if (storageProvider != null) {
                            restoreTtsCacheIfAvailable(storageProvider)
                        }
                    } catch (e: Exception) {
                        logger.e(TAG, "TTS cache restore after cloud import failed (non-fatal)", e)
                    }
                    // Also restore statistics from separate file if available
                    if (bookId.isNotEmpty()) {
                        try {
                            val storageProvider = if (isSafUri) {
                                val treeUri = extractTreeUriFromDocumentUri(fileId)
                                if (treeUri != null) DocumentFolderSyncStorageProvider(context, treeUri) else null
                            } else {
                                try { getStorageProvider(drive) } catch (_: Exception) { null }
                            }
                            if (storageProvider != null) {
                                val bookName = fileName.substringBefore(".zip").substringBefore(".json")
                                restoreStatisticsIfAvailable(storageProvider, bookId, bookName)
                            }
                        } catch (e: Exception) {
                            logger.e(TAG, "Statistics restore after cloud import failed (non-fatal)", e)
                        }
                    }
                } else {
                    syncLogProvider.addLogEntry("Cloud-Import fehlgeschlagen: ${result.exceptionOrNull()?.message}", null, null, isError = true)
                }
                
                result
            } else {
                logger.e(TAG, "Failed to download remote file $fileId")
                syncLogProvider.addLogEntry("Download für Cloud-Import fehlgeschlagen", null, null, isError = true)
                Result.failure<String>(Exception("Download der Datei fehlgeschlagen."))
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error in importCloudBackup", e)
            syncLogProvider.addLogEntry("Fehler beim Cloud-Import: ${e.message}", null, null, isError = true)
            tempFile.delete()
            Result.failure<String>(e)
        }
    }
    
    /**
     * Downloads a file from a SAF content:// URI directly via ContentResolver.
     */
    private fun downloadSafFile(
        documentUri: String, 
        destFile: File, 
        onProgress: (Float) -> Unit
    ): Boolean {
        val uri = Uri.parse(documentUri)
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                destFile.outputStream().use { os ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        // We don't know total size from InputStream, report indeterminate
                    }
                }
            }
            onProgress(1.0f)
            true
        } catch (e: Exception) {
            logger.e(TAG, "Failed to download SAF file: $documentUri", e)
            false
        }
    }
    
    /**
     * Extracts the tree URI from a document URI within a tree.
     * e.g., content://.../tree/treeId/document/docId -> content://.../tree/treeId
     */
    private fun extractTreeUriFromDocumentUri(documentUri: String): String? {
        val uri = Uri.parse(documentUri)
        val path = uri.path ?: return null
        val treeIndex = path.indexOf("/tree/")
        if (treeIndex == -1) return null
        val docIndex = path.indexOf("/document/", treeIndex)
        val treePath = if (docIndex != -1) path.substring(0, docIndex) else path
        return uri.buildUpon().path(treePath).build().toString()
    }

    private suspend fun syncTtsCache(
        storageProvider: SyncStorageProvider,
        syncMode: SyncMode
    ) = withContext(Dispatchers.IO) {
        val remoteFile = try {
            storageProvider.listFiles().find { it.name == TTS_CACHE_FILE_NAME }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to find TTS cache on Drive", e)
            null
        }
        
        val localLastModified = importExportManager.getTtsCacheLastModified()
        val remoteLastModified = remoteFile?.modifiedTime ?: 0L
        
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
                    saveToLocalBackupFolder(TTS_CACHE_FILE_NAME, tempFile)
                    val fileId = if (remoteFile != null) {
                        val updateSuccess = storageProvider.updateFile(remoteFile.id, tempFile, "application/zip", null) { _ -> }
                        if (updateSuccess) remoteFile.id else null
                    } else {
                        storageProvider.uploadFile(tempFile, "application/zip", null) { _ -> }
                    }
                    
                    if (fileId != null) {
                        val newMetadata = storageProvider.getFileMetadata(fileId)
                        val newRemoteTime = newMetadata?.modifiedTime ?: 0L
                        prefs.edit()
                            .putLong("tts_cache_last_synced_local_time", localLastModified)
                            .putLong("tts_cache_last_synced_remote_time", newRemoteTime)
                            .apply()
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
                if (storageProvider.downloadFile(remoteFile!!.id, tempFile) { _ -> }) {
                    tempFile.inputStream().use { inputStream ->
                        importExportManager.importTtsCacheFromZip(inputStream) { _, _ -> }
                    }
                    saveToLocalBackupFolder(TTS_CACHE_FILE_NAME, tempFile)
                    val newLocalLastModified = importExportManager.getTtsCacheLastModified()
                    prefs.edit()
                        .putLong("tts_cache_last_synced_local_time", newLocalLastModified)
                        .putLong("tts_cache_last_synced_remote_time", remoteLastModified)
                        .apply()
                    syncLogProvider.addLogEntry("TTS-Cache aus der Cloud wiederhergestellt", null, null)
                }
            } finally {
                tempFile.delete()
            }
        } else {
            logger.d(TAG, "TTS cache is in sync.")
            // Make sure the last synced values are aligned if they weren't yet
            if (lastSyncedLocalTime == 0L || lastSyncedRemoteTime == 0L) {
                prefs.edit()
                    .putLong("tts_cache_last_synced_local_time", localLastModified)
                    .putLong("tts_cache_last_synced_remote_time", remoteLastModified)
                    .apply()
            }
        }
    }

    private suspend fun restoreTtsCacheIfAvailable(
        storageProvider: SyncStorageProvider
    ) {
        val remoteFile = try {
            storageProvider.listFiles().find { it.name == TTS_CACHE_FILE_NAME }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to find TTS cache for restore", e)
            null
        } ?: return
        
        logger.d(TAG, "Found separate TTS cache on Drive, restoring...")
        val tempFile = File(context.cacheDir, "download_$TTS_CACHE_FILE_NAME")
        try {
            if (storageProvider.downloadFile(remoteFile.id, tempFile) { _ -> }) {
                tempFile.inputStream().use { inputStream ->
                    importExportManager.importTtsCacheFromZip(inputStream) { _, _ -> }
                }
                saveToLocalBackupFolder(TTS_CACHE_FILE_NAME, tempFile)
                val newLocalLastModified = importExportManager.getTtsCacheLastModified()
                val remoteLastModified = remoteFile.modifiedTime
                val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("tts_cache_last_synced_local_time", newLocalLastModified)
                    .putLong("tts_cache_last_synced_remote_time", remoteLastModified)
                    .apply()
                syncLogProvider.addLogEntry("TTS-Cache aus der Cloud wiederhergestellt", null, null)
            }
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun syncStatistics(
        storageProvider: SyncStorageProvider,
        syncMode: SyncMode,
        bookId: String,
        bookName: String
    ) = withContext(Dispatchers.IO) {
        val statsFileName = "statistics_$bookId.zip"
        
        val remoteFile = try {
            storageProvider.listFiles().find { it.name == statsFileName }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to find statistics backup on Drive", e)
            null
        }
        
        val localLastModified = importExportManager.getStatisticsLastModified(bookId)
        val remoteLastModified = remoteFile?.modifiedTime ?: 0L
        
        val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
        val lastSyncedLocalTime = prefs.getLong("stats_last_synced_local_time_$bookId", 0L)
        val lastSyncedRemoteTime = prefs.getLong("stats_last_synced_remote_time_$bookId", 0L)
        
        logger.w(TAG, "[STATS-DEBUG] syncStatistics called: syncMode=$syncMode, bookId='$bookId', statsFileName='$statsFileName'")
        logger.w(TAG, "[STATS-DEBUG] remoteFile found: ${remoteFile != null} (name=${remoteFile?.name}, id=${remoteFile?.id})")
        logger.w(TAG, "[STATS-DEBUG] localLastModified=$localLastModified, remoteLastModified=$remoteLastModified, lastSyncedLocal=$lastSyncedLocalTime, lastSyncedRemote=$lastSyncedRemoteTime")
        
        if (localLastModified == 0L && remoteFile == null) {
            logger.w(TAG, "[STATS-DEBUG] EARLY EXIT: No statistics to sync (localLastModified=0 AND no remote file).")
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
            SyncMode.RESTORE_ONLY -> hasRemoteChanged || localLastModified == 0L
            SyncMode.TWO_WAY -> hasRemoteChanged || localLastModified == 0L
        }
        
        logger.w(TAG, "[STATS-DEBUG] Decision: hasLocalChanged=$hasLocalChanged, hasRemoteChanged=$hasRemoteChanged, shouldUpload=$shouldUpload, shouldDownload=$shouldDownload")
        
        if (shouldUpload) {
            logger.d(TAG, "Uploading statistics...")
            val tempFile = File(context.cacheDir, statsFileName)
            try {
                tempFile.outputStream().use { os ->
                    importExportManager.exportStatisticsToZip(bookId, os)
                }
                if (tempFile.length() > 0) {
                    saveToLocalBackupFolder(statsFileName, tempFile)
                    val fileId = if (remoteFile != null) {
                        val updateSuccess = storageProvider.updateFile(remoteFile.id, tempFile, "application/zip", bookName) { _ -> }
                        if (updateSuccess) remoteFile.id else null
                    } else {
                        storageProvider.uploadFile(tempFile, "application/zip", bookName) { _ -> }
                    }
                    
                    if (fileId != null) {
                        val newMetadata = storageProvider.getFileMetadata(fileId)
                        val newRemoteTime = newMetadata?.modifiedTime ?: 0L
                        prefs.edit()
                            .putLong("stats_last_synced_local_time_$bookId", localLastModified)
                            .putLong("stats_last_synced_remote_time_$bookId", newRemoteTime)
                            .apply()
                        syncLogProvider.addLogEntry("Statistik in die Cloud hochgeladen", bookId, bookName)
                    }
                }
            } finally {
                tempFile.delete()
            }
        } else if (shouldDownload) {
            logger.d(TAG, "Downloading statistics...")
            val tempFile = File(context.cacheDir, "download_$statsFileName")
            try {
                if (storageProvider.downloadFile(remoteFile!!.id, tempFile) { _ -> }) {
                    tempFile.inputStream().use { inputStream ->
                        importExportManager.importStatisticsFromZip(bookId, inputStream)
                    }
                    saveToLocalBackupFolder(statsFileName, tempFile)
                    val newLocalLastModified = importExportManager.getStatisticsLastModified(bookId)
                    prefs.edit()
                        .putLong("stats_last_synced_local_time_$bookId", newLocalLastModified)
                        .putLong("stats_last_synced_remote_time_$bookId", remoteLastModified)
                        .apply()
                    syncLogProvider.addLogEntry("Statistik aus der Cloud wiederhergestellt", bookId, bookName)
                }
            } finally {
                tempFile.delete()
            }
        } else {
            logger.d(TAG, "Statistics are in sync.")
            if (lastSyncedLocalTime == 0L || lastSyncedRemoteTime == 0L) {
                prefs.edit()
                    .putLong("stats_last_synced_local_time_$bookId", localLastModified)
                    .putLong("stats_last_synced_remote_time_$bookId", remoteLastModified)
                    .apply()
            }
        }
    }

    private suspend fun restoreStatisticsIfAvailable(
        storageProvider: SyncStorageProvider,
        bookId: String,
        bookName: String
    ) {
        val statsFileName = "statistics_$bookId.zip"
        val remoteFile = try {
            storageProvider.listFiles().find { it.name == statsFileName }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to find statistics for restore", e)
            null
        } ?: return
        
        logger.d(TAG, "Found separate statistics on Drive, restoring...")
        val tempFile = File(context.cacheDir, "download_$statsFileName")
        try {
            if (storageProvider.downloadFile(remoteFile.id, tempFile) { _ -> }) {
                tempFile.inputStream().use { inputStream ->
                    importExportManager.importStatisticsFromZip(bookId, inputStream)
                }
                saveToLocalBackupFolder(statsFileName, tempFile)
                val newLocalLastModified = importExportManager.getStatisticsLastModified(bookId)
                val remoteLastModified = remoteFile.modifiedTime
                val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
                prefs.edit()
                    .putLong("stats_last_synced_local_time_$bookId", newLocalLastModified)
                    .putLong("stats_last_synced_remote_time_$bookId", remoteLastModified)
                    .apply()
                syncLogProvider.addLogEntry("Statistik aus der Cloud wiederhergestellt", bookId, bookName)
            }
        } finally {
            tempFile.delete()
        }
    }

    suspend fun uploadLogFile(
        drive: Drive?,
        logFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val storageProvider = getStorageProvider(drive)
            val existingFile = storageProvider.listFiles().find { it.name == logFile.name }
            if (existingFile != null) {
                storageProvider.updateFile(existingFile.id, logFile, "text/plain", "App Logcat Extract")
            } else {
                storageProvider.uploadFile(logFile, "text/plain", "App Logcat Extract") != null
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to upload log file to remote storage provider", e)
            false
        }
    }

    private fun readJsonFromFile(file: File): String {
        if (file.name.endsWith(".zip")) {
            java.util.zip.ZipInputStream(file.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json") {
                        return String(zip.readBytes(), Charsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            throw Exception("No backup.json found in ZIP")
        } else {
            return file.readText()
        }
    }

    private fun mergeBooks(local: ImportExportData, remote: ImportExportData): ImportExportData {
        val useLocalMetadata = (local.bookUpdatedAt ?: 0L) >= (remote.bookUpdatedAt ?: 0L)
        val mergedBookName = if (useLocalMetadata) local.bookName else remote.bookName
        val mergedDefaultStartPageId = if (useLocalMetadata) local.defaultStartPageId else remote.defaultStartPageId
        val mergedPageSortOrder = if (useLocalMetadata) local.pageSortOrder else remote.pageSortOrder
        val mergedTemplateSortOrder = if (useLocalMetadata) local.templateSortOrder else remote.templateSortOrder
        val mergedActionLogLimit = if (useLocalMetadata) local.actionLogLimit else remote.actionLogLimit
        val mergedLimitScanCycles = if (useLocalMetadata) local.limitScanCycles else remote.limitScanCycles
        val mergedScanCycleLimit = if (useLocalMetadata) local.scanCycleLimit else remote.scanCycleLimit
        val mergedLogIgnoredActions = if (useLocalMetadata) local.logIgnoredActions else remote.logIgnoredActions
        val mergedLogStopActions = if (useLocalMetadata) local.logStopActions else remote.logStopActions

        val localPagesMap = local.pages.associateBy { it.importId }
        val remotePagesMap = remote.pages.associateBy { it.importId }
        
        val localTombstones = local.deletedEntities?.associateBy { it.entityId } ?: emptyMap()
        val remoteTombstones = remote.deletedEntities?.associateBy { it.entityId } ?: emptyMap()

        val allPageIds = localPagesMap.keys + remotePagesMap.keys
        val mergedPages = allPageIds.mapNotNull { pageId ->
            val localPage = localPagesMap[pageId]
            val remotePage = remotePagesMap[pageId]
            
            if (localPage == null && remotePage != null) {
                val tombstone = localTombstones[pageId]
                if (tombstone != null) {
                    val remoteTime = remotePage.updatedAt ?: remotePage.createdAt ?: 0L
                    if (tombstone.deletedAt >= remoteTime) null else remotePage
                } else {
                    remotePage
                }
            } else if (remotePage == null && localPage != null) {
                val tombstone = remoteTombstones[pageId]
                if (tombstone != null) {
                    val localTime = localPage.updatedAt ?: localPage.createdAt ?: 0L
                    if (tombstone.deletedAt >= localTime) null else localPage
                } else {
                    localPage
                }
            } else if (localPage != null && remotePage != null) {
                val localPageTime = localPage.updatedAt ?: localPage.createdAt ?: 0L
                val remotePageTime = remotePage.updatedAt ?: remotePage.createdAt ?: 0L
                val useLocalPage = localPageTime >= remotePageTime

                val name = if (useLocalPage) localPage.name else remotePage.name
                val rows = if (useLocalPage) localPage.rows else remotePage.rows
                val columns = if (useLocalPage) localPage.columns else remotePage.columns
                val templateId = if (useLocalPage) localPage.templateId else remotePage.templateId
                val scanPattern = if (useLocalPage) localPage.scanPattern else remotePage.scanPattern
                val rowNames = if (useLocalPage) localPage.rowNames else remotePage.rowNames
                val orderIndex = if (useLocalPage) localPage.orderIndex else remotePage.orderIndex
                val createdAt = if (useLocalPage) localPage.createdAt else remotePage.createdAt
                val updatedAt = maxOf(localPageTime, remotePageTime)

                val localButtonsMap = localPage.buttons.associateBy { it.index }
                val remoteButtonsMap = remotePage.buttons.associateBy { it.index }
                val allButtonIndices = localButtonsMap.keys + remoteButtonsMap.keys
                val mergedButtons = allButtonIndices.mapNotNull { index ->
                    val localButton = localButtonsMap[index]
                    val remoteButton = remoteButtonsMap[index]
                    
                    if (localButton == null && remoteButton != null) {
                        val remoteTime = remoteButton.updatedAt ?: 0L
                        if (localPageTime >= remoteTime) null else remoteButton
                    } else if (remoteButton == null && localButton != null) {
                        val localTime = localButton.updatedAt ?: 0L
                        if (remotePageTime >= localTime) null else localButton
                    } else if (localButton != null && remoteButton != null) {
                        val localButtonTime = localButton.updatedAt ?: 0L
                        val remoteButtonTime = remoteButton.updatedAt ?: 0L
                        if (localButtonTime >= remoteButtonTime) localButton else remoteButton
                    } else {
                        null
                    }
                }

                ImportPage(
                    importId = pageId,
                    name = name,
                    rows = rows,
                    columns = columns,
                    templateId = templateId,
                    scanPattern = scanPattern,
                    rowNames = rowNames,
                    orderIndex = orderIndex,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    buttons = mergedButtons
                )
            } else {
                null
            }
        }

        val localTemplatesMap = (local.buttonTemplates ?: emptyList()).associateBy { it.id }
        val remoteTemplatesMap = (remote.buttonTemplates ?: emptyList()).associateBy { it.id }
        val allTemplateIds = localTemplatesMap.keys + remoteTemplatesMap.keys
        val mergedButtonTemplates = allTemplateIds.map { id ->
            val localT = localTemplatesMap[id]
            val remoteT = remoteTemplatesMap[id]
            if (localT == null) {
                remoteT!!
            } else if (remoteT == null) {
                localT
            } else {
                val localTime = localT.button?.updatedAt ?: 0L
                val remoteTime = remoteT.button?.updatedAt ?: 0L
                if (localTime >= remoteTime) localT else remoteT
            }
        }

        val mergedTombstones = if (local.deletedEntities == null && remote.deletedEntities == null) {
            null
        } else {
            val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000 // 90 days
            (local.deletedEntities.orEmpty() + remote.deletedEntities.orEmpty())
                .associateBy { it.entityId }
                .filterKeys { pageId -> mergedPages.none { it.importId == pageId } }
                .values
                .filter { it.deletedAt >= cutoff }
                .toList()
        }

        return local.copy(
            bookName = mergedBookName,
            defaultStartPageId = mergedDefaultStartPageId,
            pageSortOrder = mergedPageSortOrder,
            templateSortOrder = mergedTemplateSortOrder,
            actionLogLimit = mergedActionLogLimit,
            limitScanCycles = mergedLimitScanCycles,
            scanCycleLimit = mergedScanCycleLimit,
            logIgnoredActions = mergedLogIgnoredActions,
            logStopActions = mergedLogStopActions,
            bookUpdatedAt = maxOf(local.bookUpdatedAt ?: 0L, remote.bookUpdatedAt ?: 0L),
            pages = mergedPages,
            buttonTemplates = mergedButtonTemplates,
            deletedEntities = mergedTombstones
        )
    }

    private fun saveToLocalBackupFolder(fileName: String, tempFile: File) {
        try {
            val backupDir = File(context.filesDir, "local_backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            val destFile = File(backupDir, fileName)
            tempFile.copyTo(destFile, overwrite = true)
            logger.d(TAG, "Saved local backup copy to $destFile")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to save local backup copy for $fileName", e)
        }
    }
}
