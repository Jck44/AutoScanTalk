package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import androidx.core.net.toUri
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

class ImportCloudBackupUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val importExportManager: PageImportExportManager,
    private val syncLogProvider: SyncLogProvider,
    private val storageResolver: SyncStorageResolver,
    private val logger: Logger
) {
    private val TAG = "ImportCloudBackupUseCase"

    private val audioSyncHelper = AudioSyncHelper(context, importExportManager, logger)
    private val ttsSyncHelper = TtsSyncHelper(context, importExportManager, syncLogProvider, logger)
    private val statisticsSyncHelper = StatisticsSyncHelper(context, importExportManager, syncLogProvider, logger)

    suspend fun execute(
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
                    storageResolver.getStorageProvider(drive)
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
                    try {
                        val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        val parsedData = jsonParser.decodeFromString<ImportExportData>(json)
                        val compatCheck = VersionSafetyGuard.checkJsonCompatibility(
                            context,
                            parsedData.ghosttalk_import_version,
                            parsedData.app_version_code
                        )
                        if (compatCheck.isFailure) {
                            val errorMsg = compatCheck.exceptionOrNull()?.message ?: "Inkompatible Version"
                            logger.w(TAG, "execute: Compatibility check failed: $errorMsg")
                            syncLogProvider.addLogEntry("Import abgebrochen: $errorMsg", null, null, isError = true)
                            tempFile.delete()
                            return@withContext Result.failure<String>(Exception(errorMsg))
                        }
                    } catch (_: Exception) {}
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
                                val docUri = fileId.toUri()
                                val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, docUri)
                                val remoteTime = doc?.lastModified() ?: 0L
                                if (remoteTime > 0L) {
                                    bookRepository.updateLastModified(bookId, remoteTime, incrementSequence = false)
                                    logger.d(TAG, "Set book updatedAt to remote SAF time: $remoteTime")
                                }
                            } else {
                                try {
                                    val sp = storageResolver.getStorageProvider(drive)
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
                            try { storageResolver.getStorageProvider(drive) } catch (_: Exception) { null }
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

    suspend fun downloadAndImport(
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
                onProgress(p * 0.7f, "Downloading from Drive...")
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
                        onProgress(0.7f + p * 0.3f, s)
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
                try {
                    val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    val parsedData = jsonParser.decodeFromString<ImportExportData>(remoteJson)
                    val compatCheck = VersionSafetyGuard.checkJsonCompatibility(
                        context,
                        parsedData.ghosttalk_import_version,
                        parsedData.app_version_code
                    )
                    if (compatCheck.isFailure) {
                        val errorMsg = compatCheck.exceptionOrNull()?.message ?: "Inkompatible Version"
                        logger.w(TAG, "downloadAndImport: Compatibility check failed for $fileName: $errorMsg")
                        syncLogProvider.addLogEntry(
                            "Import abgebrochen: $errorMsg",
                            book.id,
                            book.name,
                            isError = true
                        )
                        downloadFile.delete()
                        return false
                    }
                } catch (_: Exception) {}
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

    private fun downloadSafFile(
        documentUri: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = storageResolver.downloadSafFile(documentUri, destFile, onProgress)

    private fun extractTreeUriFromDocumentUri(documentUri: String): String? =
        storageResolver.extractTreeUriFromDocumentUri(documentUri)

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
