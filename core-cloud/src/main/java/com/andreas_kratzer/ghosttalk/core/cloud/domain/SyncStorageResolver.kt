package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import androidx.core.net.toUri
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SyncStorageResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {
    private val TAG = "SyncStorageResolver"
    val folderCache = DriveFolderCache(settingsRepository, logger)

    suspend fun getStorageProvider(drive: Drive?, folderId: String? = null): SyncStorageProvider {
        logger.d(TAG, "getStorageProvider: drive=${if (drive != null) "present" else "NULL"}, folderId=$folderId, settingsRepo.googleDriveFolderId=${settingsRepository.googleDriveFolderId}, syncTargetType=${settingsRepository.syncTargetType}")
        if (drive == null && folderId?.startsWith("content://") == true) {
            logger.d(TAG, "getStorageProvider: Using DocumentFolderSyncStorageProvider (SAF mode)")
            return DocumentFolderSyncStorageProvider(context, folderId)
        }
        val actualDrive = drive ?: run {
            logger.e(TAG, "getStorageProvider: drive is NULL and folderId='$folderId' does not start with content://. syncTargetType=${settingsRepository.syncTargetType}")
            throw IllegalStateException("Drive API client not available.")
        }
        
        val actualFolderId = folderId ?: folderCache.resolveParentFolderId(actualDrive)
        logger.d(TAG, "getStorageProvider: Using DriveApiSyncStorageProvider with folderId=$actualFolderId")
        return DriveApiSyncStorageProvider(actualDrive, actualFolderId)
    }

    suspend fun resolveProfilesStorageProvider(drive: Drive?): SyncStorageProvider {
        val folderId = settingsRepository.googleDriveFolderId
        if (drive == null && folderId?.startsWith("content://") == true) {
            // SAF Folder: Find or create a subfolder named "Profiles"
            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderId.toUri())
            val profilesDoc = rootDoc?.findFile("Profiles") ?: rootDoc?.createDirectory("Profiles")
            val targetUri = profilesDoc?.uri?.toString() ?: folderId
            return DocumentFolderSyncStorageProvider(context, targetUri)
        }
        val actualDrive = drive ?: throw IllegalStateException("Drive API client not available.")
        
        val profilesFolderId = folderCache.resolveProfilesFolderId(actualDrive)
        return DriveApiSyncStorageProvider(actualDrive, profilesFolderId)
    }

    suspend fun resolveLogsStorageProvider(drive: Drive?): SyncStorageProvider {
        val folderId = settingsRepository.googleDriveFolderId
        if (drive == null && folderId?.startsWith("content://") == true) {
            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderId.toUri())
            val logsDoc = rootDoc?.findFile("Logs") ?: rootDoc?.createDirectory("Logs")
            val targetUri = logsDoc?.uri?.toString() ?: folderId
            return DocumentFolderSyncStorageProvider(context, targetUri)
        }
        val actualDrive = drive ?: throw IllegalStateException("Drive API client not available.")
        
        val logsFolderId = folderCache.resolveLogsFolderId(actualDrive)
        return DriveApiSyncStorageProvider(actualDrive, logsFolderId)
    }

    fun downloadSafFile(
        documentUri: String,
        destFile: java.io.File,
        onProgress: (Float) -> Unit
    ): Boolean {
        val uri = documentUri.toUri()
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                destFile.outputStream().use { os ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        // report indeterminate or progress if needed, but not standard in the caller context
                    }
                }
            }
            onProgress(1.0f)
            true
        } catch (e: Exception) {
            logger.e(TAG, "Failed to download SAF file: $documentUri", e)
            false
        }
    }

    fun extractTreeUriFromDocumentUri(documentUri: String): String? {
        val uri = documentUri.toUri()
        val path = uri.path ?: return null
        val treeIndex = path.indexOf("/tree/")
        if (treeIndex == -1) return null
        val docIndex = path.indexOf("/document/", treeIndex)
        val treePath = if (docIndex != -1) path.substring(0, docIndex) else path
        return uri.buildUpon().path(treePath).build().toString()
    }
}
