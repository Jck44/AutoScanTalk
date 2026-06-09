package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class GetAvailableBackupsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageResolver: SyncStorageResolver,
    private val importExportManager: PageImportExportManager,
    private val logger: Logger
) {
    private val TAG = "GetAvailableBackupsUseCase"
    private val TTS_CACHE_FILE_NAME = "tts_cache.zip"

    suspend fun execute(drive: Drive?, folderId: String? = null): List<RemoteBackupInfo> = withContext(Dispatchers.IO) {
        logger.d(TAG, "Fetching available backups (folderId: $folderId)...")
        val storageProvider = try {
            storageResolver.getStorageProvider(drive, folderId)
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
}
