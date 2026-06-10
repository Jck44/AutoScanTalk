package com.andreas_kratzer.ghosttalk.core.cloud.domain
import android.annotation.SuppressLint
import android.content.Context
import com.andreas_kratzer.ghosttalk.core.cloud.CloudSyncOptimizer
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.cloud.SyncAction
import com.andreas_kratzer.ghosttalk.core.cloud.SyncConcurrencyGuard
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.util.Logger
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
    private val bookRepository: com.andreas_kratzer.ghosttalk.core.data.BookRepository,
    private val importExportManager: PageImportExportManager,
    private val settingsRepository: SettingsRepository,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger,
    private val storageResolver: SyncStorageResolver,
    private val decisionEngine: SyncDecisionEngine,
    private val bookMergeService: BookMergeService,
    private val profileSyncOrchestrator: ProfileSyncOrchestrator,
    private val importCloudBackupUseCase: ImportCloudBackupUseCase
) {
    private val TAG = "CloudSyncUseCase"
    private val TTS_CACHE_FILE_NAME = "tts_cache.zip"

    private val bookMergeEngine = BookMergeEngine(logger)
    private val audioSyncHelper = AudioSyncHelper(context, importExportManager, logger)
    private val ttsSyncHelper = TtsSyncHelper(context, importExportManager, syncLogProvider, logger)
    private val statisticsSyncHelper = StatisticsSyncHelper(context, importExportManager, syncLogProvider, logger)

    private suspend fun getStorageProvider(drive: Drive?, folderId: String? = null): SyncStorageProvider =
        storageResolver.getStorageProvider(drive, folderId)

    suspend fun syncBook(
        drive: Drive?,
        bookId: String,
        syncMode: SyncMode,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = SyncConcurrencyGuard.runExclusive {
        withContext(Dispatchers.IO) {
            logger.d(TAG, "Syncing book $bookId (mode: $syncMode)")

            val book = bookRepository.getBookById(bookId)
            if (book == null) {
                logger.e(TAG, "Book $bookId not found in database. Sync aborted.")
                return@withContext false
            }

            var remoteSyncError: Exception? = null
            var success = false
            var audioSynced = false

            val zipFileName = "book_$bookId.zip"
            val masterFileName = "book_$bookId.json"

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
            val audioSyncMode = resolvedBookMode ?: SyncMode.TWO_WAY

            // Local Export (needed for comparison and backup)
            val tempFile = File(context.cacheDir, masterFileName)
            if (resolvedBookMode != null) {
                try {
                    logger.d(TAG, "Exportiere Buch als reines Klartext-JSON...")
                    val localJson = importExportManager.exportBookToJson(bookId)
                    tempFile.writeText(localJson, Charsets.UTF_8)
                    saveToLocalBackupFolder(masterFileName, tempFile)
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to export local JSON for sync", e)
                    syncLogProvider.addLogEntry("Lokaler JSON-Export fehlgeschlagen", bookId, book.name, isError = true)
                    return@withContext false
                }
            }

            try {
                // --- STAGE 1: Profile Sync (Lifeline) ---
                try {
                    profileSyncOrchestrator.syncProfiles(drive, runExclusive = false)
                } catch (e: Exception) {
                    logger.e(TAG, "[SYNC-STAGE-1-ERROR] Stage 1 Profile sync failed (non-fatal): ${e.message}", e)
                }

                val storageProvider = getStorageProvider(drive)

                val remoteFiles = storageProvider.listFiles()

                // Check isDataCloudSyncEnabled. If disabled, skip Stage 2 book sync.
                if (!settingsRepository.isDataCloudSyncEnabled) {
                    logger.d(TAG, "Cloud sync is disabled in active profile. Skipping Stage 2 book/media sync.")
                    return@withContext true
                }

                if (resolvedBookMode == null) {
                    logger.d(TAG, "Book sync is OFF. Skipping book synchronization.")
                    success = true
                } else {
                    val remoteMasterFile = remoteFiles.find { it.name == masterFileName }
                    val legacyZipFile = remoteFiles.find { it.name == zipFileName }
                    val effectiveMasterFile = remoteMasterFile ?: legacyZipFile
                    val remoteConflictFiles = remoteFiles.filter {
                        it.name.startsWith("merged_") && (it.name.contains(zipFileName) || it.name.contains(masterFileName))
                    }

                    val localSeq = book.versionSequence
                    val localMd5 = CloudSyncOptimizer().calculateMD5(tempFile)
                    val localStructMd5 = calculateStructuralMd5(bookId)
                    val remoteStructMd5 = remoteMasterFile?.properties?.get("structure_md5")
                    val remoteSeqFromProps = remoteMasterFile?.properties?.get("version_sequence")?.toLongOrNull()

                    if ((remoteSeqFromProps != null && localSeq == remoteSeqFromProps) ||
                        (remoteStructMd5 != null && localStructMd5 == remoteStructMd5 && localSeq >= (remoteSeqFromProps ?: 0L))) {
                        com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logSkipped(logger, TAG, masterFileName, "Sequences are identical or structural MD5 matches", "seq $localSeq vs $remoteSeqFromProps, struct MD5 $localStructMd5 vs $remoteStructMd5")
                        if (effectiveMasterFile != null) {
                            bookRepository.updateLastModified(bookId, effectiveMasterFile.modifiedTime, incrementSequence = false)
                        }
                        syncLogProvider.addLogEntry("Inhalte sind identisch (NO_OP)", bookId, book.name)
                        
                        /*
                        val configModeStr = settingsRepository.syncModeSettings
                        if (configModeStr != "OFF") {
                            val configMode = if (syncMode == SyncMode.TWO_WAY) {
                                try { SyncMode.valueOf(configModeStr) } catch (_: Exception) { SyncMode.TWO_WAY }
                            } else syncMode
                            try {
                                configSyncHelper.syncBookConfig(storageProvider, remoteFiles, configMode, bookId, book.name)
                            } catch (e: Exception) {
                                logger.e(TAG, "Config sync failed (non-fatal)", e)
                            }
                        }
                        */

                        try {
                            audioSyncHelper.syncAudioRecordings(storageProvider, remoteFiles, audioSyncMode, bookId)
                        } catch (e: Exception) {
                            logger.e(TAG, "Audio recordings sync failed (non-fatal)", e)
                        }

                        val ttsModeStr = settingsRepository.syncModeTts
                        if (ttsModeStr != "OFF") {
                            val ttsMode = if (syncMode == SyncMode.TWO_WAY) {
                                try { SyncMode.valueOf(ttsModeStr) } catch (_: Exception) { SyncMode.TWO_WAY }
                            } else syncMode
                            try {
                                ttsSyncHelper.syncTtsCache(storageProvider, remoteFiles, ttsMode)
                            } catch (e: Exception) {
                                logger.e(TAG, "TTS cache sync failed (non-fatal)", e)
                            }
                        }

                        val statsModeStr = settingsRepository.syncModeStats
                        if (statsModeStr != "OFF") {
                            val statsMode = if (syncMode == SyncMode.TWO_WAY) {
                                try { SyncMode.valueOf(statsModeStr) } catch (_: Exception) { SyncMode.BACKUP_ONLY }
                            } else syncMode
                            try {
                                statisticsSyncHelper.syncStatistics(storageProvider, remoteFiles, statsMode, bookId, book.name)
                            } catch (e: Exception) {
                                logger.e(TAG, "Statistics sync failed (non-fatal)", e)
                            }
                        }
                        return@withContext true
                    } else if (remoteMasterFile == null && remoteConflictFiles.isEmpty()) {
                        logger.d(TAG, "No remote file found. Uploading local book as master...")
                        val newFileId = storageProvider.uploadFile(
                            tempFile = tempFile,
                            mimeType = "application/json",
                            description = buildBookDescription(book.name),
                            properties = buildSyncProperties(
                                bookId = bookId,
                                bookName = book.name,
                                createdAt = book.createdAt,
                                updatedAt = book.updatedAt,
                                versionSequence = localSeq,
                                structureMd5 = localStructMd5
                            )
                        ) { p ->
                            onProgress(0.2f + p * 0.8f, "Uploading to Drive...")
                        }
                        if (newFileId != null) {
                            syncLogProvider.addLogEntry("Erster Upload in die Cloud (JSON)", bookId, book.name)
                            val metadata = storageProvider.getFileMetadata(newFileId)
                            val driveTime = metadata?.modifiedTime ?: 0L
                            if (driveTime > 0L) {
                                bookRepository.updateLastModified(bookId, driveTime, incrementSequence = false)
                            }
                            success = true
                        } else {
                            syncLogProvider.addLogEntry("Upload fehlgeschlagen", bookId, book.name, isError = true)
                            success = false
                        }
                    } else {
                        // 2. We have remote master or conflict files. Determine what action to take.
                        val remoteSeqFromProps = remoteMasterFile?.properties?.get("version_sequence")?.toLongOrNull()
                        var remoteSeq = remoteSeqFromProps ?: 0L

                        val isIdentical = remoteMasterFile != null && remoteConflictFiles.isEmpty() && localMd5 == remoteMasterFile.md5Checksum
                        if (isIdentical) {
                            logger.d(TAG, "NO_OP: Local and remote files are identical (MD5 match). Skipping evaluation download.")
                            syncLogProvider.addLogEntry("Inhalte sind identisch (NO_OP)", bookId, book.name)
                            bookRepository.updateLastModified(bookId, remoteMasterFile.modifiedTime, incrementSequence = false)
                            success = true
                        } else {
                            val needDownloadForEvaluation = remoteSeqFromProps == null
                            if (needDownloadForEvaluation && remoteMasterFile != null) {
                                val downloadFile = File(context.cacheDir, "download_${remoteMasterFile.name}")
                                try {
                                    val downloadSuccess = storageProvider.downloadFile(remoteMasterFile.id, downloadFile) { _ -> }
                                    if (downloadSuccess) {
                                        val remoteJson = readJsonFromFile(downloadFile)
                                        val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
                                        val remoteData = jsonParser.decodeFromString<ImportExportData>(remoteJson)
                                        remoteSeq = remoteData.versionSequence ?: 0L
                                    } else {
                                        logger.e(TAG, "Failed to download remote master file for evaluation.")
                                        syncLogProvider.addLogEntry("Download der remote Master-Datei fehlgeschlagen", bookId, book.name, isError = true)
                                        return@withContext false
                                    }
                                } catch (e: Exception) {
                                    logger.e(TAG, "Error downloading or parsing remote master file", e)
                                }
                            }

                            // Determine sync action using SyncDecisionEngine
                            var action = decisionEngine.determineSyncAction(
                                localFile = tempFile,
                                localSeq = localSeq,
                                localLastModified = book.updatedAt,
                                remoteFileMd5 = remoteMasterFile?.md5Checksum,
                                remoteSeq = remoteSeq,
                                remoteLastModified = remoteMasterFile?.modifiedTime ?: 0L,
                                resolvedBookMode = resolvedBookMode,
                                remoteConflictFilesNotEmpty = remoteConflictFiles.isNotEmpty()
                            )

                            logger.d(TAG, "Resolved sync action: $action for mode $resolvedBookMode (localSeq: $localSeq, remoteSeq: $remoteSeq)")

                            if (action == SyncAction.NO_OP) {
                                logger.d(TAG, "NO_OP: Skipping sync action.")
                                val isIdenticalCheck = CloudSyncOptimizer().calculateMD5(tempFile) == effectiveMasterFile?.md5Checksum
                                if (isIdenticalCheck) {
                                    syncLogProvider.addLogEntry("Inhalte sind identisch (NO_OP)", bookId, book.name)
                                } else if (resolvedBookMode == SyncMode.BACKUP_ONLY) {
                                    syncLogProvider.addLogEntry("BACKUP_ONLY: Sicherung übersprungen (Cloud-Version ist aktueller)", bookId, book.name)
                                } else {
                                    syncLogProvider.addLogEntry("RESTORE_ONLY: Wiederherstellung übersprungen (Bereits synchron)", bookId, book.name)
                                }
                                if (effectiveMasterFile != null) {
                                    bookRepository.updateLastModified(bookId, effectiveMasterFile.modifiedTime, incrementSequence = false)
                                }
                                success = true
                            } else if (action == SyncAction.UPLOAD) {
                                if (effectiveMasterFile == null) {
                                    success = false
                                } else {
                                    val expectedVersion = effectiveMasterFile.version ?: 0L
                                    val driveHelper = if (storageProvider is DriveApiSyncStorageProvider) {
                                        DriveServiceHelper(drive!!)
                                    } else null

                                    val uploadSuccess = if (remoteMasterFile == null && legacyZipFile != null) {
                                        val newId = storageProvider.uploadFile(
                                            tempFile = tempFile,
                                            mimeType = "application/json",
                                            description = buildBookDescription(book.name),
                                            properties = buildSyncProperties(
                                                bookId = bookId,
                                                bookName = book.name,
                                                createdAt = book.createdAt,
                                                updatedAt = book.updatedAt,
                                                versionSequence = localSeq,
                                                structureMd5 = localStructMd5
                                            )
                                        ) { _ -> }
                                        if (newId != null) {
                                            try {
                                                storageProvider.deleteFile(legacyZipFile.id)
                                            } catch (e: Exception) {
                                                logger.e(TAG, "Could not delete legacy zip file during migration", e)
                                            }
                                            true
                                        } else false
                                    } else {
                                        if (driveHelper != null) {
                                            driveHelper.uploadWithOptimisticLock(
                                                fileId = effectiveMasterFile.id,
                                                localFile = tempFile,
                                                mimeType = "application/json",
                                                expectedVersion = expectedVersion,
                                                properties = buildSyncProperties(
                                                    bookId = bookId,
                                                    bookName = book.name,
                                                    createdAt = book.createdAt,
                                                    updatedAt = book.updatedAt,
                                                    versionSequence = localSeq,
                                                    structureMd5 = localStructMd5
                                                ),
                                                description = buildBookDescription(book.name)
                                            )
                                        } else {
                                            storageProvider.updateFile(
                                                fileId = effectiveMasterFile.id,
                                                tempFile = tempFile,
                                                mimeType = "application/json",
                                                description = buildBookDescription(book.name),
                                                properties = buildSyncProperties(
                                                    bookId = bookId,
                                                    bookName = book.name,
                                                    createdAt = book.createdAt,
                                                    updatedAt = book.updatedAt,
                                                    versionSequence = localSeq,
                                                    structureMd5 = localStructMd5
                                                )
                                            ) { _ -> }
                                        }
                                    }

                                    if (uploadSuccess) {
                                        syncLogProvider.addLogEntry("Sicherung in der Cloud aktualisiert (Sequence: $localSeq)", bookId, book.name)
                                        val finalMasterId = remoteMasterFile?.id ?: storageProvider.listFiles().find { it.name == masterFileName }?.id ?: effectiveMasterFile.id
                                        val metadata = storageProvider.getFileMetadata(finalMasterId)
                                        val driveTime = metadata?.modifiedTime ?: 0L
                                        if (driveTime > 0L) {
                                            bookRepository.updateLastModified(bookId, driveTime, incrementSequence = false)
                                        }
                                        success = true
                                    } else {
                                        logger.w(TAG, "Optimistic lock failed during UPLOAD. Falling back to MERGE.")
                                        action = SyncAction.MERGE_CONFLICT
                                    }
                                }
                            }
                            if (action == SyncAction.DOWNLOAD) {
                                if (effectiveMasterFile == null) {
                                    success = false
                                } else {
                                    success = importCloudBackupUseCase.downloadAndImport(
                                        storageProvider = storageProvider,
                                        remoteFileId = effectiveMasterFile.id,
                                        fileName = effectiveMasterFile.name,
                                        book = book,
                                        remoteLastModified = effectiveMasterFile.modifiedTime,
                                        onProgress = onProgress
                                    )
                                }
                            } else if (action == SyncAction.MERGE_CONFLICT) {
                                val mergeResult = bookMergeService.performMergeConflict(
                                    drive = drive,
                                    storageProvider = storageProvider,
                                    bookId = bookId,
                                    book = book,
                                    remoteMasterFile = remoteMasterFile,
                                    effectiveMasterFile = effectiveMasterFile,
                                    remoteConflictFiles = remoteConflictFiles,
                                    legacyZipFile = legacyZipFile,
                                    localSeq = localSeq,
                                    remoteFiles = remoteFiles,
                                    audioSyncMode = audioSyncMode,
                                    masterFileName = masterFileName
                                )
                                success = mergeResult.success
                                audioSynced = mergeResult.audioSynced
                            }
                        }
                    }
                }

                /*
                // Sync settings/config config files before other resources
                val configModeStr = settingsRepository.syncModeSettings
                if (success && configModeStr != "OFF") {
                    val configMode = if (syncMode == SyncMode.TWO_WAY) {
                        try { SyncMode.valueOf(configModeStr) } catch (_: Exception) { SyncMode.TWO_WAY }
                    } else syncMode
                    try {
                        configSyncHelper.syncBookConfig(storageProvider, remoteFiles, configMode, bookId, book.name)
                    } catch (e: Exception) {
                        logger.e(TAG, "Config sync failed (non-fatal)", e)
                    }
                }
                */

                // Sync audio recordings after successful book sync
                if (success && !audioSynced) {
                    try {
                        audioSyncHelper.syncAudioRecordings(storageProvider, remoteFiles, audioSyncMode, bookId)
                    } catch (e: Exception) {
                        logger.e(TAG, "Audio recordings sync failed (non-fatal)", e)
                    }
                }

                // Sync TTS cache and statistics separately after successful book sync
                val ttsModeStr = settingsRepository.syncModeTts
                if (success && ttsModeStr != "OFF") {
                    val ttsMode = if (syncMode == SyncMode.TWO_WAY) {
                        try { SyncMode.valueOf(ttsModeStr) } catch (_: Exception) { SyncMode.TWO_WAY }
                    } else syncMode
                    try {
                        ttsSyncHelper.syncTtsCache(storageProvider, remoteFiles, ttsMode)
                    } catch (e: Exception) {
                        logger.e(TAG, "TTS cache sync failed (non-fatal)", e)
                    }
                }

                val statsModeStr = settingsRepository.syncModeStats
                if (success && statsModeStr != "OFF") {
                    val statsMode = if (syncMode == SyncMode.TWO_WAY) {
                        try { SyncMode.valueOf(statsModeStr) } catch (_: Exception) { SyncMode.BACKUP_ONLY }
                    } else syncMode
                    try {
                        statisticsSyncHelper.syncStatistics(storageProvider, remoteFiles, statsMode, bookId, book.name)
                    } catch (e: Exception) {
                        logger.e(TAG, "Statistics sync failed (non-fatal)", e)
                    }
                }
            } catch (e: Exception) {
                logger.e(TAG, "Sync to remote storage provider failed. Performing local fallback...", e)
                remoteSyncError = e
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                val downloadFileZip = File(context.cacheDir, "download_$zipFileName")
                if (downloadFileZip.exists()) downloadFileZip.delete()
                val downloadFileJson = File(context.cacheDir, "download_$masterFileName")
                if (downloadFileJson.exists()) downloadFileJson.delete()
            }

            // Local fallback logic
            if (remoteSyncError != null) {
                try {
                    logger.d(TAG, "Running local fallback backup for book $bookId (already exported at start)...")

                    val configModeStr = settingsRepository.syncModeSettings
                    if (configModeStr != "OFF") {
                        val configFileName = "config_$bookId.json"
                        val configTemp = File(context.cacheDir, configFileName)
                        try {
                            val configJson = importExportManager.exportBookConfigToJson(bookId)
                            configTemp.writeText(configJson)
                            saveToLocalBackupFolder(configFileName, configTemp)
                        } catch (ex: Exception) {
                            logger.e(TAG, "Fallback config export failed", ex)
                        } finally {
                            if (configTemp.exists()) configTemp.delete()
                        }
                    }

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
        }
    } ?: false



    private suspend fun resolveLogsStorageProvider(drive: Drive?): SyncStorageProvider =
        storageResolver.resolveLogsStorageProvider(drive)

    suspend fun syncProfilesOnly(
        drive: Drive?,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = profileSyncOrchestrator.syncProfiles(drive, onProgress)

    suspend fun uploadLogFile(
        drive: Drive?,
        logFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val storageProvider = resolveLogsStorageProvider(drive)
            val existingFile = storageProvider.listFiles().find { it.name == logFile.name }
            val success = if (existingFile != null) {
                storageProvider.updateFile(existingFile.id, logFile, "text/plain", buildLogDescription())
            } else {
                storageProvider.uploadFile(logFile, "text/plain", buildLogDescription()) != null
            }
            success
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

    private fun buildSyncProperties(
        bookId: String,
        bookName: String,
        createdAt: Long,
        updatedAt: Long,
        versionSequence: Long,
        structureMd5: String
    ): Map<String, String> {
        return mapOf(
            "app_name" to "GhostTalk",
            "ghosttalk_import_version" to "1.1",
            "book_id" to bookId,
            "book_name" to bookName,
            "book_created_at" to createdAt.toString(),
            "book_updated_at" to updatedAt.toString(),
            "version_sequence" to versionSequence.toString(),
            "structure_md5" to structureMd5,
            "source_device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
    }

    private suspend fun calculateStructuralMd5(bookId: String): String {
        return try {
            val jsonStr = importExportManager.exportBookToJson(bookId)
            bookMergeEngine.calculateStructuralMd5FromJson(jsonStr)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to calculate structural MD5 for book $bookId", e)
            ""
        }
    }

    @SuppressLint("HardwareIds")
    private fun buildBookDescription(bookName: String): String {
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
        val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        return "$bookName Book (Uploaded by $device - App v$versionName - Device ID: $androidId)"
    }

    @SuppressLint("HardwareIds")
    private fun buildLogDescription(): String {
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
        val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        return "App Logcat Extract (Uploaded by $device - App v$versionName - Device ID: $androidId)"
    }

    companion object {
        fun clearCache(settingsRepository: SettingsRepository? = null, clearSettings: Boolean = false) {
            DriveFolderCache.clearCache(settingsRepository, clearSettings)
        }
    }
}