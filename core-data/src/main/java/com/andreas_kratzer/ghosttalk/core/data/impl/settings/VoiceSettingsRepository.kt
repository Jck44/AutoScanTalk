package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_CUES_AUDIO_DEVICE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_TTS_AUDIO_DEVICE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_TTS_LANGUAGE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_TTS_VOICE_NAME
import kotlinx.coroutines.flow.StateFlow

class VoiceSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _ttsLanguage = StringSetting(KEY_TTS_LANGUAGE)
    private val _ttsVoiceName = StringSetting(KEY_TTS_VOICE_NAME)
    private val _ttsAudioDeviceAddress = StringSetting(KEY_TTS_AUDIO_DEVICE, isScoped = true)
    private val _cuesAudioDeviceAddress = StringSetting(KEY_CUES_AUDIO_DEVICE, isScoped = true)
    private val _ttsEngine = StringSetting(SettingsConstants.KEY_TTS_ENGINE)
    private val _googleTtsLanguage = StringSetting(SettingsConstants.KEY_GOOGLE_TTS_LANGUAGE)
    private val _googleTtsVoiceName = StringSetting(SettingsConstants.KEY_GOOGLE_TTS_VOICE_NAME)
    private val _elevenLabsTtsLanguage = StringSetting(SettingsConstants.KEY_ELEVENLABS_TTS_LANGUAGE)
    private val _elevenLabsTtsVoiceName = StringSetting(SettingsConstants.KEY_ELEVENLABS_TTS_VOICE_NAME)

    val ttsLanguageFlow = _ttsLanguage.flow
    val ttsVoiceNameFlow = _ttsVoiceName.flow
    val ttsAudioDeviceAddressFlow = _ttsAudioDeviceAddress.flow
    val cuesAudioDeviceAddressFlow = _cuesAudioDeviceAddress.flow
    val ttsEngineFlow = _ttsEngine.flow
    val googleTtsLanguageFlow = _googleTtsLanguage.flow
    val googleTtsVoiceNameFlow = _googleTtsVoiceName.flow
    val elevenLabsTtsLanguageFlow = _elevenLabsTtsLanguage.flow
    val elevenLabsTtsVoiceNameFlow = _elevenLabsTtsVoiceName.flow

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

    var ttsEngine: String?
        get() = _ttsEngine.value
        set(value) { _ttsEngine.value = value }

    var googleTtsLanguage: String?
        get() = _googleTtsLanguage.value
        set(value) { _googleTtsLanguage.value = value }

    var googleTtsVoiceName: String?
        get() = _googleTtsVoiceName.value
        set(value) { _googleTtsVoiceName.value = value }

    var elevenLabsTtsLanguage: String?
        get() = _elevenLabsTtsLanguage.value
        set(value) { _elevenLabsTtsLanguage.value = value }

    var elevenLabsTtsVoiceName: String?
        get() = _elevenLabsTtsVoiceName.value
        set(value) { _elevenLabsTtsVoiceName.value = value }

    override fun refresh() {
        _ttsLanguage.refresh()
        _ttsVoiceName.refresh()
        _ttsAudioDeviceAddress.refresh()
        _cuesAudioDeviceAddress.refresh()
        _ttsEngine.refresh()
        _googleTtsLanguage.refresh()
        _googleTtsVoiceName.refresh()
        _elevenLabsTtsLanguage.refresh()
        _elevenLabsTtsVoiceName.refresh()
    }
}
