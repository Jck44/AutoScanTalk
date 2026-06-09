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

data class AudioTopology(
    val availableDevices: List<AudioOutputDevice>
)

@Singleton
class AudioTopologyTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _topologyFlow = MutableStateFlow(AudioTopology(emptyList()))
    val topologyFlow: StateFlow<AudioTopology> = _topologyFlow.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateTopology()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateTopology()
        }
    }

    init {
        updateTopology()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    }

    fun updateTopology() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val mapped = devices.filter {
            it.type != AudioDeviceInfo.TYPE_TELEPHONY
        }.map { device ->
            val isBuiltIn = device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                            device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            
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
        _topologyFlow.value = AudioTopology(mapped)
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
}
