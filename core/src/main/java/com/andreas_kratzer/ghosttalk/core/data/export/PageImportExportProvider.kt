package com.andreas_kratzer.ghosttalk.core.data.export

interface PageImportExportProvider {
    suspend fun exportBookToJson(bookId: String): String
    suspend fun importFromJson(
        jsonString: String,
        bookId: String,
        regenerateIds: Boolean = false,
        restoreSyncSettings: Boolean = false
    ): Result<Int>
    suspend fun exportBookToZip(bookId: String, outputStream: java.io.OutputStream)
    suspend fun importFromZip(
        inputStream: java.io.InputStream,
        bookId: String,
        regenerateIds: Boolean = false,
        restoreSyncSettings: Boolean = false
    ): Result<Int>
    suspend fun extractBookIdFromJson(jsonString: String): String?
    suspend fun importCloudBackup(jsonString: String, cloudFileId: String?): Result<String>
}
