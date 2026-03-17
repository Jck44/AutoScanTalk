package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an action that can be executed when a button is triggered.
 * The actual execution logic is handled by the PageViewModel.
 */
@Serializable
sealed class ButtonAction

/**
 * An action that speaks a given text using Text-to-Speech.
 */
@Serializable
@SerialName("SpeakTextButtonAction")
data class SpeakTextButtonAction(
    val version: Int = 1
) : ButtonAction()

/**
 * An action that navigates to a different page.
 * Optionally, it can provide TTS feedback when executed.
 */
@Serializable
@SerialName("NavigateToPageButtonAction")
data class NavigateToPageButtonAction(
    val pageId: String = ""
) : ButtonAction()

/**
 * An action that triggers a Gemini AI request with arbitrary skills/tools.
 */
@Serializable
@SerialName("GeminiButtonAction")
data class GeminiButtonAction(
    val prompt: String = ""
) : ButtonAction()

/**
 * An action that triggers a Gemini AI request specifically using the Google Search Grounding tool.
 */
@Serializable
@SerialName("GeminiSearchButtonAction")
data class GeminiSearchButtonAction(
    val prompt: String = ""
) : ButtonAction()

/**
 * An action that triggers a Gemini AI request on-device using Gemini Nano for a specific intent.
 */
@Serializable
@SerialName("GeminiNanoButtonAction")
data class GeminiNanoButtonAction(
    val intent: String = ""
) : ButtonAction()

/**
 * An action that triggers a Gemini AI request with vision (image analysis).
 */
@Serializable
@SerialName("GeminiVisionButtonAction")
data class GeminiVisionButtonAction(
    val prompt: String = "",
    val useCloud: Boolean = false,
    val playShutterSound: Boolean = true
) : ButtonAction()

/**
 * An action that resolves dynamically to the N-th most frequent action.
 */
@Serializable
@SerialName("FrequentActionButtonAction")
data class FrequentActionButtonAction(
    val rank: Int = 1 // 1 = häufigste, 2 = zweithäufigste, ...
) : ButtonAction()

/**
 * An action that resolves dynamically to the N-th smart prediction from Gemini.
 */
@Serializable
@SerialName("SmartPredictionButtonAction")
data class SmartPredictionButtonAction(
    val rank: Int = 1 // 1 = most likely, 2 = second, ...
) : ButtonAction()

/**
 * An action to control device functions (Media, Volume, System Status, etc.)
 */
@Serializable
@SerialName("ControlDeviceButtonAction")
data class ControlDeviceButtonAction(
    val actionType: DeviceActionType = DeviceActionType.READ_TIME,
    val volumeValue: String? = null,      // e.g. "50", "+10", "-5"
    val contactName: String? = null,      // Display name
    val contactPhone: String? = null,     // Phone number or ID
    val messageText: String? = null       // The message content
) : ButtonAction()

/**
 * An action that reads the current weather.
 */
@Serializable
@SerialName("WeatherButtonAction")
data class WeatherButtonAction(
    val version: Int = 1
) : ButtonAction()

/**
 * An action to control Smart Home devices (Google Home, Philips Hue).
 */
@Serializable
@SerialName("SmartHomeButtonAction")
data class SmartHomeButtonAction(
    val provider: SmartHomeProvider = SmartHomeProvider.GOOGLE_HOME,
    val deviceId: String = "",
    val deviceName: String = "",
    val intent: String = "",
    val value: String? = null
) : ButtonAction()

@Serializable
enum class SmartHomeProvider {
    GOOGLE_HOME,
    PHILIPS_HUE
}

@Serializable
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
