package com.andreas_kratzer.ghosttalk.ui.pages.components

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PredictionType
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction

data class ActionParams(
    val targetPageId: String = "",
    val geminiPrompt: String = "",
    val geminiVisionPlayShutterSound: Boolean = true,
    val rank: Int = 1,
    val predictionType: PredictionType = PredictionType.ALL,
    val deviceActionType: DeviceActionType = DeviceActionType.READ_TIME,
    val volumeValue: String = "50",
    val contactName: String = "",
    val contactPhone: String = "",
    val messageText: String = "",
    val includeWeekday: Boolean = false,
    val prefixText: String = "",
    val suffixText: String = "",
    val offsetValue: String = "0",
    val ignoreEmojis: Boolean = false,
    val smartHomeProvider: SmartHomeProvider = SmartHomeProvider.PHILIPS_HUE,
    val smartHomeDeviceId: String = "",
    val smartHomeDeviceName: String = "",
    val smartHomeIntent: String = "",
    val smartHomeValue: String = "",
    val mediaProvider: MediaProvider = MediaProvider.SPOTIFY,
    val mediaContentUri: String = "",
    val mediaContentName: String = "",
    val mediaReturnToAppDelaySec: String = "2",
    val mediaForcePlayViaMediaSession: Boolean = true
)

object ButtonActionFactory {
    fun actionTypeIdOf(action: ButtonAction): ActionTypeId {
        @Suppress("DEPRECATION")
        return when (action) {
            is NavigateToPageButtonAction -> ActionTypeId.NAVIGATE
            is NavigateBackButtonAction -> ActionTypeId.NAVIGATE_BACK
            is NavigateToStartPageButtonAction -> ActionTypeId.NAVIGATE_TO_START_PAGE
            is GeminiButtonAction -> ActionTypeId.GEMINI
            is GeminiSearchButtonAction -> ActionTypeId.GEMINI_SEARCH
            is GeminiNanoButtonAction -> ActionTypeId.GEMINI
            is GeminiVisionButtonAction -> ActionTypeId.GEMINI_VISION
            is WeatherButtonAction -> ActionTypeId.WEATHER
            is ControlDeviceButtonAction -> {
                when (action.actionType) {
                    DeviceActionType.READ_NOTIFICATIONS -> ActionTypeId.READ_NOTIFICATIONS
                    DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS -> ActionTypeId.TOGGLE_AUTO_READ
                    DeviceActionType.CLEAR_NOTIFICATIONS -> ActionTypeId.CLEAR_NOTIFICATIONS
                    DeviceActionType.SEND_MESSAGE -> ActionTypeId.SEND_MESSAGE
                    DeviceActionType.SEND_LAST_SPOKEN_SMS -> ActionTypeId.SEND_LAST_SPOKEN_SMS
                    DeviceActionType.START_CALL -> ActionTypeId.START_CALL
                    DeviceActionType.MEDIA_PLAY_PAUSE -> ActionTypeId.MEDIA_PLAY_PAUSE
                    DeviceActionType.MEDIA_NEXT -> ActionTypeId.MEDIA_NEXT
                    DeviceActionType.MEDIA_PREVIOUS -> ActionTypeId.MEDIA_PREVIOUS
                    DeviceActionType.READ_TIME -> ActionTypeId.READ_TIME
                    DeviceActionType.READ_DATE -> ActionTypeId.READ_DATE
                    DeviceActionType.READ_CALENDAR_ENTRIES -> ActionTypeId.READ_CALENDAR_ENTRIES
                    DeviceActionType.READ_BATTERY -> ActionTypeId.READ_BATTERY
                    DeviceActionType.VOLUME_MEDIA -> ActionTypeId.VOLUME_MEDIA
                    DeviceActionType.VOLUME_NOTIFICATION -> ActionTypeId.VOLUME_NOTIFICATION
                    DeviceActionType.VOLUME_ALARM -> ActionTypeId.VOLUME_ALARM
                    DeviceActionType.VOLUME_CALL -> ActionTypeId.VOLUME_CALL
                    DeviceActionType.VOLUME_IN_APP_TTS -> ActionTypeId.VOLUME_IN_APP_TTS
                    DeviceActionType.VOLUME_IN_APP_CUES -> ActionTypeId.VOLUME_IN_APP_CUES
                    DeviceActionType.STATUS_SILENT -> ActionTypeId.STATUS_SILENT
                    DeviceActionType.STATUS_VIBRATE -> ActionTypeId.STATUS_VIBRATE
                    DeviceActionType.STATUS_LOUD -> ActionTypeId.STATUS_LOUD
                    DeviceActionType.TOGGLE_SCANNING -> ActionTypeId.TOGGLE_SCANNING
                    DeviceActionType.INSTALL_UPDATE -> ActionTypeId.INSTALL_UPDATE
                    DeviceActionType.START_SYNC -> ActionTypeId.START_SYNC
                }
            }
            is PlayMediaButtonAction -> {
                when (action.provider) {
                    MediaProvider.SPOTIFY -> ActionTypeId.SPOTIFY
                    MediaProvider.YOUTUBE -> ActionTypeId.YOUTUBE
                    MediaProvider.YOUTUBE_MUSIC -> ActionTypeId.YOUTUBE_MUSIC
                    MediaProvider.AUDIBLE -> ActionTypeId.AUDIBLE
                }
            }
            is SmartHomeButtonAction -> {
                when (action.provider) {
                    SmartHomeProvider.PHILIPS_HUE -> ActionTypeId.PHILIPS_HUE
                    SmartHomeProvider.GOOGLE_HOME -> ActionTypeId.GOOGLE_HOME
                }
            }
            is FrequentActionButtonAction -> ActionTypeId.FREQUENT
            is SmartPredictionButtonAction -> ActionTypeId.SMART
            is PreviousActionButtonAction -> ActionTypeId.PREVIOUS
            else -> ActionTypeId.SPEAK
        }
    }

    fun buildAction(id: ActionTypeId, params: ActionParams): ButtonAction {
        return when (id) {
            ActionTypeId.NAVIGATE -> NavigateToPageButtonAction(params.targetPageId)
            ActionTypeId.NAVIGATE_BACK -> NavigateBackButtonAction()
            ActionTypeId.NAVIGATE_TO_START_PAGE -> NavigateToStartPageButtonAction()
            ActionTypeId.GEMINI -> GeminiButtonAction(params.geminiPrompt)
            ActionTypeId.GEMINI_SEARCH -> GeminiSearchButtonAction(params.geminiPrompt)
            ActionTypeId.GEMINI_VISION -> GeminiVisionButtonAction(params.geminiPrompt, true, params.geminiVisionPlayShutterSound)
            ActionTypeId.FREQUENT -> FrequentActionButtonAction(params.rank)
            ActionTypeId.PREVIOUS -> PreviousActionButtonAction(params.rank)
            ActionTypeId.SMART -> SmartPredictionButtonAction(params.rank, params.predictionType)
            ActionTypeId.WEATHER -> WeatherButtonAction()
            
            // Kommunikation
            ActionTypeId.READ_NOTIFICATIONS -> ControlDeviceButtonAction(
                actionType = DeviceActionType.READ_NOTIFICATIONS,
                contactName = params.contactName,
                contactPhone = params.contactPhone,
                ignoreEmojis = params.ignoreEmojis
            )
            ActionTypeId.TOGGLE_AUTO_READ -> ControlDeviceButtonAction(
                actionType = DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS
            )
            ActionTypeId.CLEAR_NOTIFICATIONS -> ControlDeviceButtonAction(
                actionType = DeviceActionType.CLEAR_NOTIFICATIONS,
                contactName = params.contactName,
                contactPhone = params.contactPhone
            )
            ActionTypeId.SEND_MESSAGE -> ControlDeviceButtonAction(
                actionType = DeviceActionType.SEND_MESSAGE,
                contactName = params.contactName,
                contactPhone = params.contactPhone,
                messageText = params.messageText
            )
            ActionTypeId.SEND_LAST_SPOKEN_SMS -> ControlDeviceButtonAction(
                actionType = DeviceActionType.SEND_LAST_SPOKEN_SMS,
                contactName = params.contactName,
                contactPhone = params.contactPhone
            )
            ActionTypeId.START_CALL -> ControlDeviceButtonAction(
                actionType = DeviceActionType.START_CALL,
                contactName = params.contactName,
                contactPhone = params.contactPhone
            )

            // Medien & Musik
            ActionTypeId.SPOTIFY -> PlayMediaButtonAction(
                provider = MediaProvider.SPOTIFY,
                contentUri = params.mediaContentUri,
                contentName = params.mediaContentName,
                returnToAppDelayMs = (params.mediaReturnToAppDelaySec.toLongOrNull() ?: 2L) * 1000L,
                forcePlayViaMediaSession = params.mediaForcePlayViaMediaSession
            )
            ActionTypeId.YOUTUBE -> PlayMediaButtonAction(
                provider = MediaProvider.YOUTUBE,
                contentUri = params.mediaContentUri,
                contentName = params.mediaContentName,
                returnToAppDelayMs = (params.mediaReturnToAppDelaySec.toLongOrNull() ?: 2L) * 1000L,
                forcePlayViaMediaSession = params.mediaForcePlayViaMediaSession
            )
            ActionTypeId.YOUTUBE_MUSIC -> PlayMediaButtonAction(
                provider = MediaProvider.YOUTUBE_MUSIC,
                contentUri = params.mediaContentUri,
                contentName = params.mediaContentName,
                returnToAppDelayMs = (params.mediaReturnToAppDelaySec.toLongOrNull() ?: 2L) * 1000L,
                forcePlayViaMediaSession = params.mediaForcePlayViaMediaSession
            )
            ActionTypeId.AUDIBLE -> PlayMediaButtonAction(
                provider = MediaProvider.AUDIBLE,
                contentUri = params.mediaContentUri,
                contentName = params.mediaContentName,
                returnToAppDelayMs = (params.mediaReturnToAppDelaySec.toLongOrNull() ?: 2L) * 1000L,
                forcePlayViaMediaSession = params.mediaForcePlayViaMediaSession
            )
            ActionTypeId.MEDIA_PLAY_PAUSE -> ControlDeviceButtonAction(actionType = DeviceActionType.MEDIA_PLAY_PAUSE)
            ActionTypeId.MEDIA_NEXT -> ControlDeviceButtonAction(actionType = DeviceActionType.MEDIA_NEXT)
            ActionTypeId.MEDIA_PREVIOUS -> ControlDeviceButtonAction(actionType = DeviceActionType.MEDIA_PREVIOUS)

            // Geräte & Einstellungen
            ActionTypeId.READ_TIME -> ControlDeviceButtonAction(
                actionType = DeviceActionType.READ_TIME,
                prefixText = params.prefixText.takeIf { it.isNotBlank() },
                suffixText = params.suffixText.takeIf { it.isNotBlank() },
                offsetValue = params.offsetValue.toIntOrNull() ?: 0
            )
            ActionTypeId.READ_DATE -> ControlDeviceButtonAction(
                actionType = DeviceActionType.READ_DATE,
                includeWeekday = params.includeWeekday,
                prefixText = params.prefixText.takeIf { it.isNotBlank() },
                suffixText = params.suffixText.takeIf { it.isNotBlank() },
                offsetValue = params.offsetValue.toIntOrNull() ?: 0
            )
            ActionTypeId.READ_CALENDAR_ENTRIES -> ControlDeviceButtonAction(
                actionType = DeviceActionType.READ_CALENDAR_ENTRIES,
                prefixText = params.prefixText.takeIf { it.isNotBlank() },
                suffixText = params.suffixText.takeIf { it.isNotBlank() },
                offsetValue = params.offsetValue.toIntOrNull() ?: 0
            )
            ActionTypeId.READ_BATTERY -> ControlDeviceButtonAction(actionType = DeviceActionType.READ_BATTERY)
            ActionTypeId.VOLUME_MEDIA -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_MEDIA, volumeValue = params.volumeValue)
            ActionTypeId.VOLUME_NOTIFICATION -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_NOTIFICATION, volumeValue = params.volumeValue)
            ActionTypeId.VOLUME_ALARM -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_ALARM, volumeValue = params.volumeValue)
            ActionTypeId.VOLUME_CALL -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_CALL, volumeValue = params.volumeValue)
            ActionTypeId.VOLUME_IN_APP_TTS -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_IN_APP_TTS, volumeValue = params.volumeValue)
            ActionTypeId.VOLUME_IN_APP_CUES -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_IN_APP_CUES, volumeValue = params.volumeValue)
            ActionTypeId.STATUS_SILENT -> ControlDeviceButtonAction(actionType = DeviceActionType.STATUS_SILENT)
            ActionTypeId.STATUS_VIBRATE -> ControlDeviceButtonAction(actionType = DeviceActionType.STATUS_VIBRATE)
            ActionTypeId.STATUS_LOUD -> ControlDeviceButtonAction(actionType = DeviceActionType.STATUS_LOUD)
            ActionTypeId.TOGGLE_SCANNING -> ControlDeviceButtonAction(actionType = DeviceActionType.TOGGLE_SCANNING)
            ActionTypeId.INSTALL_UPDATE -> ControlDeviceButtonAction(actionType = DeviceActionType.INSTALL_UPDATE)
            ActionTypeId.START_SYNC -> ControlDeviceButtonAction(actionType = DeviceActionType.START_SYNC)

            // Smart Home
            ActionTypeId.PHILIPS_HUE -> SmartHomeButtonAction(
                provider = SmartHomeProvider.PHILIPS_HUE,
                deviceId = params.smartHomeDeviceId,
                deviceName = params.smartHomeDeviceName,
                intent = params.smartHomeIntent,
                value = params.smartHomeValue.takeIf { it.isNotBlank() }
            )
            ActionTypeId.GOOGLE_HOME -> SmartHomeButtonAction(
                provider = SmartHomeProvider.GOOGLE_HOME,
                deviceId = params.smartHomeDeviceId,
                deviceName = params.smartHomeDeviceName,
                intent = params.smartHomeIntent,
                value = params.smartHomeValue.takeIf { it.isNotBlank() }
            )
            
            ActionTypeId.SPEAK -> SpeakTextButtonAction()
        }
    }
}
