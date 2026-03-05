package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.speech.tts.Voice
import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.tts.GetAudioDevicesUseCase
import com.andreas_kratzer.ghosttalk.domain.tts.SetAudioDeviceUseCase
import com.andreas_kratzer.ghosttalk.domain.tts.SetTtsLanguageUseCase
import com.andreas_kratzer.ghosttalk.domain.tts.SetTtsVoiceUseCase
import com.andreas_kratzer.ghosttalk.domain.tts.SetTtsVolumeUseCase
import com.andreas_kratzer.ghosttalk.model.AudioOutputDevice
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsSettingsDelegate @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper,
    private val audioDeviceManager: AudioDeviceManager,
    private val setTtsLanguageUseCase: SetTtsLanguageUseCase,
    private val setTtsVoiceUseCase: SetTtsVoiceUseCase,
    private val setTtsVolumeUseCase: SetTtsVolumeUseCase,
    private val setAudioDeviceUseCase: SetAudioDeviceUseCase,
    private val getAudioDevicesUseCase: GetAudioDevicesUseCase
) {
    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private val _availableAudioDevices = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val availableAudioDevices: StateFlow<List<AudioOutputDevice>> = _availableAudioDevices.asStateFlow()

    fun initialize(scope: CoroutineScope, onVoiceFallback: (String, String?) -> Unit) {
        ttsHelper.setLanguageAndVoice(
            settingsRepository.ttsLanguage ?: "default",
            settingsRepository.ttsVoiceName
        )

        loadAvailableLanguages()
        loadAvailableVoices()
        loadAvailableAudioDevices()

        scope.launch {
            audioDeviceManager.availableDevicesFlow.collect {
                loadAvailableAudioDevices()
            }
        }

        ttsHelper.fallbackListener = object : TextToSpeechHelper.OnVoiceFallbackListener {
            override fun onVoiceFallback(originalVoice: String, fallbackVoice: String?, reason: String) {
                scope.launch {
                    onVoiceFallback(originalVoice, fallbackVoice)
                }
            }
        }
    }

    fun loadAvailableLanguages() {
        if (ttsHelper.isReady) {
            _availableLanguages.value = ttsHelper.getAvailableLanguages()
        }
    }

    fun loadAvailableVoices() {
        if (ttsHelper.isReady) {
            _availableVoices.value = ttsHelper.getAvailableVoices(settingsRepository.ttsLanguage ?: "default")
        }
    }

    fun loadAvailableAudioDevices() {
        _availableAudioDevices.value = getAudioDevicesUseCase.execute()
    }

    fun setTtsLanguage(languageTag: String) {
        setTtsLanguageUseCase(languageTag)
        loadAvailableVoices()
    }

    fun setTtsVoice(voiceName: String?) {
        setTtsVoiceUseCase(voiceName)
    }

    fun setTtsVolumeMultiplier(multiplier: Float) {
        setTtsVolumeUseCase.execute(multiplier, isForCues = false)
    }
    
    fun setCuesVolumeMultiplier(multiplier: Float) {
        setTtsVolumeUseCase.execute(multiplier, isForCues = true)
    }

    fun setTtsAudioDevice(address: String?) {
        setAudioDeviceUseCase.execute(address, isForCues = false)
    }

    fun setCuesAudioDevice(address: String?) {
        setAudioDeviceUseCase.execute(address, isForCues = true)
    }

    fun getResolvedDeviceName(savedAddress: String?): String {
        if (savedAddress.isNullOrBlank()) return "System-Standard (Automatisch)"
        
        val devices = _availableAudioDevices.value
        val exactMatch = devices.find { it.address == savedAddress }
        if (exactMatch != null) return exactMatch.name
        
        val persistentId = savedAddress.split("|").lastOrNull() ?: savedAddress
        val cachedName = settingsRepository.getDeviceName(persistentId)
        if (cachedName != null) return "$cachedName (Laden...)"
        
        return "System-Standard (Automatisch)"
    }

    fun shutdown() {
        ttsHelper.shutdown()
    }
}
