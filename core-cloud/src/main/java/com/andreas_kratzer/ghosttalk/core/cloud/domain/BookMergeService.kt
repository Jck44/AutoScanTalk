package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.annotation.SuppressLint
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

enum class MergeStatus {
    /** Merge lokal UND in der Cloud vollständig abgeschlossen. */
    SUCCESS,

    /**
     * Der lokale Merge wurde committed (keine Daten verloren), aber der Cloud-Upload
     * wurde abgewiesen (Optimistic Lock / Netzwerk). Der Sync-Lauf ist damit NICHT
     * abgeschlossen — Aufrufer müssen einen Retry einplanen, sonst divergiert die Cloud still.
     */
    MERGED_LOCALLY_UPLOAD_PENDING,

    /** Merge fehlgeschlagen — der lokale Import konnte nicht durchgeführt werden. */
    FAILED
}

data class MergeResult(
    val status: MergeStatus,
    val audioSynced: Boolean
) {
    /** Lokale Daten sind konsistent (auch bei ausstehendem Upload). */
    val success: Boolean get() = status != MergeStatus.FAILED
}

class BookMergeService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val importExportManager: PageImportExportManager,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger,
    private val storageResolver: SyncStorageResolver,
    private val syncAnchorStore: SyncAnchorStore
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
                            extractAudioRecordingsFromZip(downloadFile)
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
                syncAnchorStore.setAnchor(bookId, mergedStructMd5)
                // Clean up remote conflict files
                for (conflictFile in remoteConflictFiles) {
                    try {
                        storageProvider.deleteFile(conflictFile.id)
                    } catch (ex: Exception) {
                        logger.e(TAG, "Failed to delete conflict file ${conflictFile.name}", ex)
                    }
                }
                return MergeResult(status = MergeStatus.SUCCESS, audioSynced = false)
            } else {
                logger.e(TAG, "Failed to import trivial merge locally: ${importResult.exceptionOrNull()?.message}")
                return MergeResult(status = MergeStatus.FAILED, audioSynced = false)
            }
        } else {
            val newSeq = maxOf(localSeq, maxRemoteSeq) + 1
            val mergedWithNewSeq = merged.copy(
                versionSequence = newSeq,
                app_version_code = VersionSafetyGuard.getLocalVersionCode(context),
                ghosttalk_import_version = VersionSafetyGuard.CURRENT_FORMAT_VERSION
            )
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
                        syncAnchorStore.setAnchor(bookId, mergedStructMd5Upload)

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
                        return MergeResult(status = MergeStatus.SUCCESS, audioSynced = audioSynced)
                    } else {
                        // Lokaler Import ist committed (kein Datenverlust), aber die Cloud hat den
                        // Merge NICHT erhalten. Das darf nicht als voller Erfolg gemeldet werden,
                        // sonst plant der Worker keinen Retry und die Cloud divergiert still.
                        Log.w(TAG, "[COMMIT-SEQ] Cloud-Upload ABGEWIESEN (Lock/Netzwerkfehler). Lokaler Import bleibt erhalten, Upload ausstehend.")
                        syncLogProvider.addLogEntry(
                            "Merge lokal gespeichert, Cloud-Upload ausstehend (wird wiederholt)",
                            bookId,
                            book.name,
                            isError = true
                        )
                        return MergeResult(status = MergeStatus.MERGED_LOCALLY_UPLOAD_PENDING, audioSynced = false)
                    }
                } finally {
                    mergedTempFile.delete()
                }
            } else {
                Log.e(TAG, "[COMMIT-SEQ] Lokaler Import des Merges FEHLGESCHLAGEN: ${importResult.exceptionOrNull()?.message}")
                return MergeResult(status = MergeStatus.FAILED, audioSynced = false)
            }
        }
    }

    /**
     * Extrahiert Audio-Aufnahmen aus einem Sync-ZIP nach filesDir/audio_recordings.
     *
     * Atomar pro Datei: Es wird zuerst in eine .part-Tempdatei geschrieben und erst nach
     * vollständigem Schreiben per rename auf den Zielnamen verschoben. Bricht der Vorgang
     * mittendrin ab, bleibt höchstens eine .part-Leiche zurück — niemals eine halb
     * geschriebene Audio-Datei, die ein Button später "anspielen" würde.
     * Zusätzlich werden Einträge verworfen, deren Pfad das Zielverzeichnis verlässt (Zip Slip).
     */
    internal fun extractAudioRecordingsFromZip(zipFile: File) {
        zipFile.inputStream().use { inputStream ->
            ZipInputStream(inputStream).use { zipIn ->
                val audioDir = File(context.filesDir, "audio_recordings")
                if (!audioDir.exists()) audioDir.mkdirs()
                val canonicalAudioDir = audioDir.canonicalPath
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("audio_recordings/")) {
                        val fileName = entry.name.substringAfter("audio_recordings/")
                        if (fileName.isNotEmpty()) {
                            val targetFile = File(audioDir, fileName)
                            if (!targetFile.canonicalPath.startsWith(canonicalAudioDir + File.separator)) {
                                logger.w(TAG, "Zip-Eintrag verlässt Zielverzeichnis, übersprungen: ${entry.name}")
                            } else {
                                val shouldExtract = !targetFile.exists() || (entry.time > targetFile.lastModified())
                                if (shouldExtract) {
                                    val tempFile = File(audioDir, "$fileName.part")
                                    try {
                                        FileOutputStream(tempFile).use { out -> zipIn.copyTo(out) }
                                        if (entry.time != -1L) {
                                            tempFile.setLastModified(entry.time)
                                        }
                                        if (targetFile.exists()) targetFile.delete()
                                        if (!tempFile.renameTo(targetFile)) {
                                            logger.w(TAG, "Konnte Tempdatei nicht umbenennen: ${tempFile.name}")
                                            tempFile.delete()
                                        }
                                    } catch (e: Exception) {
                                        tempFile.delete()
                                        throw e
                                    }
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
            "ghosttalk_import_version" to VersionSafetyGuard.CURRENT_FORMAT_VERSION,
            "book_id" to bookId,
            "book_name" to bookName,
            "book_created_at" to createdAt.toString(),
            "book_updated_at" to updatedAt.toString(),
            "version_sequence" to versionSequence.toString(),
            "structure_md5" to structureMd5,
            "source_device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "app_version_code" to VersionSafetyGuard.getLocalVersionCode(context).toString()
        )
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
}
