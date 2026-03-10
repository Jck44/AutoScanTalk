package com.andreas_kratzer.ghosttalk.data.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_GEMINI_ENABLED
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_GEMINI_REDO_PREDICTION
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_GEMINI_TIMEOUT
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_USE_LOCAL_GENERATIVE_AI
import kotlinx.coroutines.flow.StateFlow

class GenAiSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _isGeminiEnabled = BooleanSetting(KEY_GEMINI_ENABLED, false)
    private val _useLocalGenerativeAi = BooleanSetting(KEY_USE_LOCAL_GENERATIVE_AI, true)
    private val _geminiRedoPrediction = BooleanSetting(KEY_GEMINI_REDO_PREDICTION, false)
    private val _geminiTimeout = LongSetting(KEY_GEMINI_TIMEOUT, 6000L)

    val isGeminiEnabledFlow = _isGeminiEnabled.flow
    val useLocalGenerativeAiFlow = _useLocalGenerativeAi.flow
    val geminiRedoPredictionFlow = _geminiRedoPrediction.flow
    val geminiTimeoutFlow = _geminiTimeout.flow

    var isGeminiEnabled: Boolean
        get() = _isGeminiEnabled.value
        set(value) { _isGeminiEnabled.value = value }

    var useLocalGenerativeAi: Boolean
        get() = _useLocalGenerativeAi.value
        set(value) { _useLocalGenerativeAi.value = value }

    var geminiRedoPrediction: Boolean
        get() = _geminiRedoPrediction.value
        set(value) { _geminiRedoPrediction.value = value }

    var geminiTimeout: Long
        get() = _geminiTimeout.value
        set(value) { _geminiTimeout.value = value }

    override fun refresh() {
        _isGeminiEnabled.refresh()
        _useLocalGenerativeAi.refresh()
        _geminiRedoPrediction.refresh()
        _geminiTimeout.refresh()
    }
}
