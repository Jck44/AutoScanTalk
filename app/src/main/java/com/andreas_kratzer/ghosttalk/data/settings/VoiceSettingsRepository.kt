package com.andreas_kratzer.ghosttalk.data.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_CUES_AUDIO_DEVICE
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_TTS_AUDIO_DEVICE
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_TTS_LANGUAGE
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_TTS_VOICE_NAME
import kotlinx.coroutines.flow.StateFlow

class VoiceSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _ttsLanguage = StringSetting(KEY_TTS_LANGUAGE)
    private val _ttsVoiceName = StringSetting(KEY_TTS_VOICE_NAME)
    private val _ttsAudioDeviceAddress = StringSetting(KEY_TTS_AUDIO_DEVICE, isScoped = true)
    private val _cuesAudioDeviceAddress = StringSetting(KEY_CUES_AUDIO_DEVICE, isScoped = true)

    val ttsLanguageFlow = _ttsLanguage.flow
    val ttsVoiceNameFlow = _ttsVoiceName.flow
    val ttsAudioDeviceAddressFlow = _ttsAudioDeviceAddress.flow
    val cuesAudioDeviceAddressFlow = _cuesAudioDeviceAddress.flow

    var ttsLanguage: String?
        get() = _ttsLanguage.value
        set(value) { _ttsLanguage.value = value }

    var ttsVoiceName: String?
        get() = _ttsVoiceName.value
        set(value) { _ttsVoiceName.value = value }

    var ttsAudioDeviceAddress: String?
        get() = _ttsAudioDeviceAddress.value
        set(value) { _ttsAudioDeviceAddress.value = value }

    var cuesAudioDeviceAddress: String?
        get() = _cuesAudioDeviceAddress.value
        set(value) { _cuesAudioDeviceAddress.value = value }

    override fun refresh() {
        _ttsLanguage.refresh()
        _ttsVoiceName.refresh()
        _ttsAudioDeviceAddress.refresh()
        _cuesAudioDeviceAddress.refresh()
    }
}
