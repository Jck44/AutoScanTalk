package com.andreas_kratzer.ghosttalk.core.cloud.domain

import java.io.File

interface SyncStorageProvider {
    suspend fun listFiles(): List<RemoteSyncFile>
    suspend fun uploadFile(
        tempFile: File,
        mimeType: String,
        description: String?,
        properties: Map<String, String>? = null,
        onProgress: (Float) -> Unit = {}
    ): String?
    suspend fun updateFile(
        fileId: String,
        tempFile: File,
        mimeType: String,
        description: String?,
        properties: Map<String, String>? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean
    suspend fun downloadFile(fileId: String, destFile: File, onProgress: (Float) -> Unit = {}): Boolean
    suspend fun getFileMetadata(fileId: String): RemoteSyncFile?
    suspend fun deleteFile(fileId: String): Boolean
    suspend fun updateProperties(fileId: String, properties: Map<String, String>): Boolean
}

data class RemoteSyncFile(
    val id: String,
    val name: String,
    val description: String?,
    val modifiedTime: Long,
    val md5Checksum: String? = null,
    val version: Long? = null,
    val properties: Map<String, String>? = null
)
