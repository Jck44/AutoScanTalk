package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface NotificationSettings {
    val isNotificationReadingEnabledFlow: StateFlow<Boolean>
    val monitoredNotificationAppsFlow: StateFlow<Set<String>>

    var isNotificationReadingEnabled: Boolean
    var monitoredNotificationApps: Set<String>
}
