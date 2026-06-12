package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_AUTO_READ_IN_STANDBY
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_AUTO_READ_MODE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_AUTO_READ_ONLY_IN_USER_MODE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_MONITORED_NOTIFICATION_APPS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_NOTIFICATION_READING_ENABLED
import com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode
import com.andreas_kratzer.ghosttalk.core.settings.NotificationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationSettingsRepository @Inject constructor(
    prefs: SharedPreferences,
    activeBookIdManager: ActiveBookIdManager
) : BaseSettingsRepository(prefs, activeBookIdManager.activeBookIdFlow), NotificationSettings {

    private val _isNotificationReadingEnabled = BooleanSetting(KEY_NOTIFICATION_READING_ENABLED, false, isScoped = false)
    private val _monitoredNotificationApps = StringSetSetting(KEY_MONITORED_NOTIFICATION_APPS, isScoped = false)
    private val _autoReadModeStr = NonNullStringSetting(KEY_AUTO_READ_MODE, AutoReadMode.OFF.name, isScoped = false)
    private val _autoReadOnlyInUserMode = BooleanSetting(KEY_AUTO_READ_ONLY_IN_USER_MODE, true, isScoped = false)
    private val _autoReadInStandby = BooleanSetting(KEY_AUTO_READ_IN_STANDBY, false, isScoped = false)

    override val isNotificationReadingEnabledFlow = _isNotificationReadingEnabled.flow
    override val monitoredNotificationAppsFlow = _monitoredNotificationApps.flow

    private val _autoReadModeFlow = MutableStateFlow(getAutoReadModeEnum(_autoReadModeStr.value))
    override val autoReadModeFlow: StateFlow<AutoReadMode> = _autoReadModeFlow.asStateFlow()

    override val autoReadOnlyInUserModeFlow = _autoReadOnlyInUserMode.flow
    override val autoReadInStandbyFlow = _autoReadInStandby.flow

    override var isNotificationReadingEnabled: Boolean by _isNotificationReadingEnabled
    override var monitoredNotificationApps: Set<String> by _monitoredNotificationApps

    override var autoReadMode: AutoReadMode
        get() = getAutoReadModeEnum(_autoReadModeStr.value)
        set(value) {
            _autoReadModeStr.value = value.name
            _autoReadModeFlow.value = value
        }

    override var autoReadOnlyInUserMode: Boolean by _autoReadOnlyInUserMode
    override var autoReadInStandby: Boolean by _autoReadInStandby

    private fun getAutoReadModeEnum(name: String): AutoReadMode {
        return try {
            AutoReadMode.valueOf(name)
        } catch (_: Exception) {
            AutoReadMode.OFF
        }
    }

    override fun refresh() {
        _isNotificationReadingEnabled.refresh()
        _monitoredNotificationApps.refresh()
        _autoReadModeStr.refresh()
        _autoReadModeFlow.value = getAutoReadModeEnum(_autoReadModeStr.value)
        _autoReadOnlyInUserMode.refresh()
        _autoReadInStandby.refresh()
    }
}
