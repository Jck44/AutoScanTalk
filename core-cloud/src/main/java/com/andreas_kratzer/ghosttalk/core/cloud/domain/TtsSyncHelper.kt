package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import androidx.core.content.edit
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

    private fun saveToLocalBackupFolder(tempFile: File) {
        try {
            val backupDir = File(context.filesDir, "local_backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            val destFile = File(backupDir, TTS_CACHE_FILE_NAME)
            tempFile.copyTo(destFile, overwrite = true)
            logger.d(TAG, "Saved local backup copy to $destFile")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to save local backup copy for $TTS_CACHE_FILE_NAME", e)
        }
    }

    suspend fun syncTtsCache(
        storageProvider: SyncStorageProvider,
        remoteFiles: List<RemoteSyncFile>,
        syncMode: SyncMode
    ) = withContext(Dispatchers.IO) {
        val remoteFile = remoteFiles.find { it.name == TTS_CACHE_FILE_NAME }

        var localLastModified = importExportManager.getTtsCacheLastModified()
        val remoteLastModified = remoteFile?.modifiedTime ?: 0L

        val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
        val lastSyncedLocalTime = prefs.getLong("tts_cache_last_synced_local_time", 0L)
        val lastSyncedRemoteTime = prefs.getLong("tts_cache_last_synced_remote_time", 0L)

        com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logStatus(logger, TAG, TTS_CACHE_FILE_NAME, localLastModified, remoteLastModified, lastSyncedLocalTime, lastSyncedRemoteTime)

        if (localLastModified == 0L && remoteFile == null) {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logSkipped(logger, TAG, TTS_CACHE_FILE_NAME, "local and remote are empty")
            return@withContext
        }

        var hasLocalChanged = localLastModified > lastSyncedLocalTime + 2000 && localLastModified > 0L
        var hasRemoteChanged = remoteFile != null && remoteLastModified > lastSyncedRemoteTime + 2000

        if (syncMode == SyncMode.TWO_WAY && hasLocalChanged && hasRemoteChanged && remoteFile != null) {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logAction(logger, TAG, TTS_CACHE_FILE_NAME, "TTS conflict detected", "Performing two-way TTS merge")
            val tempDownloadFile = File(context.cacheDir, "download_merge_$TTS_CACHE_FILE_NAME")
            try {
                val downloadSuccess = storageProvider.downloadFile(remoteFile.id, tempDownloadFile) { _ -> }
                if (downloadSuccess) {
                    tempDownloadFile.inputStream().use { isStream ->
                        importExportManager.importTtsCacheFromZip(isStream) { _, _ -> }
                    }
                    localLastModified = importExportManager.getTtsCacheLastModified()
                    hasRemoteChanged = false
                    hasLocalChanged = true
                }
            } catch (e: Exception) {
                logger.e(TAG, "TTS merge download/extract failed for $TTS_CACHE_FILE_NAME", e)
            } finally {
                if (tempDownloadFile.exists()) tempDownloadFile.delete()
            }
        }

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
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logAction(logger, TAG, TTS_CACHE_FILE_NAME, "Uploading TTS cache zip")
            val tempFile = File(context.cacheDir, TTS_CACHE_FILE_NAME)
            try {
                tempFile.outputStream().use { os ->
                    importExportManager.exportTtsCacheToZip(os) { _, _ -> }
                }
                if (tempFile.length() > 0) {
                    saveToLocalBackupFolder(tempFile)
                    val fileId = if (remoteFile != null) {
                        val updateSuccess = storageProvider.updateFile(remoteFile.id, tempFile, "application/zip", buildTtsDescription()) { _ -> }
                        if (updateSuccess) remoteFile.id else null
                    } else {
                        storageProvider.uploadFile(tempFile, "application/zip", buildTtsDescription()) { _ -> }
                    }

                    if (fileId != null) {
                        val newMetadata = storageProvider.getFileMetadata(fileId)
                        val newRemoteTime = newMetadata?.modifiedTime ?: 0L
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
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logAction(logger, TAG, TTS_CACHE_FILE_NAME, "Downloading TTS cache zip")
            val tempFile = File(context.cacheDir, "download_$TTS_CACHE_FILE_NAME")
            try {
                if (storageProvider.downloadFile(remoteFile!!.id, tempFile) { _ -> }) {
                    tempFile.inputStream().use { inputStream ->
                        importExportManager.importTtsCacheFromZip(inputStream) { _, _ -> }
                    }
                    saveToLocalBackupFolder(tempFile)
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
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logSkipped(logger, TAG, TTS_CACHE_FILE_NAME, "TTS cache is in sync")
            if (lastSyncedLocalTime == 0L || lastSyncedRemoteTime == 0L) {
                prefs.edit {
                    putLong("tts_cache_last_synced_local_time", localLastModified)
                    putLong("tts_cache_last_synced_remote_time", remoteLastModified)
                }
            }
        }
    }

    suspend fun restoreTtsCacheIfAvailable(
        storageProvider: SyncStorageProvider,
        remoteFiles: List<RemoteSyncFile>
    ) {
        val remoteFile = remoteFiles.find { it.name == TTS_CACHE_FILE_NAME } ?: return

        logger.d(TAG, "Found separate TTS cache on Drive, restoring...")
        val tempFile = File(context.cacheDir, "download_$TTS_CACHE_FILE_NAME")
        try {
            if (storageProvider.downloadFile(remoteFile.id, tempFile) { _ -> }) {
                tempFile.inputStream().use { inputStream ->
                    importExportManager.importTtsCacheFromZip(inputStream) { _, _ -> }
                }
                saveToLocalBackupFolder(tempFile)
                val newLocalLastModified = importExportManager.getTtsCacheLastModified()
                val remoteLastModified = remoteFile.modifiedTime
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

    private fun buildTtsDescription(): String {
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
        return "TTS Cache (Uploaded by $device - App v$versionName)"
    }
}
