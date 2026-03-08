package com.andreas_kratzer.ghosttalk.model

/**
 * Represents an action that can be executed when a button is triggered.
 * The actual execution logic is handled by the PageViewModel.
 */
sealed class ButtonAction {
    abstract val ttsMode: String
}

/**
 * An action that speaks a given text using Text-to-Speech.
 */
data class SpeakTextButtonAction(
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
 * An action that triggers a Gemini AI request on-device using Gemini Nano for a specific intent.
 */
data class GeminiNanoButtonAction(
    val intent: String,
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

/**
 * An action to control device functions (Media, Volume, System Status, etc.)
 */
data class ControlDeviceButtonAction(
    val actionType: DeviceActionType,
    override val ttsMode: String = "NORMAL"
) : ButtonAction()

enum class DeviceActionType {
    READ_NOTIFICATIONS,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    MEDIA_PLAY_PAUSE,
    VOLUME_NOTIFICATION, // For later
    VOLUME_ALARM,        // For later
    VOLUME_MEDIA,        // For later
    VOLUME_CALL,         // For later
    STATUS_SILENT,       // For later
    STATUS_VIBRATE,      // For later
    STATUS_LOUD,         // For later
    CLEAR_NOTIFICATIONS, // For later
    SEND_MESSAGE         // For later
}

// Zukünftige Aktionen könnten hier als weitere data classes hinzugefügt werden,
// die von `Action` erben.
