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
    val actionTypes = listOf("Text Sprechen", "Zu Seite navigieren")
    var selectedActionType by remember {
        mutableStateOf(
            if (initialConfig?.buttonAction is NavigateToPageButtonAction) "Zu Seite navigieren" 
            else "Text Sprechen"
        )
    }
    var expandedActionType by remember { mutableStateOf(false) }

    // Navigation Details
    val navAction = initialConfig?.buttonAction as? NavigateToPageButtonAction
    var navigateToPageId by remember { mutableStateOf(navAction?.pageId ?: "") }
    var expandedPageSelect by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialConfig == null) "Neuer Button" else "Button bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Label Input
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Aufschrift (Label)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Explicit Spoken Text Input
                OutlinedTextField(
                    value = spokenText,
                    onValueChange = { spokenText = it },
                    label = { Text("Gesprochener Text (Optional)") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )

                // Auditory Cue (Hover/Scan feedback)
                OutlinedTextField(
                    value = ttsFeedback,
                    onValueChange = { ttsFeedback = it },
                    label = { Text("Auditory Cue (Vorlesen beim Scannen)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // IsActive Toggle
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ist Aktiv", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                }

                // Action Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedActionType,
                    onExpandedChange = { expandedActionType = !expandedActionType }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedActionType,
                        onValueChange = { },
                        label = { Text("Aktion") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedActionType) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
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
                if (selectedActionType == "Zu Seite navigieren") {
                    ExposedDropdownMenuBox(
                        expanded = expandedPageSelect,
                        onExpandedChange = { expandedPageSelect = !expandedPageSelect }
                    ) {
                        val selectedPageName = availablePages.find { it.id == navigateToPageId }?.name ?: "Keine Seite ausgewählt"
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedPageName,
                            onValueChange = { },
                            label = { Text("Ziel-Seite") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPageSelect) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPageSelect,
                            onDismissRequest = { expandedPageSelect = false }
                        ) {
                            availablePages.forEach { pageOption ->
                                DropdownMenuItem(
                                    text = { Text(pageOption.name) },
                                    onClick = {
                                        navigateToPageId = pageOption.id
                                        expandedPageSelect = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isNotBlank()) {
                        val action: ButtonAction = if (selectedActionType == "Zu Seite navigieren") {
                            NavigateToPageButtonAction(pageId = navigateToPageId)
                        } else {
                            // We use the new spokenText as primary for textToSpeech, fallback to label for legacy support 
                            // in PageViewModel if it checks action.textToSpeech natively.
                            SpeakTextButtonAction(textToSpeech = spokenText.takeIf { it.isNotBlank() } ?: label)
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
                Text("Speichern")
            }
        },
        dismissButton = {
            Button(
                onClick = { onSave(null) }, // Return null internally acts as a delete/clear if they press "Löschen"
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Leeren / Löschen")
            }
        }
    )
}
