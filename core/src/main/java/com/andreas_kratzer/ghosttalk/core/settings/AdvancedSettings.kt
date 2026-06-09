package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface AdvancedSettings {
    val persistActionLogsFlow: StateFlow<Boolean>
    val showTestButtonsFlow: StateFlow<Boolean>
    val showPageIdInLogFlow: StateFlow<Boolean>
    val smartPredictionDelayFlow: StateFlow<Long>
    val isSmartPredictionEnabledFlow: StateFlow<Boolean>
    val weatherCacheTimeoutFlow: StateFlow<Long>
    val actionLogLimitFlow: StateFlow<Int>
    val logIgnoredActionsFlow: StateFlow<Boolean>
    val logStopActionsFlow: StateFlow<Boolean>
    val backgroundLocationEnabledFlow: StateFlow<Boolean>
    val backgroundLocationIntervalFlow: StateFlow<Long>
    val backgroundWeatherEnabledFlow: StateFlow<Boolean>
    val backgroundWeatherIntervalFlow: StateFlow<Long>

    var persistActionLogs: Boolean
    var showTestButtons: Boolean
    var showPageIdInLog: Boolean
    var smartPredictionDelay: Long
    var isSmartPredictionEnabled: Boolean
    var weatherCacheTimeout: Long
    var actionLogLimit: Int
    var logIgnoredActions: Boolean
    var logStopActions: Boolean
    var backgroundLocationEnabled: Boolean
    var backgroundLocationInterval: Long
    var backgroundWeatherEnabled: Boolean
    var backgroundWeatherInterval: Long
}
