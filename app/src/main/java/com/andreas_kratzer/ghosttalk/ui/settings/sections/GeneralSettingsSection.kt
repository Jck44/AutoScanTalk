package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    var expandedTheme by remember { mutableStateOf(false) }

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
                    text = { Text(stringResource(R.string.settings_theme_system)) },
                    onClick = { viewModel.setThemeMode("SYSTEM"); expandedTheme = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_theme_light)) },
                    onClick = { viewModel.setThemeMode("LIGHT"); expandedTheme = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_theme_dark)) },
                    onClick = { viewModel.setThemeMode("DARK"); expandedTheme = false }
                )
            }
        }
    }

    PreferenceCategory(stringResource(R.string.settings_category_general)) {
        SettingsToggleItem(
            label = "Aktionen persistent protokollieren",
            checked = persistLogs,
            onCheckedChange = { viewModel.setPersistActionLogs(it) }
        )
    }
}
