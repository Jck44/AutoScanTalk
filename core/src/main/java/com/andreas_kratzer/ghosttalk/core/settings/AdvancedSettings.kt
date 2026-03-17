package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface AdvancedSettings {
    val persistActionLogsFlow: StateFlow<Boolean>
    val actionLogsStorageFlow: StateFlow<String?>
    val showTestButtonsFlow: StateFlow<Boolean>
    val showPageIdInLogFlow: StateFlow<Boolean>
    val smartPredictionDelayFlow: StateFlow<Long>
    val isSmartPredictionEnabledFlow: StateFlow<Boolean>
    val weatherCacheTimeoutFlow: StateFlow<Long>
    val actionLogLimitFlow: StateFlow<Int>

    var persistActionLogs: Boolean
    var actionLogsStorage: String?
    var showTestButtons: Boolean
    var showPageIdInLog: Boolean
    var smartPredictionDelay: Long
    var isSmartPredictionEnabled: Boolean
    var weatherCacheTimeout: Long
    var actionLogLimit: Int
}
