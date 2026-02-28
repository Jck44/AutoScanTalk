package com.example.gostalk.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.example.gostalk.core.AudioDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RoutedAudioPlayer(
    private val context: Context,
    private val audioDeviceManager: AudioDeviceManager
) {
    private val activePlayers = ConcurrentHashMap<MediaPlayer, Boolean>()
    fun playAudioFile(file: File, deviceAddress: String?, onCompletion: (() -> Unit)? = null) {
        if (!file.exists()) {
            Log.e("RoutedAudioPlayer", "Audio file does not exist: ${file.absolutePath}")
            onCompletion?.invoke()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Determine target device first to prevent scope issues
                var targetDevice: AudioDeviceInfo? = null
                if (deviceAddress != null) {
                    targetDevice = audioDeviceManager.getAudioDeviceInfo(deviceAddress)
                }
                if (targetDevice == null) {
                    targetDevice = audioDeviceManager.getBuiltInSpeaker()
                    Log.d("RoutedAudioPlayer", "Target device not found or not set, falling back to built-in speaker.")
                }

                val isCommunicationDevice = targetDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        targetDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
                        targetDevice?.type == AudioDeviceInfo.TYPE_TELEPHONY

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                
                val mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    
                    if (targetDevice != null) {
                        val routed = setPreferredDevice(targetDevice)
                        Log.d("RoutedAudioPlayer", "Routing to ${targetDevice.productName} (address: ${targetDevice.address}): Success=$routed")
                    }
                    
                    if (isCommunicationDevice && targetDevice != null) {
                        val commRouted = audioManager.setCommunicationDevice(targetDevice)
                        Log.d("RoutedAudioPlayer", "setCommunicationDevice: Success=$commRouted")
                    }

                    val usageType = if (isCommunicationDevice) {
                        AudioAttributes.USAGE_VOICE_COMMUNICATION
                    } else {
                        AudioAttributes.USAGE_MEDIA
                    }

                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(usageType)
                            .build()
                    )

                    setOnCompletionListener {
                        if (isCommunicationDevice) {
                            audioManager.clearCommunicationDevice()
                        }
                        activePlayers.remove(this)
                        it.release()
                        onCompletion?.invoke()
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        Log.e("RoutedAudioPlayer", "MediaPlayer Error $what, $extra")
                        if (isCommunicationDevice) {
                            audioManager.clearCommunicationDevice()
                        }
                        activePlayers.remove(this)
                        release()
                        onCompletion?.invoke()
                        true
                    }
                }

                activePlayers[mediaPlayer] = true
                mediaPlayer.prepare()
                mediaPlayer.start()
            } catch (e: Exception) {
                Log.e("RoutedAudioPlayer", "Error playing audio", e)
                onCompletion?.invoke()
            }
        }
    }
}
