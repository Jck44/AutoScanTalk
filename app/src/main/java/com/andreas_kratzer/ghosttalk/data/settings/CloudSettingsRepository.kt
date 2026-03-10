package com.andreas_kratzer.ghosttalk.data.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_CLOUD_SYNC_ENABLED
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_LAST_SYNC_TIME
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_SYNC_INTERVAL_MINUTES
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_SYNC_MODE
import kotlinx.coroutines.flow.StateFlow

class CloudSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _isCloudSyncEnabled = BooleanSetting(KEY_CLOUD_SYNC_ENABLED, false)
    private val _syncIntervalMinutes = LongSetting(KEY_SYNC_INTERVAL_MINUTES, 15L)
    private val _syncMode = NonNullStringSetting(KEY_SYNC_MODE, "TWO_WAY")
    private val _lastSuccessfulSyncTime = LongSetting(KEY_LAST_SYNC_TIME, 0L)

    val isCloudSyncEnabledFlow = _isCloudSyncEnabled.flow
    val syncIntervalMinutesFlow = _syncIntervalMinutes.flow
    val syncModeFlow = _syncMode.flow
    val lastSuccessfulSyncTimeFlow = _lastSuccessfulSyncTime.flow

    var isCloudSyncEnabled: Boolean
        get() = _isCloudSyncEnabled.value
        set(value) { _isCloudSyncEnabled.value = value }

    var syncIntervalMinutes: Long
        get() = _syncIntervalMinutes.value
        set(value) { _syncIntervalMinutes.value = value }

    var syncMode: String
        get() = _syncMode.value
        set(value) { _syncMode.value = value }

    var lastSuccessfulSyncTime: Long
        get() = _lastSuccessfulSyncTime.value
        set(value) { _lastSuccessfulSyncTime.value = value }

    override fun refresh() {
        _isCloudSyncEnabled.refresh()
        _syncIntervalMinutes.refresh()
        _syncMode.refresh()
        _lastSuccessfulSyncTime.refresh()
    }
}
