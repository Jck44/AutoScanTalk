package com.andreas_kratzer.ghosttalk.model

/**
 * Represents an action that can be executed when a button is triggered.
 * The actual execution logic is handled by the PageViewModel.
 */
sealed class ButtonAction

/**
 * An action that speaks a given text using Text-to-Speech.
 */
data class SpeakTextButtonAction(
    val textToSpeech: String
) : ButtonAction()

/**
 * An action that navigates to a different page.
 * Optionally, it can provide TTS feedback when executed.
 */
data class NavigateToPageButtonAction(
    val pageId: String
) : ButtonAction()

// Zukünftige Aktionen könnten hier als weitere data classes hinzugefügt werden,
// die von `Action` erben.
