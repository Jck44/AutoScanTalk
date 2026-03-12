package com.andreas_kratzer.ghosttalk.domain.tts

import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.model.AudioOutputDevice
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import javax.inject.Inject

class GetAudioDevicesUseCase @Inject constructor(
    private val audioDeviceManager: AudioDeviceManager,
    private val settingsRepository: SettingsRepository
) {
    fun execute(): List<AudioOutputDevice> {
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
        return mergedList
    }
}