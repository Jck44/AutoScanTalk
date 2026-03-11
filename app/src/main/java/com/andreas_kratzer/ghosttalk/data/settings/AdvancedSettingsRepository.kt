package com.andreas_kratzer.ghosttalk.data.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_ACTION_LOGS_STORAGE
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_PERSIST_ACTION_LOGS
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_SHOW_PAGE_ID_IN_LOG
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_SHOW_TEST_BUTTONS
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_SMART_PREDICTION_DELAY
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_SMART_PREDICTION_ENABLED
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_WEATHER_CACHE_TIMEOUT
import kotlinx.coroutines.flow.StateFlow

class AdvancedSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _persistActionLogs = BooleanSetting(KEY_PERSIST_ACTION_LOGS, true, isScoped = false)
    private val _actionLogsStorage = StringSetting(KEY_ACTION_LOGS_STORAGE, isScoped = false)
    private val _showTestButtons = BooleanSetting(KEY_SHOW_TEST_BUTTONS, false, isScoped = false)
    private val _showPageIdInLog = BooleanSetting(KEY_SHOW_PAGE_ID_IN_LOG, false, isScoped = false)
    private val _smartPredictionDelay = LongSetting(KEY_SMART_PREDICTION_DELAY, 2000L)
    private val _isSmartPredictionEnabled = BooleanSetting(KEY_SMART_PREDICTION_ENABLED, false)
    private val _weatherCacheTimeout = LongSetting(KEY_WEATHER_CACHE_TIMEOUT, 60L, isScoped = false)

    val persistActionLogsFlow = _persistActionLogs.flow
    val actionLogsStorageFlow = _actionLogsStorage.flow
    val showTestButtonsFlow = _showTestButtons.flow
    val showPageIdInLogFlow = _showPageIdInLog.flow
    val smartPredictionDelayFlow = _smartPredictionDelay.flow
    val isSmartPredictionEnabledFlow = _isSmartPredictionEnabled.flow
    val weatherCacheTimeoutFlow = _weatherCacheTimeout.flow

    var persistActionLogs: Boolean
        get() = _persistActionLogs.value
        set(value) { _persistActionLogs.value = value }

    var actionLogsStorage: String?
        get() = _actionLogsStorage.value
        set(value) { _actionLogsStorage.value = value }

    var showTestButtons: Boolean
        get() = _showTestButtons.value
        set(value) { _showTestButtons.value = value }

    var showPageIdInLog: Boolean
        get() = _showPageIdInLog.value
        set(value) { _showPageIdInLog.value = value }

    var smartPredictionDelay: Long
        get() = _smartPredictionDelay.value
        set(value) { _smartPredictionDelay.value = value }

    var isSmartPredictionEnabled: Boolean
        get() = _isSmartPredictionEnabled.value
        set(value) { _isSmartPredictionEnabled.value = value }

    var weatherCacheTimeout: Long
        get() = _weatherCacheTimeout.value
        set(value) { _weatherCacheTimeout.value = value }

    override fun refresh() {
        _persistActionLogs.refresh()
        _actionLogsStorage.refresh()
        _showTestButtons.refresh()
        _showPageIdInLog.refresh()
        _smartPredictionDelay.refresh()
        _isSmartPredictionEnabled.refresh()
        _weatherCacheTimeout.refresh()
    }
}
