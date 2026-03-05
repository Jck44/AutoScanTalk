package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.model.NotificationButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.model.ChangeVolumeButtonAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R

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
    // Current State
    var label by remember { mutableStateOf(initialConfig?.label ?: "") }
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
    val actionTypeNotification = stringResource(R.string.button_action_notification)
    val actionTypeVolumeTts = stringResource(R.string.button_action_volume_tts)
    val actionTypeVolumeCues = stringResource(R.string.button_action_volume_cues)

    val actionTypes = remember(featureGuard, actionTypeNotification) {
        val base = mutableListOf(
            actionTypeSpeak, actionTypeNavigate, actionTypeFrequent,
            actionTypeVolumeTts, actionTypeVolumeCues
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
        base.add(actionTypeNotification)
        base.toList()
    }
    
    var selectedActionType by remember {
        mutableStateOf(
            when (val action = initialConfig?.buttonAction) {
                is NavigateToPageButtonAction -> actionTypeNavigate
                is GeminiButtonAction -> actionTypeGemini
                is GeminiSearchButtonAction -> actionTypeGeminiSearch
                is GeminiNanoButtonAction -> actionTypeGeminiNano
                is FrequentActionButtonAction -> actionTypeFrequent
                is SmartPredictionButtonAction -> actionTypeSmart
                is NotificationButtonAction -> actionTypeNotification
                is ChangeVolumeButtonAction -> if (action.isForCues) actionTypeVolumeCues else actionTypeVolumeTts
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
    var geminiPrompt by remember { mutableStateOf(geminiAction?.prompt ?: geminiSearchAction?.prompt ?: geminiNanoAction?.prompt ?: "") }

    // Frequent Action Details
    val frequentActionDef = initialConfig?.buttonAction as? FrequentActionButtonAction
    var frequentRank by remember { mutableStateOf((frequentActionDef?.rank ?: 1).toString()) }

    // Smart Prediction Details
    val smartActionDef = initialConfig?.buttonAction as? SmartPredictionButtonAction
    var smartRank by remember { mutableStateOf((smartActionDef?.rank ?: 1).toString()) }

    // Notification Details
    val notificationActionDef = initialConfig?.buttonAction as? NotificationButtonAction
    var notificationTargetApp by remember { mutableStateOf(notificationActionDef?.targetApp ?: "ALL") }
    val appAllLabel = stringResource(R.string.button_notification_target_all)
    val notificationApps = mapOf(
        "ALL" to appAllLabel,
        "com.whatsapp" to "WhatsApp",
        "org.thoughtcrime.securesms" to "Signal",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.apps.messaging" to "SMS (Messages)"
    )

    // Volume Details
    val volumeActionDef = initialConfig?.buttonAction as? ChangeVolumeButtonAction
    val volumeAbsolutLabel = "Absolut"
    val volumeRelativLabel = "Relativ"

    var selectedVolumeType by remember {
        mutableStateOf(
            if (volumeActionDef?.isAbsolute == true) volumeAbsolutLabel else volumeRelativLabel
        )
    }
    val volumeTypes = listOf(volumeAbsolutLabel, volumeRelativLabel)

    var volumePercentInput by remember {
        mutableStateOf(
            if (volumeActionDef != null) {
                (volumeActionDef.amount * 100).toInt().toString()
            } else {
                "10" // Default 10%
            }
        )
    }

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
            actionTypeGeminiNano -> GeminiNanoButtonAction(prompt = geminiPrompt, ttsMode = resolvedTtsMode)
            actionTypeFrequent -> FrequentActionButtonAction(rank = frequentRank.toIntOrNull()?.coerceAtLeast(1) ?: 1, ttsMode = resolvedTtsMode)
            actionTypeSmart -> SmartPredictionButtonAction(rank = smartRank.toIntOrNull()?.coerceAtLeast(1) ?: 1, ttsMode = resolvedTtsMode)
            actionTypeNotification -> NotificationButtonAction(targetApp = notificationTargetApp, ttsMode = resolvedTtsMode)
            actionTypeVolumeTts, actionTypeVolumeCues -> {
                val isAbsoluteAmount = selectedVolumeType == volumeAbsolutLabel
                val parseAmount = volumePercentInput.toFloatOrNull() ?: 10f
                val volAmount = parseAmount / 100.0f
                val isForCuesAmount = selectedActionType == actionTypeVolumeCues
                ChangeVolumeButtonAction(isAbsolute = isAbsoluteAmount, amount = volAmount, isForCues = isForCuesAmount, ttsMode = resolvedTtsMode)
            }
            else -> SpeakTextButtonAction(ttsMode = resolvedTtsMode)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialConfig == null) stringResource(R.string.button_dialog_new_title) else stringResource(R.string.button_dialog_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // IsActive Toggle (Moved to top)
                androidx.compose.foundation.layout.Row(
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
                            .padding(bottom = 8.dp)
                    )
                }
                
                if (!featureGuard.isActionEnabled(NotificationButtonAction()) && initialConfig?.buttonAction is NotificationButtonAction) {
                    Text(
                        text = stringResource(R.string.settings_notification_disabled_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                if (!featureGuard.isActionEnabled(GeminiNanoButtonAction("")) && initialConfig?.buttonAction is GeminiNanoButtonAction) {
                    Text(
                        text = stringResource(R.string.settings_gemini_nano_disabled_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                // Label Input
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.button_label_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Explicit Spoken Text Input
                OutlinedTextField(
                    value = spokenText,
                    onValueChange = { spokenText = it },
                    label = { Text(stringResource(R.string.button_spoken_text_field)) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )

                // Hinweistext (formerly Auditory Cue)
                OutlinedTextField(
                    value = ttsFeedback,
                    onValueChange = { ttsFeedback = it },
                    label = { Text(stringResource(R.string.button_auditory_cue_field)) },
                    singleLine = true,
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
                            onCreatePage = onCreatePage,
                            onDismissDialog = onDismiss
                        )
                    }
                    actionTypeGemini, actionTypeGeminiSearch, actionTypeGeminiNano -> {
                        GeminiActionFields(
                            prompt = geminiPrompt,
                            onPromptChanged = { geminiPrompt = it }
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
                    actionTypeNotification -> {
                        NotificationActionFields(
                            targetApp = notificationTargetApp,
                            onTargetAppChanged = { notificationTargetApp = it }
                        )
                    }
                    actionTypeVolumeTts, actionTypeVolumeCues -> {
                        VolumeActionFields(
                            selectedVolumeType = selectedVolumeType,
                            onVolumeTypeChanged = { selectedVolumeType = it },
                            volumePercentInput = volumePercentInput,
                            onVolumePercentChanged = { volumePercentInput = it }
                        )
                    }
                }

                // Auditory Cue Routing Toggle (Visible for actions with audio output)
                if (selectedActionType == actionTypeSpeak || 
                    selectedActionType == actionTypeGemini || 
                    selectedActionType == actionTypeGeminiSearch ||
                    selectedActionType == actionTypeGeminiNano ||
                    selectedActionType == actionTypeSmart || 
                    selectedActionType == actionTypeNotification) {
                    androidx.compose.foundation.layout.Row(
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
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.button_action_test))
                    }
                }
                
                Button(
                    onClick = {
                        if (label.isNotBlank()) {
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
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            Button(
                onClick = { onSave(null) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_clear_delete))
            }
        }
    )
}
