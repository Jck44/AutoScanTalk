package com.andreas_kratzer.ghosttalk.core.domain.actions

import com.andreas_kratzer.ghosttalk.core.data.ActionLogProvider
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ActionLogEntry
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_LOG_SIZE = 500

/**
 * Use case for managing action logs (timestamping, limiting size, and persistence).
 */
@Singleton
class ActionLogUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bookRepository: com.andreas_kratzer.ghosttalk.core.data.BookRepository,
    private val logger: Logger
) : ActionLogProvider {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    private var cachedEntries: List<ActionLogEntry>? = null
    private val mutex = Mutex()

    override suspend fun loadSavedLogEntries(): List<ActionLogEntry> = mutex.withContextAndLock {
        cachedEntries?.let { return@withContextAndLock it }
        
        if (!settingsRepository.persistActionLogs) return@withContextAndLock emptyList()
        
        val activeBookId = settingsRepository.activeBookId
        val savedJson = if (activeBookId != null) {
            bookRepository.getBookById(activeBookId)?.actionLogsStorage
        } else {
            null
        }
        val entries: List<ActionLogEntry> = if (!savedJson.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val element = Json.parseToJsonElement(savedJson)
                    if (element is kotlinx.serialization.json.JsonArray) {
                        val first = element.getOrNull(0)
                        if (first is kotlinx.serialization.json.JsonPrimitive && first.isString) {
                            val strings = Json.decodeFromString<List<String>>(savedJson)
                            return@withContext strings.map { ActionLogEntry(it, System.currentTimeMillis()) }
                        }
                    }
                    Json.decodeFromString<List<ActionLogEntry>>(savedJson)
                } catch (e: Exception) {
                    logger.e("ActionLogUseCase", "Error parsing stored action logs", e)
                    emptyList()
                }
            }
        } else {
            emptyList()
        }
        
        // Cap at MAX_LOG_SIZE if coming from disk with more
        val capped = if (entries.size > MAX_LOG_SIZE) entries.take(MAX_LOG_SIZE) else entries
        cachedEntries = capped
        capped
    }

    private suspend fun <T> Mutex.withContextAndLock(block: suspend () -> T): T = withContext(Dispatchers.Default) {
        withLock { block() }
    }

    override suspend fun loadSavedLogs(): List<String> {
        return loadSavedLogEntries().map { formatEntryForDisplay(it) }
    }

    suspend fun formatAndAddEntry(
        actionText: String, 
        currentEntries: List<ActionLogEntry>, 
        limit: Int, 
        action: ButtonAction? = null, 
        label: String? = null
    ): List<ActionLogEntry> = mutex.withContextAndLock {
        val entry = ActionLogEntry(actionText, System.currentTimeMillis(), action, label)
        
        // Use the globally enforced limit, but respect lower limits if passed (though we usually want MAX_LOG_SIZE now)
        val finalLimit = minOf(limit, MAX_LOG_SIZE)
        
        val updatedEntries = currentEntries.toMutableList()
        updatedEntries.add(0, entry)
        if (updatedEntries.size > finalLimit) {
            val toRemove = updatedEntries.size - finalLimit
            repeat(toRemove) {
                updatedEntries.removeAt(updatedEntries.size - 1)
            }
        }

        cachedEntries = updatedEntries

        if (settingsRepository.persistActionLogs) {
            withContext(Dispatchers.IO) {
                val activeBookId = settingsRepository.activeBookId
                if (activeBookId != null) {
                    val book = bookRepository.getBookById(activeBookId)
                    if (book != null) {
                        bookRepository.updateBook(book.copy(actionLogsStorage = Json.encodeToString(updatedEntries)))
                    }
                }
            }
        }
        
        updatedEntries
    }

    fun formatEntryForDisplay(entry: ActionLogEntry): String {
        val entryDate = Date(entry.timestamp)
        val today = Date()
        val sdfDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        
        val prefix = if (sdfDate.format(entryDate) == sdfDate.format(today)) {
            "[${timeFormat.format(entryDate)}]"
        } else {
            "[${dateFormat.format(entryDate)}]"
        }
        
        return "$prefix ${entry.message}"
    }

    suspend fun clearLogs() = mutex.withLock {
        cachedEntries = emptyList()
        if (settingsRepository.persistActionLogs) {
            withContext(Dispatchers.IO) {
                val activeBookId = settingsRepository.activeBookId
                if (activeBookId != null) {
                    val book = bookRepository.getBookById(activeBookId)
                    if (book != null) {
                        bookRepository.updateBook(book.copy(actionLogsStorage = "[]"))
                    }
                }
            }
        }
    }
}
