package com.andreas_kratzer.ghosttalk.tts

import android.content.Context
import android.util.Log
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A test-double for TextToSpeechHelper that records all spoken text.
 */
@Singleton
class TtsRecordingHelper @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository,
    // We mock the dependencies or just use dummy ones
) : TextToSpeechHelper(context, CoroutineScope(kotlinx.coroutines.Dispatchers.Main), settingsRepository, mockk(relaxed = true), mockk(relaxed = true)) {
    
    private val _spokenTexts = MutableStateFlow<List<String>>(emptyList())
    val spokenTexts: StateFlow<List<String>> = _spokenTexts.asStateFlow()

    override fun speak(text: String, queueMode: Int, onDone: (() -> Unit)?) {
        Log.d("TtsRecordingHelper", "Recording speak: $text")
        val current = _spokenTexts.value.toMutableList()
        current.add(text)
        _spokenTexts.value = current
        onDone?.invoke()
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
        onDone?.invoke()
    }
    
    fun clear() {
        _spokenTexts.value = emptyList()
    }
}
