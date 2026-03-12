package com.andreas_kratzer.ghosttalk.core.data

interface ActionLogProvider {
    suspend fun loadSavedLogs(): List<String>
}
