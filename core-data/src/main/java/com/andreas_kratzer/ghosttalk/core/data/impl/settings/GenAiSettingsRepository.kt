package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GEMINI_API_KEY
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GEMINI_ENABLED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GEMINI_REDO_PREDICTION
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GEMINI_TIMEOUT
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GEMINI_VERIFIED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_HAS_ACCEPTED_PAGE_SPLIT_OPT_IN
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_USE_GEMINI_API_KEY
import com.andreas_kratzer.ghosttalk.core.settings.GenAiSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenAiSettingsRepository @Inject constructor(
    prefs: SharedPreferences,
    activeBookIdManager: ActiveBookIdManager,
    @ApplicationContext private val context: android.content.Context,
    private val advancedSettings: AdvancedSettingsRepository
) : BaseSettingsRepository(prefs, activeBookIdManager.activeBookIdFlow), GenAiSettings {

    override var isSmartPredictionEnabled: Boolean
        get() = advancedSettings.isSmartPredictionEnabled
        set(value) { advancedSettings.isSmartPredictionEnabled = value }
    override val isSmartPredictionEnabledFlow: StateFlow<Boolean>
        get() = advancedSettings.isSmartPredictionEnabledFlow

    override var showPageIdInLog: Boolean
        get() = advancedSettings.showPageIdInLog
        set(value) { advancedSettings.showPageIdInLog = value }
    override val showPageIdInLogFlow: StateFlow<Boolean>
        get() = advancedSettings.showPageIdInLogFlow

    private val _isGeminiEnabled = BooleanSetting(KEY_GEMINI_ENABLED, false)
    private val _geminiRedoPrediction = BooleanSetting(KEY_GEMINI_REDO_PREDICTION, false)
    private val _geminiTimeout = LongSetting(KEY_GEMINI_TIMEOUT, 6000L)
    private val _geminiApiKey = StringSetting(
        key = KEY_GEMINI_API_KEY,
        encrypt = { SecuritySettingsEncryptor.encryptLocal(it, context) },
        decrypt = { SecuritySettingsEncryptor.decryptLocal(it, context) }
    )
    private val _useGeminiApiKey = BooleanSetting(KEY_USE_GEMINI_API_KEY, false)
    private val _isGeminiVerified = BooleanSetting(KEY_GEMINI_VERIFIED, false)
    private val _hasAcceptedPageSplitOptIn = BooleanSetting(KEY_HAS_ACCEPTED_PAGE_SPLIT_OPT_IN, false)

    override val isGeminiEnabledFlow = _isGeminiEnabled.flow
    override val geminiRedoPredictionFlow = _geminiRedoPrediction.flow
    override val geminiTimeoutFlow = _geminiTimeout.flow
    override val geminiApiKeyFlow = _geminiApiKey.flow
    override val useGeminiApiKeyFlow = _useGeminiApiKey.flow
    override val isGeminiVerifiedFlow = _isGeminiVerified.flow
    override val hasAcceptedPageSplitOptInFlow = _hasAcceptedPageSplitOptIn.flow

    override var isGeminiEnabled: Boolean by _isGeminiEnabled
    override var geminiRedoPrediction: Boolean by _geminiRedoPrediction
    override var geminiTimeout: Long by _geminiTimeout
    override var geminiApiKey: String? by _geminiApiKey
    override var useGeminiApiKey: Boolean by _useGeminiApiKey
    override var isGeminiVerified: Boolean by _isGeminiVerified
    override var hasAcceptedPageSplitOptIn: Boolean by _hasAcceptedPageSplitOptIn

    override fun refresh() {
        _isGeminiEnabled.refresh()
        _geminiRedoPrediction.refresh()
        _geminiTimeout.refresh()
        _geminiApiKey.refresh()
        _useGeminiApiKey.refresh()
        _isGeminiVerified.refresh()
        _hasAcceptedPageSplitOptIn.refresh()
    }
}
