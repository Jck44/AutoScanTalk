package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_CLOUD_SYNC_ENABLED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_LAST_SYNC_TIME
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SYNC_INTERVAL_MINUTES
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SYNC_MODE
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import kotlinx.coroutines.flow.StateFlow

class CloudSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow), CloudSettings {

    override val activeBookIdFlow: StateFlow<String?> = super.activeBookIdFlow
    override var activeBookId: String
        get() = super.activeBookId
        set(value) { /* Handled by SettingsRepositoryImpl */ }

    private val _isCloudSyncEnabled = BooleanSetting(KEY_CLOUD_SYNC_ENABLED, false)
    private val _syncIntervalMinutes = LongSetting(KEY_SYNC_INTERVAL_MINUTES, 15L)
    private val _syncMode = NonNullStringSetting(KEY_SYNC_MODE, "TWO_WAY")
    private val _lastSuccessfulSyncTime = LongSetting(KEY_LAST_SYNC_TIME, 0L)
    private val _googleHomeProjectId = NonNullStringSetting(SettingsConstants.KEY_GOOGLE_HOME_PROJECT_ID, "")

    override val isCloudSyncEnabledFlow = _isCloudSyncEnabled.flow
    override val syncIntervalMinutesFlow = _syncIntervalMinutes.flow
    override val syncModeFlow = _syncMode.flow
    override val lastSuccessfulSyncTimeFlow = _lastSuccessfulSyncTime.flow
    override val googleHomeProjectIdFlow = _googleHomeProjectId.flow

    override var isCloudSyncEnabled: Boolean
        get() = _isCloudSyncEnabled.value
        set(value) { _isCloudSyncEnabled.value = value }

    override var syncIntervalMinutes: Long
        get() = _syncIntervalMinutes.value
        set(value) { _syncIntervalMinutes.value = value }

    override var syncMode: String
        get() = _syncMode.value
        set(value) { _syncMode.value = value }

    override var lastSuccessfulSyncTime: Long
        get() = _lastSuccessfulSyncTime.value
        set(value) { _lastSuccessfulSyncTime.value = value }

    override var googleHomeProjectId: String
        get() = _googleHomeProjectId.value
        set(value) { _googleHomeProjectId.value = value }
        

    override fun refresh() {
        _isCloudSyncEnabled.refresh()
        _syncIntervalMinutes.refresh()
        _syncMode.refresh()
        _lastSuccessfulSyncTime.refresh()
        _googleHomeProjectId.refresh()
    }
}
