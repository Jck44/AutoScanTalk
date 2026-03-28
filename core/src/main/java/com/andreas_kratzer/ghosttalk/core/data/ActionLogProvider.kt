package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.ActionLogEntry

interface ActionLogProvider {
    suspend fun loadSavedLogEntries(): List<ActionLogEntry>
    suspend fun loadSavedLogs(): List<String>
}
