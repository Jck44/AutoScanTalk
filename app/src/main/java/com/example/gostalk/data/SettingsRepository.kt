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

    private val _scanDelayFlow = MutableStateFlow(prefs.getLong(KEY_SCAN_DELAY_MILLIS, 1000L))
    val scanDelayFlow: StateFlow<Long> = _scanDelayFlow.asStateFlow()

    var ttsLanguage: String?
        get() = prefs.getString(KEY_TTS_LANGUAGE, null) // null bedeutet "System default"
        set(value) {
            prefs.edit().putString(KEY_TTS_LANGUAGE, value).apply()
            _ttsLanguageFlow.value = value
        }

    var autoStartScanning: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_SCANNING, true) // Standardmäßig eingeschaltet
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_START_SCANNING, value).apply()
        }

    var scanDelayMillis: Long
        get() = prefs.getLong(KEY_SCAN_DELAY_MILLIS, 1000L) // Standardmäßig 1000ms
        set(value) {
            prefs.edit().putLong(KEY_SCAN_DELAY_MILLIS, value).apply()
            _scanDelayFlow.value = value
        }

    private val _defaultStartPageIdFlow = MutableStateFlow(prefs.getString(KEY_DEFAULT_START_PAGE_ID, null))
    val defaultStartPageIdFlow: StateFlow<String?> = _defaultStartPageIdFlow.asStateFlow()

    var defaultStartPageId: String?
        get() = prefs.getString(KEY_DEFAULT_START_PAGE_ID, null)
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_START_PAGE_ID, value).apply()
            _defaultStartPageIdFlow.value = value
        }

    private val _ttsVoiceNameFlow = MutableStateFlow(prefs.getString(KEY_TTS_VOICE_NAME, null))
    val ttsVoiceNameFlow: StateFlow<String?> = _ttsVoiceNameFlow.asStateFlow()

    var ttsVoiceName: String?
        get() = prefs.getString(KEY_TTS_VOICE_NAME, null)
        set(value) {
            prefs.edit().putString(KEY_TTS_VOICE_NAME, value).apply()
            _ttsVoiceNameFlow.value = value
        }

    companion object {
        private const val PREFS_NAME = "gostalk_settings"
        private const val KEY_TTS_LANGUAGE = "tts_language"
        private const val KEY_TTS_VOICE_NAME = "tts_voice_name"
        private const val KEY_AUTO_START_SCANNING = "auto_start_scanning"
        private const val KEY_SCAN_DELAY_MILLIS = "scan_delay_millis"
        private const val KEY_DEFAULT_START_PAGE_ID = "default_start_page_id"
    }
}
