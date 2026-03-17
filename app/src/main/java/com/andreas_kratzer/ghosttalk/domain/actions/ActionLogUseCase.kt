package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    override suspend fun loadSavedLogs(): List<String> {
        if (!settingsRepository.persistActionLogs) return emptyList()
        
        val savedJson = settingsRepository.actionLogsStorage
        return if (!savedJson.isNullOrBlank()) {
            try {
                Json.decodeFromString<List<String>>(savedJson)
            } catch (e: Exception) {
                logger.e("ActionLogUseCase", "Error parsing stored action logs", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun formatAndAddEntry(actionText: String, currentLogs: List<String>): List<String> {
        val timeString = timeFormat.format(Date())
        val entry = "[$timeString] $actionText"
        
        val updatedActions = currentLogs.toMutableList()
        updatedActions.add(0, entry)
        if (updatedActions.size > 100) {
            updatedActions.removeLast()
        }

        if (settingsRepository.persistActionLogs) {
            settingsRepository.actionLogsStorage = Json.encodeToString(updatedActions)
        }
        
        return updatedActions
    }

    fun clearLogs() {
        if (settingsRepository.persistActionLogs) {
            settingsRepository.actionLogsStorage = "[]"
        }
    }
}
