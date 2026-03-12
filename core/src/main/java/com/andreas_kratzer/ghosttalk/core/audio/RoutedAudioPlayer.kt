package com.andreas_kratzer.ghosttalk.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class RoutedAudioPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val audioDeviceManager: AudioDeviceManager,
    private val audioSettings: AudioSettings
) {
    private val activePlayers = ConcurrentHashMap<MediaPlayer, Boolean>()
    private val playbackJobs = ConcurrentHashMap<MediaPlayer, Job>()

    private fun isBluetoothDevice(type: Int): Boolean {
        val isBT = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                type == AudioDeviceInfo.TYPE_BLE_BROADCAST
        Log.d("RoutedAudioPlayer", "isBluetoothDevice check for type $type: $isBT")
        return isBT
    }

    open fun playAudioFile(file: File, deviceAddress: String?, volumeMultiplier: Float = 1.0f, onCompletion: (() -> Unit)? = null) {
        if (!file.exists()) {
            Log.e("RoutedAudioPlayer", "Audio file does not exist: ${file.absolutePath}")
            onCompletion?.invoke()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            var focusRequest: android.media.AudioFocusRequest? = null
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            var communicationDeviceSet = false
            var targetDevice: AudioDeviceInfo? = null

            try {
                // Determine target device
                if (deviceAddress != null) {
                    targetDevice = audioDeviceManager.getAudioDeviceInfo(deviceAddress)
                }
                if (targetDevice == null) {
                    targetDevice = audioDeviceManager.getBuiltInSpeaker()
                    Log.d("RoutedAudioPlayer", "Target device not found or not set, falling back to built-in speaker.")
                } else {
                    Log.d("RoutedAudioPlayer", "Target device: ${targetDevice.productName}, Type: ${targetDevice.type}, Address: ${targetDevice.address}")
                }

                val isCommunicationMode = targetDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        targetDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
                        targetDevice?.type == AudioDeviceInfo.TYPE_TELEPHONY

                val usageType = if (isCommunicationMode) {
                    AudioAttributes.USAGE_VOICE_COMMUNICATION
                } else {
                    AudioAttributes.USAGE_MEDIA
                }

                // Request Audio Focus
                focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(usageType)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                    .build()
                
                val focusResult = audioManager.requestAudioFocus(focusRequest)
                Log.d("RoutedAudioPlayer", "AudioFocus requested. Result: $focusResult")

                val mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    
                    if (targetDevice != null) {
                        val routed = setPreferredDevice(targetDevice)
                        Log.d("RoutedAudioPlayer", "Routing to ${targetDevice.productName} (address: ${targetDevice.address}): Success=$routed")
                    }
                    
                    if (isCommunicationMode) {
                        communicationDeviceSet = audioManager.setCommunicationDevice(targetDevice)
                        Log.d("RoutedAudioPlayer", "setCommunicationDevice: Success=$communicationDeviceSet")
                    }

                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(usageType)
                            .build()
                    )

                    setOnCompletionListener {
                        if (communicationDeviceSet) {
                            audioManager.clearCommunicationDevice()
                        }
                        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                        activePlayers.remove(this)
                        playbackJobs.remove(this)
                        it.release()
                        onCompletion?.invoke()
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        Log.e("RoutedAudioPlayer", "MediaPlayer Error $what, $extra")
                        if (communicationDeviceSet) {
                            audioManager.clearCommunicationDevice()
                        }
                        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                        activePlayers.remove(this)
                        release()
                        onCompletion?.invoke()
                        true
                    }
                }

                activePlayers[mediaPlayer] = true
                playbackJobs[mediaPlayer] = coroutineContext[Job]!!
                
                mediaPlayer.prepare()

                // Bluetooth specific logic (Warm-up + Delay)
                if (targetDevice != null && isBluetoothDevice(targetDevice.type)) {
                    val delayMs = audioSettings.bluetoothDelay
                    Log.d("RoutedAudioPlayer", "Starting Bluetooth warm-up silence + delay: ${delayMs}ms")
                    
                    playSilenceWarmUp(targetDevice)
                    kotlinx.coroutines.delay(delayMs)
                    Log.d("RoutedAudioPlayer", "Bluetooth delay finished, starting playback.")
                } else {
                    Log.d("RoutedAudioPlayer", "No Bluetooth delay needed, starting playback immediately.")
                }

                mediaPlayer.setVolume(volumeMultiplier, volumeMultiplier)
                mediaPlayer.start()

            } catch (e: Exception) {
                Log.e("RoutedAudioPlayer", "Error playing audio", e)
                // Cleanup on error
                if (communicationDeviceSet) {
                    audioManager.clearCommunicationDevice()
                }
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                onCompletion?.invoke()
            }
        }
    }

    private fun playSilenceWarmUp(device: AudioDeviceInfo) {
        try {
            val sampleRate = 44100
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )
            
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(minBufferSize)
                .build()

            audioTrack.setPreferredDevice(device)
            
            // 500ms of silence
            val silenceDuration = 0.5
            val silenceCount = (sampleRate * silenceDuration).toInt()
            val silenceData = ShortArray(silenceCount)
            
            audioTrack.play()
            audioTrack.write(silenceData, 0, silenceData.size)
            
            // Release after a short while
            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(1000)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) { /* ignore */ }
            }
            Log.d("RoutedAudioPlayer", "Silence warm-up started for $silenceDuration seconds")
        } catch (e: Exception) {
            Log.e("RoutedAudioPlayer", "Error during silence warm-up", e)
        }
    }

    open fun stopAll() {
        Log.d("RoutedAudioPlayer", "stopAll() called. Cancelling ${playbackJobs.size} jobs and stopping ${activePlayers.size} players.")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        playbackJobs.values.forEach { it.cancel() }
        playbackJobs.clear()

        activePlayers.keys.forEach { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
                // Communication device cleanup is safer to just call here just in case
                audioManager.clearCommunicationDevice()
            } catch (e: Exception) {
                Log.e("RoutedAudioPlayer", "Error stopping player", e)
            }
        }
        activePlayers.clear()
    }
}
