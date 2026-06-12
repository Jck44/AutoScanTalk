package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_KEEP_SCREEN_ON_USER_MODE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_USER_MODE_SCREEN_BEHAVIOR
import javax.inject.Inject
import javax.inject.Singleton
import com.andreas_kratzer.ghosttalk.core.settings.UserSettings

@Singleton
class UserSettingsRepository @Inject constructor(
    prefs: SharedPreferences,
    activeBookIdManager: ActiveBookIdManager
) : BaseSettingsRepository(prefs, activeBookIdManager.activeBookIdFlow), UserSettings {

    private val _keepScreenOnUserMode = BooleanSetting(KEY_KEEP_SCREEN_ON_USER_MODE, true)
    private val _userModeScreenBehavior = NonNullStringSetting(KEY_USER_MODE_SCREEN_BEHAVIOR, "NORMAL")
    private val _statsRetentionDays = IntSetting(SettingsConstants.KEY_STATS_RETENTION_DAYS, 90)
    private val _statsAggregationHours = IntSetting(SettingsConstants.KEY_STATS_AGGREGATION_HOURS, 3)
    private val _onlyRecordHardwareStats = BooleanSetting(SettingsConstants.KEY_ONLY_RECORD_HARDWARE_STATS, false)
    private val _firebaseAnalyticsEnabled = BooleanSetting(SettingsConstants.KEY_FIREBASE_ANALYTICS_ENABLED, true, isScoped = false)

    override val keepScreenOnUserModeFlow = _keepScreenOnUserMode.flow
    override val userModeScreenBehaviorFlow = _userModeScreenBehavior.flow
    override val statsRetentionDaysFlow = _statsRetentionDays.flow
    override val statsAggregationHoursFlow = _statsAggregationHours.flow
    override val onlyRecordHardwareStatsFlow = _onlyRecordHardwareStats.flow
    override val firebaseAnalyticsEnabledFlow = _firebaseAnalyticsEnabled.flow

    override var keepScreenOnUserMode: Boolean by _keepScreenOnUserMode
    override var userModeScreenBehavior: String by _userModeScreenBehavior
    override var statsRetentionDays: Int by _statsRetentionDays
    override var statsAggregationHours: Int by _statsAggregationHours
    override var onlyRecordHardwareStats: Boolean by _onlyRecordHardwareStats
    override var firebaseAnalyticsEnabled: Boolean by _firebaseAnalyticsEnabled


    override fun refresh() {
        _keepScreenOnUserMode.refresh()
        _userModeScreenBehavior.refresh()
        _statsRetentionDays.refresh()
        _statsAggregationHours.refresh()
        _onlyRecordHardwareStats.refresh()
        _firebaseAnalyticsEnabled.refresh()
    }
}
