package com.example.gostalk.model

/**
 * Represents an auditory cue that can be played to the user.
 */
sealed interface AuditoryCue {
    fun play() // Placeholder for actual playback logic

    /**
     * An auditory cue that uses Text-to-Speech.
     */
    data class TextToSpeechCue(val text: String) : AuditoryCue {
        override fun play() {
            // TODO: Implement TTS playback
            println("Playing TTS cue: $text")
        }
    }

    /**
     * Future placeholder: An auditory cue from a recorded audio file.
     * data class RecordedAudioCue(val filePath: String) : AuditoryCue
     */
}
