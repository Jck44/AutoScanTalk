package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Use case for managing action logs (timestamping, limiting size, and persistence).
 */
class ActionLogUseCase @javax.inject.Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {
    private val gson = Gson()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun loadSavedLogs(): List<String> {
        if (!settingsRepository.persistActionLogs) return emptyList()
        
        val savedJson = settingsRepository.actionLogsStorage
        return if (!savedJson.isNullOrBlank()) {
            try {
                gson.fromJson(savedJson, Array<String>::class.java).toList()
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
            settingsRepository.actionLogsStorage = gson.toJson(updatedActions)
        }
        
        return updatedActions
    }

    fun clearLogs() {
        if (settingsRepository.persistActionLogs) {
            settingsRepository.actionLogsStorage = "[]"
        }
    }
}
