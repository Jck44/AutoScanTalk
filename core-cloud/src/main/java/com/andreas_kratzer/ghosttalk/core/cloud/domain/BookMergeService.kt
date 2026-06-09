package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

data class MergeResult(
    val success: Boolean,
    val audioSynced: Boolean
)

class BookMergeService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val importExportManager: PageImportExportManager,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger,
    private val storageResolver: SyncStorageResolver
) {
    private val TAG = "BookMergeService"
    private val bookMergeEngine = BookMergeEngine(logger)
    private val audioSyncHelper = AudioSyncHelper(context, importExportManager, logger)

    suspend fun performMergeConflict(
        drive: Drive?,
        storageProvider: SyncStorageProvider,
        bookId: String,
        book: Book,
        remoteMasterFile: RemoteSyncFile?,
        effectiveMasterFile: RemoteSyncFile?,
        remoteConflictFiles: List<RemoteSyncFile>,
        legacyZipFile: RemoteSyncFile?,
        localSeq: Long,
        remoteFiles: List<RemoteSyncFile>,
        audioSyncMode: SyncMode,
        masterFileName: String
    ): MergeResult {
        logger.d(TAG, "performMergeConflict: Performing granular merge of all versions...")
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
                                ZipInputStream(inputStream).use { zipIn ->
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

        var audioSynced = false

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
                return MergeResult(success = true, audioSynced = false)
            } else {
                logger.e(TAG, "Failed to import trivial merge locally: ${importResult.exceptionOrNull()?.message}")
                return MergeResult(success = false, audioSynced = false)
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
                    val driveHelper = if (storageProvider is DriveApiSyncStorageProvider && drive != null) {
                        DriveServiceHelper(drive)
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

                        if (storageProvider is DriveApiSyncStorageProvider && drive != null) {
                             val folderId = storageResolver.folderCache.resolveParentFolderId(drive)
                             DriveServiceHelper(drive).cleanOldConflictFiles(folderId)
                        }
                        return MergeResult(success = true, audioSynced = audioSynced)
                    } else {
                        Log.w(TAG, "[COMMIT-SEQ] Cloud-Upload ABGEWIESEN (Lock/Netzwerkfehler). Lokaler Import bleibt erhalten (Self-Healing bei nächstem Sync).")
                        return MergeResult(success = true, audioSynced = false)
                    }
                } finally {
                    mergedTempFile.delete()
                }
            } else {
                Log.e(TAG, "[COMMIT-SEQ] Lokaler Import des Merges FEHLGESCHLAGEN: ${importResult.exceptionOrNull()?.message}")
                return MergeResult(success = false, audioSynced = false)
            }
        }
    }

    private fun readJsonFromFile(file: File): String {
        if (file.name.endsWith(".zip")) {
            ZipInputStream(file.inputStream()).use { zip ->
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
}
