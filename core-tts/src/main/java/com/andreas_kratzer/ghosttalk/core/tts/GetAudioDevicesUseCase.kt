package com.andreas_kratzer.ghosttalk.core.tts

import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.model.AudioOutputDevice
import javax.inject.Inject

class GetAudioDevicesUseCase @Inject constructor(
    private val audioDeviceManager: AudioDeviceManager
) {
    suspend fun execute(): List<AudioOutputDevice> {
        return audioDeviceManager.getAvailableOutputDevices()
    }
}
