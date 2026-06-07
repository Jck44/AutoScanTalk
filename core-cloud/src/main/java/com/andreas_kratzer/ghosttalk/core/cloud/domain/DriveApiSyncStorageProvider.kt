package com.andreas_kratzer.ghosttalk.core.cloud.domain

import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.google.api.services.drive.Drive
import java.io.File

class DriveApiSyncStorageProvider(
    drive: Drive,
    private val folderId: String
) : SyncStorageProvider {

    private val helper = DriveServiceHelper(drive)

    override suspend fun listFiles(): List<RemoteSyncFile> {
        val files = helper.listFiles(folderId)
        return files.map { file ->
            RemoteSyncFile(
                id = file.id,
                name = file.name,
                description = file.description,
                modifiedTime = file.modifiedTime?.value ?: 0L,
                md5Checksum = file.md5Checksum,
                version = file.version,
                properties = file.properties
            )
        }
    }

    override suspend fun uploadFile(
        tempFile: File,
        mimeType: String,
        description: String?,
        properties: Map<String, String>?,
        onProgress: (Float) -> Unit
    ): String? {
        return helper.uploadFile(folderId, tempFile, mimeType, description, properties, onProgress)
    }

    override suspend fun updateFile(
        fileId: String,
        tempFile: File,
        mimeType: String,
        description: String?,
        properties: Map<String, String>?,
        onProgress: (Float) -> Unit
    ): Boolean {
        return helper.updateFile(fileId, tempFile, mimeType, description, properties, onProgress)
    }

    override suspend fun downloadFile(
        fileId: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        return helper.downloadFile(fileId, destFile, onProgress)
    }

    override suspend fun getFileMetadata(fileId: String): RemoteSyncFile? {
        val file = helper.getFileMetadata(fileId) ?: return null
        return RemoteSyncFile(
            id = file.id,
            name = file.name,
            description = file.description,
            modifiedTime = file.modifiedTime?.value ?: 0L,
            md5Checksum = file.md5Checksum,
            version = file.version,
            properties = file.properties
        )
    }

    override suspend fun deleteFile(fileId: String): Boolean {
        return helper.deleteFile(fileId)
    }
}
