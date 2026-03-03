package com.andreas_kratzer.ghosttalk.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.PageImportExportManager
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

import android.util.Log

class CloudSyncUseCase @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    private val importExportManager: PageImportExportManager
) {
    private val TAG = "CloudSyncUseCase"
    private val FOLDER_NAME = "GhosTTalk_Sync"

    suspend fun syncBook(drive: Drive, bookId: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync for book: $bookId")
        if (!settingsRepository.isCloudSyncEnabled) {
            Log.w(TAG, "Cloud sync is disabled in settings. Skipping.")
            return@withContext
        }

        val helper = DriveServiceHelper(drive)
        var folderId = helper.findFolder(FOLDER_NAME)
        Log.d(TAG, "findFolder result: $folderId")
        if (folderId == null) {
            Log.d(TAG, "Folder not found, creating folder: $FOLDER_NAME")
            folderId = helper.createFolder(FOLDER_NAME)
            Log.d(TAG, "createFolder result: $folderId")
        }

        if (folderId == null) {
            Log.e(TAG, "Failed to find or create folder. Sync aborted.")
            return@withContext
        }

        val fileName = "book_$bookId.json"
        Log.d(TAG, "Listing files for folderId: $folderId")
        val driveFiles = helper.listFiles(folderId)
        val remoteFile = driveFiles.find { it.name == fileName }
        Log.d(TAG, "Remote file found: ${remoteFile != null} (id: ${remoteFile?.id})")

        // Local Export
        Log.d(TAG, "Exporting local book data to JSON")
        val localJson = importExportManager.exportBookToJson(bookId)
        val tempFile = File(context.cacheDir, fileName).apply {
            writeText(localJson)
        }
        val localLastModified = tempFile.lastModified()
        Log.d(TAG, "Local file size: ${tempFile.length()} bytes, lastModified: $localLastModified")

        if (remoteFile == null) {
            // Upload for the first time
            Log.d(TAG, "No remote file found. Uploading for the first time...")
            val newFileId = helper.uploadFile(folderId, tempFile, "application/json")
            Log.d(TAG, "Upload result id: $newFileId")
        } else {
            // Last-write-wins logic
            val remoteLastModified = remoteFile.modifiedTime.value // Long from RFC 3339
            Log.d(TAG, "Remote modifiedTime: $remoteLastModified")

            if (localLastModified > remoteLastModified + 2000) { // 2s Grace period
                // Local is newer
                Log.d(TAG, "Local version is newer. Updating remote file...")
                val success = helper.updateFile(remoteFile.id, tempFile, "application/json")
                Log.d(TAG, "Update result: $success")
            } else if (remoteLastModified > localLastModified + 2000) {
                // Remote is newer
                Log.d(TAG, "Remote version is newer. Downloading and importing...")
                val downloadFile = File(context.cacheDir, "download_$fileName")
                if (helper.downloadFile(remoteFile.id, downloadFile)) {
                    Log.d(TAG, "Download successful. Importing JSON...")
                    val remoteJson = downloadFile.readText()
                    importExportManager.importBookFromJson(remoteJson, bookId)
                    // Update local timestamp to match remote to avoid loop
                    tempFile.setLastModified(remoteLastModified)
                    Log.d(TAG, "Import completed.")
                } else {
                    Log.e(TAG, "Failed to download remote file.")
                }
            } else {
                Log.d(TAG, "Local and remote versions are synchronized (within grace period).")
            }
        }
        
        tempFile.delete()
        Log.d(TAG, "Sync process finished.")
    }
}
