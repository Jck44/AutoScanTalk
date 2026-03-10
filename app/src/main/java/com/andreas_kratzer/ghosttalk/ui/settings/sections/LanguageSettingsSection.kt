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
import com.andreas_kratzer.ghosttalk.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.components.SettingsClickableItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import java.util.Locale

@Composable
fun LanguageSettingsSection(viewModel: SettingsViewModel) {
    val selectedAppLanguage by viewModel.selectedAppLanguage.collectAsState("default")
    var expanded by remember { mutableStateOf(false) }
    
    PreferenceCategory(stringResource(R.string.settings_app_language)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val label = if (selectedAppLanguage == "default" || selectedAppLanguage == null) {
                stringResource(R.string.settings_system_default)
            } else Locale.forLanguageTag(selectedAppLanguage!!).displayName
            
            SettingsClickableItem(stringResource(R.string.settings_app_language), label) { expanded = true }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_system_default), style = MaterialTheme.typography.bodyLarge) },
                    onClick = { viewModel.setAppLanguage("default"); expanded = false }
                )
                listOf("de", "en").forEach { code ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = Locale.forLanguageTag(code).getDisplayName(Locale.forLanguageTag(code)),
                                style = MaterialTheme.typography.bodyLarge
                            ) 
                        },
                        onClick = { viewModel.setAppLanguage(code); expanded = false }
                    )
                }
            }
        }
    }
}
