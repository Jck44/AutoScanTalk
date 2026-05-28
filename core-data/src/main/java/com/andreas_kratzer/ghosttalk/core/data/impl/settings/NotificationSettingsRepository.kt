package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_MONITORED_NOTIFICATION_APPS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_NOTIFICATION_READING_ENABLED
import kotlinx.coroutines.flow.StateFlow

class NotificationSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _isNotificationReadingEnabled = BooleanSetting(KEY_NOTIFICATION_READING_ENABLED, false, isScoped = false)
    private val _monitoredNotificationApps = StringSetSetting(KEY_MONITORED_NOTIFICATION_APPS, isScoped = false)

    val isNotificationReadingEnabledFlow = _isNotificationReadingEnabled.flow
    val monitoredNotificationAppsFlow = _monitoredNotificationApps.flow

    var isNotificationReadingEnabled: Boolean by _isNotificationReadingEnabled
    var monitoredNotificationApps: Set<String> by _monitoredNotificationApps


    override fun refresh() {
        _isNotificationReadingEnabled.refresh()
        _monitoredNotificationApps.refresh()
    }
}
