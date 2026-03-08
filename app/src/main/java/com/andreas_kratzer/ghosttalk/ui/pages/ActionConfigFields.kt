package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationActionFields(
    navigateToPageId: String,
    onPageSelected: (String) -> Unit,
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    onNavigateToPage: ((String) -> Unit)?,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)?,
    onBeforeCreatePage: (() -> Boolean)? = null,

    onDismissDialog: () -> Unit
) {
    var expandedPageSelect by remember { mutableStateOf(false) }
    var pageSearchQuery by remember { mutableStateOf("") }
    val showAddPageDialogState = remember { mutableStateOf(false) }

    val dimensions = LocalDimensions.current

    val filteredPages = remember(pageSearchQuery, availablePages) {
        val trimmedQuery = pageSearchQuery.trim()
        if (trimmedQuery.isBlank()) availablePages
        else availablePages.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)) {
        ExposedDropdownMenuBox(
            expanded = expandedPageSelect,
            onExpandedChange = { expandedPageSelect = !expandedPageSelect }
        ) {
            val selectedPageName = availablePages.find { it.id == navigateToPageId }?.name 
                ?: stringResource(R.string.button_no_page_selected)
            
            OutlinedTextField(
                value = if (expandedPageSelect) pageSearchQuery else selectedPageName,
                onValueChange = { if (expandedPageSelect) pageSearchQuery = it },
                readOnly = !expandedPageSelect,
                label = { Text(stringResource(R.string.button_target_page_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPageSelect) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = MaterialTheme.shapes.large,
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
                        text = { Text(pageOption.name, style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            onPageSelected(pageOption.id)
                            expandedPageSelect = false
                            pageSearchQuery = ""
                        }
                    )
                }
                if (filteredPages.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Keine Seiten gefunden", style = MaterialTheme.typography.bodyLarge) },
                        onClick = { },
                        enabled = false
                    )
                }
            }
        }

        if (navigateToPageId.isNotEmpty() && onNavigateToPage != null) {
            OutlinedButton(
                onClick = {
                    onDismissDialog()
                    onNavigateToPage(navigateToPageId)
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ziel-Seite verwalten")
            }
        }

        if (onCreatePage != null) {
            OutlinedButton(
                onClick = { 
                    if (onBeforeCreatePage == null || onBeforeCreatePage()) {
                        showAddPageDialogState.value = true 
                    }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text("Neue Ziel-Seite erstellen")
            }
        }
    }

    if (showAddPageDialogState.value) {
        AddPageDialog(
            templates = templates,
            onDismiss = { showAddPageDialogState.value = false },
            onConfirm = { name, rows, cols, templateId ->
                onCreatePage?.invoke(name, rows, cols, templateId) { newId ->
                    onPageSelected(newId)
                    showAddPageDialogState.value = false
                }
            }
        )
    }
}

@Composable
fun GeminiActionFields(
    prompt: String,
    onPromptChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChanged,
        label = { Text(stringResource(R.string.button_gemini_prompt_field)) },
        placeholder = { Text(stringResource(R.string.button_gemini_prompt_hint)) },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiNanoActionFields(
    selectedIntent: String,
    onIntentSelected: (String) -> Unit
) {
    val dimensions = LocalDimensions.current
    var expanded by remember { mutableStateOf(false) }

    val intentTime = "time"
    val intentDate = "date"
    val intentWeather = "weather"
    val intentBattery = "battery"
    val intentAlarm = "alarm"

    val intentLabels = mapOf(
        intentTime to stringResource(R.string.button_gemini_nano_intent_time),
        intentDate to stringResource(R.string.button_gemini_nano_intent_date),
        intentWeather to stringResource(R.string.button_gemini_nano_intent_weather),
        intentBattery to stringResource(R.string.button_gemini_nano_intent_battery),
        intentAlarm to stringResource(R.string.button_gemini_nano_intent_alarm)
    )

    val currentLabel = intentLabels[selectedIntent] ?: stringResource(R.string.button_gemini_nano_intent_time)

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        Text(stringResource(R.string.button_gemini_nano_intent_label), style = MaterialTheme.typography.labelMedium)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = currentLabel,
                onValueChange = { },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                intentLabels.forEach { (intent, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onIntentSelected(intent)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RankActionFields(
    rank: String,
    onRankChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = rank,
        onValueChange = { newValue ->
            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                onRankChanged(newValue)
            }
        },
        label = { Text(stringResource(R.string.button_smart_prediction_rank_label)) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlDeviceActionFields(
    selectedType: com.andreas_kratzer.ghosttalk.model.DeviceActionType,
    onTypeSelected: (com.andreas_kratzer.ghosttalk.model.DeviceActionType) -> Unit
) {
    val dimensions = LocalDimensions.current
    var expanded by remember { mutableStateOf(false) }

    val types = listOf(
        com.andreas_kratzer.ghosttalk.model.DeviceActionType.READ_NOTIFICATIONS to stringResource(R.string.button_action_notification),
        com.andreas_kratzer.ghosttalk.model.DeviceActionType.MEDIA_PLAY_PAUSE to stringResource(R.string.button_device_control_media_play_pause),
        com.andreas_kratzer.ghosttalk.model.DeviceActionType.MEDIA_NEXT to stringResource(R.string.button_device_control_media_next),
        com.andreas_kratzer.ghosttalk.model.DeviceActionType.MEDIA_PREVIOUS to stringResource(R.string.button_device_control_media_previous)
    )

    val currentLabel = types.find { it.first == selectedType }?.second ?: types.first().second

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        Text(stringResource(R.string.button_device_control_type_label), style = MaterialTheme.typography.labelMedium)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = currentLabel,
                onValueChange = { },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                types.forEach { (type, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onTypeSelected(type)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
