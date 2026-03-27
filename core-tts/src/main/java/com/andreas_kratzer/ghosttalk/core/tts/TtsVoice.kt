package com.andreas_kratzer.ghosttalk.core.tts

import java.util.Locale

/**
 * A generic representation of a Text-to-Speech voice.
 * Decouples the UI and settings from engine-specific implementations (like android.speech.tts.Voice).
 */
data class TtsVoice(
    val id: String,
    val name: String,
    val locale: Locale,
    val isNetworkRequired: Boolean,
    val quality: Int = QUALITY_NORMAL,
    val gender: Gender = Gender.UNKNOWN,
    val provider: String = "unknown"
) {
    enum class Gender {
        MALE, FEMALE, UNKNOWN
    }

    companion object {
        const val QUALITY_LOW = 100
        const val QUALITY_NORMAL = 200
        const val QUALITY_HIGH = 300
        const val QUALITY_VERY_HIGH = 400
    }
}
