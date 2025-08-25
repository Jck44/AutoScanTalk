package com.example.gostalk.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TextToSpeechHelper(
    context: Context,
    private val onReady: (() -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var initialized = false

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e("TextToSpeechHelper", "Error initializing TTS", e)
            onError?.invoke("Error initializing TTS: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Versuche Deutsch als Sprache einzustellen
            val result = tts?.setLanguage(Locale.GERMAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TextToSpeechHelper", "German language is not supported or missing data, using default.")
                // Fallback zur Gerätesprache
                tts?.setLanguage(Locale.getDefault())
            } else {
                Log.i("TextToSpeechHelper", "TTS initialized successfully with German.")
            }
            initialized = true
            onReady?.invoke()
        } else {
            Log.e("TextToSpeechHelper", "TTS initialization failed with status: $status")
            onError?.invoke("TTS initialization failed with status: $status")
            initialized = false
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (initialized && tts != null) {
            tts?.speak(text, queueMode, null, null)
        } else {
            Log.w("TextToSpeechHelper", "TTS not initialized, cannot speak: '$text'")
            onError?.invoke("TTS not initialized, cannot speak.")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        initialized = false
        Log.i("TextToSpeechHelper", "TTS shut down.")
    }
}
