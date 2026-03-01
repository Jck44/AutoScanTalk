package com.andreas_kratzer.ghosttalk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.andreas_kratzer.ghosttalk.util.VoiceUtils

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
    val defaultScanPattern by settingsViewModel.defaultScanPattern.collectAsState()
    val holdingTimeInput by settingsViewModel.holdingTimeInput.collectAsState()

    var expandedLanguage by remember { mutableStateOf(false) }
    var expandedStartPage by remember { mutableStateOf(false) }
    var expandedVoice by remember { mutableStateOf(false) }
    var expandedTtsDevice by remember { mutableStateOf(false) }
    var expandedCuesDevice by remember { mutableStateOf(false) }
    var expandedDefaultScanPattern by remember { mutableStateOf(false) }

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
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GeneralSettings(allPages, defaultStartPageId, defaultScanPattern, settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        VoiceSettings(selectedLanguage, availableLanguages, selectedVoiceName, availableVoices, settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        AudioOutputSettings(availableAudioDevices, selectedTtsAudioDeviceAddress, selectedCuesAudioDeviceAddress, settingsViewModel)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ScanningSettings(autoStartScanning, resumeScanningFromStart, scanDelayInput, holdingTimeInput, settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        HardwareSettings(switchActivationKey, volumeKeysActivate, settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        ActionLogSettings(persistActionLogs, settingsViewModel)
                    }
                }
            } else {
                GeneralSettings(allPages, defaultStartPageId, defaultScanPattern, settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                VoiceSettings(selectedLanguage, availableLanguages, selectedVoiceName, availableVoices, settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                AudioOutputSettings(availableAudioDevices, selectedTtsAudioDeviceAddress, selectedCuesAudioDeviceAddress, settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                ScanningSettings(autoStartScanning, resumeScanningFromStart, scanDelayInput, holdingTimeInput, settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                HardwareSettings(switchActivationKey, volumeKeysActivate, settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                ActionLogSettings(persistActionLogs, settingsViewModel)
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun PreferenceCategory(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun GeneralSettings(
    allPages: List<com.andreas_kratzer.ghosttalk.model.Page>,
    defaultStartPageId: String?,
    defaultScanPattern: String,
    settingsViewModel: SettingsViewModel
) {
    var expandedStartPage by remember { mutableStateOf(false) }
    var expandedDefaultScanPattern by remember { mutableStateOf(false) }

    PreferenceCategory("Allgemein") {
        // Startseite
        Box(modifier = Modifier.fillMaxWidth()) {
            val currentStartPageName = allPages.find { it.id == defaultStartPageId }?.name ?: "Automatisch (Erste Seite)"
            OutlinedTextField(
                value = currentStartPageName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Standard Startseite") },
                modifier = Modifier.fillMaxWidth().clickable { expandedStartPage = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedStartPage = true })
            DropdownMenu(expanded = expandedStartPage, onDismissRequest = { expandedStartPage = false }) {
                DropdownMenuItem(text = { Text("Automatisch (Erste Seite)") }, onClick = {
                    settingsViewModel.setDefaultStartPageId(null)
                    expandedStartPage = false
                })
                allPages.forEach { page ->
                    DropdownMenuItem(text = { Text(page.name) }, onClick = {
                        settingsViewModel.setDefaultStartPageId(page.id)
                        expandedStartPage = false
                    })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Scanmuster
        Box(modifier = Modifier.fillMaxWidth()) {
            val currentPatternLabel = when (defaultScanPattern) {
                "linear" -> "Button für Button"
                "row_by_row" -> "Zeilenweise"
                else -> "Button für Button"
            }
            OutlinedTextField(
                value = currentPatternLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Scanmuster (Standard)") },
                modifier = Modifier.fillMaxWidth().clickable { expandedDefaultScanPattern = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedDefaultScanPattern = true })
            DropdownMenu(expanded = expandedDefaultScanPattern, onDismissRequest = { expandedDefaultScanPattern = false }) {
                DropdownMenuItem(text = { Text("Button für Button") }, onClick = {
                    settingsViewModel.setDefaultScanPattern("linear")
                    expandedDefaultScanPattern = false
                })
                DropdownMenuItem(text = { Text("Zeilenweise") }, onClick = {
                    settingsViewModel.setDefaultScanPattern("row_by_row")
                    expandedDefaultScanPattern = false
                })
            }
        }
    }
}

@Composable
fun VoiceSettings(
    selectedLanguage: String,
    availableLanguages: List<Locale>,
    selectedVoiceName: String?,
    availableVoices: List<android.speech.tts.Voice>,
    settingsViewModel: SettingsViewModel
) {
    var expandedLanguage by remember { mutableStateOf(false) }
    var expandedVoice by remember { mutableStateOf(false) }

    LaunchedEffect(expandedLanguage) {
        if (expandedLanguage && availableLanguages.isEmpty()) settingsViewModel.loadAvailableLanguages()
    }
    LaunchedEffect(expandedVoice) {
        if (expandedVoice && availableVoices.isEmpty()) settingsViewModel.loadAvailableVoices()
    }

    PreferenceCategory("Stimme & Sprache") {
        // Sprache
        Box(modifier = Modifier.fillMaxWidth()) {
            val currentDisplayName = if (selectedLanguage == "default" || selectedLanguage.isEmpty()) {
                "System Standard (${Locale.getDefault().displayName})"
            } else Locale.forLanguageTag(selectedLanguage).displayName
            
            OutlinedTextField(
                value = currentDisplayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("TTS Sprache") },
                modifier = Modifier.fillMaxWidth().clickable { expandedLanguage = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedLanguage = true })
            DropdownMenu(expanded = expandedLanguage, onDismissRequest = { expandedLanguage = false }) {
                DropdownMenuItem(text = { Text("System Standard") }, onClick = {
                    settingsViewModel.setTtsLanguage("default")
                    expandedLanguage = false
                })
                availableLanguages.forEach { locale ->
                    DropdownMenuItem(text = { Text(locale.displayName) }, onClick = {
                        settingsViewModel.setTtsLanguage(locale.toLanguageTag())
                        expandedLanguage = false
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stimme
        Box(modifier = Modifier.fillMaxWidth()) {
            val currentVoiceDisplayName = if (selectedVoiceName.isNullOrEmpty()) "Standard" else VoiceUtils.formatVoiceName(selectedVoiceName)
            OutlinedTextField(
                value = currentVoiceDisplayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Stimme wählen") },
                modifier = Modifier.fillMaxWidth().clickable { expandedVoice = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedVoice = true })
            DropdownMenu(expanded = expandedVoice, onDismissRequest = { expandedVoice = false }) {
                DropdownMenuItem(text = { Text("Standard") }, onClick = {
                    settingsViewModel.setTtsVoice(null)
                    expandedVoice = false
                })
                availableVoices.forEach { voice ->
                    val isNetwork = voice.name.lowercase().contains("network")
                    val qualityHint = if (isNetwork) " (HQ/Online)" else " (Lokal)"
                    DropdownMenuItem(text = { Text("${VoiceUtils.formatVoiceName(voice.name)}$qualityHint") }, onClick = {
                        settingsViewModel.setTtsVoice(voice.name)
                        expandedVoice = false
                    })
                }
            }
        }
    }
}

@Composable
fun AudioOutputSettings(
    availableAudioDevices: List<com.andreas_kratzer.ghosttalk.model.AudioOutputDevice>,
    selectedTtsAudioDeviceAddress: String?,
    selectedCuesAudioDeviceAddress: String?,
    settingsViewModel: SettingsViewModel
) {
    var expandedTtsDevice by remember { mutableStateOf(false) }
    var expandedCuesDevice by remember { mutableStateOf(false) }

    LaunchedEffect(expandedTtsDevice, expandedCuesDevice) {
        if (expandedTtsDevice || expandedCuesDevice) settingsViewModel.loadAvailableAudioDevices()
    }

    PreferenceCategory("Audio-Ausgabe") {
        // TTS Device
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = settingsViewModel.getResolvedDeviceName(selectedTtsAudioDeviceAddress),
                onValueChange = {},
                readOnly = true,
                label = { Text("Ausgabe: Laut Sprechen") },
                modifier = Modifier.fillMaxWidth().clickable { expandedTtsDevice = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedTtsDevice = true })
            DropdownMenu(expanded = expandedTtsDevice, onDismissRequest = { expandedTtsDevice = false }) {
                DropdownMenuItem(text = { Text("System-Standard (Automatisch)") }, onClick = {
                    settingsViewModel.setTtsAudioDevice(null)
                    expandedTtsDevice = false
                })
                availableAudioDevices.forEach { device ->
                    DropdownMenuItem(text = { Text(device.name) }, onClick = {
                        settingsViewModel.setTtsAudioDevice(device.address)
                        expandedTtsDevice = false
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cues Device
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = settingsViewModel.getResolvedDeviceName(selectedCuesAudioDeviceAddress),
                onValueChange = {},
                readOnly = true,
                label = { Text("Ausgabe: Auditory Cues") },
                modifier = Modifier.fillMaxWidth().clickable { expandedCuesDevice = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedCuesDevice = true })
            DropdownMenu(expanded = expandedCuesDevice, onDismissRequest = { expandedCuesDevice = false }) {
                DropdownMenuItem(text = { Text("System-Standard (Automatisch)") }, onClick = {
                    settingsViewModel.setCuesAudioDevice(null)
                    expandedCuesDevice = false
                })
                availableAudioDevices.forEach { device ->
                    DropdownMenuItem(text = { Text(device.name) }, onClick = {
                        settingsViewModel.setCuesAudioDevice(device.address)
                        expandedCuesDevice = false
                    })
                }
            }
        }
    }
}

@Composable
fun ScanningSettings(
    autoStartScanning: Boolean,
    resumeScanningFromStart: Boolean,
    scanDelayInput: String,
    holdingTimeInput: String,
    settingsViewModel: SettingsViewModel
) {
    PreferenceCategory("Scannen & Bedienung") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { settingsViewModel.setAutoStartScanning(!autoStartScanning) }) {
            Text("Auto-Scan beim Start", modifier = Modifier.weight(1f))
            Switch(checked = autoStartScanning, onCheckedChange = { settingsViewModel.setAutoStartScanning(it) })
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { settingsViewModel.setResumeScanningFromStart(!resumeScanningFromStart) }) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Scan nach Aktion neu starten")
                Text("Aus = Weiter beim letzten Fokus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = resumeScanningFromStart, onCheckedChange = { settingsViewModel.setResumeScanningFromStart(it) })
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = scanDelayInput,
            onValueChange = { settingsViewModel.setScanDelayInput(it) },
            label = { Text("Scangeschwindigkeit (ms)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = holdingTimeInput,
            onValueChange = { settingsViewModel.setHoldingTimeInput(it) },
            label = { Text("Haltezeit / Doppelklick-Schutz (ms)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun HardwareSettings(
    switchActivationKey: String,
    volumeKeysActivate: Boolean,
    settingsViewModel: SettingsViewModel
) {
    PreferenceCategory("Hardware & Schalter") {
        OutlinedTextField(
            value = switchActivationKey,
            onValueChange = { settingsViewModel.setSwitchActivationKey(it) },
            label = { Text("Taste für externen Taster (z.B. Space)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { settingsViewModel.setVolumeKeysActivate(!volumeKeysActivate) }) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Lautstärke-Tasten als Auslöser")
                Text("Nutzt Volume +/- zum Bestätigen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = volumeKeysActivate, onCheckedChange = { settingsViewModel.setVolumeKeysActivate(it) })
        }
    }
}

@Composable
fun ActionLogSettings(
    persistActionLogs: Boolean,
    settingsViewModel: SettingsViewModel
) {
    PreferenceCategory("Verlauf & System") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { settingsViewModel.setPersistActionLogs(!persistActionLogs) }) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Aktionenverlauf speichern")
                Text("Behält Verlauf auch nach Neustart", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = persistActionLogs, onCheckedChange = { settingsViewModel.setPersistActionLogs(it) })
        }
    }
}


