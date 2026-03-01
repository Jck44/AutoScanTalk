package com.andreas_kratzer.ghosttalk.model

import java.util.UUID

/**
 * Configuration for a single button in the grid.
 *
 * @param id Unique identifier for the button.
 * @param label Text displayed on the button, also used as fallback auditory cue if specific cue is complex.
 * @param auditoryCue The auditory cue to be played when the button is focused.
 * @param buttonAction The action to be executed when the button is triggered.
 */
data class ButtonConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val spokenText: String? = null,
    val auditoryCue: AuditoryCue?,
    val isActive: Boolean = true,
    val buttonAction: ButtonAction
)
