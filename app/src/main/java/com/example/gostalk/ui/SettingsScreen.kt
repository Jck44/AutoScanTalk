package com.example.gostalk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedLanguage by settingsViewModel.selectedLanguageTag.collectAsState()
    val availableLanguages by settingsViewModel.availableLanguages.collectAsState()
    val autoStartScanning by settingsViewModel.autoStartScanning.collectAsState()
    val scanDelayInput by settingsViewModel.scanDelayInput.collectAsState()
    val defaultStartPageId by settingsViewModel.defaultStartPageId.collectAsState()
    val allPages by pageViewModel.allPages.collectAsState()
    val availableVoices by settingsViewModel.availableVoices.collectAsState()
    val selectedVoiceName by settingsViewModel.selectedVoiceName.collectAsState()
    val availableAudioDevices by settingsViewModel.availableAudioDevices.collectAsState()
    val selectedTtsAudioDeviceAddress by settingsViewModel.selectedTtsAudioDeviceAddress.collectAsState()
    val selectedCuesAudioDeviceAddress by settingsViewModel.selectedCuesAudioDeviceAddress.collectAsState()
    val resumeScanningFromStart by settingsViewModel.resumeScanningFromStart.collectAsState()
    val persistActionLogs by settingsViewModel.persistActionLogs.collectAsState()
    val switchActivationKey by settingsViewModel.switchActivationKey.collectAsState()
    val volumeKeysActivate by settingsViewModel.volumeKeysActivate.collectAsState()

    var expandedLanguage by remember { mutableStateOf(false) }
    var expandedStartPage by remember { mutableStateOf(false) }
    var expandedVoice by remember { mutableStateOf(false) }
    var expandedTtsDevice by remember { mutableStateOf(false) }
    var expandedCuesDevice by remember { mutableStateOf(false) }

    // Versuche regelmäßig die Sprachen zu laden, falls sie initial noch nicht da waren
    LaunchedEffect(expandedLanguage) {
        if (expandedLanguage && availableLanguages.isEmpty()) {
            settingsViewModel.loadAvailableLanguages()
        }
    }
    
    // Versuche die Stimmen initial zu laden, wenn das Menü geöffnet wird
    LaunchedEffect(expandedVoice) {
        if (expandedVoice && availableVoices.isEmpty()) {
            settingsViewModel.loadAvailableVoices()
        }
    }

    // Aktualisiere Audio-Geräte, wenn Menüs geöffnet werden
    LaunchedEffect(expandedTtsDevice, expandedCuesDevice) {
        if (expandedTtsDevice || expandedCuesDevice) {
            settingsViewModel.loadAvailableAudioDevices()
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Standard Startseite (Nutzer Modus)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                val currentStartPageName = allPages.find { it.id == defaultStartPageId }?.name ?: "Automatisch (Erste Seite)"

                OutlinedTextField(
                    value = currentStartPageName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Startseite wählen") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedStartPage = true }
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expandedStartPage = true }
                )

                DropdownMenu(
                    expanded = expandedStartPage,
                    onDismissRequest = { expandedStartPage = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("Automatisch (Erste Seite)") },
                        onClick = {
                            settingsViewModel.setDefaultStartPageId(null)
                            expandedStartPage = false
                        }
                    )
                    
                    allPages.forEach { page ->
                        DropdownMenuItem(
                            text = { Text(page.name) },
                            onClick = {
                                settingsViewModel.setDefaultStartPageId(page.id)
                                expandedStartPage = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                        .clickable { expandedLanguage = true }
                )

                // Ein transparentes Overlay, da clickables auf TextFields manchmal Probleme machen
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expandedLanguage = true }
                )

                DropdownMenu(
                    expanded = expandedLanguage,
                    onDismissRequest = { expandedLanguage = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("System default (${Locale.getDefault().displayName})") },
                        onClick = {
                            settingsViewModel.setTtsLanguage("default")
                            expandedLanguage = false
                        }
                    )

                    availableLanguages.forEach { locale ->
                        DropdownMenuItem(
                            text = { Text(locale.displayName) },
                            onClick = {
                                settingsViewModel.setTtsLanguage(locale.toLanguageTag())
                                expandedLanguage = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -- NEW: Voice Selection Dropdown --
            fun formatVoiceName(technicalName: String): String {
                var name = technicalName.lowercase()
                // Entferne gängige Locale-Prefixe (z.B. de-de-, en-us-)
                val prefixRegex = Regex("^[a-z]{2}-[a-z]{2}-")
                name = name.replace(prefixRegex, "")
                
                // Entferne technische Suffixe
                name = name.replace("-network", "").replace("-local", "")
                
                // Ersetze Trennzeichen "-x-" und alleinstehende "x" durch Leerzeichen bzw. löschen
                name = name.replace("-x-", " ")
                name = name.replace(Regex("\\bx\\b"), " ")
                name = name.replace("-", " ")
                
                return name.split(" ").filter { it.isNotBlank() }
                    .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                    .ifEmpty { "Stimme" }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                val currentVoiceDisplayName = if (selectedVoiceName.isNullOrEmpty()) {
                    "Standard"
                } else {
                    formatVoiceName(selectedVoiceName ?: "")
                }

                OutlinedTextField(
                    value = currentVoiceDisplayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Stimme wählen") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedVoice = true }
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expandedVoice = true }
                )

                DropdownMenu(
                    expanded = expandedVoice,
                    onDismissRequest = { expandedVoice = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("Standard") },
                        onClick = {
                            settingsViewModel.setTtsVoice(null)
                            expandedVoice = false
                        }
                    )

                    availableVoices.forEach { voice ->
                        // Fallback check against the name itself if the API flag is unreliable across devices
                        val isNetwork = voice.isNetworkConnectionRequired || voice.name.lowercase().contains("network")
                        val qualityHint = if (isNetwork) " (Online/HQ)" else " (Lokal)"
                        val readableName = formatVoiceName(voice.name)
                        
                        DropdownMenuItem(
                            text = { Text("$readableName$qualityHint") },
                            onClick = {
                                settingsViewModel.setTtsVoice(voice.name)
                                expandedVoice = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Ausgabegerät: Laut Sprechen (TTS)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                val currentTtsDeviceName = settingsViewModel.getResolvedDeviceName(selectedTtsAudioDeviceAddress)

                OutlinedTextField(
                    value = currentTtsDeviceName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Audiogerät wählen") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedTtsDevice = true }
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expandedTtsDevice = true }
                )

                DropdownMenu(
                    expanded = expandedTtsDevice,
                    onDismissRequest = { expandedTtsDevice = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("System-Standard (Automatisch)") },
                        onClick = {
                            settingsViewModel.setTtsAudioDevice(null)
                            expandedTtsDevice = false
                        }
                    )
                    
                    availableAudioDevices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name) },
                            onClick = {
                                settingsViewModel.setTtsAudioDevice(device.address)
                                expandedTtsDevice = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Ausgabegerät: Auditory Cues",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                val currentCuesDeviceName = settingsViewModel.getResolvedDeviceName(selectedCuesAudioDeviceAddress)

                OutlinedTextField(
                    value = currentCuesDeviceName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Audiogerät wählen") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedCuesDevice = true }
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expandedCuesDevice = true }
                )

                DropdownMenu(
                    expanded = expandedCuesDevice,
                    onDismissRequest = { expandedCuesDevice = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("System-Standard (Automatisch)") },
                        onClick = {
                            settingsViewModel.setCuesAudioDevice(null)
                            expandedCuesDevice = false
                        }
                    )
                    
                    availableAudioDevices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name) },
                            onClick = {
                                settingsViewModel.setCuesAudioDevice(device.address)
                                expandedCuesDevice = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Checkbox/Switch für Auto-Start Scanning
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.setAutoStartScanning(!autoStartScanning) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Automatisches Scannen beim Start",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = autoStartScanning,
                    onCheckedChange = { settingsViewModel.setAutoStartScanning(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Checkbox/Switch für Resume Scanning Behavior
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.setResumeScanningFromStart(!resumeScanningFromStart) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Scannen nach Aktion von Beginn starten",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Aus = Beim zuletzt fokussierten Button fortsetzen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = resumeScanningFromStart,
                    onCheckedChange = { settingsViewModel.setResumeScanningFromStart(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Checkbox/Switch für Action Logs Persistence
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.setPersistActionLogs(!persistActionLogs) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aktionenverlauf speichern",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Letzte Aktionen auch nach App-Neustart behalten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = persistActionLogs,
                    onCheckedChange = { settingsViewModel.setPersistActionLogs(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = scanDelayInput,
                onValueChange = { settingsViewModel.setScanDelayInput(it) },
                label = { Text("Scangeschwindigkeit (ms)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "Wie viele Millisekunden soll beim automatischen Scannen gewartet werden?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, bottom = 24.dp)
            )

            Text(
                text = "Hardware Eingabe & Schalter",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = switchActivationKey,
                onValueChange = { settingsViewModel.setSwitchActivationKey(it) },
                label = { Text("Zeichen für externen Taster/Switch") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Dieses Zeichen (z.B. Enter, Space, a) löst den fokussierten Button aus.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.setVolumeKeysActivate(!volumeKeysActivate) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lautstärke-Tasten als Auslöser",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Erlaubt die Button-Aktivierung über die Lauter/Leiser Tasten am Gerät (für Testzwecke).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = volumeKeysActivate,
                    onCheckedChange = { settingsViewModel.setVolumeKeysActivate(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
