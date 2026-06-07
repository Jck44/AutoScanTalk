package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.provider.DocumentsContract
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
        val resultList = mutableListOf<RemoteSyncFile>()
        
        try {
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex)
                    val modifiedTime = cursor.getLong(modifiedIndex)
                    
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    resultList.add(
                        RemoteSyncFile(
                            id = docUri.toString(),
                            name = name,
                            description = name,
                            modifiedTime = modifiedTime
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Fallback to cached listFiles
            val files = root.listFiles()
            return files.map { doc ->
                RemoteSyncFile(
                    id = doc.uri.toString(),
                    name = doc.name ?: "",
                    description = doc.name,
                    modifiedTime = doc.lastModified()
                )
            }
        }
        return resultList
    }

    override suspend fun uploadFile(
        tempFile: File,
        mimeType: String,
        description: String?,
        properties: Map<String, String>?,
        onProgress: (Float) -> Unit
    ): String? {
        val root = rootDoc ?: return null
        
        // If file already exists, delete it first to prevent duplicates (SAF createDocument appends suffixes)
        val existing = listFiles().find { it.name == tempFile.name }
        if (existing != null) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, existing.id.toUri())
            } catch (_: Exception) {
                root.findFile(tempFile.name)?.delete()
            }
        }
        
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
        properties: Map<String, String>?,
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
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val name = cursor.getString(nameIndex)
                    val modifiedTime = cursor.getLong(modifiedIndex)
                    
                    return RemoteSyncFile(
                        id = fileId,
                        name = name,
                        description = name,
                        modifiedTime = modifiedTime
                    )
                }
            }
        } catch (_: Exception) {}
        
        val doc = DocumentFile.fromSingleUri(context, uri) ?: return null
        if (!doc.exists()) return null
        return RemoteSyncFile(
            id = doc.uri.toString(),
            name = doc.name ?: "",
            description = doc.name,
            modifiedTime = doc.lastModified()
        )
    }

    override suspend fun deleteFile(fileId: String): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, fileId.toUri())
            true
        } catch (_: Exception) {
            try {
                val doc = DocumentFile.fromSingleUri(context, fileId.toUri())
                doc?.delete() ?: false
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun updateProperties(fileId: String, properties: Map<String, String>): Boolean {
        return false
    }
}
