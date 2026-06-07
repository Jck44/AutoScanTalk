package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TtsSyncHelper(
    private val context: Context,
    private val importExportManager: PageImportExportManager,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger
) {
    private val TAG = "TtsSyncHelper"
    private val TTS_CACHE_FILE_NAME = "tts_cache.zip"

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

    suspend fun syncTtsCache(
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
            if (lastSyncedLocalTime == 0L || lastSyncedRemoteTime == 0L) {
                prefs.edit()
                    .putLong("tts_cache_last_synced_local_time", localLastModified)
                    .putLong("tts_cache_last_synced_remote_time", remoteLastModified)
                    .apply()
            }
        }
    }

    suspend fun restoreTtsCacheIfAvailable(
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
}
