package com.example.gostalk.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _ttsLanguageFlow = MutableStateFlow(prefs.getString(KEY_TTS_LANGUAGE, null))
    val ttsLanguageFlow: StateFlow<String?> = _ttsLanguageFlow.asStateFlow()

    var ttsLanguage: String?
        get() = prefs.getString(KEY_TTS_LANGUAGE, null) // null bedeutet "System default"
        set(value) {
            prefs.edit().putString(KEY_TTS_LANGUAGE, value).apply()
            _ttsLanguageFlow.value = value
        }

    companion object {
        private const val PREFS_NAME = "gostalk_settings"
        private const val KEY_TTS_LANGUAGE = "tts_language"
    }
}
