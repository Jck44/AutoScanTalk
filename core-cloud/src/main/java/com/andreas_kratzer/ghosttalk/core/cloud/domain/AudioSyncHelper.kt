package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioSyncHelper(
    private val context: Context,
    private val importExportManager: PageImportExportManager,
    private val logger: Logger
) {
    private val TAG = "AudioSyncHelper"

    suspend fun syncAudioRecordings(
        storageProvider: SyncStorageProvider,
        remoteFiles: List<RemoteSyncFile>,
        syncMode: SyncMode,
        bookId: String
    ) = withContext(Dispatchers.IO) {
        val audioFileName = "audio_$bookId.zip"
        val remoteFile = remoteFiles.find { it.name == audioFileName }

        var localLastModified = importExportManager.getAudioRecordingsLastModified()
        val remoteLastModified = remoteFile?.modifiedTime ?: 0L

        val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
        val lastSyncedLocalTime = prefs.getLong("audio_last_synced_local_time_$bookId", 0L)
        val lastSyncedRemoteTime = prefs.getLong("audio_last_synced_remote_time_$bookId", 0L)

        com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logStatus(logger, TAG, audioFileName, localLastModified, remoteLastModified, lastSyncedLocalTime, lastSyncedRemoteTime)

        if (localLastModified == 0L && remoteFile == null) {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logSkipped(logger, TAG, audioFileName, "local and remote are empty")
            return@withContext
        }

        var hasLocalChanged = localLastModified > lastSyncedLocalTime + 2000 && localLastModified > 0L
        var hasRemoteChanged = remoteFile != null && remoteLastModified > lastSyncedRemoteTime + 2000
        val isLocalAudioEmpty = localLastModified == 0L

        if (syncMode == SyncMode.TWO_WAY && hasLocalChanged && hasRemoteChanged && remoteFile != null) {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logAction(logger, TAG, audioFileName, "Audio conflict detected", "Performing two-way audio merge")
            val tempDownloadFile = File(context.cacheDir, "download_merge_$audioFileName")
            try {
                val downloadSuccess = storageProvider.downloadFile(remoteFile.id, tempDownloadFile) { _ -> }
                if (downloadSuccess) {
                    tempDownloadFile.inputStream().use { isStream ->
                        importExportManager.importAudioRecordingsFromZip(isStream) { _, _ -> }
                    }
                    localLastModified = importExportManager.getAudioRecordingsLastModified()
                    hasRemoteChanged = false
                    hasLocalChanged = true
                }
            } catch (e: Exception) {
                logger.e(TAG, "Audio merge download/extract failed for $audioFileName", e)
            } finally {
                if (tempDownloadFile.exists()) tempDownloadFile.delete()
            }
        }

        val shouldUpload = when (syncMode) {
            SyncMode.RESTORE_ONLY -> false
            SyncMode.BACKUP_ONLY -> hasLocalChanged || remoteFile == null
            SyncMode.TWO_WAY -> (hasLocalChanged && !hasRemoteChanged) || (localLastModified > 0L && remoteFile == null)
        }

        val shouldDownload = when (syncMode) {
            SyncMode.BACKUP_ONLY -> false
            SyncMode.RESTORE_ONLY -> hasRemoteChanged || (remoteFile != null && isLocalAudioEmpty)
            SyncMode.TWO_WAY -> hasRemoteChanged || (remoteFile != null && isLocalAudioEmpty)
        }

        if (shouldUpload) {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logAction(logger, TAG, audioFileName, "Uploading audio recordings zip")
            val tempFile = File(context.cacheDir, audioFileName)
            try {
                tempFile.outputStream().use { os ->
                    importExportManager.exportAudioRecordingsToZip(os) { _, _ -> }
                }

                val fileId = if (remoteFile != null) {
                    val updateSuccess = storageProvider.updateFile(remoteFile.id, tempFile, "application/zip", null) { _ -> }
                    if (updateSuccess) remoteFile.id else null
                } else {
                    storageProvider.uploadFile(tempFile, "application/zip", null) { _ -> }
                }

                if (fileId != null) {
                    val newMetadata = storageProvider.getFileMetadata(fileId)
                    val newRemoteTime = newMetadata?.modifiedTime ?: 0L
                    prefs.edit {
                        putLong("audio_last_synced_local_time_$bookId", localLastModified)
                        putLong("audio_last_synced_remote_time_$bookId", newRemoteTime)
                    }
                    logger.d(TAG, "Audio recordings upload success for $audioFileName. synced local=$localLastModified remote=$newRemoteTime")
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to upload audio recordings zip $audioFileName", e)
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        } else if (shouldDownload && remoteFile != null) {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logAction(logger, TAG, audioFileName, "Downloading audio recordings zip")
            val tempFile = File(context.cacheDir, "download_$audioFileName")
            try {
                val downloadSuccess = storageProvider.downloadFile(remoteFile.id, tempFile) { _ -> }
                if (downloadSuccess) {
                    tempFile.inputStream().use { isStream ->
                        importExportManager.importAudioRecordingsFromZip(isStream) { _, _ -> }
                    }
                    prefs.edit {
                        putLong("audio_last_synced_local_time_$bookId", importExportManager.getAudioRecordingsLastModified())
                        putLong("audio_last_synced_remote_time_$bookId", remoteLastModified)
                    }
                    logger.d(TAG, "Audio recordings download and extract success.")
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to download audio recordings", e)
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    suspend fun restoreAudioRecordingsIfAvailable(
        storageProvider: SyncStorageProvider,
        remoteFiles: List<RemoteSyncFile>,
        bookId: String
    ) = withContext(Dispatchers.IO) {
        val audioFileName = "audio_$bookId.zip"
        val remoteFile = remoteFiles.find { it.name == audioFileName } ?: return@withContext

        logger.d(TAG, "Found separate audio recordings on Drive, restoring...")
        val tempFile = File(context.cacheDir, "download_$audioFileName")
        try {
            val success = storageProvider.downloadFile(remoteFile.id, tempFile) { _ -> }
            if (success) {
                tempFile.inputStream().use { isStream ->
                    importExportManager.importAudioRecordingsFromZip(isStream) { _, _ -> }
                }
                val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
                prefs.edit {
                    putLong("audio_last_synced_local_time_$bookId", importExportManager.getAudioRecordingsLastModified())
                    putLong("audio_last_synced_remote_time_$bookId", remoteFile.modifiedTime)
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to restore separate audio recordings", e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
