package com.example.gostalk.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.util.Locale

class TextToSpeechHelper(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var initialized = false
    val isReady: Boolean get() = initialized
    private val handler = Handler(Looper.getMainLooper())

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            showToast("Error initializing TTS: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.GERMAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                showToast("German language missing/unsupported. Using default.")
                tts?.setLanguage(Locale.getDefault())
            } else {
                showToast("TTS initialized with German.")
            }
            initialized = true
        } else {
            showToast("TTS init failed! Status code: $status")
            initialized = false
        }
    }

    private fun showToast(message: String) {
        Log.e("TextToSpeechHelper", message)
        handler.post {
            Toast.makeText(context, "TTS Debug: $message", Toast.LENGTH_LONG).show()
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (initialized && tts != null) {
            tts?.speak(text, queueMode, null, null)
        } else {
            showToast("TTS not initialized, cannot speak.")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        initialized = false
    }
}
