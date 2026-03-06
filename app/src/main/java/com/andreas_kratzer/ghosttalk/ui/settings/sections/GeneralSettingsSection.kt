package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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

@Composable
fun GeneralSettingsSection(viewModel: SettingsViewModel) {
    val theme by viewModel.themeMode.collectAsState("SYSTEM")
    val persistLogs by viewModel.persistActionLogs.collectAsState(false)
    val defaultStartPageId by viewModel.defaultStartPageId.collectAsState(null)
    val allPages by viewModel.allPages.collectAsState()
    
    val keepScreenOn by viewModel.keepScreenOnUserMode.collectAsState(true)
    val screenBehavior by viewModel.userModeScreenBehavior.collectAsState("NORMAL")

    var expandedTheme by remember { mutableStateOf(false) }
    var expandedStartPage by remember { mutableStateOf(false) }
    var expandedScreenBehavior by remember { mutableStateOf(false) }

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
    }

    PreferenceCategory(stringResource(R.string.settings_category_general)) {
        // Default Start Page Selector
        Box(modifier = Modifier.fillMaxWidth()) {
            val startPageLabel = allPages.find { it.id == defaultStartPageId }?.name 
                ?: stringResource(R.string.settings_start_page_auto)
            
            SettingsClickableItem(
                label = stringResource(R.string.settings_start_page),
                value = startPageLabel,
                onClick = { expandedStartPage = true }
            )
            DropdownMenu(expanded = expandedStartPage, onDismissRequest = { expandedStartPage = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_start_page_auto), style = MaterialTheme.typography.bodyLarge) },
                    onClick = { viewModel.setDefaultStartPageId(null); expandedStartPage = false }
                )
                allPages.forEach { page ->
                    DropdownMenuItem(
                        text = { Text(page.name, style = MaterialTheme.typography.bodyLarge) },
                        onClick = { viewModel.setDefaultStartPageId(page.id); expandedStartPage = false }
                    )
                }
            }
        }

        SettingsToggleItem(
            label = stringResource(R.string.settings_persist_logs),
            checked = persistLogs,
            onCheckedChange = { viewModel.setPersistActionLogs(it) }
        )
    }
}
