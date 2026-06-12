package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import android.media.MediaRecorder
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_CUES_AUDIO_DEVICE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_RECORDING_AUDIO_SOURCE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_TTS_AUDIO_DEVICE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_TTS_LANGUAGE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_TTS_VOICE_NAME
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Provider
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import kotlinx.coroutines.flow.StateFlow

@Singleton
class VoiceSettingsRepository @Inject constructor(
    prefs: SharedPreferences,
    activeBookIdManager: ActiveBookIdManager,
    private val generalSettingsRepository: Provider<GeneralSettingsRepository>
) : BaseSettingsRepository(prefs, activeBookIdManager.activeBookIdFlow), TtsSettings {

    override var appLanguage: String?
        get() = generalSettingsRepository.get().appLanguage
        set(value) { generalSettingsRepository.get().appLanguage = value }
    override val appLanguageFlow: StateFlow<String?>
        get() = generalSettingsRepository.get().appLanguageFlow

    private val _ttsLanguage = StringSetting(KEY_TTS_LANGUAGE)
    private val _ttsVoiceName = StringSetting(KEY_TTS_VOICE_NAME)
    private val _ttsAudioDeviceAddress = StringSetting(KEY_TTS_AUDIO_DEVICE, isScoped = true)
    private val _cuesAudioDeviceAddress = StringSetting(KEY_CUES_AUDIO_DEVICE, isScoped = true)
    private val _ttsEngine = StringSetting(SettingsConstants.KEY_TTS_ENGINE)
    private val _googleTtsLanguage = StringSetting(SettingsConstants.KEY_GOOGLE_TTS_LANGUAGE)
    private val _googleTtsVoiceName = StringSetting(SettingsConstants.KEY_GOOGLE_TTS_VOICE_NAME)
    private val _elevenLabsTtsLanguage = StringSetting(SettingsConstants.KEY_ELEVENLABS_TTS_LANGUAGE)
    private val _elevenLabsTtsVoiceName = StringSetting(SettingsConstants.KEY_ELEVENLABS_TTS_VOICE_NAME)
    private val _recordingAudioSource = IntSetting(KEY_RECORDING_AUDIO_SOURCE, MediaRecorder.AudioSource.VOICE_COMMUNICATION, isScoped = false)
    private val _ttsPlaybackSpeed = FloatSetting(SettingsConstants.KEY_TTS_PLAYBACK_SPEED, 1.0f, isScoped = false)
    private val _preferredMainSpeakerName = StringSetting("preferred_main_speaker_name", isScoped = false)
    private val _preferredCueSpeakerName = StringSetting("preferred_cue_speaker_name", isScoped = false)

    override val ttsLanguageFlow = _ttsLanguage.flow
    override val ttsVoiceNameFlow = _ttsVoiceName.flow
    val ttsAudioDeviceAddressFlow = _ttsAudioDeviceAddress.flow
    val cuesAudioDeviceAddressFlow = _cuesAudioDeviceAddress.flow
    override val ttsEngineFlow = _ttsEngine.flow
    override val googleTtsLanguageFlow = _googleTtsLanguage.flow
    override val googleTtsVoiceNameFlow = _googleTtsVoiceName.flow
    override val elevenLabsTtsLanguageFlow = _elevenLabsTtsLanguage.flow
    override val elevenLabsTtsVoiceNameFlow = _elevenLabsTtsVoiceName.flow
    val recordingAudioSourceFlow = _recordingAudioSource.flow
    override val ttsPlaybackSpeedFlow = _ttsPlaybackSpeed.flow
    val preferredMainSpeakerNameFlow = _preferredMainSpeakerName.flow
    val preferredCueSpeakerNameFlow = _preferredCueSpeakerName.flow

    override var ttsLanguage: String? by _ttsLanguage
    override var ttsVoiceName: String? by _ttsVoiceName
    var ttsAudioDeviceAddress: String? by _ttsAudioDeviceAddress
    var cuesAudioDeviceAddress: String? by _cuesAudioDeviceAddress
    override var ttsEngine: String? by _ttsEngine
    override var googleTtsLanguage: String? by _googleTtsLanguage
    override var googleTtsVoiceName: String? by _googleTtsVoiceName
    override var elevenLabsTtsLanguage: String? by _elevenLabsTtsLanguage
    override var elevenLabsTtsVoiceName: String? by _elevenLabsTtsVoiceName
    var recordingAudioSource: Int by _recordingAudioSource
    override var ttsPlaybackSpeed: Float by _ttsPlaybackSpeed
    var preferredMainSpeakerName: String? by _preferredMainSpeakerName
    var preferredCueSpeakerName: String? by _preferredCueSpeakerName


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
        _recordingAudioSource.refresh()
        _ttsPlaybackSpeed.refresh()
        _preferredMainSpeakerName.refresh()
        _preferredCueSpeakerName.refresh()
    }
}
