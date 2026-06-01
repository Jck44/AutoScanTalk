package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_KEEP_SCREEN_ON_USER_MODE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_USER_MODE_SCREEN_BEHAVIOR
import kotlinx.coroutines.flow.StateFlow

class UserSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _keepScreenOnUserMode = BooleanSetting(KEY_KEEP_SCREEN_ON_USER_MODE, true)
    private val _userModeScreenBehavior = NonNullStringSetting(KEY_USER_MODE_SCREEN_BEHAVIOR, "NORMAL")
    private val _statsRetentionDays = IntSetting(SettingsConstants.KEY_STATS_RETENTION_DAYS, 90)
    private val _statsAggregationHours = IntSetting(SettingsConstants.KEY_STATS_AGGREGATION_HOURS, 3)

    val keepScreenOnUserModeFlow = _keepScreenOnUserMode.flow
    val userModeScreenBehaviorFlow = _userModeScreenBehavior.flow
    val statsRetentionDaysFlow = _statsRetentionDays.flow
    val statsAggregationHoursFlow = _statsAggregationHours.flow

    var keepScreenOnUserMode: Boolean by _keepScreenOnUserMode
    var userModeScreenBehavior: String by _userModeScreenBehavior
    var statsRetentionDays: Int by _statsRetentionDays
    var statsAggregationHours: Int by _statsAggregationHours


    override fun refresh() {
        _keepScreenOnUserMode.refresh()
        _userModeScreenBehavior.refresh()
        _statsRetentionDays.refresh()
        _statsAggregationHours.refresh()
    }
}
