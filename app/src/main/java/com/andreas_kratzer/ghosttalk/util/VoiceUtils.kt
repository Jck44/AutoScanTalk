package com.andreas_kratzer.ghosttalk.util

import android.content.Context
import android.speech.tts.Voice
import com.andreas_kratzer.ghosttalk.R

object VoiceUtils {
    /**
     * Formats a technical TTS voice name into a human-readable display name.
     * Example: "de-de-x-deb-network" -> "Deb"
     */
    fun formatVoiceName(technicalName: String): String {
        var name = technicalName.lowercase()
        val prefixRegex = Regex("^[a-z]{2}-[a-z]{2}-")
        name = name.replace(prefixRegex, "")
            .replace("-network", "")
            .replace("-local", "")
            .replace("-x-", " ")
            .replace(Regex("\\bx\\b"), " ")
            .replace("-", " ")
        
        return name.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            .ifEmpty { "Stimme" }
    }

    /**
     * Formats a Voice object into a beautiful display name like "Stimme 1 (Weiblich, Hohe Qualität)".
     * @param voiceIndex The index of the voice group (same base names get same index)
     */
    fun formatVoiceDisplay(context: Context, voice: Voice, voiceIndex: Int): String {
        val traits = mutableListOf<String>()
        
        // Quality
        val qualityStr = when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> context.getString(R.string.voice_quality_very_high)
            Voice.QUALITY_HIGH -> context.getString(R.string.voice_quality_high)
            Voice.QUALITY_NORMAL -> context.getString(R.string.voice_quality_normal)
            Voice.QUALITY_LOW -> context.getString(R.string.voice_quality_low)
            else -> null
        }
        qualityStr?.let { traits.add(it) }

        // Gender (heuristics)
        val lowerName = voice.name.lowercase()
        val genderStr = when {
            lowerName.contains("female") || lowerName.contains("-f-") || lowerName.contains("-w-") -> 
                context.getString(R.string.voice_gender_female)
            lowerName.contains("male") || lowerName.contains("-m-") -> 
                context.getString(R.string.voice_gender_male)
            else -> null
        }
        genderStr?.let { traits.add(it) }

        // Network hint
        if (voice.isNetworkConnectionRequired) {
            traits.add("Online")
        } else {
            traits.add(context.getString(R.string.settings_voice_local_hint).trim().removePrefix("(").removeSuffix(")"))
        }

        val traitsCombined = traits.joinToString(", ")
        
        return context.getString(R.string.voice_format_pattern, voiceIndex + 1, traitsCombined)
    }
}
