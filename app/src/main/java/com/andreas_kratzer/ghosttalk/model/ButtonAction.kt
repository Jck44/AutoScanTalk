package com.andreas_kratzer.ghosttalk.model

/**
 * Represents an action that can be executed when a button is triggered.
 * The actual execution logic is handled by the PageViewModel.
 */
abstract class ButtonAction {
    abstract val ttsMode: String
}

/**
 * An action that speaks a given text using Text-to-Speech.
 */
data class SpeakTextButtonAction(
    val textToSpeech: String,
    override val ttsMode: String = "NORMAL"
) : ButtonAction()

/**
 * An action that navigates to a different page.
 * Optionally, it can provide TTS feedback when executed.
 */
data class NavigateToPageButtonAction(
    val pageId: String,
    override val ttsMode: String = "NORMAL"
) : ButtonAction()

/**
 * An action that triggers a Gemini AI request with arbitrary skills/tools.
 */
data class GeminiButtonAction(
    val prompt: String,
    override val ttsMode: String = "NORMAL"
) : ButtonAction()

/**
 * An action that triggers a Gemini AI request specifically using the Google Search Grounding tool.
 */
data class GeminiSearchButtonAction(
    val prompt: String,
    override val ttsMode: String = "NORMAL"
) : ButtonAction()

/**
 * An action that resolves dynamically to the N-th most frequent action.
 */
data class FrequentActionButtonAction(
    val rank: Int,  // 1 = häufigste, 2 = zweithäufigste, ...
    override val ttsMode: String = "NORMAL"
) : ButtonAction()

/**
 * An action that resolves dynamically to the N-th smart prediction from Gemini.
 */
data class SmartPredictionButtonAction(
    val rank: Int = 1, // 1 = most likely, 2 = second, ...
    override val ttsMode: String = "NORMAL"
) : ButtonAction()

// Zukünftige Aktionen könnten hier als weitere data classes hinzugefügt werden,
// die von `Action` erben.

/**
 * An action that reads out notifications from a specified app (or all allowed apps).
 */
data class NotificationButtonAction(
    val targetApp: String = "ALL", // "ALL", "com.whatsapp", "org.thoughtcrime.securesms", etc.
    override val ttsMode: String = "NORMAL"
) : ButtonAction()

/**
 * An action to change the volume multiplier directly via a button click.
 */
data class ChangeVolumeButtonAction(
    val isAbsolute: Boolean,
    val amount: Float,
    val isForCues: Boolean = false,
    override val ttsMode: String = "NORMAL"
) : ButtonAction()
