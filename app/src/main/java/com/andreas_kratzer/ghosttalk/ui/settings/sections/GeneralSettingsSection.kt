package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsClickableItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsSection(viewModel: SettingsViewModel, isGlobal: Boolean) {
    val theme by viewModel.themeMode.collectAsState("SYSTEM")
    val persistLogs by viewModel.persistActionLogs.collectAsState(false)
    val defaultStartPageId by viewModel.defaultStartPageId.collectAsState(null)
    val allPages by viewModel.allPages.collectAsState()
    
    val keepScreenOn by viewModel.keepScreenOnUserMode.collectAsState(true)
    val screenBehavior by viewModel.userModeScreenBehavior.collectAsState("NORMAL")
    val startupBehavior by viewModel.startupBehavior.collectAsState("BOOK_SELECTION")

    var expandedTheme by remember { mutableStateOf(false) }
    var expandedStartPage by remember { mutableStateOf(false) }
    var startPageSearchQuery by remember { mutableStateOf("") }
    var expandedScreenBehavior by remember { mutableStateOf(false) }
    var expandedStartupBehavior by remember { mutableStateOf(false) }

    val dimensions = LocalDimensions.current

    if (isGlobal) {
        val userEmail by viewModel.userEmail.collectAsState(null)
        val context = LocalContext.current

        PreferenceCategory(stringResource(R.string.settings_category_google_account)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = userEmail ?: stringResource(R.string.settings_google_account_status_not_signed_in),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (userEmail == null) {
                    Button(
                        onClick = { viewModel.signIn(context) },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.settings_google_account_sign_in))
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.signOut() },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.settings_google_account_sign_out))
                    }
                }
            }
        }

        PreferenceCategory(stringResource(R.string.settings_category_ui)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val themeLabel = when (theme) {
                    "LIGHT" -> stringResource(R.string.settings_theme_light)
                    "DARK" -> stringResource(R.string.settings_theme_dark)
                    else -> stringResource(R.string.settings_theme_system)
                }
                
                SettingsClickableItem(stringResource(R.string.settings_theme_mode), themeLabel) { expandedTheme = true }
                DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_theme_system), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { viewModel.setThemeMode("SYSTEM"); expandedTheme = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_theme_light), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { viewModel.setThemeMode("LIGHT"); expandedTheme = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_theme_dark), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { viewModel.setThemeMode("DARK"); expandedTheme = false }
                    )
                }
            }

            SettingsToggleItem(
                label = stringResource(R.string.settings_persist_logs),
                checked = persistLogs,
                onCheckedChange = { viewModel.setPersistActionLogs(it) }
            )
        }

        PreferenceCategory(stringResource(R.string.settings_category_general)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val startupLabel = when (startupBehavior) {
                    "SELECTED_BOOK" -> stringResource(R.string.settings_startup_behavior_last_book)
                    "USER_MODE" -> stringResource(R.string.settings_startup_behavior_user_mode)
                    else -> stringResource(R.string.settings_startup_behavior_book_list)
                }

                SettingsClickableItem(
                    label = stringResource(R.string.settings_startup_behavior),
                    value = startupLabel,
                    onClick = { expandedStartupBehavior = true }
                )

                DropdownMenu(
                    expanded = expandedStartupBehavior,
                    onDismissRequest = { expandedStartupBehavior = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_startup_behavior_book_list), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { viewModel.setStartupBehavior("BOOK_SELECTION"); expandedStartupBehavior = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_startup_behavior_last_book), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { viewModel.setStartupBehavior("SELECTED_BOOK"); expandedStartupBehavior = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_startup_behavior_user_mode), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { viewModel.setStartupBehavior("USER_MODE"); expandedStartupBehavior = false }
                    )
                }
            }
        }
    }

    if (!isGlobal) {
        PreferenceCategory(stringResource(R.string.settings_category_general)) {
            SettingsToggleItem(
                label = stringResource(R.string.settings_keep_screen_on),
                checked = keepScreenOn,
                onCheckedChange = { viewModel.setKeepScreenOnUserMode(it) }
            )

            if (keepScreenOn) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val behaviorLabel = when (screenBehavior) {
                        "DIMMED" -> stringResource(R.string.settings_screen_behavior_dimmed)
                        "BLACK" -> stringResource(R.string.settings_screen_behavior_black)
                        else -> stringResource(R.string.settings_screen_behavior_normal)
                    }

                    SettingsClickableItem(
                        label = stringResource(R.string.settings_screen_behavior),
                        value = behaviorLabel,
                        onClick = { expandedScreenBehavior = true }
                    )
                    DropdownMenu(expanded = expandedScreenBehavior, onDismissRequest = { expandedScreenBehavior = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_screen_behavior_normal), style = MaterialTheme.typography.bodyLarge) },
                            onClick = { viewModel.setUserModeScreenBehavior("NORMAL"); expandedScreenBehavior = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_screen_behavior_dimmed), style = MaterialTheme.typography.bodyLarge) },
                            onClick = { viewModel.setUserModeScreenBehavior("DIMMED"); expandedScreenBehavior = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_screen_behavior_black), style = MaterialTheme.typography.bodyLarge) },
                            onClick = { viewModel.setUserModeScreenBehavior("BLACK"); expandedScreenBehavior = false }
                        )
                    }
                }
            }

            // Default Start Page Selector with Filter
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
            ) {
                Text(
                    text = stringResource(R.string.settings_start_page),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                ExposedDropdownMenuBox(
                    expanded = expandedStartPage,
                    onExpandedChange = { expandedStartPage = !expandedStartPage }
                ) {
                    val startPageLabel = allPages.find { it.id == defaultStartPageId }?.name 
                        ?: stringResource(R.string.settings_start_page_auto)

                    OutlinedTextField(
                        value = if (expandedStartPage) startPageSearchQuery else startPageLabel,
                        onValueChange = { if (expandedStartPage) startPageSearchQuery = it },
                        readOnly = !expandedStartPage,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStartPage) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedStartPage,
                        onDismissRequest = { 
                            expandedStartPage = false
                            startPageSearchQuery = ""
                        }
                    ) {
                        val filteredPages = remember(startPageSearchQuery, allPages) {
                            val trimmed = startPageSearchQuery.trim()
                            if (trimmed.isEmpty()) allPages
                            else allPages.filter { it.name.contains(trimmed, ignoreCase = true) }
                        }

                        // Auto Option (always show when query is empty)
                        if (startPageSearchQuery.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_start_page_auto), style = MaterialTheme.typography.bodyLarge) },
                                onClick = { 
                                    viewModel.setDefaultStartPageId(null)
                                    expandedStartPage = false
                                }
                            )
                        }

                        filteredPages.forEach { page ->
                            DropdownMenuItem(
                                text = { Text(page.name, style = MaterialTheme.typography.bodyLarge) },
                                onClick = { 
                                    viewModel.setDefaultStartPageId(page.id)
                                    startPageSearchQuery = ""
                                    expandedStartPage = false
                                }
                            )
                        }

                        if (filteredPages.isEmpty() && startPageSearchQuery.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Keine Seiten gefunden", style = MaterialTheme.typography.bodyLarge) },
                                onClick = { },
                                enabled = false
                            )
                        }
                    }
                }
            }
        }
    }
}
