package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.speech.tts.Voice
import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
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
    private val audioDeviceManager: AudioDeviceManager
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
        val available = audioDeviceManager.getAvailableOutputDevices()
        
        available.forEach { device ->
            val persistentId = device.address.split("|").lastOrNull() ?: device.address
            settingsRepository.saveDeviceName(persistentId, device.name)
        }

        val ttsAddress = settingsRepository.ttsAudioDeviceAddress
        val cuesAddress = settingsRepository.cuesAudioDeviceAddress
        
        val mergedList = available.toMutableList()
        
        listOfNotNull(ttsAddress, cuesAddress).distinct().forEach { selectedAddress ->
            if (mergedList.none { it.address == selectedAddress }) {
                val persistentId = selectedAddress.split("|").lastOrNull() ?: selectedAddress
                val cachedName = settingsRepository.getDeviceName(persistentId)
                if (cachedName != null) {
                    val fallbackMatch = available.find { 
                        val devPersistentId = it.address.split("|").lastOrNull() ?: it.address
                        devPersistentId == persistentId 
                    }
                    
                    if (fallbackMatch == null) {
                        mergedList.add(
                            AudioOutputDevice(
                                address = selectedAddress,
                                name = "$cachedName (Inaktiv)",
                                type = 0,
                                isBuiltIn = false
                            )
                        )
                    }
                }
            }
        }
        _availableAudioDevices.value = mergedList
    }

    fun setTtsLanguage(languageTag: String) {
        val tagToSave = if (languageTag == "default") null else languageTag
        settingsRepository.ttsLanguage = tagToSave
        
        settingsRepository.ttsVoiceName = null
        loadAvailableVoices()
        
        ttsHelper.setLanguageAndVoice(languageTag, null)
        ttsHelper.speakRouted("Sprache geändert", settingsRepository.ttsAudioDeviceAddress)
    }

    fun setTtsVoice(voiceName: String?) {
        settingsRepository.ttsVoiceName = voiceName
        ttsHelper.setVoice(voiceName)
        ttsHelper.speakRouted("Stimme ausgewählt", settingsRepository.ttsAudioDeviceAddress)
    }

    fun setTtsVolumeMultiplier(multiplier: Float) {
        settingsRepository.ttsVolumeMultiplier = multiplier
        ttsHelper.speakRouted("Lautstärke geändert", settingsRepository.ttsAudioDeviceAddress)
    }
    
    fun setCuesVolumeMultiplier(multiplier: Float) {
        settingsRepository.cuesVolumeMultiplier = multiplier
        ttsHelper.speakRouted("Hinweis Lautstärke geändert", settingsRepository.cuesAudioDeviceAddress)
    }

    fun setTtsAudioDevice(address: String?) {
        settingsRepository.ttsAudioDeviceAddress = address
        ttsHelper.speakRouted("Ausgabegerät für Sprechen ausgewählt", address)
    }

    fun setCuesAudioDevice(address: String?) {
        settingsRepository.cuesAudioDeviceAddress = address
        ttsHelper.speakRouted("Ausgabegerät für Feedback ausgewählt", address)
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
