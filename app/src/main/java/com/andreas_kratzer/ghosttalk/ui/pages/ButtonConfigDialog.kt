package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@Composable
fun ButtonConfigDialog(
    buttonConfig: ButtonConfig,
    pages: List<Page>,
    templates: List<PageTemplate>,
    onSave: (ButtonConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(buttonConfig.label) }
    var spokenText by remember { mutableStateOf(buttonConfig.spokenText ?: "") }
    var auditoryCueText by remember { 
        mutableStateOf((buttonConfig.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text ?: "") 
    }
    var isActive by remember { mutableStateOf(buttonConfig.isActive) }
    var playActionAsAuditoryCue by remember { mutableStateOf(buttonConfig.playActionAsAuditoryCue) }
    
    val actionTypeSpeak = stringResource(R.string.button_action_speak_text)
    val actionTypeNavigate = stringResource(R.string.button_action_navigate_page)
    val actionTypeGemini = stringResource(R.string.button_action_gemini)
    val actionTypeGeminiSearch = stringResource(R.string.button_action_gemini_search)
    val actionTypeGeminiNano = stringResource(R.string.button_action_gemini_nano)
    val actionTypeFrequent = stringResource(R.string.button_action_frequent_action)
    val actionTypeSmart = stringResource(R.string.button_action_smart_prediction)
    val actionTypeDevice = stringResource(R.string.button_action_control_device)
    val actionTypeWeather = stringResource(R.string.button_action_weather)

    var selectedActionType by remember {
        mutableStateOf(
            when (val action = buttonConfig.buttonAction) {
                is NavigateToPageButtonAction -> actionTypeNavigate
                is GeminiButtonAction -> actionTypeGemini
                is GeminiSearchButtonAction -> actionTypeGeminiSearch
                is GeminiNanoButtonAction -> actionTypeGeminiNano
                is FrequentActionButtonAction -> actionTypeFrequent
                is SmartPredictionButtonAction -> actionTypeSmart
                is ControlDeviceButtonAction -> actionTypeDevice
                is WeatherButtonAction -> actionTypeWeather
                else -> actionTypeSpeak
            }
        )
    }

    // Navigation specific state
    var targetPageId by remember {
        mutableStateOf((buttonConfig.buttonAction as? NavigateToPageButtonAction)?.pageId ?: "")
    }

    // Gemini specific state
    var geminiPrompt by remember {
        mutableStateOf(
            when(val action = buttonConfig.buttonAction) {
                is GeminiButtonAction -> action.prompt
                is GeminiSearchButtonAction -> action.prompt
                is GeminiNanoButtonAction -> action.intent
                else -> ""
            }
        )
    }

    // Frequent/Smart specific state
    var rank by remember {
        mutableStateOf(
            when(val action = buttonConfig.buttonAction) {
                is FrequentActionButtonAction -> action.rank
                is SmartPredictionButtonAction -> action.rank
                else -> 1
            }
        )
    }

    // Device control specific state
    var deviceActionType by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.actionType ?: DeviceActionType.READ_TIME)
    }
    var volumeValue by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.volumeValue ?: "50")
    }
    var contactName by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.contactName ?: "")
    }
    var contactPhone by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.contactPhone ?: "")
    }
    var messageText by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.messageText ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.button_dialog_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.paddingSmall)
            ) {
                SettingsEditTextItem(
                    label = stringResource(R.string.button_label_field),
                    value = label,
                    onValueChange = { label = it }
                )
                
                SettingsEditTextItem(
                    label = stringResource(R.string.button_spoken_text_field),
                    value = spokenText,
                    onValueChange = { spokenText = it }
                )

                SettingsEditTextItem(
                    label = stringResource(R.string.button_auditory_cue_field),
                    value = auditoryCueText,
                    onValueChange = { auditoryCueText = it }
                )

                SettingsToggleItem(
                    label = stringResource(R.string.button_is_active_label),
                    checked = isActive,
                    onCheckedChange = { isActive = it }
                )

                SettingsToggleItem(
                    label = stringResource(R.string.button_play_as_cue),
                    checked = playActionAsAuditoryCue,
                    onCheckedChange = { playActionAsAuditoryCue = it }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsDropdownItem(
                    label = stringResource(R.string.button_action_label),
                    selectedOption = selectedActionType,
                    options = listOf(
                        actionTypeSpeak,
                        actionTypeNavigate,
                        actionTypeGemini,
                        actionTypeGeminiSearch,
                        actionTypeGeminiNano,
                        actionTypeFrequent,
                        actionTypeSmart,
                        actionTypeWeather,
                        actionTypeDevice
                    ).map { type -> type to { selectedActionType = type } }
                )

                ActionConfigFields(
                    selectedActionType = selectedActionType,
                    pages = pages,
                    templates = templates,
                    targetPageId = targetPageId,
                    onTargetPageIdChange = { targetPageId = it },
                    geminiPrompt = geminiPrompt,
                    onGeminiPromptChange = { geminiPrompt = it },
                    rank = rank,
                    onRankChange = { rank = it },
                    deviceActionType = deviceActionType,
                    onDeviceActionTypeChange = { deviceActionType = it },
                    volumeValue = volumeValue,
                    onVolumeValueChange = { volumeValue = it },
                    contactName = contactName,
                    onContactNameChange = { contactName = it },
                    contactPhone = contactPhone,
                    onContactPhoneChange = { contactPhone = it },
                    messageText = messageText,
                    onMessageTextChange = { messageText = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val action = when (selectedActionType) {
                    actionTypeNavigate -> NavigateToPageButtonAction(targetPageId)
                    actionTypeGemini -> GeminiButtonAction(geminiPrompt)
                    actionTypeGeminiSearch -> GeminiSearchButtonAction(geminiPrompt)
                    actionTypeGeminiNano -> GeminiNanoButtonAction(geminiPrompt)
                    actionTypeFrequent -> FrequentActionButtonAction(rank)
                    actionTypeSmart -> SmartPredictionButtonAction(rank)
                    actionTypeWeather -> WeatherButtonAction()
                    actionTypeDevice -> ControlDeviceButtonAction(
                        actionType = deviceActionType,
                        volumeValue = volumeValue,
                        contactName = contactName,
                        contactPhone = contactPhone,
                        messageText = messageText
                    )
                    else -> SpeakTextButtonAction()
                }

                onSave(
                    buttonConfig.copy(
                        label = label,
                        spokenText = spokenText.ifBlank { null },
                        auditoryCue = if (auditoryCueText.isNotBlank()) AuditoryCue.TextToSpeechCue(auditoryCueText) else null,
                        isActive = isActive,
                        playActionAsAuditoryCue = playActionAsAuditoryCue,
                        buttonAction = action
                    )
                )
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
