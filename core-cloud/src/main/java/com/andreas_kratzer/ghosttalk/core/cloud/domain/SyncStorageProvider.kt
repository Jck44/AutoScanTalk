package com.andreas_kratzer.ghosttalk.core.cloud.domain

import java.io.File

interface SyncStorageProvider {
    suspend fun listFiles(): List<RemoteSyncFile>
    suspend fun uploadFile(tempFile: File, mimeType: String, description: String?, onProgress: (Float) -> Unit = {}): String?
    suspend fun updateFile(fileId: String, tempFile: File, mimeType: String, description: String?, onProgress: (Float) -> Unit = {}): Boolean
    suspend fun downloadFile(fileId: String, destFile: File, onProgress: (Float) -> Unit = {}): Boolean
    suspend fun getFileMetadata(fileId: String): RemoteSyncFile?
}

data class RemoteSyncFile(
    val id: String,
    val name: String,
    val description: String?,
    val modifiedTime: Long
)
