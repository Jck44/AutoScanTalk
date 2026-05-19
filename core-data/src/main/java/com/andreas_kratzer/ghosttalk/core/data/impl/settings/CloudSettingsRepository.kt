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
    private val _elevenLabsApiKey = StringSetting(SettingsConstants.KEY_ELEVENLABS_API_KEY)
    private val _elevenLabsModel = NonNullStringSetting(SettingsConstants.KEY_ELEVENLABS_MODEL, "eleven_multilingual_v2")
    private val _elevenLabsStability = FloatSetting(SettingsConstants.KEY_ELEVENLABS_STABILITY, 0.5f)
    private val _elevenLabsSimilarityBoost = FloatSetting(SettingsConstants.KEY_ELEVENLABS_SIMILARITY_BOOST, 0.75f)
    private val _elevenLabsTtsLanguage = StringSetting(SettingsConstants.KEY_ELEVENLABS_TTS_LANGUAGE)

    override val isCloudSyncEnabledFlow = _isCloudSyncEnabled.flow
    override val syncIntervalMinutesFlow = _syncIntervalMinutes.flow
    override val syncModeFlow = _syncMode.flow
    override val lastSuccessfulSyncTimeFlow = _lastSuccessfulSyncTime.flow
    override val elevenLabsApiKeyFlow = _elevenLabsApiKey.flow
    override val elevenLabsModelFlow = _elevenLabsModel.flow
    override val elevenLabsStabilityFlow = _elevenLabsStability.flow
    override val elevenLabsSimilarityBoostFlow = _elevenLabsSimilarityBoost.flow
    override val elevenLabsTtsLanguageFlow = _elevenLabsTtsLanguage.flow

    override var isCloudSyncEnabled: Boolean by _isCloudSyncEnabled
    override var syncIntervalMinutes: Long by _syncIntervalMinutes
    override var syncMode: String by _syncMode
    override var lastSuccessfulSyncTime: Long by _lastSuccessfulSyncTime
    override var elevenLabsApiKey: String? by _elevenLabsApiKey
    override var elevenLabsModel: String by _elevenLabsModel
    override var elevenLabsStability: Float by _elevenLabsStability
    override var elevenLabsSimilarityBoost: Float by _elevenLabsSimilarityBoost
    override var elevenLabsTtsLanguage: String? by _elevenLabsTtsLanguage


    override fun refresh() {
        _isCloudSyncEnabled.refresh()
        _syncIntervalMinutes.refresh()
        _syncMode.refresh()
        _lastSuccessfulSyncTime.refresh()
        _elevenLabsApiKey.refresh()
        _elevenLabsModel.refresh()
        _elevenLabsStability.refresh()
        _elevenLabsSimilarityBoost.refresh()
        _elevenLabsTtsLanguage.refresh()
    }
}
