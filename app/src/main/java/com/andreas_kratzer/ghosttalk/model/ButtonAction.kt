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
    val dummy: Unit = Unit // Just to keep it a data class if needed, or better, make it an empty data class if supported or just a simple class.
    // Actually, Kotlin allows empty data classes if they have at least one parameter.
    // Let's see if I can just remove the parameter.
) : ButtonAction()

/**
 * An action that navigates to a different page.
 * Optionally, it can provide TTS feedback when executed.
 */
data class NavigateToPageButtonAction(
    val pageId: String
) : ButtonAction()

/**
 * An action that triggers a Gemini AI request with arbitrary skills/tools.
 */
data class GeminiButtonAction(
    val prompt: String
) : ButtonAction()

/**
 * An action that triggers a Gemini AI request specifically using the Google Search Grounding tool.
 */
data class GeminiSearchButtonAction(
    val prompt: String
) : ButtonAction()

/**
 * An action that triggers a Gemini AI request on-device using Gemini Nano for a specific intent.
 */
data class GeminiNanoButtonAction(
    val intent: String
) : ButtonAction()

/**
 * An action that resolves dynamically to the N-th most frequent action.
 */
data class FrequentActionButtonAction(
    val rank: Int  // 1 = häufigste, 2 = zweithäufigste, ...
) : ButtonAction()

/**
 * An action that resolves dynamically to the N-th smart prediction from Gemini.
 */
data class SmartPredictionButtonAction(
    val rank: Int = 1 // 1 = most likely, 2 = second, ...
) : ButtonAction()

/**
 * An action to control device functions (Media, Volume, System Status, etc.)
 */
data class ControlDeviceButtonAction(
    val actionType: DeviceActionType,
    val volumeValue: String? = null,      // e.g. "50", "+10", "-5"
    val contactName: String? = null,      // Display name
    val contactPhone: String? = null,     // Phone number or ID
    val messageText: String? = null       // The message content
) : ButtonAction()

/**
 * An action that reads the current weather.
 */
data class WeatherButtonAction(
    val dummy: Unit = Unit
) : ButtonAction()

enum class DeviceActionType {
    READ_NOTIFICATIONS,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    MEDIA_PLAY_PAUSE,
    VOLUME_NOTIFICATION,
    VOLUME_ALARM,
    VOLUME_MEDIA,
    VOLUME_CALL,
    STATUS_SILENT,
    STATUS_VIBRATE,
    STATUS_LOUD,
    CLEAR_NOTIFICATIONS,
    SEND_MESSAGE,
    READ_BATTERY,
    READ_TIME,
    READ_DATE
}
