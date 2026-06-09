package com.andreas_kratzer.ghosttalk.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.andreas_kratzer.ghosttalk.core.model.AudioOutputDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDeviceManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val topologyTracker: AudioTopologyTracker
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val availableDevicesFlow: StateFlow<List<AudioOutputDevice>>
        get() {
            // Keep backwards compatibility by providing a state flow from the tracker
            val flow = MutableStateFlow(topologyTracker.topologyFlow.value.availableDevices)
            // In a real app we would map, but for simplicity of returning a StateFlow, we can just return a flow representing the current state or a delegate state flow
            return flow
        }

    fun getAvailableOutputDevices(): List<AudioOutputDevice> {
        return topologyTracker.topologyFlow.value.availableDevices
    }

    fun getReadableDeviceName(device: AudioDeviceInfo): String {
        val typeName = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Lautsprecher"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Telefon-Hörmuschel"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Kabel-Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Kabel-Kopfhörer"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (Media)"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (Anruf)"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_HEARING_AID -> "Hörgerät"
            else -> "Audiogerät (${device.type})"
        }

        val nameFromDevice = device.productName?.toString()
        
        return if (!nameFromDevice.isNullOrBlank() && !nameFromDevice.startsWith("Built") && nameFromDevice != typeName) {
            "$nameFromDevice ($typeName)"
        } else {
            typeName
        }
    }

    fun getAudioDeviceInfo(address: String?): AudioDeviceInfo? {
        if (address.isNullOrBlank()) return null
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        
        val parts = address.split("|", limit = 2)
        val idPart = parts.getOrNull(0)?.toIntOrNull()
        val fallbackPart = if (parts.size > 1) parts[1] else parts[0]
        
        if (idPart != null) {
            val exactMatch = devices.find { it.id == idPart }
            if (exactMatch != null) return exactMatch
        }
        
        return devices.find { 
            val safeProductName = it.productName?.toString()?.replace(" ", "_") ?: "unknown"
            val computedPersistentId = if (it.address.isNotBlank()) it.address else "type_${it.type}_$safeProductName"
            computedPersistentId == fallbackPart
        }
    }

    fun getBuiltInSpeaker(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: devices.firstOrNull()
    }
}
