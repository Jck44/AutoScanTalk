package com.andreas_kratzer.ghosttalk.core.data

interface SyncLogProvider {
    suspend fun addLogEntry(
        message: String,
        bookId: String? = null,
        bookName: String? = null,
        isError: Boolean = false
    )
    suspend fun loadSavedLogs(): List<String>
    suspend fun clearLogs()
}
