package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.andreas_kratzer.ghosttalk.core.model.ActionLogEntry
import com.andreas_kratzer.ghosttalk.core.data.ActionLogProvider
import javax.inject.Inject

/**
 * Use case for managing action logs (timestamping, limiting size, and persistence).
 */
class ActionLogUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) : ActionLogProvider {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    suspend fun loadSavedLogEntries(): List<ActionLogEntry> {
        if (!settingsRepository.persistActionLogs) return emptyList()
        
        val savedJson = settingsRepository.actionLogsStorage
        return if (!savedJson.isNullOrBlank()) {
            try {
                // Try to migrate from old List<String> format if needed
                val element = Json.parseToJsonElement(savedJson)
                if (element is kotlinx.serialization.json.JsonArray) {
                    val first = element.getOrNull(0)
                    if (first is kotlinx.serialization.json.JsonPrimitive && first.isString) {
                        // Old format: List<String>
                        val strings = Json.decodeFromString<List<String>>(savedJson)
                        return strings.map { ActionLogEntry(it, System.currentTimeMillis()) }
                    }
                }
                Json.decodeFromString<List<ActionLogEntry>>(savedJson)
            } catch (e: Exception) {
                logger.e("ActionLogUseCase", "Error parsing stored action logs", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    override suspend fun loadSavedLogs(): List<String> {
        return loadSavedLogEntries().map { formatEntryForDisplay(it) }
    }

    fun formatAndAddEntry(actionText: String, currentEntries: List<ActionLogEntry>, limit: Int): List<ActionLogEntry> {
        val entry = ActionLogEntry(actionText, System.currentTimeMillis())
        
        val updatedEntries = currentEntries.toMutableList()
        updatedEntries.add(0, entry)
        if (updatedEntries.size > limit) {
            val toRemove = updatedEntries.size - limit
            repeat(toRemove) {
                updatedEntries.removeAt(updatedEntries.size - 1)
            }
        }

        if (settingsRepository.persistActionLogs) {
            settingsRepository.actionLogsStorage = Json.encodeToString(updatedEntries)
        }
        
        return updatedEntries
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

    fun clearLogs() {
        if (settingsRepository.persistActionLogs) {
            settingsRepository.actionLogsStorage = "[]"
        }
    }
}
