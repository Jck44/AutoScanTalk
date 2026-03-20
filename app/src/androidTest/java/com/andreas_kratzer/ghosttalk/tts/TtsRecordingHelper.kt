package com.andreas_kratzer.ghosttalk.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.audio.AudioSettings
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.core.model.AudioOutputDevice
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.core.tts.TtsVoiceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A test-double for TextToSpeechHelper that records all spoken text without using MockK.
 * This avoids binary compatibility issues on some Android versions during instrumentation tests.
 */
@Singleton
class TtsRecordingHelper @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository,
    voiceManager: TtsVoiceManager
) : TextToSpeechHelper(
    context, 
    CoroutineScope(Dispatchers.Main),
    settingsRepository, 
    // Manual No-Op implementation for RoutedAudioPlayer to avoid MockK in AndroidTest
    TestRoutedAudioPlayer(context, TestAudioDeviceManager(context), settingsRepository as AudioSettings),
    voiceManager
) {
    
    private val _spokenTexts = MutableStateFlow<List<String>>(emptyList())
    val spokenTexts: StateFlow<List<String>> = _spokenTexts.asStateFlow()

    private val testScope = CoroutineScope(Dispatchers.Main)

    override fun speak(text: String, queueMode: Int, onDone: (() -> Unit)?) {
        Log.d("TtsRecordingHelper", "Recording speak: $text")
        val current = _spokenTexts.value.toMutableList()
        current.add(text)
        _spokenTexts.value = current
        testScope.launch {
            delay(500)
            onDone?.invoke()
        }
    }

    override fun speakRouted(
        text: String,
        deviceAddress: String?,
        queueMode: Int,
        isForCues: Boolean,
        onDone: (() -> Unit)?
    ) {
        Log.d("TtsRecordingHelper", "Recording speakRouted: $text")
        val current = _spokenTexts.value.toMutableList()
        current.add(text)
        _spokenTexts.value = current
        testScope.launch {
            delay(500)
            onDone?.invoke()
        }
    }
    
    fun clear() {
        _spokenTexts.value = emptyList()
    }

    // Manual doubles to avoid MockK
    private class TestAudioDeviceManager(context: Context) : AudioDeviceManager(context) {
        override fun getAvailableOutputDevices(): List<AudioOutputDevice> = emptyList()
        override fun getAudioDeviceInfo(address: String?) = null
        override fun getBuiltInSpeaker() = null
    }

    private class TestRoutedAudioPlayer(
        context: Context, 
        deviceManager: AudioDeviceManager, 
        settings: AudioSettings
    ) : RoutedAudioPlayer(context, deviceManager, settings) {
        override fun playAudioFile(file: File, deviceAddress: String?, volumeMultiplier: Float, onCompletion: (() -> Unit)?) {
            onCompletion?.invoke()
        }
        override fun stopAll() {}
    }
}
