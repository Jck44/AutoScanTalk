package com.andreas_kratzer.ghosttalk.core.tts

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.tts.R

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
     * Formats a TtsVoice object into a beautiful display name like "Stimme 1 (Weiblich, Hohe Qualität)".
     * @param voiceIndex The index of the voice group (same base names get same index)
     */
    fun formatVoiceDisplay(context: Context, voice: TtsVoice, voiceIndex: Int): String {
        val traits = mutableListOf<String>()
        
        // Quality
        val qualityStr = when (voice.quality) {
            TtsVoice.QUALITY_VERY_HIGH -> context.getString(R.string.voice_quality_very_high)
            TtsVoice.QUALITY_HIGH -> context.getString(R.string.voice_quality_high)
            TtsVoice.QUALITY_NORMAL -> context.getString(R.string.voice_quality_normal)
            TtsVoice.QUALITY_LOW -> context.getString(R.string.voice_quality_low)
            else -> null
        }
        qualityStr?.let { traits.add(it) }
 
        // Gender (heuristics)
        val genderStr = when (voice.gender) {
            TtsVoice.Gender.FEMALE -> context.getString(R.string.voice_gender_female)
            TtsVoice.Gender.MALE -> context.getString(R.string.voice_gender_male)
            else -> {
                val lowerName = voice.name.lowercase()
                when {
                    lowerName.contains("female") || lowerName.contains("-f-") || lowerName.contains("-w-") -> 
                        context.getString(R.string.voice_gender_female)
                    lowerName.contains("male") || lowerName.contains("-m-") -> 
                        context.getString(R.string.voice_gender_male)
                    else -> null
                }
            }
        }
        genderStr?.let { traits.add(it) }
 
        // Provider specific info
        if (voice.provider == "elevenlabs") {
            traits.add("Cloud")
        } else if (voice.isNetworkRequired) {
            traits.add("Online")
        } else {
            traits.add("Lokal") 
        }
 
        val traitsCombined = traits.joinToString(", ")
        
        if (voice.provider == "elevenlabs") {
            return if (traitsCombined.isNotEmpty()) {
                "${voice.name} ($traitsCombined)"
            } else {
                voice.name
            }
        }
        
        return context.getString(R.string.voice_format_pattern, voiceIndex + 1, traitsCombined)
    }
}
