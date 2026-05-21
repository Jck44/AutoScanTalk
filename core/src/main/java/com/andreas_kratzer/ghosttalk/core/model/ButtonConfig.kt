package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class SpokenTextMode {
    TTS,
    AUDIO
}

/**
 * Configuration for a single button in the grid.
 */
@Serializable
data class ButtonConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val spokenText: String? = null,
    val spokenTextMode: SpokenTextMode = SpokenTextMode.TTS,
    val audioFileName: String? = null,
    val auditoryCue: AuditoryCue? = null,
    val isActive: Boolean = true,
    val playActionAsAuditoryCue: Boolean = false,
    val buttonAction: ButtonAction = SpeakTextButtonAction()
)

