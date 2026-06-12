package com.andreas_kratzer.ghosttalk.ui.pages.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.PredictionType
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode

@Stable
class ButtonConfigDialogState(
    val buttonConfig: ButtonConfig
) {
    var label by mutableStateOf(buttonConfig.label)
    var spokenText by mutableStateOf(buttonConfig.spokenText ?: "")
    var spokenTextMode by mutableStateOf(buttonConfig.spokenTextMode)
    var audioFileName by mutableStateOf(buttonConfig.audioFileName)
    var auditoryCueText by mutableStateOf((buttonConfig.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text ?: "")
    var isActive by mutableStateOf(buttonConfig.isActive)
    var playActionAsAuditoryCue by mutableStateOf(buttonConfig.playActionAsAuditoryCue)

    var selectedActionType by mutableStateOf(ButtonActionFactory.actionTypeIdOf(buttonConfig.buttonAction))

    // Action specific parameters
    var targetPageId by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction)?.pageId ?: "")
    var geminiPrompt by mutableStateOf(
        when (val action = buttonConfig.buttonAction) {
            is com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction -> action.prompt
            is com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction -> action.prompt
            is com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction -> action.intent
            is com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction -> action.prompt
            else -> ""
        }
    )
    var geminiVisionPlayShutterSound by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction)?.playShutterSound ?: true)
    
    var rank by mutableIntStateOf(
        when (val action = buttonConfig.buttonAction) {
            is com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction -> action.rank
            is com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction -> action.rank
            is com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction -> action.rank
            else -> 1
        }
    )
    var predictionType by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction)?.predictionType ?: PredictionType.ALL)

    var deviceActionType by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction)?.actionType ?: DeviceActionType.READ_TIME)
    var volumeValue by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction)?.volumeValue ?: "50")
    var contactName by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction)?.contactName ?: "")
    var contactPhone by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction)?.contactPhone ?: "")
    var messageText by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction)?.messageText ?: "")
    var includeWeekday by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction)?.includeWeekday ?: false)
    var prefixText by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction)?.prefixText ?: "")
    var suffixText by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction)?.suffixText ?: "")
    var offsetValue by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction)?.offsetValue?.toString() ?: "0")
    var ignoreEmojis by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction)?.ignoreEmojis ?: false)

    var smartHomeProvider by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction)?.provider ?: SmartHomeProvider.PHILIPS_HUE)
    var smartHomeDeviceId by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction)?.deviceId ?: "")
    var smartHomeDeviceName by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction)?.deviceName ?: "")
    var smartHomeIntent by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction)?.intent ?: "")
    var smartHomeValue by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction)?.value ?: "")

    var mediaProvider by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction)?.provider ?: MediaProvider.SPOTIFY)
    var mediaContentUri by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction)?.contentUri ?: "")
    var mediaContentName by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction)?.contentName ?: "")
    var mediaReturnToAppDelaySec by mutableStateOf(((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction)?.returnToAppDelayMs ?: 2000L).div(1000L).toString())
    var mediaForcePlayViaMediaSession by mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction)?.forcePlayViaMediaSession ?: true)

    var isRecording by mutableStateOf(false)
    var isPlayingAudio by mutableStateOf(false)
    var showDeleteConfirmation by mutableStateOf(false)
    var isFetchingDevices by mutableStateOf(false)

    fun buildAction(): ButtonAction {
        val params = ActionParams(
            targetPageId = targetPageId,
            geminiPrompt = geminiPrompt,
            geminiVisionPlayShutterSound = geminiVisionPlayShutterSound,
            rank = rank,
            predictionType = predictionType,
            deviceActionType = deviceActionType,
            volumeValue = volumeValue,
            contactName = contactName,
            contactPhone = contactPhone,
            messageText = messageText,
            includeWeekday = includeWeekday,
            prefixText = prefixText,
            suffixText = suffixText,
            offsetValue = offsetValue,
            ignoreEmojis = ignoreEmojis,
            smartHomeProvider = smartHomeProvider,
            smartHomeDeviceId = smartHomeDeviceId,
            smartHomeDeviceName = smartHomeDeviceName,
            smartHomeIntent = smartHomeIntent,
            smartHomeValue = smartHomeValue,
            mediaProvider = mediaProvider,
            mediaContentUri = mediaContentUri,
            mediaContentName = mediaContentName,
            mediaReturnToAppDelaySec = mediaReturnToAppDelaySec,
            mediaForcePlayViaMediaSession = mediaForcePlayViaMediaSession
        )
        return ButtonActionFactory.buildAction(selectedActionType, params)
    }

    fun buildConfig(): ButtonConfig {
        return buttonConfig.copy(
            label = label,
            spokenText = if (spokenText.isNotBlank()) spokenText else null,
            spokenTextMode = spokenTextMode,
            audioFileName = audioFileName,
            auditoryCue = if (auditoryCueText.isNotBlank()) AuditoryCue.TextToSpeechCue(auditoryCueText) else null,
            isActive = isActive,
            playActionAsAuditoryCue = playActionAsAuditoryCue,
            buttonAction = buildAction()
        )
    }
}

@Composable
fun rememberButtonConfigDialogState(buttonConfig: ButtonConfig): ButtonConfigDialogState {
    return remember(buttonConfig) {
        ButtonConfigDialogState(buttonConfig)
    }
}
