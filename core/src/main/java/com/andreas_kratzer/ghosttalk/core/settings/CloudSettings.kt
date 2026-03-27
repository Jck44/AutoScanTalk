package com.andreas_kratzer.ghosttalk.core.settings

interface CloudSettings {
    var isCloudSyncEnabled: Boolean
    val isCloudSyncEnabledFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    var syncIntervalMinutes: Long
    val syncIntervalMinutesFlow: kotlinx.coroutines.flow.StateFlow<Long>
    var syncMode: String
    val syncModeFlow: kotlinx.coroutines.flow.StateFlow<String>
    var lastSuccessfulSyncTime: Long
    val lastSuccessfulSyncTimeFlow: kotlinx.coroutines.flow.StateFlow<Long>
    var activeBookId: String
    val activeBookIdFlow: kotlinx.coroutines.flow.StateFlow<String?>

    var elevenLabsApiKey: String?
    val elevenLabsApiKeyFlow: kotlinx.coroutines.flow.StateFlow<String?>
}
