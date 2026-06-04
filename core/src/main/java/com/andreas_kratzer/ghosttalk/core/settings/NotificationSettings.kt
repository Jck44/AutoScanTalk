package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface NotificationSettings {
    val isNotificationReadingEnabledFlow: StateFlow<Boolean>
    val monitoredNotificationAppsFlow: StateFlow<Set<String>>

    var isNotificationReadingEnabled: Boolean
    var monitoredNotificationApps: Set<String>

    val autoReadModeFlow: StateFlow<AutoReadMode>
    var autoReadMode: AutoReadMode

    val autoReadOnlyInUserModeFlow: StateFlow<Boolean>
    var autoReadOnlyInUserMode: Boolean

    val autoReadInStandbyFlow: StateFlow<Boolean>
    var autoReadInStandby: Boolean
}
