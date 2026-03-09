package com.andreas_kratzer.ghosttalk.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Represents an auditory cue that is associated with a button
 * and can be played when the button is focused during scanning.
 * The actual playback logic is handled by the PageViewModel.
 */
@Serializable
sealed class AuditoryCue {
    /**
     * An auditory cue that uses Text-to-Speech.
     */
    @Serializable
    @SerialName("TextToSpeechCue")
    data class TextToSpeechCue(val text: String) : AuditoryCue()

    /**
     * Future placeholder: An auditory cue from a recorded audio file.
     * data class RecordedAudioCue(val filePath: String) : AuditoryCue
     */
    // We could also consider adding SoundResourceCue here if it's planned:
    // data class SoundResourceCue(val resourceId: Int) : AuditoryCue()
}
