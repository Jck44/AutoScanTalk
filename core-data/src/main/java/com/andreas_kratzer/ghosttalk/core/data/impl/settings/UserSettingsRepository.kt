package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_KEEP_SCREEN_ON_USER_MODE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_USER_MODE_SCREEN_BEHAVIOR
import javax.inject.Inject

class UserSettingsRepository @Inject constructor(
    prefs: SharedPreferences,
    activeBookIdManager: ActiveBookIdManager
) : BaseSettingsRepository(prefs, activeBookIdManager.activeBookIdFlow) {

    private val _keepScreenOnUserMode = BooleanSetting(KEY_KEEP_SCREEN_ON_USER_MODE, true)
    private val _userModeScreenBehavior = NonNullStringSetting(KEY_USER_MODE_SCREEN_BEHAVIOR, "NORMAL")
    private val _statsRetentionDays = IntSetting(SettingsConstants.KEY_STATS_RETENTION_DAYS, 90)
    private val _statsAggregationHours = IntSetting(SettingsConstants.KEY_STATS_AGGREGATION_HOURS, 3)
    private val _onlyRecordHardwareStats = BooleanSetting(SettingsConstants.KEY_ONLY_RECORD_HARDWARE_STATS, false)
    private val _firebaseAnalyticsEnabled = BooleanSetting(SettingsConstants.KEY_FIREBASE_ANALYTICS_ENABLED, true, isScoped = false)

    val keepScreenOnUserModeFlow = _keepScreenOnUserMode.flow
    val userModeScreenBehaviorFlow = _userModeScreenBehavior.flow
    val statsRetentionDaysFlow = _statsRetentionDays.flow
    val statsAggregationHoursFlow = _statsAggregationHours.flow
    val onlyRecordHardwareStatsFlow = _onlyRecordHardwareStats.flow
    val firebaseAnalyticsEnabledFlow = _firebaseAnalyticsEnabled.flow

    var keepScreenOnUserMode: Boolean by _keepScreenOnUserMode
    var userModeScreenBehavior: String by _userModeScreenBehavior
    var statsRetentionDays: Int by _statsRetentionDays
    var statsAggregationHours: Int by _statsAggregationHours
    var onlyRecordHardwareStats: Boolean by _onlyRecordHardwareStats
    var firebaseAnalyticsEnabled: Boolean by _firebaseAnalyticsEnabled


    override fun refresh() {
        _keepScreenOnUserMode.refresh()
        _userModeScreenBehavior.refresh()
        _statsRetentionDays.refresh()
        _statsAggregationHours.refresh()
        _onlyRecordHardwareStats.refresh()
        _firebaseAnalyticsEnabled.refresh()
    }
}
