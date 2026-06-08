package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StatisticsSyncHelper(
    private val context: Context,
    private val importExportManager: PageImportExportManager,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger
) {
    private val TAG = "StatisticsSyncHelper"

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

    suspend fun syncStatistics(
        storageProvider: SyncStorageProvider,
        remoteFiles: List<RemoteSyncFile>,
        syncMode: SyncMode,
        bookId: String,
        bookName: String
    ) = withContext(Dispatchers.IO) {
        val statsFileName = "statistics_$bookId.zip"
        val remoteFile = remoteFiles.find { it.name == statsFileName }

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
                        val updateSuccess = storageProvider.updateFile(remoteFile.id, tempFile, "application/zip", buildStatsDescription(bookName)) { _ -> }
                        if (updateSuccess) remoteFile.id else null
                    } else {
                        storageProvider.uploadFile(tempFile, "application/zip", buildStatsDescription(bookName)) { _ -> }
                    }

                    if (fileId != null) {
                        val newMetadata = storageProvider.getFileMetadata(fileId)
                        val newRemoteTime = newMetadata?.modifiedTime ?: 0L
                        prefs.edit {
                            putLong("stats_last_synced_local_time_$bookId", localLastModified)
                            putLong("stats_last_synced_remote_time_$bookId", newRemoteTime)
                        }
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
                    prefs.edit {
                        putLong("stats_last_synced_local_time_$bookId", newLocalLastModified)
                        putLong("stats_last_synced_remote_time_$bookId", remoteLastModified)
                    }
                    syncLogProvider.addLogEntry("Statistik aus der Cloud wiederhergestellt", bookId, bookName)
                }
            } finally {
                tempFile.delete()
            }
        } else {
            logger.d(TAG, "Statistics are in sync.")
            if (lastSyncedLocalTime == 0L || lastSyncedRemoteTime == 0L) {
                prefs.edit {
                    putLong("stats_last_synced_local_time_$bookId", localLastModified)
                    putLong("stats_last_synced_remote_time_$bookId", remoteLastModified)
                }
            }
        }
    }

    suspend fun restoreStatisticsIfAvailable(
        storageProvider: SyncStorageProvider,
        remoteFiles: List<RemoteSyncFile>,
        bookId: String,
        bookName: String
    ) {
        val statsFileName = "statistics_$bookId.zip"
        val remoteFile = remoteFiles.find { it.name == statsFileName } ?: return

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
                prefs.edit {
                    putLong("stats_last_synced_local_time_$bookId", newLocalLastModified)
                    putLong("stats_last_synced_remote_time_$bookId", remoteLastModified)
                }
                syncLogProvider.addLogEntry("Statistik aus der Cloud wiederhergestellt", bookId, bookName)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun buildStatsDescription(bookName: String): String {
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "unknown"
        }
        return "$bookName Stats (Uploaded by $device - App v$versionName)"
    }
}
