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
fun NotificationActionFields(
    targetApp: String,
    onTargetAppChanged: (String) -> Unit
) {
    val appAllLabel = stringResource(R.string.button_notification_target_all)
    val notificationApps = mapOf(
        "ALL" to appAllLabel,
        "com.whatsapp" to "WhatsApp",
        "org.thoughtcrime.securesms" to "Signal",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.apps.messaging" to "SMS (Messages)"
    )
    var expandedNotificationApp by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expandedNotificationApp,
        onExpandedChange = { expandedNotificationApp = !expandedNotificationApp }
    ) {
        OutlinedTextField(
            readOnly = true,
            value = notificationApps[targetApp] ?: "Unbekannt",
            onValueChange = { },
            label = { Text(stringResource(R.string.button_notification_target_app)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedNotificationApp) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expandedNotificationApp,
            onDismissRequest = { expandedNotificationApp = false }
        ) {
            notificationApps.forEach { (appId, appName) ->
                DropdownMenuItem(
                    text = { Text(appName, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onTargetAppChanged(appId)
                        expandedNotificationApp = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeActionFields(
    selectedVolumeType: String,
    onVolumeTypeChanged: (String) -> Unit,
    volumePercentInput: String,
    onVolumePercentChanged: (String) -> Unit
) {
    val volumeAbsolutLabel = "Absolut"
    val volumeRelativLabel = "Relativ"
    val volumeTypes = listOf(volumeAbsolutLabel, volumeRelativLabel)
    var expandedVolumeType by remember { mutableStateOf(false) }
    val dimensions = LocalDimensions.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
    ) {
        ExposedDropdownMenuBox(
            expanded = expandedVolumeType,
            onExpandedChange = { expandedVolumeType = !expandedVolumeType },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedVolumeType,
                onValueChange = { },
                label = { Text("Art") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVolumeType) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expandedVolumeType,
                onDismissRequest = { expandedVolumeType = false }
            ) {
                volumeTypes.forEach { typeLabel ->
                    DropdownMenuItem(
                        text = { Text(typeLabel, style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            onVolumeTypeChanged(typeLabel)
                            expandedVolumeType = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = volumePercentInput,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '-' }) {
                    onVolumePercentChanged(newValue)
                }
            },
            label = { Text("Wert (%)") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}
