package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface UserSettings {
    val keepScreenOnUserModeFlow: StateFlow<Boolean>
    val userModeScreenBehaviorFlow: StateFlow<String>
    val statsRetentionDaysFlow: StateFlow<Int>
    val statsAggregationHoursFlow: StateFlow<Int>
    val onlyRecordHardwareStatsFlow: StateFlow<Boolean>

    var keepScreenOnUserMode: Boolean
    var userModeScreenBehavior: String
    var statsRetentionDays: Int
    var statsAggregationHours: Int
    var onlyRecordHardwareStats: Boolean
}
