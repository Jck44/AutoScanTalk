package com.andreas_kratzer.ghosttalk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
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
    onDismiss: () -> Unit,
    onSave: (ButtonConfig?) -> Unit
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
    
    // Action Type selection
    val actionTypeSpeak = stringResource(R.string.button_action_speak_text)
    val actionTypeNavigate = stringResource(R.string.button_action_navigate_page)
    val actionTypeGemini = stringResource(R.string.button_action_gemini)
    val actionTypeFrequent = stringResource(R.string.button_action_frequent_action)
    val actionTypeSmart = stringResource(R.string.button_action_smart_prediction)
    val actionTypes = listOf(actionTypeSpeak, actionTypeNavigate, actionTypeGemini, actionTypeFrequent, actionTypeSmart)
    
    var selectedActionType by remember {
        mutableStateOf(
            when (initialConfig?.buttonAction) {
                is NavigateToPageButtonAction -> actionTypeNavigate
                is GeminiButtonAction -> actionTypeGemini
                is FrequentActionButtonAction -> actionTypeFrequent
                is SmartPredictionButtonAction -> actionTypeSmart
                else -> actionTypeSpeak
            }
        )
    }
    var expandedActionType by remember { mutableStateOf(false) }

    // Navigation Details
    val navAction = initialConfig?.buttonAction as? NavigateToPageButtonAction
    var navigateToPageId by remember { mutableStateOf(navAction?.pageId ?: "") }
    var expandedPageSelect by remember { mutableStateOf(false) }
    var pageSearchQuery by remember { mutableStateOf("") }
    val filteredPages = remember(pageSearchQuery, availablePages) {
        val trimmedQuery = pageSearchQuery.trim()
        if (trimmedQuery.isBlank()) availablePages
        else availablePages.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
    }

    // Gemini Details
    val geminiAction = initialConfig?.buttonAction as? GeminiButtonAction
    var geminiPrompt by remember { mutableStateOf(geminiAction?.prompt ?: "") }

    // Frequent Action Details
    val frequentActionDef = initialConfig?.buttonAction as? FrequentActionButtonAction
    var frequentRank by remember { mutableStateOf((frequentActionDef?.rank ?: 1).toString()) }

    // Smart Prediction Details
    val smartActionDef = initialConfig?.buttonAction as? SmartPredictionButtonAction
    var smartRank by remember { mutableStateOf((smartActionDef?.rank ?: 1).toString()) }

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

                // Conditional fields for Navigation
                if (selectedActionType == actionTypeNavigate) {
                    ExposedDropdownMenuBox(
                        expanded = expandedPageSelect,
                        onExpandedChange = { expandedPageSelect = !expandedPageSelect }
                    ) {
                        val selectedPageName = availablePages.find { it.id == navigateToPageId }?.name ?: stringResource(R.string.button_no_page_selected)
                        OutlinedTextField(
                            value = if (expandedPageSelect) pageSearchQuery else selectedPageName,
                            onValueChange = { 
                                if (expandedPageSelect) pageSearchQuery = it 
                            },
                            readOnly = !expandedPageSelect,
                            label = { Text(stringResource(R.string.button_target_page_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPageSelect) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPageSelect,
                            onDismissRequest = { 
                                expandedPageSelect = false
                                pageSearchQuery = ""
                            }
                        ) {
                            filteredPages.forEach { pageOption ->
                                DropdownMenuItem(
                                    text = { Text(pageOption.name) },
                                    onClick = {
                                        navigateToPageId = pageOption.id
                                        expandedPageSelect = false
                                        pageSearchQuery = ""
                                    }
                                )
                            }
                            if (filteredPages.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Keine Seiten gefunden") },
                                    onClick = { },
                                    enabled = false
                                )
                            }
                        }
                    }
                }

                // Conditional fields for Gemini
                if (selectedActionType == actionTypeGemini) {
                    OutlinedTextField(
                        value = geminiPrompt,
                        onValueChange = { geminiPrompt = it },
                        label = { Text(stringResource(R.string.button_gemini_prompt_field)) },
                        placeholder = { Text(stringResource(R.string.button_gemini_prompt_hint)) },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Conditional fields for Frequent Action
                if (selectedActionType == actionTypeFrequent) {
                    OutlinedTextField(
                        value = frequentRank,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                frequentRank = newValue
                            }
                        },
                        label = { Text(stringResource(R.string.button_smart_prediction_rank_label)) }, // Reusing same rank label
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Conditional fields for Smart Prediction
                if (selectedActionType == actionTypeSmart) {
                    OutlinedTextField(
                        value = smartRank,
                        onValueChange = { newValue -> 
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                smartRank = newValue
                            }
                        },
                        label = { Text(stringResource(R.string.button_smart_prediction_rank_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isNotBlank()) {
                        val action: ButtonAction = when (selectedActionType) {
                            actionTypeNavigate -> NavigateToPageButtonAction(pageId = navigateToPageId)
                            actionTypeGemini -> GeminiButtonAction(prompt = geminiPrompt)
                            actionTypeFrequent -> FrequentActionButtonAction(rank = frequentRank.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                            actionTypeSmart -> SmartPredictionButtonAction(rank = smartRank.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                            else -> SpeakTextButtonAction(textToSpeech = spokenText.takeIf { it.isNotBlank() } ?: label)
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
                                auditoryCue = cue
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
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
