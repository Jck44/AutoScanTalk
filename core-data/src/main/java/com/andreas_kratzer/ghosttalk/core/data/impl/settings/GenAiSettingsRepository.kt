package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GEMINI_API_KEY
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GEMINI_ENABLED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GEMINI_REDO_PREDICTION
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_GEMINI_TIMEOUT
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_USE_GEMINI_API_KEY
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_USE_LOCAL_GENERATIVE_AI
import kotlinx.coroutines.flow.StateFlow

class GenAiSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _isGeminiEnabled = BooleanSetting(KEY_GEMINI_ENABLED, false)
    private val _useLocalGenerativeAi = BooleanSetting(KEY_USE_LOCAL_GENERATIVE_AI, true)
    private val _geminiRedoPrediction = BooleanSetting(KEY_GEMINI_REDO_PREDICTION, false)
    private val _geminiTimeout = LongSetting(KEY_GEMINI_TIMEOUT, 6000L)
    private val _geminiApiKey = StringSetting(KEY_GEMINI_API_KEY)
    private val _useGeminiApiKey = BooleanSetting(KEY_USE_GEMINI_API_KEY, false)

    val isGeminiEnabledFlow = _isGeminiEnabled.flow
    val useLocalGenerativeAiFlow = _useLocalGenerativeAi.flow
    val geminiRedoPredictionFlow = _geminiRedoPrediction.flow
    val geminiTimeoutFlow = _geminiTimeout.flow
    val geminiApiKeyFlow = _geminiApiKey.flow
    val useGeminiApiKeyFlow = _useGeminiApiKey.flow

    var isGeminiEnabled: Boolean by _isGeminiEnabled
    var useLocalGenerativeAi: Boolean by _useLocalGenerativeAi
    var geminiRedoPrediction: Boolean by _geminiRedoPrediction
    var geminiTimeout: Long by _geminiTimeout
    var geminiApiKey: String? by _geminiApiKey
    var useGeminiApiKey: Boolean by _useGeminiApiKey


    override fun refresh() {
        _isGeminiEnabled.refresh()
        _useLocalGenerativeAi.refresh()
        _geminiRedoPrediction.refresh()
        _geminiTimeout.refresh()
        _geminiApiKey.refresh()
        _useGeminiApiKey.refresh()
    }
}
