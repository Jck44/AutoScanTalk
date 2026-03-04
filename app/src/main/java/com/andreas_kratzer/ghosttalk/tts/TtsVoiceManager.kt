package com.andreas_kratzer.ghosttalk.tts

import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import javax.inject.Inject

class TtsVoiceManager @Inject constructor() {

    fun getAvailableLanguages(tts: TextToSpeech?): List<Locale> {
        return try {
            tts?.availableLanguages?.toList()?.sortedBy { it.displayName } ?: emptyList()
        } catch (e: Exception) {
            Log.e("TtsVoiceManager", "Error getting languages", e)
            emptyList()
        }
    }

    fun getAvailableVoices(tts: TextToSpeech?, languageTag: String?): List<android.speech.tts.Voice> {
        if (tts == null) return emptyList()
        
        val targetLocale = if (languageTag.isNullOrEmpty() || languageTag == "default") {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageTag)
        }
        
        return try {
            tts.voices?.filter { voice ->
                voice.locale.language == targetLocale.language && voice.locale.country == targetLocale.country
            }?.sortedBy { it.name } ?: emptyList()
        } catch (e: Exception) {
            Log.e("TtsVoiceManager", "Error getting voices", e)
            emptyList()
        }
    }

    fun findVoice(tts: TextToSpeech?, voiceName: String?): android.speech.tts.Voice? {
        if (tts == null || voiceName.isNullOrEmpty()) return null
        return tts.voices?.find { it.name == voiceName }
    }
}
