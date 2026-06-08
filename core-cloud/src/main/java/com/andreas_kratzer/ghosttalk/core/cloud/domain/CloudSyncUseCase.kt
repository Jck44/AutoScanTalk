@file:Suppress("UseKtx", "REDUNDANT_ELVIS", "RedundantInitializer")
package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.net.Uri
import android.util.Log
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

    private val bookMergeEngine = BookMergeEngine(logger)
    private val audioSyncHelper = AudioSyncHelper(context, importExportManager, logger)
    private val ttsSyncHelper = TtsSyncHelper(context, importExportManager, syncLogProvider, logger)
    private val statisticsSyncHelper = StatisticsSyncHelper(context, importExportManager, syncLogProvider, logger)
    private val configSyncHelper = ConfigSyncHelper(context, importExportManager, syncLogProvider, logger)

    private suspend fun getStorageProvider(drive: Drive?, folderId: String? = null): SyncStorageProvider {
        logger.d(TAG, "getStorageProvider: drive=${if (drive != null) "present" else "NULL"}, folderId=$folderId, settingsRepo.googleDriveFolderId=${settingsRepository.googleDriveFolderId}, syncTargetType=${settingsRepository.syncTargetType}")
        if (drive == null && folderId?.startsWith("content://") == true) {
            logger.d(TAG, "getStorageProvider: Using DocumentFolderSyncStorageProvider (SAF mode)")
            return DocumentFolderSyncStorageProvider(context, folderId)
        }
        val actualDrive = drive ?: run {
            logger.e(TAG, "getStorageProvider: drive is NULL and folderId='$folderId' does not start with content://. syncTargetType=${settingsRepository.syncTargetType}")
            throw IllegalStateException("Drive API client not available.")
        }
        val actualFolderId = folderId ?: settingsRepository.googleDriveFolderId ?: DriveServiceHelper(
            actualDrive
        ).findFolder(FOLDER_NAME) ?: throw IllegalStateException("No valid folder ID found for Drive API.")
        logger.d(TAG, "getStorageProvider: Using DriveApiSyncStorageProvider with folderId=$actualFolderId")
        return DriveApiSyncStorageProvider(actualDrive, actualFolderId)
    }

    private suspend fun resolveProfilesStorageProvider(drive: Drive?): SyncStorageProvider {
        val folderId = settingsRepository.googleDriveFolderId
        if (drive == null && folderId?.startsWith("content://") == true) {
            // SAF Folder: Find or create a subfolder named "Profiles"
            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(folderId))
            val profilesDoc = rootDoc?.findFile("Profiles") ?: rootDoc?.createDirectory("Profiles")
            val targetUri = profilesDoc?.uri?.toString() ?: folderId
            return DocumentFolderSyncStorageProvider(context, targetUri)
        }
        val actualDrive = drive ?: throw IllegalStateException("Drive API client not available.")
        val helper = DriveServiceHelper(actualDrive)
        val parentFolderId = folderId ?: helper.findFolder(FOLDER_NAME) ?: helper.createFolder(FOLDER_NAME) ?: throw IllegalStateException("Failed to resolve sync folder.")
        val profilesFolderId = helper.findFolder("Profiles", parentFolderId) ?: helper.createFolder("Profiles", parentFolderId) ?: parentFolderId
        return DriveApiSyncStorageProvider(actualDrive, profilesFolderId)
    }

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
                val storageProvider = getStorageProvider(drive)

                val remoteFiles = storageProvider.listFiles()

                // --- STAGE 1: Profile Sync (Lifeline) ---
                try {
                    val profilesProvider = resolveProfilesStorageProvider(drive)
                    val remoteProfileFiles = profilesProvider.listFiles()

                    // 1. Sync all local profiles
                    val localProfiles = settingsRepository.getAllProfiles()
                    logger.d(TAG, "Stage 1: Syncing ${localProfiles.size} local profiles")
                    localProfiles.forEach { profile ->
                        configSyncHelper.syncProfile(profilesProvider, remoteProfileFiles, profile, settingsRepository)
                    }

                    // 2. Scan and download/import all other remote profiles
                    val jsonSerializer = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
                    remoteProfileFiles.forEach { file ->
                        if (file.name.startsWith("profile_") && file.name.endsWith(".json")) {
                            val remoteProfileId = file.name.substringAfter("profile_").substringBefore(".json")
                            if (settingsRepository.getProfileById(remoteProfileId) == null) {
                                logger.d(TAG, "Auto-importing new remote profile: ${file.name}")
                                val tempFile = File(context.cacheDir, "import_${file.name}")
                                try {
                                    if (profilesProvider.downloadFile(file.id, tempFile)) {
                                        val profileJson = tempFile.readText()
                                        val importedProfile = try {
                                            jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), profileJson)
                                        } catch (_: Exception) {
                                            try {
                                                val config = jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.ProfileConfig.serializer(), profileJson)
                                                com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
                                                    id = remoteProfileId,
                                                    name = file.description ?: "Importiertes Profil",
                                                    config = config,
                                                    profileVersionSequence = file.version ?: 1L,
                                                    updatedAt = file.modifiedTime
                                                )
                                            } catch (e2: Exception) {
                                                logger.e(TAG, "Failed to parse imported profile JSON as SettingsProfile or legacy ProfileConfig", e2)
                                                null
                                            }
                                        }

                                        if (importedProfile != null) {
                                            settingsRepository.insertProfile(importedProfile)
                                            syncLogProvider.addLogEntry("Remote-Profil ${importedProfile.name} importiert", importedProfile.id, importedProfile.name)
                                        }
                                    }
                                } catch (ex: Exception) {
                                    logger.e(TAG, "Failed to auto-import remote profile ${file.name}", ex)
                                } finally {
                                    tempFile.delete()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.e(TAG, "[SYNC-STAGE-1-ERROR] Stage 1 Profile sync failed (non-fatal): ${e.message}", e)
                }

                // Check isCloudSyncEnabled. If disabled, skip Stage 2 book sync.
                if (!settingsRepository.isCloudSyncEnabled) {
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

                    // Schutzgurt: Wenn Sequenzen gleich sind ODER die Struktur-MD5 übereinstimmt, KEIN Konflikt/Download nötig!
                    if ((remoteSeqFromProps != null && localSeq == remoteSeqFromProps) ||
                        (remoteStructMd5 != null && localStructMd5 == remoteStructMd5 && localSeq >= (remoteSeqFromProps ?: 0L))) {
                        logger.d(TAG, "Sequenzen sind identisch oder Struktur-MD5 stimmt überein ($localSeq). Überspringe Merge-Check und Download.")
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

                            // Determine sync action using CloudSyncOptimizer and resolvedBookMode constraints
                            var action = if (remoteConflictFiles.isNotEmpty() && resolvedBookMode == SyncMode.TWO_WAY) {
                                SyncAction.MERGE_CONFLICT
                            } else {
                                val localLastModified = book.updatedAt
                                val remoteLastModified = remoteMasterFile?.modifiedTime ?: 0L

                                if (localSeq == 0L || remoteSeq == 0L) {
                                    // Legacy fallback based on timestamps
                                    if (CloudSyncOptimizer().calculateMD5(tempFile) == remoteMasterFile?.md5Checksum) {
                                        SyncAction.NO_OP
                                    } else {
                                        when (resolvedBookMode) {
                                            SyncMode.BACKUP_ONLY -> {
                                                if (localLastModified > remoteLastModified + 2000) SyncAction.UPLOAD else SyncAction.NO_OP
                                            }
                                            SyncMode.RESTORE_ONLY -> {
                                                SyncAction.DOWNLOAD
                                            }
                                            SyncMode.TWO_WAY -> {
                                                SyncAction.MERGE_CONFLICT
                                            }
                                        }
                                    }
                                } else {
                                    CloudSyncOptimizer().evaluateSync(tempFile, remoteMasterFile?.md5Checksum, localSeq, remoteSeq)
                                }
                            }

                            // Translate action based on resolvedBookMode (only for modern clients)
                            if (localSeq > 0L && remoteSeq > 0L) {
                                action = when (resolvedBookMode) {
                                    SyncMode.BACKUP_ONLY -> {
                                        if (action == SyncAction.UPLOAD) SyncAction.UPLOAD else SyncAction.NO_OP
                                    }
                                    SyncMode.RESTORE_ONLY -> {
                                        if (action != SyncAction.NO_OP) SyncAction.DOWNLOAD else SyncAction.NO_OP
                                    }
                                    SyncMode.TWO_WAY -> action
                                }
                            }

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
                                    success = downloadAndImport(
                                        storageProvider = storageProvider,
                                        remoteFileId = effectiveMasterFile.id,
                                        fileName = effectiveMasterFile.name,
                                        book = book,
                                        remoteLastModified = effectiveMasterFile.modifiedTime,
                                        onProgress = onProgress
                                    )
                                }
                            } else if (action == SyncAction.MERGE_CONFLICT) {
                                logger.d(TAG, "SyncAction: MERGE_CONFLICT. Performing granular merge of all versions...")
                                val localJson = importExportManager.exportBookToJson(bookId)
                                val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
                                val localData = jsonParser.decodeFromString<ImportExportData>(localJson)

                                val filesToMerge = mutableListOf<RemoteSyncFile>()
                                if (remoteMasterFile != null) {
                                    filesToMerge.add(remoteMasterFile)
                                }
                                filesToMerge.addAll(remoteConflictFiles)

                                val remoteDataList = mutableListOf<ImportExportData>()
                                for (remoteFile in filesToMerge) {
                                    val downloadFile = File(context.cacheDir, "merge_download_${remoteFile.name}")
                                    try {
                                        val downloadSuccess = storageProvider.downloadFile(remoteFile.id, downloadFile) { _ -> }
                                        if (downloadSuccess) {
                                            // Extract audio files from ZIP
                                            if (remoteFile.name.endsWith(".zip")) {
                                                try {
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
                                                } catch (e: Exception) {
                                                    logger.e(TAG, "Failed to extract audio recordings from ${remoteFile.name}", e)
                                                }
                                            }
                                            val remoteJson = readJsonFromFile(downloadFile)
                                            val parsedData = jsonParser.decodeFromString<ImportExportData>(remoteJson)
                                            remoteDataList.add(parsedData)
                                        }
                                    } catch (e: Exception) {
                                        logger.e(TAG, "Error downloading or parsing remote file during merge", e)
                                    } finally {
                                        downloadFile.delete()
                                    }
                                }

                                // Perform sequential merge on detail level
                                var merged = localData
                                for (remoteItem in remoteDataList) {
                                    merged = bookMergeEngine.mergeBooks(merged, remoteItem)
                                }

                                // Determine new sequence sequence
                                val maxRemoteSeq = remoteDataList.mapNotNull { it.versionSequence }.maxOrNull() ?: 0L
                                val mergedStructMd5 = bookMergeEngine.calculateStructuralMd5FromJson(jsonParser.encodeToString(merged))
                                val remoteMasterStructMd5 = remoteMasterFile?.properties?.get("structure_md5")
                                val isTrivialMerge = remoteMasterFile != null && remoteMasterStructMd5 != null && mergedStructMd5 == remoteMasterStructMd5

                                if (isTrivialMerge) {
                                    logger.d(TAG, "Trivial Merge / Fast-Forward detected. Local node has no new changes. Skipping upload.")
                                    val mergedWithRemoteSeq = merged.copy(versionSequence = maxRemoteSeq)
                                    val mergedJson = jsonParser.encodeToString(mergedWithRemoteSeq)
                                    
                                    val importResult = importExportManager.importFromJson(mergedJson, bookId, restoreSyncSettings = false)
                                    if (importResult.isSuccess) {
                                        logger.d(TAG, "Trivial Merge (Fast-Forward) successful (Sequence: $maxRemoteSeq)")
                                        syncLogProvider.addLogEntry("Trivial-Merge (Fast-Forward) erfolgreich (Sequence: $maxRemoteSeq)", bookId, book.name)
                                        val driveTime = remoteMasterFile.modifiedTime
                                        if (driveTime > 0L) {
                                            bookRepository.updateLastModified(bookId, driveTime, incrementSequence = false)
                                        }
                                        // Clean up remote conflict files
                                        for (conflictFile in remoteConflictFiles) {
                                            try {
                                                storageProvider.deleteFile(conflictFile.id)
                                            } catch (ex: Exception) {
                                                logger.e(TAG, "Failed to delete conflict file ${conflictFile.name}", ex)
                                            }
                                        }
                                        success = true
                                    } else {
                                        logger.e(TAG, "Failed to import trivial merge locally: ${importResult.exceptionOrNull()?.message}")
                                        success = false
                                    }
                                } else {
                                    val newSeq = maxOf(localSeq, maxRemoteSeq) + 1
                                    val mergedWithNewSeq = merged.copy(versionSequence = newSeq)
                                    val mergedJson = jsonParser.encodeToString(mergedWithNewSeq)

                                    Log.d(TAG, "[COMMIT-SEQ] Phase 1: Starte lokalen Datenbank-Commit via importFromJson...")
                                    val importResult = importExportManager.importFromJson(mergedJson, bookId, restoreSyncSettings = false)

                                    if (importResult.isSuccess) {
                                        Log.d(TAG, "[COMMIT-SEQ] Phase 2: Lokaler DB-Commit ERFOLGREICH. versionSequence in DB ist: $newSeq. Bereite Pure-JSON-Upload vor.")

                                        val mergedTempFile = File(context.cacheDir, "merged_upload_book_$bookId.json")
                                        try {
                                            mergedTempFile.writeText(mergedJson, Charsets.UTF_8)
                                            val expectedVersion = effectiveMasterFile?.version ?: 0L
                                            val driveHelper = if (storageProvider is DriveApiSyncStorageProvider) {
                                                DriveServiceHelper(drive!!)
                                            } else null
                                            
                                            val mergedStructMd5Upload = bookMergeEngine.calculateStructuralMd5FromJson(mergedJson)

                                            val uploadSuccess = if (driveHelper != null && effectiveMasterFile != null) {
                                                driveHelper.uploadWithOptimisticLock(
                                                    fileId = effectiveMasterFile.id,
                                                    localFile = mergedTempFile,
                                                    mimeType = "application/json",
                                                    expectedVersion = expectedVersion,
                                                    properties = buildSyncProperties(
                                                        bookId = bookId,
                                                        bookName = mergedWithNewSeq.bookName ?: book.name,
                                                        createdAt = mergedWithNewSeq.bookCreatedAt ?: book.createdAt,
                                                        updatedAt = mergedWithNewSeq.bookUpdatedAt ?: System.currentTimeMillis(),
                                                        versionSequence = newSeq,
                                                        structureMd5 = mergedStructMd5Upload
                                                    ),
                                                    description = buildBookDescription(mergedWithNewSeq.bookName ?: book.name)
                                                )
                                            } else if (effectiveMasterFile != null) {
                                                storageProvider.updateFile(
                                                    effectiveMasterFile.id,
                                                    mergedTempFile,
                                                    "application/json",
                                                    buildBookDescription(mergedWithNewSeq.bookName ?: book.name),
                                                    properties = buildSyncProperties(
                                                        bookId = bookId,
                                                        bookName = mergedWithNewSeq.bookName ?: book.name,
                                                        createdAt = mergedWithNewSeq.bookCreatedAt ?: book.createdAt,
                                                        updatedAt = mergedWithNewSeq.bookUpdatedAt ?: System.currentTimeMillis(),
                                                        versionSequence = newSeq,
                                                        structureMd5 = mergedStructMd5Upload
                                                    )
                                                ) { _ -> }
                                            } else {
                                                storageProvider.uploadFile(
                                                    mergedTempFile,
                                                    "application/json",
                                                    buildBookDescription(mergedWithNewSeq.bookName ?: book.name),
                                                    properties = buildSyncProperties(
                                                        bookId = bookId,
                                                        bookName = mergedWithNewSeq.bookName ?: book.name,
                                                        createdAt = mergedWithNewSeq.bookCreatedAt ?: book.createdAt,
                                                        updatedAt = mergedWithNewSeq.bookUpdatedAt ?: System.currentTimeMillis(),
                                                        versionSequence = newSeq,
                                                        structureMd5 = mergedStructMd5Upload
                                                    )
                                                ) != null
                                            }

                                            if (uploadSuccess) {
                                                Log.d(TAG, "[COMMIT-SEQ] Phase 3: Cloud-Upload vom Server BESTÄTIGT. Schließe Sync-Lauf ab.")
                                                syncLogProvider.addLogEntry("Zwei-Wege-Merge erfolgreich abgeschlossen (Sequence: $newSeq)", bookId, book.name)

                                                try {
                                                    audioSyncHelper.syncAudioRecordings(storageProvider, remoteFiles, audioSyncMode, bookId)
                                                    audioSynced = true
                                                } catch (e: Exception) {
                                                    logger.e(TAG, "Audio recordings sync failed during merge conflict (non-fatal)", e)
                                                }

                                                val finalMasterFile = storageProvider.listFiles().find { it.name == masterFileName }
                                                val driveTime = finalMasterFile?.modifiedTime ?: 0L
                                                if (driveTime > 0L) {
                                                    bookRepository.updateLastModified(bookId, driveTime, incrementSequence = false)
                                                }

                                                // Bereinige konsolidierte Konfliktdateien
                                                for (conflictFile in remoteConflictFiles) {
                                                    try {
                                                        storageProvider.deleteFile(conflictFile.id)
                                                    } catch (ex: Exception) {
                                                        logger.e(TAG, "Failed to delete conflict file ${conflictFile.name}", ex)
                                                    }
                                                }

                                                // Optionale Bereinigung der alten ZIP-Leiche aus der Cloud, falls migriert
                                                if (legacyZipFile != null && remoteMasterFile == null) {
                                                    try {
                                                        storageProvider.deleteFile(legacyZipFile.id)
                                                        logger.d(TAG, "Alte ZIP-Leiche erfolgreich nach JSON-Migration gelöscht: ${legacyZipFile.name}")
                                                    } catch(_: Exception){
                                                        logger.w(TAG, "Alte ZIP-Datei konnte nicht gelöscht werden (nicht fatal).")
                                                    }
                                                }

                                                if (storageProvider is DriveApiSyncStorageProvider) {
                                                    val folderId = settingsRepository.googleDriveFolderId ?: DriveServiceHelper(drive!!).findFolder(FOLDER_NAME)
                                                    if (folderId != null) {
                                                        DriveServiceHelper(drive!!).cleanOldConflictFiles(folderId)
                                                    }
                                                }
                                                success = true
                                            } else {
                                                Log.w(TAG, "[COMMIT-SEQ] Cloud-Upload ABGEWIESEN (Lock/Netzwerkfehler). Lokaler Import bleibt erhalten (Self-Healing bei nächstem Sync).")
                                                success = true
                                            }
                                        } finally {
                                            mergedTempFile.delete()
                                        }
                                    } else {
                                        Log.e(TAG, "[COMMIT-SEQ] Lokaler Import des Merges FEHLGESCHLAGEN: ${importResult.exceptionOrNull()?.message}")
                                        success = false
                                    }
                                }
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

        files.filter { 
            it.name != TTS_CACHE_FILE_NAME && 
            !it.name.startsWith("statistics_") &&
            it.mimeType != "application/vnd.google-apps.folder" &&
            it.mimeType != "vnd.android.document/directory"
        }.mapNotNull { file ->
            try {
                val bookName = file.properties?.get("book_name") ?: file.description ?: if (file.name.endsWith(".json")) {
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
        val downloadFile = File(context.cacheDir, "download_$fileName")
        logger.d(TAG, "downloadAndImport: Starting for $remoteFileId ($fileName)")
        val downloadSuccess = if (downloadFile.exists()) {
            logger.d(TAG, "Reusing already downloaded remote master file: ${downloadFile.absolutePath}")
            true
        } else {
            storageProvider.downloadFile(remoteFileId, downloadFile) { p ->
                onProgress(p * 0.7f, "Downloading from Drive...") // Download is 0-70%
            }
        }
        return if (downloadSuccess) {
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
                    downloadFile.delete()
                    return false
                }
                importExportManager.importFromJson(remoteJson, book.id, restoreSyncSettings = false)
            }

            if (result.isSuccess) {
                logger.d(TAG, "Import successful. Updating local timestamp to $remoteLastModified")
                syncLogProvider.addLogEntry("Cloud-Version war neuer -> Lokal aktualisiert (${if (fileName.endsWith(".zip")) "ZIP" else "JSON"})", book.id, book.name)
                bookRepository.updateLastModified(book.id, remoteLastModified, incrementSequence = false)
                saveToLocalBackupFolder(fileName, downloadFile)
                downloadFile.delete()
                if (!fileName.endsWith(".zip")) {
                    try {
                        val remoteFiles = storageProvider.listFiles()
                        audioSyncHelper.restoreAudioRecordingsIfAvailable(storageProvider, remoteFiles, book.id)
                    } catch (e: Exception) {
                        logger.e(TAG, "Failed to restore separate audio recordings (non-fatal)", e)
                    }
                }
                true
            } else {
                logger.e(TAG, "Import failed: ${result.exceptionOrNull()?.message}")
                syncLogProvider.addLogEntry("Import der Cloud-Datei fehlgeschlagen: ${result.exceptionOrNull()?.message}", book.id, book.name, isError = true)
                downloadFile.delete()
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
                                    bookRepository.updateLastModified(bookId, remoteTime, incrementSequence = false)
                                    logger.d(TAG, "Set book updatedAt to remote SAF time: $remoteTime")
                                }
                            } else {
                                try {
                                    val sp = getStorageProvider(drive)
                                    val metadata = sp.getFileMetadata(fileId)
                                    if (metadata != null && metadata.modifiedTime > 0L) {
                                        bookRepository.updateLastModified(bookId, metadata.modifiedTime, incrementSequence = false)
                                        logger.d(TAG, "Set book updatedAt to remote Drive time: ${metadata.modifiedTime}")
                                    }
                                } catch (_: Exception) { /* non-fatal */ }
                            }
                        } catch (e: Exception) {
                            logger.e(TAG, "Failed to align book timestamp after import (non-fatal)", e)
                        }
                    }
                    try {
                        val storageProvider = if (isSafUri) {
                            val treeUri = extractTreeUriFromDocumentUri(fileId)
                            if (treeUri != null) DocumentFolderSyncStorageProvider(context, treeUri) else null
                        } else {
                            try { getStorageProvider(drive) } catch (_: Exception) { null }
                        }
                        if (storageProvider != null) {
                            val remoteFiles = storageProvider.listFiles()
                            try {
                                ttsSyncHelper.restoreTtsCacheIfAvailable(storageProvider, remoteFiles)
                            } catch (e: Exception) {
                                logger.e(TAG, "TTS cache restore after cloud import failed (non-fatal)", e)
                            }
                            if (bookId.isNotEmpty()) {
                                try {
                                    audioSyncHelper.restoreAudioRecordingsIfAvailable(storageProvider, remoteFiles, bookId)
                                } catch (e: Exception) {
                                    logger.e(TAG, "Audio recordings restore after cloud import failed (non-fatal)", e)
                                }
                            }
                            if (bookId.isNotEmpty()) {
                                val bookName = fileName.substringBefore(".zip").substringBefore(".json")
                                /*
                                try {
                                    configSyncHelper.restoreBookConfigIfAvailable(storageProvider, remoteFiles, bookId, bookName)
                                } catch (e: Exception) {
                                    logger.e(TAG, "Config restore after cloud import failed (non-fatal)", e)
                                }
                                */
                                try {
                                    statisticsSyncHelper.restoreStatisticsIfAvailable(storageProvider, remoteFiles, bookId, bookName)
                                } catch (e: Exception) {
                                    logger.e(TAG, "Statistics/Config restore after cloud import failed (non-fatal)", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.e(TAG, "Resource restore after cloud import failed (non-fatal)", e)
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

    suspend fun uploadLogFile(
        drive: Drive?,
        logFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val storageProvider = getStorageProvider(drive)
            val existingFile = storageProvider.listFiles().find { it.name == logFile.name }
            if (existingFile != null) {
                                storageProvider.updateFile(existingFile.id, logFile, "text/plain", buildLogDescription())
                            } else {
                                storageProvider.uploadFile(logFile, "text/plain", buildLogDescription()) != null
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

    private fun buildBookDescription(bookName: String): String {
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
        return "$bookName Book (Uploaded by $device - App v$versionName)"
    }

    private fun buildLogDescription(): String {
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
        return "App Logcat Extract (Uploaded by $device - App v$versionName)"
    }


}