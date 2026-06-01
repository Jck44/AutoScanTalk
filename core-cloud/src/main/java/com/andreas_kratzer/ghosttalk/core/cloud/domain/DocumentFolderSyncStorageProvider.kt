package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File

class DocumentFolderSyncStorageProvider(
    private val context: Context,
    treeUriString: String
) : SyncStorageProvider {

    private val treeUri = treeUriString.toUri()
    private val rootDoc = DocumentFile.fromTreeUri(context, treeUri)

    override suspend fun listFiles(): List<RemoteSyncFile> {
        val root = rootDoc ?: return emptyList()
        val files = root.listFiles()
        return files.map { doc ->
            RemoteSyncFile(
                id = doc.uri.toString(),
                name = doc.name ?: "",
                description = doc.name, // SAF doesn't support custom descriptions, fallback to name
                modifiedTime = doc.lastModified()
            )
        }
    }

    override suspend fun uploadFile(
        tempFile: File,
        mimeType: String,
        description: String?,
        onProgress: (Float) -> Unit
    ): String? {
        val root = rootDoc ?: return null
        
        // If file already exists, delete it first to prevent duplicates (SAF createDocument appends suffixes)
        val existing = root.findFile(tempFile.name)
        existing?.delete()
        
        val doc = root.createFile(mimeType, tempFile.name) ?: return null
        return try {
            context.contentResolver.openOutputStream(doc.uri)?.use { os ->
                tempFile.inputStream().use { inputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val fileSize = tempFile.length()
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (fileSize > 0) {
                            onProgress(totalBytesRead.toFloat() / fileSize)
                        }
                    }
                }
            }
            onProgress(1.0f)
            doc.uri.toString()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun updateFile(
        fileId: String,
        tempFile: File,
        mimeType: String,
        description: String?,
        onProgress: (Float) -> Unit
    ): Boolean {
        val uri = fileId.toUri()
        val doc = DocumentFile.fromSingleUri(context, uri) ?: return false
        return try {
            context.contentResolver.openOutputStream(doc.uri, "rwt")?.use { os ->
                tempFile.inputStream().use { inputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val fileSize = tempFile.length()
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (fileSize > 0) {
                            onProgress(totalBytesRead.toFloat() / fileSize)
                        }
                    }
                }
            }
            onProgress(1.0f)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun downloadFile(
        fileId: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        val uri = fileId.toUri()
        val doc = DocumentFile.fromSingleUri(context, uri) ?: return false
        return try {
            context.contentResolver.openInputStream(doc.uri)?.use { inputStream ->
                destFile.outputStream().use { os ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val fileSize = doc.length()
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (fileSize > 0) {
                            onProgress(totalBytesRead.toFloat() / fileSize)
                        }
                    }
                }
            }
            onProgress(1.0f)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getFileMetadata(fileId: String): RemoteSyncFile? {
        val uri = fileId.toUri()
        val doc = DocumentFile.fromSingleUri(context, uri) ?: return null
        if (!doc.exists()) return null
        return RemoteSyncFile(
            id = doc.uri.toString(),
            name = doc.name ?: "",
            description = doc.name,
            modifiedTime = doc.lastModified()
        )
    }
}
