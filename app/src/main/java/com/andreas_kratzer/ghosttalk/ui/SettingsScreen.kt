package com.andreas_kratzer.ghosttalk.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.util.VoiceUtils
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit
) {
    // ... values ...
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

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // User granted permission, trigger sync again
            settingsViewModel.syncNow()
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.authIntentFlow.collect { intent ->
            authLauncher.launch(intent)
        }
    }

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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = stringResource(R.string.back_button_content_description)
                        )
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
                        GoogleAccountSettings(settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        CloudSettings(settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        GeminiSettings(settingsViewModel)
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
                GoogleAccountSettings(settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                CloudSettings(settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                GeminiSettings(settingsViewModel)
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

    PreferenceCategory(stringResource(R.string.settings_category_general)) {
        // Startseite
        Box(modifier = Modifier.fillMaxWidth()) {
            val currentStartPageName = allPages.find { it.id == defaultStartPageId }?.name 
                ?: stringResource(R.string.settings_start_page_auto)
            OutlinedTextField(
                value = currentStartPageName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_start_page)) },
                modifier = Modifier.fillMaxWidth().clickable { expandedStartPage = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedStartPage = true })
            DropdownMenu(expanded = expandedStartPage, onDismissRequest = { expandedStartPage = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_start_page_auto)) }, onClick = {
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
                "linear" -> stringResource(R.string.settings_pattern_linear)
                "row_by_row" -> stringResource(R.string.settings_pattern_row_by_row)
                else -> stringResource(R.string.settings_pattern_linear)
            }
            OutlinedTextField(
                value = currentPatternLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_scan_pattern)) },
                modifier = Modifier.fillMaxWidth().clickable { expandedDefaultScanPattern = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedDefaultScanPattern = true })
            DropdownMenu(expanded = expandedDefaultScanPattern, onDismissRequest = { expandedDefaultScanPattern = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_pattern_linear)) }, onClick = {
                    settingsViewModel.setDefaultScanPattern("linear")
                    expandedDefaultScanPattern = false
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_pattern_row_by_row)) }, onClick = {
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

    PreferenceCategory(stringResource(R.string.settings_category_voice)) {
        // Sprache
        Box(modifier = Modifier.fillMaxWidth()) {
            val currentDisplayName = if (selectedLanguage == "default" || selectedLanguage.isEmpty()) {
                "${stringResource(R.string.settings_system_default)} (${Locale.getDefault().displayName})"
            } else Locale.forLanguageTag(selectedLanguage).displayName
            
            OutlinedTextField(
                value = currentDisplayName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_tts_language)) },
                modifier = Modifier.fillMaxWidth().clickable { expandedLanguage = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedLanguage = true })
            DropdownMenu(expanded = expandedLanguage, onDismissRequest = { expandedLanguage = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_system_default)) }, onClick = {
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
            val currentVoiceDisplayName = if (selectedVoiceName.isNullOrEmpty()) {
                stringResource(R.string.settings_voice_default)
            } else VoiceUtils.formatVoiceName(selectedVoiceName)
            OutlinedTextField(
                value = currentVoiceDisplayName,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_select_voice)) },
                modifier = Modifier.fillMaxWidth().clickable { expandedVoice = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedVoice = true })
            DropdownMenu(expanded = expandedVoice, onDismissRequest = { expandedVoice = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_voice_default)) }, onClick = {
                    settingsViewModel.setTtsVoice(null)
                    expandedVoice = false
                })
                availableVoices.forEach { voice ->
                    val isNetwork = voice.name.lowercase().contains("network")
                    val qualityHint = if (isNetwork) {
                        stringResource(R.string.settings_voice_network_hint)
                    } else {
                        stringResource(R.string.settings_voice_local_hint)
                    }
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

    PreferenceCategory(stringResource(R.string.settings_category_audio)) {
        // TTS Device
        Box(modifier = Modifier.fillMaxWidth()) {
            val defaultLabel = stringResource(R.string.settings_audio_default)
            OutlinedTextField(
                value = settingsViewModel.getResolvedDeviceName(selectedTtsAudioDeviceAddress)
                    .takeUnless { it == "System-Standard (Automatisch)" } ?: defaultLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_audio_tts)) },
                modifier = Modifier.fillMaxWidth().clickable { expandedTtsDevice = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedTtsDevice = true })
            DropdownMenu(expanded = expandedTtsDevice, onDismissRequest = { expandedTtsDevice = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_audio_default)) }, onClick = {
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
            val defaultLabel = stringResource(R.string.settings_audio_default)
            OutlinedTextField(
                value = settingsViewModel.getResolvedDeviceName(selectedCuesAudioDeviceAddress)
                    .takeUnless { it == "System-Standard (Automatisch)" } ?: defaultLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_audio_cues)) },
                modifier = Modifier.fillMaxWidth().clickable { expandedCuesDevice = true }
            )
            Box(modifier = Modifier.matchParentSize().clickable { expandedCuesDevice = true })
            DropdownMenu(expanded = expandedCuesDevice, onDismissRequest = { expandedCuesDevice = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_audio_default)) }, onClick = {
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
    PreferenceCategory(stringResource(R.string.settings_category_scanning)) {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.clickable { settingsViewModel.setAutoStartScanning(!autoStartScanning) }
        ) {
            Text(stringResource(R.string.settings_auto_scan), modifier = Modifier.weight(1f))
            Switch(checked = autoStartScanning, onCheckedChange = { settingsViewModel.setAutoStartScanning(it) })
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.clickable { settingsViewModel.setResumeScanningFromStart(!resumeScanningFromStart) }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_restart_scan))
                Text(
                    text = stringResource(R.string.settings_restart_scan_hint), 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = resumeScanningFromStart, onCheckedChange = { settingsViewModel.setResumeScanningFromStart(it) })
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = scanDelayInput,
            onValueChange = { settingsViewModel.setScanDelayInput(it) },
            label = { Text(stringResource(R.string.settings_scan_delay)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = holdingTimeInput,
            onValueChange = { settingsViewModel.setHoldingTimeInput(it) },
            label = { Text(stringResource(R.string.settings_holding_time)) },
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
    PreferenceCategory(stringResource(R.string.settings_category_hardware)) {
        OutlinedTextField(
            value = switchActivationKey,
            onValueChange = { settingsViewModel.setSwitchActivationKey(it) },
            label = { Text(stringResource(R.string.settings_switch_key)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.clickable { settingsViewModel.setVolumeKeysActivate(!volumeKeysActivate) }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_volume_keys_trigger))
                Text(
                    text = stringResource(R.string.settings_volume_keys_hint), 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = volumeKeysActivate, onCheckedChange = { settingsViewModel.setVolumeKeysActivate(it) })
        }
    }
}

@Composable
fun GoogleAccountSettings(settingsViewModel: SettingsViewModel) {
    val userEmail by settingsViewModel.userEmail.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    PreferenceCategory(stringResource(R.string.settings_category_google_account)) {
        Text(
            text = stringResource(R.string.settings_google_account_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (userEmail != null) {
                        stringResource(R.string.settings_google_account_status_signed_in_as, userEmail!!)
                    } else {
                        stringResource(R.string.settings_google_account_status_not_signed_in)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (userEmail != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            
            if (userEmail == null) {
                Button(onClick = { settingsViewModel.signIn(context) }) {
                    Text(stringResource(R.string.settings_google_account_sign_in))
                }
            } else {
                OutlinedButton(onClick = { settingsViewModel.signOut() }) {
                    Text(stringResource(R.string.settings_google_account_sign_out))
                }
            }
        }
    }
}

@Composable
fun CloudSettings(settingsViewModel: SettingsViewModel) {
    val isCloudSyncEnabled by settingsViewModel.isCloudSyncEnabled.collectAsState()
    val userEmail by settingsViewModel.userEmail.collectAsState()
    val isSyncing by settingsViewModel.isSyncing.collectAsState()
    
    PreferenceCategory(stringResource(R.string.settings_category_cloud)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { settingsViewModel.setCloudSyncEnabled(!isCloudSyncEnabled) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_cloud_sync_enabled))
            }
            Switch(
                checked = isCloudSyncEnabled,
                onCheckedChange = { settingsViewModel.setCloudSyncEnabled(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { settingsViewModel.syncNow() },
            enabled = !isSyncing && userEmail != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.settings_cloud_sync_now))
            }
        }
    }
}

@Composable
fun ActionLogSettings(
    persistActionLogs: Boolean,
    settingsViewModel: SettingsViewModel
) {
    PreferenceCategory(stringResource(R.string.settings_category_system)) {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.clickable { settingsViewModel.setPersistActionLogs(!persistActionLogs) }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_persist_logs))
                Text(
                    text = stringResource(R.string.settings_persist_logs_hint), 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = persistActionLogs, onCheckedChange = { settingsViewModel.setPersistActionLogs(it) })
        }
    }
}

@Composable
fun GeminiSettings(settingsViewModel: SettingsViewModel) {
    val isGeminiEnabled by settingsViewModel.isGeminiEnabled.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    PreferenceCategory(stringResource(R.string.settings_category_gemini)) {
        Text(
            text = stringResource(R.string.settings_gemini_activation_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_google_account_status_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = if (isGeminiEnabled) {
                        stringResource(R.string.settings_gemini_status_enabled)
                    } else {
                        stringResource(R.string.settings_gemini_status_disabled)
                    },
                    color = if (isGeminiEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Button(
                onClick = { settingsViewModel.activateGemini(context) }
            ) {
                Text(stringResource(R.string.settings_gemini_activate_button))
            }
        }
    }
}


