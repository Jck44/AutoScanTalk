package com.andreas_kratzer.ghosttalk.core.data.export

import com.andreas_kratzer.ghosttalk.core.model.Page

interface PageImportExportProvider {
    suspend fun exportPageListToJson(pages: List<Page>): String
    suspend fun exportBookToJson(bookId: String, includeSettings: Boolean = true): String
    fun exportBookConfigToJson(bookId: String): String
    fun importBookConfigFromJson(json: String, bookId: String): Result<Unit>
    fun getBookConfigLastModified(bookId: String): Long
    suspend fun importFromJson(
        jsonString: String,
        bookId: String,
        regenerateIds: Boolean = false,
        restoreSyncSettings: Boolean = false
    ): Result<Int>
    suspend fun exportBookToZip(
        bookId: String, 
        outputStream: java.io.OutputStream,
        includeTtsCache: Boolean = true,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    )
    suspend fun importFromZip(
        inputStream: java.io.InputStream,
        bookId: String,
        regenerateIds: Boolean = false,
        restoreSyncSettings: Boolean = false,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Int>
    suspend fun extractBookIdFromJson(jsonString: String): String?
    suspend fun importCloudBackup(jsonString: String, cloudFileId: String?): Result<String>
    suspend fun importCloudBackupFromZip(
        inputStream: java.io.InputStream,
        cloudFileId: String?,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<String>
    suspend fun exportTtsCacheToZip(
        outputStream: java.io.OutputStream,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    )
    suspend fun importTtsCacheFromZip(
        inputStream: java.io.InputStream,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    )
    fun getTtsCacheLastModified(): Long

    suspend fun exportStatisticsToZip(
        bookId: String,
        outputStream: java.io.OutputStream
    )
    suspend fun importStatisticsFromZip(
        bookId: String,
        inputStream: java.io.InputStream
    )
    suspend fun getStatisticsLastModified(bookId: String): Long

    suspend fun exportAudioRecordingsToZip(
        outputStream: java.io.OutputStream,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    )
    suspend fun importAudioRecordingsFromZip(
        inputStream: java.io.InputStream,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    )
    fun getAudioRecordingsLastModified(): Long
}

