package com.andreas_kratzer.ghosttalk.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.andreas_kratzer.ghosttalk.core.model.AudioOutputDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AudioDeviceManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _availableDevicesFlow = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val availableDevicesFlow: StateFlow<List<AudioOutputDevice>> = _availableDevicesFlow.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            _availableDevicesFlow.value = getAvailableOutputDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            _availableDevicesFlow.value = getAvailableOutputDevices()
        }
    }

    init {
        _availableDevicesFlow.value = getAvailableOutputDevices()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    }

    open fun getAvailableOutputDevices(): List<AudioOutputDevice> {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.filter { 
            // Filter out telephony devices to avoid duplicates of the earpiece/phone
            it.type != AudioDeviceInfo.TYPE_TELEPHONY
        }.map { device ->
            val isBuiltIn = device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                            device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            
            // Generate a unique identifier. `address` is usually available for Bluetooth devices.
            val safeProductName = device.productName?.toString()?.replace(" ", "_") ?: "unknown"
            val persistentId = if (device.address.isNotBlank()) device.address else "type_${device.type}_$safeProductName"
            val address = "${device.id}|$persistentId"
            val name = getReadableDeviceName(device)
            
            AudioOutputDevice(
                address = address,
                name = name,
                type = device.type,
                isBuiltIn = isBuiltIn
            )
        }
    }

    private fun getReadableDeviceName(device: AudioDeviceInfo): String {
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

    open fun getAudioDeviceInfo(address: String?): AudioDeviceInfo? {
        if (address.isNullOrBlank()) return null
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        
        val parts = address.split("|", limit = 2)
        val idPart = parts.getOrNull(0)?.toIntOrNull()
        val fallbackPart = if (parts.size > 1) parts[1] else parts[0]
        
        // 1. Priorität: Finde genau das Gerät anhand der Android-internen ID
        if (idPart != null) {
            val exactMatch = devices.find { it.id == idPart }
            if (exactMatch != null) return exactMatch
        }
        
        // 2. Priorität: Fallback anhand von MAC-Adresse oder Geräte-Typ/Name
        return devices.find { 
            val safeProductName = it.productName?.toString()?.replace(" ", "_") ?: "unknown"
            val computedPersistentId = if (it.address.isNotBlank()) it.address else "type_${it.type}_$safeProductName"
            computedPersistentId == fallbackPart
        }
    }

    open fun getBuiltInSpeaker(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: devices.firstOrNull()
    }
}
