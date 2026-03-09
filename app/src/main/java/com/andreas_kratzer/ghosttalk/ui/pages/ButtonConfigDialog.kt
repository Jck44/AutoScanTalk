package com.andreas_kratzer.ghosttalk.ui.pages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonConfigDialog(
    initialConfig: ButtonConfig?,
    availablePages: List<Page>,
    buttonId: String,
    featureGuard: com.andreas_kratzer.ghosttalk.domain.settings.FeatureGuard,
    templates: List<com.andreas_kratzer.ghosttalk.model.PageTemplate>,
    onDismiss: () -> Unit,
    onSave: (ButtonConfig?) -> Unit,
    onTest: ((ButtonConfig) -> Unit)? = null,
    onNavigateToPage: ((String) -> Unit)? = null,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)? = null
) {
    val dimensions = LocalDimensions.current
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> 
        // Permissions handled reactively
    }
    
    // Current State
    var label by remember { mutableStateOf(initialConfig?.label ?: "") }
    var isError by remember { mutableStateOf(false) }
    var spokenText by remember { mutableStateOf(initialConfig?.spokenText ?: "") }

    var ttsFeedback by remember { 
        mutableStateOf(
            (initialConfig?.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text ?: ""
        ) 
    }
    var isActive by remember { mutableStateOf(initialConfig?.isActive ?: true) }
    var playActionAsAuditoryCue by remember { mutableStateOf(initialConfig?.playActionAsAuditoryCue ?: false) }
    
    // Action Type selection
    val actionTypeSpeak = stringResource(R.string.button_action_speak_text)
    val actionTypeNavigate = stringResource(R.string.button_action_navigate_page)
    val actionTypeGemini = stringResource(R.string.button_action_gemini)
    val actionTypeGeminiSearch = stringResource(R.string.button_action_gemini_search)
    val actionTypeGeminiNano = stringResource(R.string.button_action_gemini_nano)
    val actionTypeFrequent = stringResource(R.string.button_action_frequent_action)
    val actionTypeSmart = stringResource(R.string.button_action_smart_prediction)
    val actionTypeControlDevice = stringResource(R.string.button_action_control_device)
    val actionTypeWeather = stringResource(R.string.button_action_weather)

    val actionTypes = remember(featureGuard, actionTypeControlDevice, actionTypeWeather) {
        val base = mutableListOf(
            actionTypeSpeak, actionTypeNavigate, actionTypeFrequent, actionTypeControlDevice, actionTypeWeather
        )
        if (featureGuard.isActionEnabled(GeminiButtonAction(""))) {
            base.add(actionTypeGemini)
            base.add(actionTypeGeminiSearch)
        }
        if (featureGuard.isActionEnabled(GeminiNanoButtonAction("")) || initialConfig?.buttonAction is GeminiNanoButtonAction) {
            base.add(actionTypeGeminiNano)
        }
        if (featureGuard.isActionEnabled(SmartPredictionButtonAction())) {
            base.add(actionTypeSmart)
        }
        base.toList()
    }
    
    var selectedActionType by remember {
        mutableStateOf(
            when (initialConfig?.buttonAction) {
                is NavigateToPageButtonAction -> actionTypeNavigate
                is GeminiButtonAction -> actionTypeGemini
                is GeminiSearchButtonAction -> actionTypeGeminiSearch
                is GeminiNanoButtonAction -> actionTypeGeminiNano
                is FrequentActionButtonAction -> actionTypeFrequent
                is SmartPredictionButtonAction -> actionTypeSmart
                is ControlDeviceButtonAction -> actionTypeControlDevice
                is com.andreas_kratzer.ghosttalk.model.WeatherButtonAction -> actionTypeWeather
                else -> actionTypeSpeak
            }
        )
    }
    var expandedActionType by remember { mutableStateOf(false) }

    // TTS Mode Details (Global for all button actions)
    val ttsModeNormalLabel = stringResource(R.string.settings_tts_mode_normal)
    val ttsModeWhisperLabel = stringResource(R.string.settings_tts_mode_whisper)
    val ttsModeShoutLabel = stringResource(R.string.settings_tts_mode_shout)

    var selectedButtonTtsMode by remember { 
        mutableStateOf(
            when (initialConfig?.buttonAction?.ttsMode) {
                "WHISPER" -> ttsModeWhisperLabel
                "SHOUT" -> ttsModeShoutLabel
                else -> ttsModeNormalLabel
            }
        ) 
    }
    var expandedButtonTtsMode by remember { mutableStateOf(false) }
    val buttonTtsModes = listOf(ttsModeNormalLabel, ttsModeWhisperLabel, ttsModeShoutLabel)


    // Navigation Details
    val navAction = initialConfig?.buttonAction as? NavigateToPageButtonAction
    var navigateToPageId by remember { mutableStateOf(navAction?.pageId ?: "") }

    // Gemini Details
    val geminiAction = initialConfig?.buttonAction as? GeminiButtonAction
    val geminiSearchAction = initialConfig?.buttonAction as? GeminiSearchButtonAction
    val geminiNanoAction = initialConfig?.buttonAction as? GeminiNanoButtonAction
    var geminiPrompt by remember { mutableStateOf(geminiAction?.prompt ?: geminiSearchAction?.prompt ?: geminiNanoAction?.intent ?: "") }

    // Frequent Action Details
    val frequentActionDef = initialConfig?.buttonAction as? FrequentActionButtonAction
    var frequentRank by remember { mutableStateOf((frequentActionDef?.rank ?: 1).toString()) }

    // Smart Prediction Details
    val smartActionDef = initialConfig?.buttonAction as? SmartPredictionButtonAction
    var smartRank by remember { mutableStateOf((smartActionDef?.rank ?: 1).toString()) }

    // Device Control Details
    val controlActionDef = initialConfig?.buttonAction as? ControlDeviceButtonAction
    var controlActionType by remember { 
        mutableStateOf(controlActionDef?.actionType ?: DeviceActionType.READ_NOTIFICATIONS) 
    }
    var controlVolumeValue by remember { mutableStateOf(controlActionDef?.volumeValue ?: "50") }
    val contactPickerTitle = stringResource(R.string.contact_picker_title)
    var controlContactName by remember { mutableStateOf(controlActionDef?.contactName ?: contactPickerTitle) }
    var controlContactPhone by remember { mutableStateOf(controlActionDef?.contactPhone ?: "") }
    var controlMessageText by remember { mutableStateOf(controlActionDef?.messageText ?: "") }

    // Helper to build the action object from current UI state
    fun buildButtonAction(): ButtonAction {
        val resolvedTtsMode = when (selectedButtonTtsMode) {
            ttsModeWhisperLabel -> "WHISPER"
            ttsModeShoutLabel -> "SHOUT"
            else -> "NORMAL"
        }
        return when (selectedActionType) {
            actionTypeNavigate -> NavigateToPageButtonAction(pageId = navigateToPageId, ttsMode = resolvedTtsMode)
            actionTypeGemini -> GeminiButtonAction(prompt = geminiPrompt, ttsMode = resolvedTtsMode)
            actionTypeGeminiSearch -> GeminiSearchButtonAction(prompt = geminiPrompt, ttsMode = resolvedTtsMode)
            actionTypeGeminiNano -> GeminiNanoButtonAction(intent = geminiPrompt, ttsMode = resolvedTtsMode)
            actionTypeFrequent -> FrequentActionButtonAction(rank = frequentRank.toIntOrNull()?.coerceAtLeast(1) ?: 1, ttsMode = resolvedTtsMode)
            actionTypeSmart -> SmartPredictionButtonAction(rank = smartRank.toIntOrNull()?.coerceAtLeast(1) ?: 1, ttsMode = resolvedTtsMode)
            actionTypeWeather -> com.andreas_kratzer.ghosttalk.model.WeatherButtonAction(ttsMode = resolvedTtsMode)
            actionTypeControlDevice -> ControlDeviceButtonAction(
                actionType = controlActionType,
                volumeValue = controlVolumeValue,
                contactName = controlContactName,
                contactPhone = controlContactPhone,
                messageText = controlMessageText,
                ttsMode = resolvedTtsMode
            )
            else -> SpeakTextButtonAction(ttsMode = resolvedTtsMode)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialConfig == null) stringResource(R.string.button_dialog_new_title) else stringResource(R.string.button_dialog_edit_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // IsActive Toggle (Moved to top)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.button_is_active_label), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                }

                if (!featureGuard.isActionEnabled(SmartPredictionButtonAction()) && initialConfig?.buttonAction is SmartPredictionButtonAction) {
                    Text(
                        text = stringResource(R.string.settings_smart_prediction_disabled_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = dimensions.paddingMedium)
                    )
                }
                

                if (!featureGuard.isActionEnabled(GeminiNanoButtonAction("")) && initialConfig?.buttonAction is GeminiNanoButtonAction) {
                    Text(
                        text = stringResource(R.string.settings_gemini_nano_disabled_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = dimensions.paddingMedium)
                    )
                }

                // Label Input
                OutlinedTextField(
                    value = label,
                    onValueChange = { 
                        label = it 
                        if (it.isNotBlank()) isError = false
                    },
                    label = { Text(stringResource(R.string.button_label_field)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(stringResource(R.string.error_button_label_required))
                        }
                    }
                )


                // Explicit Spoken Text Input
                OutlinedTextField(
                    value = spokenText,
                    onValueChange = { spokenText = it },
                    label = { Text(stringResource(R.string.button_spoken_text_field)) },
                    singleLine = false,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )

                // Hinweistext (formerly Auditory Cue)
                OutlinedTextField(
                    value = ttsFeedback,
                    onValueChange = { ttsFeedback = it },
                    label = { Text(stringResource(R.string.button_auditory_cue_field)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )

                // Action Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedActionType,
                    onExpandedChange = { expandedActionType = !expandedActionType }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedActionType,
                        onValueChange = { },
                        label = { Text(stringResource(R.string.button_action_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedActionType) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedActionType,
                        onDismissRequest = { expandedActionType = false }
                    ) {
                        actionTypes.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    selectedActionType = selectionOption
                                    expandedActionType = false
                                }
                            )
                        }
                    }
                }

                // Conditional fields for specific actions
                when (selectedActionType) {
                    actionTypeNavigate -> {
                        NavigationActionFields(
                            navigateToPageId = navigateToPageId,
                            onPageSelected = { navigateToPageId = it },
                            availablePages = availablePages,
                            templates = templates,
                            onNavigateToPage = onNavigateToPage,
                            onBeforeCreatePage = {
                                if (label.isBlank()) {
                                    isError = true
                                    false
                                } else {
                                    true
                                }
                            },
                            onCreatePage = { name, rows, cols, templateId, onCreated ->
                                onCreatePage?.invoke(name, rows, cols, templateId) { newId ->
                                    // Update local state
                                    navigateToPageId = newId
                                    selectedActionType = actionTypeNavigate
                                    
                                    // Save the button immediately with the new page ID
                                    val action = buildButtonAction()
                                    val cue = if (ttsFeedback.isNotBlank()) {
                                        AuditoryCue.TextToSpeechCue(text = ttsFeedback)
                                    } else {
                                        null
                                    }

                                    onSave(
                                        ButtonConfig(
                                            id = buttonId, 
                                            label = label, 
                                            spokenText = spokenText.takeIf { it.isNotBlank() },
                                            buttonAction = action,
                                            isActive = isActive,
                                            playActionAsAuditoryCue = playActionAsAuditoryCue,
                                            auditoryCue = cue
                                        )
                                    )
                                    onCreated(newId)
                                }
                            },
                            onDismissDialog = onDismiss
                        )

                    }
                    actionTypeGemini, actionTypeGeminiSearch -> {
                        GeminiActionFields(
                            prompt = geminiPrompt,
                            onPromptChanged = { geminiPrompt = it }
                        )
                    }
                    actionTypeGeminiNano -> {
                        GeminiNanoActionFields(
                            selectedIntent = geminiPrompt,
                            onIntentSelected = { geminiPrompt = it }
                        )
                    }
                    actionTypeFrequent -> {
                        RankActionFields(
                            rank = frequentRank,
                            onRankChanged = { frequentRank = it }
                        )
                    }
                    actionTypeSmart -> {
                        RankActionFields(
                            rank = smartRank,
                            onRankChanged = { smartRank = it }
                        )
                    }
                    actionTypeWeather -> {
                         // No additional fields for simple weather action
                    }
                    actionTypeControlDevice -> {
                        ControlDeviceActionFields(
                            selectedType = controlActionType,
                            onTypeSelected = { controlActionType = it },
                            volumeValue = controlVolumeValue,
                            onVolumeValueChange = { controlVolumeValue = it },
                            contactName = controlContactName,
                            onContactSelected = { name, phone ->
                                controlContactName = name
                                controlContactPhone = phone
                            },
                            messageText = controlMessageText,
                            onMessageTextChange = { controlMessageText = it }
                        )
                    }
                }

                // Auditory Cue Routing Toggle (Visible for actions with audio output)
                if (selectedActionType == actionTypeSpeak || 
                    selectedActionType == actionTypeGemini || 
                    selectedActionType == actionTypeGeminiSearch ||
                    selectedActionType == actionTypeGeminiNano ||
                    selectedActionType == actionTypeSmart || 
                    selectedActionType == actionTypeWeather || 
                    selectedActionType == actionTypeControlDevice) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.button_play_as_cue), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = playActionAsAuditoryCue,
                            onCheckedChange = { playActionAsAuditoryCue = it }
                        )
                    }

                    // TTS Mode Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedButtonTtsMode,
                        onExpandedChange = { expandedButtonTtsMode = !expandedButtonTtsMode }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedButtonTtsMode,
                            onValueChange = { },
                            label = { Text(stringResource(R.string.settings_tts_mode)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedButtonTtsMode) },
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedButtonTtsMode,
                            onDismissRequest = { expandedButtonTtsMode = false }
                        ) {
                            buttonTtsModes.forEach { modeLabel ->
                                DropdownMenuItem(
                                    text = { Text(modeLabel) },
                                    onClick = {
                                        selectedButtonTtsMode = modeLabel
                                        expandedButtonTtsMode = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)) {
                if (onTest != null) {
                    Button(
                        onClick = {
                            if (label.isNotBlank()) {
                                val action = buildButtonAction()
                                val cue = if (ttsFeedback.isNotBlank()) {
                                    AuditoryCue.TextToSpeechCue(text = ttsFeedback)
                                } else {
                                    null
                                }

                                onTest(
                                    ButtonConfig(
                                        id = buttonId, 
                                        label = label, 
                                        spokenText = spokenText.takeIf { it.isNotBlank() },
                                        buttonAction = action,
                                        isActive = isActive,
                                        playActionAsAuditoryCue = playActionAsAuditoryCue,
                                        auditoryCue = cue
                                    )
                                )
                            } else {
                                isError = true
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.button_action_test))
                    }
                }
                
                Button(
                    onClick = {
                        if (label.isNotBlank()) {
                            val action = buildButtonAction()
                            
                            // Check for weather permission if weather is selected
                            if (action is GeminiNanoButtonAction && action.intent == "weather") {
                                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (!hasFine && !hasCoarse) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }

                            val cue = if (ttsFeedback.isNotBlank()) {
                                AuditoryCue.TextToSpeechCue(text = ttsFeedback)

                            } else {
                                null
                            }

                            onSave(
                                ButtonConfig(
                                    id = buttonId, 
                                    label = label, 
                                    spokenText = spokenText.takeIf { it.isNotBlank() },
                                    buttonAction = action,
                                    isActive = isActive,
                                    playActionAsAuditoryCue = playActionAsAuditoryCue,
                                    auditoryCue = cue
                                )
                            )
                        } else {
                            isError = true
                        }
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            Button(
                onClick = { onSave(null) },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_clear_delete))
            }
        }
    )
}
