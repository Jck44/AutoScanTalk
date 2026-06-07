package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ConfigSyncHelper(
    private val context: Context,
    private val importExportManager: PageImportExportManager,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger
) {
    private val TAG = "ConfigSyncHelper"

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

    suspend fun syncBookConfig(
        storageProvider: SyncStorageProvider,
        syncMode: SyncMode,
        bookId: String,
        bookName: String
    ) = withContext(Dispatchers.IO) {
        val configFileName = "config_$bookId.json"

        val remoteFile = try {
            storageProvider.listFiles().find { it.name == configFileName }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to find config backup on Drive", e)
            null
        }

        val localLastModified = importExportManager.getBookConfigLastModified(bookId)
        val remoteLastModified = remoteFile?.modifiedTime ?: 0L

        val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
        val lastSyncedLocalTime = prefs.getLong("config_last_synced_local_time_$bookId", 0L)
        val lastSyncedRemoteTime = prefs.getLong("config_last_synced_remote_time_$bookId", 0L)

        logger.w(TAG, "[CONFIG-SYNC] syncBookConfig called: syncMode=$syncMode, bookId='$bookId', configFileName='$configFileName'")
        logger.w(TAG, "[CONFIG-SYNC] remoteFile found: ${remoteFile != null} (name=${remoteFile?.name}, id=${remoteFile?.id})")
        logger.w(TAG, "[CONFIG-SYNC] localLastModified=$localLastModified, remoteLastModified=$remoteLastModified, lastSyncedLocal=$lastSyncedLocalTime, lastSyncedRemote=$lastSyncedRemoteTime")

        if (localLastModified == 0L && remoteFile == null) {
            logger.w(TAG, "[CONFIG-SYNC] EARLY EXIT: No config to sync (localLastModified=0 AND no remote file).")
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

        logger.w(TAG, "[CONFIG-SYNC] Decision: hasLocalChanged=$hasLocalChanged, hasRemoteChanged=$hasRemoteChanged, shouldUpload=$shouldUpload, shouldDownload=$shouldDownload")

        if (shouldUpload) {
            logger.d(TAG, "Uploading config...")
            val tempFile = File(context.cacheDir, configFileName)
            try {
                val configJson = importExportManager.exportBookConfigToJson(bookId)
                tempFile.writeText(configJson)
                if (tempFile.length() > 0) {
                    saveToLocalBackupFolder(configFileName, tempFile)
                    val fileId = if (remoteFile != null) {
                        val updateSuccess = storageProvider.updateFile(remoteFile.id, tempFile, "application/json", bookName) { _ -> }
                        if (updateSuccess) remoteFile.id else null
                    } else {
                        storageProvider.uploadFile(tempFile, "application/json", bookName) { _ -> }
                    }

                    if (fileId != null) {
                        val newMetadata = storageProvider.getFileMetadata(fileId)
                        val newRemoteTime = newMetadata?.modifiedTime ?: 0L
                        prefs.edit()
                            .putLong("config_last_synced_local_time_$bookId", localLastModified)
                            .putLong("config_last_synced_remote_time_$bookId", newRemoteTime)
                            .apply()
                        syncLogProvider.addLogEntry("Einstellungen in die Cloud hochgeladen", bookId, bookName)
                    }
                }
            } finally {
                tempFile.delete()
            }
        } else if (shouldDownload) {
            logger.d(TAG, "Downloading config...")
            val tempFile = File(context.cacheDir, "download_$configFileName")
            try {
                if (storageProvider.downloadFile(remoteFile!!.id, tempFile) { _ -> }) {
                    val configJson = tempFile.readText()
                    importExportManager.importBookConfigFromJson(configJson, bookId)
                    saveToLocalBackupFolder(configFileName, tempFile)
                    syncLogProvider.addLogEntry("Einstellungen aus der Cloud wiederhergestellt", bookId, bookName)
                }
            } finally {
                tempFile.delete()
            }
        } else {
            logger.d(TAG, "Config settings are in sync.")
            if (lastSyncedLocalTime == 0L || lastSyncedRemoteTime == 0L) {
                prefs.edit()
                    .putLong("config_last_synced_local_time_$bookId", localLastModified)
                    .putLong("config_last_synced_remote_time_$bookId", remoteLastModified)
                    .apply()
            }
        }
    }

    suspend fun restoreBookConfigIfAvailable(
        storageProvider: SyncStorageProvider,
        bookId: String,
        bookName: String
    ) {
        val configFileName = "config_$bookId.json"
        val remoteFile = try {
            storageProvider.listFiles().find { it.name == configFileName }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to find config for restore", e)
            null
        } ?: return

        logger.d(TAG, "Found separate config on Drive, restoring...")
        val tempFile = File(context.cacheDir, "download_$configFileName")
        try {
            if (storageProvider.downloadFile(remoteFile.id, tempFile) { _ -> }) {
                val configJson = tempFile.readText()
                importExportManager.importBookConfigFromJson(configJson, bookId)
                saveToLocalBackupFolder(configFileName, tempFile)
                syncLogProvider.addLogEntry("Einstellungen aus der Cloud wiederhergestellt", bookId, bookName)
            }
        } finally {
            tempFile.delete()
        }
    }
}
