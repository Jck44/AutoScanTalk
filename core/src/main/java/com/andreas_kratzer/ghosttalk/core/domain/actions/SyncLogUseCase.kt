package com.andreas_kratzer.ghosttalk.core.domain.actions

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.model.SyncLogEntry
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Use case for managing cloud sync logs.
 */
class SyncLogUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) : SyncLogProvider {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    private val logLimit = 50 // Limit sync logs to 50 entries

    suspend fun loadSavedLogEntries(): List<SyncLogEntry> {
        val savedJson = settingsRepository.syncLogsStorage
        return if (!savedJson.isNullOrBlank()) {
            try {
                Json.decodeFromString<List<SyncLogEntry>>(savedJson)
            } catch (e: Exception) {
                logger.e("SyncLogUseCase", "Error parsing stored sync logs", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    override suspend fun addLogEntry(
        message: String,
        bookId: String?,
        bookName: String?,
        isError: Boolean
    ) {
        val entry = SyncLogEntry(
            message = message,
            timestamp = System.currentTimeMillis(),
            bookId = bookId,
            bookName = bookName,
            isError = isError
        )
        
        val currentEntries = loadSavedLogEntries().toMutableList()
        currentEntries.add(0, entry)
        
        if (currentEntries.size > logLimit) {
            val toRemove = currentEntries.size - logLimit
            repeat(toRemove) {
                currentEntries.removeAt(currentEntries.size - 1)
            }
        }

        settingsRepository.syncLogsStorage = Json.encodeToString(currentEntries)
    }

    override suspend fun loadSavedLogs(): List<String> {
        return loadSavedLogEntries().map { formatEntryForDisplay(it) }
    }

    fun formatEntryForDisplay(entry: SyncLogEntry): String {
        val entryDate = Date(entry.timestamp)
        val today = Date()
        val sdfDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        
        val prefix = if (sdfDate.format(entryDate) == sdfDate.format(today)) {
            "[${timeFormat.format(entryDate)}]"
        } else {
            "[${dateFormat.format(entryDate)}]"
        }
        
        val bookContext = if (entry.bookName != null) " (${entry.bookName})" else ""
        return "$prefix$bookContext ${entry.message}"
    }

    override suspend fun clearLogs() {
        settingsRepository.syncLogsStorage = "[]"
    }
}
