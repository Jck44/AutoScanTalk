package com.example.gostalk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedLanguage by settingsViewModel.selectedLanguageTag.collectAsState()
    val availableLanguages by settingsViewModel.availableLanguages.collectAsState()

    var expanded by remember { mutableStateOf(false) }

    // Versuche regelmäßig die Sprachen zu laden, falls sie initial noch nicht da waren
    LaunchedEffect(expanded) {
        if (expanded && availableLanguages.isEmpty()) {
            settingsViewModel.loadAvailableLanguages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Text-to-Speech Sprache",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                val currentDisplayName = if (selectedLanguage == "default" || selectedLanguage.isEmpty()) {
                    "System default (${Locale.getDefault().displayName})"
                } else {
                    Locale.forLanguageTag(selectedLanguage).displayName
                }

                OutlinedTextField(
                    value = currentDisplayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sprache wählen") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true }
                )

                // Ein transparentes Overlay, da clickables auf TextFields manchmal Probleme machen
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = true }
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("System default (${Locale.getDefault().displayName})") },
                        onClick = {
                            settingsViewModel.setTtsLanguage("default")
                            expanded = false
                        }
                    )

                    availableLanguages.forEach { locale ->
                        DropdownMenuItem(
                            text = { Text(locale.displayName) },
                            onClick = {
                                settingsViewModel.setTtsLanguage(locale.toLanguageTag())
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
