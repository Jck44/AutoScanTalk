package com.andreas_kratzer.ghosttalk.core.settings

interface CloudSettings {
    var isCloudSyncEnabled: Boolean
    var syncIntervalMinutes: Long
    var syncMode: String
    var lastSuccessfulSyncTime: Long
    var activeBookId: String
    val activeBookIdFlow: kotlinx.coroutines.flow.StateFlow<String?>
}
