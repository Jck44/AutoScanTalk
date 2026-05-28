package com.andreas_kratzer.ghosttalk.core.settings

interface ImportExportSettings {
    var syncLogsStorage: String?
    val syncLogsStorageFlow: kotlinx.coroutines.flow.StateFlow<String?>
}
