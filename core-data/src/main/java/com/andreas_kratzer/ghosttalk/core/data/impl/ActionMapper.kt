@file:Suppress("DEPRECATION")
package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.PredictionType
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the mapping between [ButtonAction] and [ImportAction].
 * This decouples the core action models from the import/export format.
 */
@Singleton
class ActionMapper @Inject constructor() {

    /**
     * Converts a [ButtonAction] to an [ImportAction] for export.
     */
    fun exportAction(action: ButtonAction, spokenText: String? = null): ImportAction {
        return when (action) {
            is SpeakTextButtonAction -> ImportAction(type = "SPEAK", textToSpeech = spokenText)
            is NavigateToPageButtonAction -> ImportAction(type = "NAVIGATE", targetPageId = action.pageId, targetPageImportId = action.pageId)
            is FrequentActionButtonAction -> ImportAction(type = "SMART_PREDICTION", rank = action.rank)
            is GeminiButtonAction -> ImportAction(type = "GEMINI", prompt = action.prompt)
            is GeminiSearchButtonAction -> ImportAction(type = "GEMINI_SEARCH", prompt = action.prompt)
            is GeminiNanoButtonAction -> ImportAction(type = "GEMINI_NANO", intent = action.intent)
            is SmartPredictionButtonAction -> ImportAction(
                type = "SMART_PREDICTION",
                rank = action.rank,
                predictionType = action.predictionType.name
            )
            is GeminiVisionButtonAction -> ImportAction(type = "GEMINI_VISION", prompt = action.prompt, useCloud = action.useCloud)
            is ControlDeviceButtonAction -> ImportAction(
                type = "DEVICE_CONTROL",
                deviceActionType = action.actionType.name,
                volumeValue = action.volumeValue,
                contactName = action.contactName,
                contactPhone = action.contactPhone,
                messageText = action.messageText,
                includeWeekday = action.includeWeekday,
                prefixText = action.prefixText,
                suffixText = action.suffixText,
                offsetValue = action.offsetValue,
                ignoreEmojis = action.ignoreEmojis
            )
            is WeatherButtonAction -> ImportAction(type = "WEATHER")
            is SmartHomeButtonAction -> ImportAction(
                type = "SMART_HOME",
                smartHomeProvider = action.provider.name,
                smartHomeDeviceId = action.deviceId,
                smartHomeDeviceName = action.deviceName,
                smartHomeIntent = action.intent,
                smartHomeValue = action.value
            )
            is PreviousActionButtonAction -> ImportAction(type = "PREVIOUS_ACTION", rank = action.rank)
            is PlayMediaButtonAction -> ImportAction(
                type = "PLAY_MEDIA",
                mediaProvider = action.provider.name,
                mediaContentUri = action.contentUri,
                mediaContentName = action.contentName,
                mediaReturnDelayMs = action.returnToAppDelayMs
            )
        }
    }

    /**
     * Converts an [ImportAction] to a [ButtonAction] during import.
     * Uses [idMap] to resolve target page IDs for navigation actions.
     */
    fun importAction(importAction: ImportAction, idMap: Map<String, String>): ButtonAction? {
        val type = importAction.type.uppercase()
        return when (type) {
            "SPEAK", "SPEAKTEXT" -> SpeakTextButtonAction()
            "NAVIGATE", "NAVIGATETOPAGE" -> {
                val oldId = importAction.targetPageId ?: importAction.targetPageImportId ?: ""
                NavigateToPageButtonAction(idMap[oldId] ?: oldId)
            }
            "GEMINI" -> GeminiButtonAction(importAction.prompt ?: "")
            "GEMINI_SEARCH" -> GeminiSearchButtonAction(importAction.prompt ?: "")
            "GEMINI_NANO" -> GeminiNanoButtonAction(importAction.intent ?: "")
            "GEMINI_VISION" -> GeminiVisionButtonAction(importAction.prompt ?: "", importAction.useCloud ?: false)
            "SMART_PREDICTION" -> {
                val predType = importAction.predictionType?.let {
                    try { PredictionType.valueOf(it) } catch(_: Exception) { PredictionType.ALL }
                } ?: PredictionType.ALL
                SmartPredictionButtonAction(
                    rank = importAction.rank ?: 1,
                    predictionType = predType
                )
            }
            "DEVICE_CONTROL" -> {
                val typeName = importAction.deviceActionType ?: "READ_TIME"
                ControlDeviceButtonAction(
                    actionType = try { DeviceActionType.valueOf(typeName) } catch(_: Exception) { DeviceActionType.READ_TIME },
                    volumeValue = importAction.volumeValue,
                    contactName = importAction.contactName,
                    contactPhone = importAction.contactPhone,
                    messageText = importAction.messageText,
                    includeWeekday = importAction.includeWeekday ?: false,
                    prefixText = importAction.prefixText,
                    suffixText = importAction.suffixText,
                    offsetValue = importAction.offsetValue ?: 0,
                    ignoreEmojis = importAction.ignoreEmojis ?: false
                )
            }
            "WEATHER" -> WeatherButtonAction()
            "GOOGLE_HOME" -> SmartHomeButtonAction(
                provider = SmartHomeProvider.GOOGLE_HOME,
                deviceId = importAction.googleHomeDeviceId ?: "",
                intent = importAction.googleHomeCommand ?: "",
                value = importAction.googleHomeValue
            )
            "SMART_HOME" -> SmartHomeButtonAction(
                provider = SmartHomeProvider.valueOf(importAction.smartHomeProvider ?: "PHILIPS_HUE"),
                deviceId = importAction.smartHomeDeviceId ?: "",
                deviceName = importAction.smartHomeDeviceName ?: "",
                intent = importAction.smartHomeIntent ?: "",
                value = importAction.smartHomeValue
            )
            "PREVIOUS_ACTION" -> PreviousActionButtonAction(importAction.rank ?: 1)
            "PLAY_MEDIA" -> PlayMediaButtonAction(
                provider = try { MediaProvider.valueOf(importAction.mediaProvider ?: "SPOTIFY") } catch(_: Exception) { MediaProvider.SPOTIFY },
                contentUri = importAction.mediaContentUri ?: "",
                contentName = importAction.mediaContentName ?: "",
                returnToAppDelayMs = importAction.mediaReturnDelayMs ?: 2000L
            )
            else -> null
        }
    }
}
