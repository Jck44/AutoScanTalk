package com.andreas_kratzer.ghosttalk.core.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallAudioController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CallAudioController"
    }

    private val handler = Handler(Looper.getMainLooper())

    fun configureAudioForConnectedCall(inCallService: InCallService?, autoEnableSpeakerphone: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.i(TAG, "configureAudioForConnectedCall: current mode=${audioManager.mode}, autoSpeakerphone=$autoEnableSpeakerphone")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (autoEnableSpeakerphone) {
            // Delay speakerphone activation slightly to ensure the audio route is established
            handler.postDelayed({
                Log.i(TAG, "configureAudioForConnectedCall: activating speakerphone (delayed)")
                setSpeakerphoneEnabled(inCallService, true)
                // Second retry after another delay for robustness
                handler.postDelayed({
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    @Suppress("DEPRECATION")
                    if (!am.isSpeakerphoneOn) {
                        Log.w(TAG, "Speakerphone still not on after first attempt, retrying...")
                        setSpeakerphoneEnabled(inCallService, true)
                    }
                }, 500)
            }, 200)
        }
    }

    fun revertAudioMode(inCallService: InCallService?) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setSpeakerphoneEnabled(inCallService, false)
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    @Suppress("DEPRECATION")
    fun setSpeakerphoneEnabled(inCallService: InCallService?, enabled: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.i(TAG, "setSpeakerphoneEnabled: enabled=$enabled, SDK=${Build.VERSION.SDK_INT}, currentMode=${audioManager.mode}")
        if (enabled) {
            inCallService?.let { service ->
                Log.i(TAG, "setSpeakerphoneEnabled: Calling InCallService.setAudioRoute(ROUTE_SPEAKER)")
                service.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }
        } else {
            inCallService?.let { service ->
                Log.i(TAG, "setSpeakerphoneEnabled: Calling InCallService.setAudioRoute(ROUTE_EARPIECE)")
                service.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            Log.i(TAG, "setSpeakerphoneEnabled: available devices: ${devices.map { "${it.type}(${it.productName})" }}")
            val speakerDevice = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (enabled) {
                if (speakerDevice != null) {
                    val result = audioManager.setCommunicationDevice(speakerDevice)
                    Log.i(TAG, "setSpeakerphoneEnabled: setCommunicationDevice result=$result")
                }
                // Always also set the legacy flag as a fallback
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                @Suppress("DEPRECATION")
                Log.i(TAG, "setSpeakerphoneEnabled: isSpeakerphoneOn after set=${audioManager.isSpeakerphoneOn}")
            } else {
                audioManager.clearCommunicationDevice()
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
    }
}
