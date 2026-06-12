package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_BACKGROUND_LOCATION_ENABLED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_BACKGROUND_LOCATION_INTERVAL
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_BACKGROUND_WEATHER_ENABLED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_BACKGROUND_WEATHER_INTERVAL
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_LOG_IGNORED_ACTIONS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_LOG_STOP_ACTIONS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_PERSIST_ACTION_LOGS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SHOW_PAGE_ID_IN_LOG
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SHOW_TEST_BUTTONS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SMART_PREDICTION_DELAY
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SMART_PREDICTION_ENABLED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_WEATHER_CACHE_TIMEOUT
import com.andreas_kratzer.ghosttalk.core.settings.AdvancedSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdvancedSettingsRepository @Inject constructor(
    prefs: SharedPreferences,
    activeBookIdManager: ActiveBookIdManager
) : BaseSettingsRepository(prefs, activeBookIdManager.activeBookIdFlow), AdvancedSettings {

    private val _persistActionLogs = BooleanSetting(KEY_PERSIST_ACTION_LOGS, true, isScoped = false)
    private val _showTestButtons = BooleanSetting(KEY_SHOW_TEST_BUTTONS, false, isScoped = false)
    private val _showPageIdInLog = BooleanSetting(KEY_SHOW_PAGE_ID_IN_LOG, false, isScoped = false)
    private val _smartPredictionDelay = LongSetting(KEY_SMART_PREDICTION_DELAY, 2000L)
    private val _isSmartPredictionEnabled = BooleanSetting(KEY_SMART_PREDICTION_ENABLED, true)
    private val _weatherCacheTimeout = LongSetting(KEY_WEATHER_CACHE_TIMEOUT, 60L, isScoped = false)
    private val _actionLogLimit = IntSetting(SettingsConstants.KEY_ACTION_LOG_LIMIT, 100)
    private val _logIgnoredActions = BooleanSetting(KEY_LOG_IGNORED_ACTIONS, false)
    private val _logStopActions = BooleanSetting(KEY_LOG_STOP_ACTIONS, false)
    private val _backgroundLocationEnabled = BooleanSetting(KEY_BACKGROUND_LOCATION_ENABLED, false, isScoped = false)
    private val _backgroundLocationInterval = LongSetting(KEY_BACKGROUND_LOCATION_INTERVAL, 1L, isScoped = false)
    private val _backgroundWeatherEnabled = BooleanSetting(KEY_BACKGROUND_WEATHER_ENABLED, false, isScoped = false)
    private val _backgroundWeatherInterval = LongSetting(KEY_BACKGROUND_WEATHER_INTERVAL, 6L, isScoped = false)

    override val persistActionLogsFlow = _persistActionLogs.flow
    override val showTestButtonsFlow = _showTestButtons.flow
    override val showPageIdInLogFlow = _showPageIdInLog.flow
    override val smartPredictionDelayFlow = _smartPredictionDelay.flow
    override val isSmartPredictionEnabledFlow = _isSmartPredictionEnabled.flow
    override val weatherCacheTimeoutFlow = _weatherCacheTimeout.flow
    override val actionLogLimitFlow = _actionLogLimit.flow
    override val logIgnoredActionsFlow = _logIgnoredActions.flow
    override val logStopActionsFlow = _logStopActions.flow
    override val backgroundLocationEnabledFlow = _backgroundLocationEnabled.flow
    override val backgroundLocationIntervalFlow = _backgroundLocationInterval.flow
    override val backgroundWeatherEnabledFlow = _backgroundWeatherEnabled.flow
    override val backgroundWeatherIntervalFlow = _backgroundWeatherInterval.flow

    override var persistActionLogs: Boolean by _persistActionLogs
    override var showTestButtons: Boolean by _showTestButtons
    override var showPageIdInLog: Boolean by _showPageIdInLog
    override var smartPredictionDelay: Long by _smartPredictionDelay
    override var isSmartPredictionEnabled: Boolean by _isSmartPredictionEnabled
    override var weatherCacheTimeout: Long by _weatherCacheTimeout
    override var actionLogLimit: Int by _actionLogLimit
    override var logIgnoredActions: Boolean by _logIgnoredActions
    override var logStopActions: Boolean by _logStopActions
    override var backgroundLocationEnabled: Boolean by _backgroundLocationEnabled
    override var backgroundLocationInterval: Long by _backgroundLocationInterval
    override var backgroundWeatherEnabled: Boolean by _backgroundWeatherEnabled
    override var backgroundWeatherInterval: Long by _backgroundWeatherInterval


    override fun refresh() {
        _persistActionLogs.refresh()
        _showTestButtons.refresh()
        _showPageIdInLog.refresh()
        _smartPredictionDelay.refresh()
        _isSmartPredictionEnabled.refresh()
        _weatherCacheTimeout.refresh()
        _actionLogLimit.refresh()
        _logIgnoredActions.refresh()
        _logStopActions.refresh()
        _backgroundLocationEnabled.refresh()
        _backgroundLocationInterval.refresh()
        _backgroundWeatherEnabled.refresh()
        _backgroundWeatherInterval.refresh()
    }
}
