package com.andreas_kratzer.ghosttalk.ui.settings

import android.content.res.Configuration
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.AudioOutputDevice
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.util.VoiceUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    val showTestButtons by settingsViewModel.showTestButtons.collectAsState()
    val defaultScanPattern by settingsViewModel.defaultScanPattern.collectAsState()
    val holdingTimeInput by settingsViewModel.holdingTimeInput.collectAsState()
    val selectedAppLanguage by settingsViewModel.selectedAppLanguage.collectAsState()
    val bluetoothDelayInput by settingsViewModel.bluetoothDelayInput.collectAsState()

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
                        AppLanguageSettings(selectedAppLanguage ?: "default", settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        VoiceSettings(selectedLanguage, availableLanguages, selectedVoiceName, availableVoices, settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        GoogleAccountSettings(settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        CloudSettings(settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        GeminiSettings(settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        AudioOutputSettings(availableAudioDevices, selectedTtsAudioDeviceAddress, selectedCuesAudioDeviceAddress, bluetoothDelayInput, settingsViewModel)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ScanningSettings(autoStartScanning, resumeScanningFromStart, scanDelayInput, holdingTimeInput, settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        NotificationSettings(settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        HardwareSettings(switchActivationKey, settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        TestSettings(showTestButtons, volumeKeysActivate, settingsViewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        ActionLogSettings(persistActionLogs, settingsViewModel)
                    }
                }
            } else {
                GeneralSettings(
                    allPages = allPages,
                    defaultStartPageId = defaultStartPageId,
                    defaultScanPattern = defaultScanPattern,
                    settingsViewModel = settingsViewModel
                )
                Spacer(modifier = Modifier.height(24.dp))
                AppLanguageSettings(selectedAppLanguage ?: "default", settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                VoiceSettings(selectedLanguage, availableLanguages, selectedVoiceName, availableVoices, settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                GoogleAccountSettings(settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                CloudSettings(settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                GeminiSettings(settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                AudioOutputSettings(availableAudioDevices, selectedTtsAudioDeviceAddress, selectedCuesAudioDeviceAddress, bluetoothDelayInput, settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                ScanningSettings(autoStartScanning, resumeScanningFromStart, scanDelayInput, holdingTimeInput, settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                NotificationSettings(settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                HardwareSettings(switchActivationKey, settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                TestSettings(showTestButtons, volumeKeysActivate, settingsViewModel)
                Spacer(modifier = Modifier.height(24.dp))
                ActionLogSettings(persistActionLogs, settingsViewModel)
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun PreferenceCategory(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsClickableItem(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun SettingsToggleItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) }
        )
    }
}

@Composable
fun GeneralSettings(
    allPages: List<Page>,
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
            SettingsClickableItem(
                label = stringResource(R.string.settings_start_page),
                value = currentStartPageName,
                onClick = { expandedStartPage = true }
            )
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
        
        // Scanmuster
        Box(modifier = Modifier.fillMaxWidth()) {
            val currentPatternLabel = when (defaultScanPattern) {
                "linear" -> stringResource(R.string.settings_pattern_linear)
                "row_by_row" -> stringResource(R.string.settings_pattern_row_by_row)
                else -> stringResource(R.string.settings_pattern_linear)
            }
            SettingsClickableItem(
                label = stringResource(R.string.settings_scan_pattern),
                value = currentPatternLabel,
                onClick = { expandedDefaultScanPattern = true }
            )
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
        
        ThemeSettings(settingsViewModel)
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
    
    val ttsVolumeMultiplier by settingsViewModel.ttsVolumeMultiplier.collectAsState()
    val cuesVolumeMultiplier by settingsViewModel.cuesVolumeMultiplier.collectAsState()

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
            
            SettingsClickableItem(
                label = stringResource(R.string.settings_tts_language),
                value = currentDisplayName,
                onClick = { expandedLanguage = true }
            )
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

        // Stimme
        Box(modifier = Modifier.fillMaxWidth()) {
            val currentVoiceDisplayName = if (selectedVoiceName.isNullOrEmpty()) {
                stringResource(R.string.settings_voice_default)
            } else VoiceUtils.formatVoiceName(selectedVoiceName)
            SettingsClickableItem(
                label = stringResource(R.string.settings_select_voice),
                value = currentVoiceDisplayName,
                onClick = { expandedVoice = true }
            )
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


        // TTS Volume Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_tts_volume, (ttsVolumeMultiplier * 100).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            androidx.compose.material3.Slider(
                value = ttsVolumeMultiplier,
                onValueChange = { settingsViewModel.setTtsVolumeMultiplier(it) },
                valueRange = 0.0f..1.0f,
                steps = 9 // Every 0.1 intervals
            )
        }
        
        // Cues Volume Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_cues_volume, (cuesVolumeMultiplier * 100).toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            androidx.compose.material3.Slider(
                value = cuesVolumeMultiplier,
                onValueChange = { settingsViewModel.setCuesVolumeMultiplier(it) },
                valueRange = 0.0f..1.0f,
                steps = 9 // Every 0.1 intervals
            )
        }
    }
}

@Composable
fun AudioOutputSettings(
    availableAudioDevices: List<AudioOutputDevice>,
    selectedTtsAudioDeviceAddress: String?,
    selectedCuesAudioDeviceAddress: String?,
    bluetoothDelayInput: String,
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
            SettingsClickableItem(
                label = stringResource(R.string.settings_audio_tts),
                value = settingsViewModel.getResolvedDeviceName(selectedTtsAudioDeviceAddress)
                    .takeUnless { it == "System-Standard (Automatisch)" } ?: defaultLabel,
                onClick = { expandedTtsDevice = true }
            )
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

        // Cues Device
        Box(modifier = Modifier.fillMaxWidth()) {
            val defaultLabel = stringResource(R.string.settings_audio_default)
            SettingsClickableItem(
                label = stringResource(R.string.settings_audio_cues),
                value = settingsViewModel.getResolvedDeviceName(selectedCuesAudioDeviceAddress)
                    .takeUnless { it == "System-Standard (Automatisch)" } ?: defaultLabel,
                onClick = { expandedCuesDevice = true }
            )
            DropdownMenu(expanded = expandedCuesDevice, onDismissRequest = { expandedCuesDevice = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_audio_default)) }, onClick = {
                    settingsViewModel.setSelectedCuesAudioDeviceAddress(null)
                    expandedCuesDevice = false
                })
                availableAudioDevices.forEach { device ->
                    DropdownMenuItem(text = { Text(device.name) }, onClick = {
                        settingsViewModel.setSelectedCuesAudioDeviceAddress(device.address)
                        expandedCuesDevice = false
                    })
                }
            }
        }

        // Bluetooth Delay
        SettingsTextField(
            label = "Bluetooth Verzögerung (ms)",
            value = bluetoothDelayInput,
            onValueChange = { settingsViewModel.setBluetoothDelay(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
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
        SettingsTextField(
            label = stringResource(R.string.settings_scan_delay),
            value = scanDelayInput,
            onValueChange = { settingsViewModel.setScanDelayInput(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        SettingsTextField(
            label = stringResource(R.string.settings_holding_time),
            value = holdingTimeInput,
            onValueChange = { settingsViewModel.setHoldingTimeInput(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
fun HardwareSettings(
    switchActivationKey: String,
    settingsViewModel: SettingsViewModel
) {
    PreferenceCategory(stringResource(R.string.settings_category_hardware)) {
        SettingsTextField(
            label = stringResource(R.string.settings_switch_key),
            value = switchActivationKey,
            onValueChange = { settingsViewModel.setSwitchActivationKey(it) }
        )
    }
}

@Composable
fun TestSettings(
    showTestButtons: Boolean,
    volumeKeysActivate: Boolean,
    settingsViewModel: SettingsViewModel
) {
    PreferenceCategory(stringResource(R.string.settings_category_test)) {
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.clickable { settingsViewModel.setShowTestButtons(!showTestButtons) }
        ) {
            Text(stringResource(R.string.settings_show_test_buttons), modifier = Modifier.weight(1f))
            Switch(checked = showTestButtons, onCheckedChange = { settingsViewModel.setShowTestButtons(it) })
        }
        
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
    val signInErrorMessage by settingsViewModel.signInErrorMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    PreferenceCategory(stringResource(R.string.settings_category_google_account)) {
        Text(
            text = stringResource(R.string.settings_google_account_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
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
                
                signInErrorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
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
    val syncIntervalMinutesInput by settingsViewModel.syncIntervalMinutesInput.collectAsState()
    val syncMode by settingsViewModel.syncMode.collectAsState()
    val userEmail by settingsViewModel.userEmail.collectAsState()
    val isSyncing by settingsViewModel.isSyncing.collectAsState()
    val lastSuccessfulSyncTime by settingsViewModel.lastSuccessfulSyncTime.collectAsState()
    
    var expandedMode by remember { mutableStateOf(false) }
    
    PreferenceCategory(stringResource(R.string.settings_category_cloud)) {
        SettingsToggleItem(
            label = stringResource(R.string.settings_cloud_sync_enabled),
            checked = isCloudSyncEnabled,
            onCheckedChange = { settingsViewModel.setCloudSyncEnabled(it) }
        )
        
        Text(
            text = stringResource(R.string.settings_cloud_sync_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (lastSuccessfulSyncTime > 0) {
            val sdf = remember { java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()) }
            val formattedDate = remember(lastSuccessfulSyncTime) { sdf.format(java.util.Date(lastSuccessfulSyncTime)) }
            Text(
                text = "Letzter erfolgreicher Sync: $formattedDate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        Box(modifier = Modifier.fillMaxWidth()) {
            val currentModeLabel = when (syncMode) {
                "BACKUP_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_backup)
                "RESTORE_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_restore)
                else -> stringResource(R.string.settings_cloud_sync_mode_two_way)
            }
            SettingsClickableItem(
                label = stringResource(R.string.settings_cloud_sync_mode),
                value = currentModeLabel,
                onClick = { expandedMode = true }
            )
            DropdownMenu(expanded = expandedMode, onDismissRequest = { expandedMode = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_cloud_sync_mode_two_way)) }, onClick = {
                    settingsViewModel.setSyncMode("TWO_WAY")
                    expandedMode = false
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_cloud_sync_mode_backup)) }, onClick = {
                    settingsViewModel.setSyncMode("BACKUP_ONLY")
                    expandedMode = false
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_cloud_sync_mode_restore)) }, onClick = {
                    settingsViewModel.setSyncMode("RESTORE_ONLY")
                    expandedMode = false
                })
            }
        }

        SettingsTextField(
            label = stringResource(R.string.settings_cloud_sync_interval),
            value = syncIntervalMinutesInput,
            onValueChange = { settingsViewModel.setSyncIntervalMinutesInput(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { settingsViewModel.backupNow() },
                enabled = !isSyncing && userEmail != null,
                modifier = Modifier.weight(1f)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.settings_cloud_backup_now))
                }
            }
            OutlinedButton(
                onClick = { settingsViewModel.restoreNow() },
                enabled = !isSyncing && userEmail != null,
                modifier = Modifier.weight(1f)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.settings_cloud_restore_now))
                }
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

        val showPageIdInLog by settingsViewModel.showPageIdInLog.collectAsState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { settingsViewModel.setShowPageIdInLog(!showPageIdInLog) }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_show_page_id_in_log))
                Text(
                    text = stringResource(R.string.settings_show_page_id_in_log_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = showPageIdInLog, onCheckedChange = { settingsViewModel.setShowPageIdInLog(it) })
        }

        val experimentalManualSorting by settingsViewModel.experimentalManualSorting.collectAsState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { settingsViewModel.setExperimentalManualSorting(!experimentalManualSorting) }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_experimental_manual_sorting))
                Text(
                    text = stringResource(R.string.settings_experimental_manual_sorting_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = experimentalManualSorting, onCheckedChange = { settingsViewModel.setExperimentalManualSorting(it) })
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Statistiken zurücksetzen
        var showResetDialog by remember { mutableStateOf(false) }
        TextButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Häufige Aktionen zurücksetzen")
        }
        
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Statistiken zurücksetzen") },
                text = { Text("Alle Statistiken für 'Häufigste Aktionen' in diesem Buch werden gelöscht. Fortfahren?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val bookId = settingsViewModel.activeBookId
                            settingsViewModel.clearButtonUsageStats(bookId)
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Zurücksetzen")
                    }
                },
                dismissButton = {
                    Button(onClick = { showResetDialog = false }, colors = ButtonDefaults.textButtonColors()) {
                        Text("Abbrechen")
                    }
                }
            )
        }
    }
}

@Composable
fun GeminiSettings(settingsViewModel: SettingsViewModel) {
    val isGeminiEnabled by settingsViewModel.isGeminiEnabled.collectAsState()
    val toolStatus by settingsViewModel.geminiToolStatus.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    PreferenceCategory(stringResource(R.string.settings_category_gemini)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { settingsViewModel.setGeminiEnabled(!isGeminiEnabled) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_gemini_enable),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = isGeminiEnabled,
                onCheckedChange = { settingsViewModel.setGeminiEnabled(it) }
            )
        }

        Text(
            text = stringResource(R.string.settings_gemini_activation_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Button(
            onClick = { settingsViewModel.activateGemini(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_gemini_activate_button))
        }

        if (isGeminiEnabled && toolStatus.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            val isSmartPredictionEnabled by settingsViewModel.isSmartPredictionEnabled.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_smart_prediction_enable),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_smart_prediction_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSmartPredictionEnabled,
                    onCheckedChange = { settingsViewModel.setSmartPredictionEnabled(it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.settings_gemini_tools_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            toolStatus.forEach { (toolName, status) ->
                GeminiToolStatusItem(toolName, status)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            val delayInput by settingsViewModel.smartPredictionDelayMillisInput.collectAsState()
            OutlinedTextField(
                value = delayInput,
                onValueChange = { settingsViewModel.setSmartPredictionDelayInput(it) },
                label = { Text(stringResource(R.string.settings_gemini_prediction_delay)) },
                placeholder = { Text("z.B. 2000") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(stringResource(R.string.settings_gemini_prediction_delay_hint))
                }
            )
        }
    }
}

@Composable
fun GeminiToolStatusItem(toolName: String, status: com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus) {
    val label = when (toolName) {
        "google_search" -> stringResource(R.string.settings_gemini_tool_google_search)
        "search_drive" -> stringResource(R.string.settings_gemini_tool_drive)
        "list_calendar_events" -> stringResource(R.string.settings_gemini_tool_calendar)
        "list_tasks" -> stringResource(R.string.settings_gemini_tool_tasks)
        "wikipedia_search" -> stringResource(R.string.settings_gemini_tool_wikipedia)
        "get_weather" -> stringResource(R.string.settings_gemini_tool_weather)
        "play_on_spotify" -> stringResource(R.string.settings_gemini_tool_spotify)
        else -> toolName
    }

    val statusText = when (status) {
        com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus.AVAILABLE -> stringResource(R.string.settings_gemini_tool_status_active)
        com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus.REQUIRES_AUTH -> stringResource(R.string.settings_gemini_tool_status_requires_auth)
        com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus.FAILED -> stringResource(R.string.settings_gemini_tool_status_failed)
        com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus.PENDING -> stringResource(R.string.settings_gemini_tool_status_pending)
    }

    val color = when (status) {
        com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
        com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus.REQUIRES_AUTH -> MaterialTheme.colorScheme.error
        com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus.FAILED -> MaterialTheme.colorScheme.error
        com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus.PENDING -> MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
fun AppLanguageSettings(
    selectedAppLanguage: String,
    settingsViewModel: SettingsViewModel
) {
    var expanded by remember { mutableStateOf(false) }

    PreferenceCategory(stringResource(R.string.settings_app_language)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val currentLabel = when (selectedAppLanguage) {
                "de" -> stringResource(R.string.settings_app_language_de)
                "en" -> stringResource(R.string.settings_app_language_en)
                else -> stringResource(R.string.settings_app_language_system)
            }
            SettingsClickableItem(
                label = stringResource(R.string.settings_app_language),
                value = currentLabel,
                onClick = { expanded = true }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_app_language_system)) },
                    onClick = {
                        settingsViewModel.setAppLanguage("default")
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_app_language_de)) },
                    onClick = {
                        settingsViewModel.setAppLanguage("de")
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_app_language_en)) },
                    onClick = {
                        settingsViewModel.setAppLanguage("en")
                        expanded = false
                    }
                )
            }
        }
    }
}



@Composable
fun ThemeSettings(settingsViewModel: SettingsViewModel) {
    val themeMode by settingsViewModel.themeMode.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        val currentLabel = when (themeMode) {
            "LIGHT" -> stringResource(R.string.settings_theme_light)
            "DARK" -> stringResource(R.string.settings_theme_dark)
            else -> stringResource(R.string.settings_theme_system)
        }
        SettingsClickableItem(
            label = stringResource(R.string.settings_theme_mode),
            value = currentLabel,
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_theme_system)) },
                onClick = {
                    settingsViewModel.setThemeMode("SYSTEM")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_theme_light)) },
                onClick = {
                    settingsViewModel.setThemeMode("LIGHT")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_theme_dark)) },
                onClick = {
                    settingsViewModel.setThemeMode("DARK")
                    expanded = false
                }
            )
        }
    }
}

@Composable
fun NotificationSettings(settingsViewModel: SettingsViewModel) {
    val isNotificationReadingEnabled by settingsViewModel.isNotificationReadingEnabled.collectAsState()
    val monitoredNotificationApps by settingsViewModel.monitoredNotificationApps.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val packageName = context.packageName

    // Berechtigungsstatus ermitteln
    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
    val hasPermission = enabledListeners.contains(packageName)

    PreferenceCategory(stringResource(R.string.settings_category_notifications)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { 
                if (hasPermission) {
                    settingsViewModel.setNotificationReadingEnabled(!isNotificationReadingEnabled)
                } else {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }
            }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_notifications_enable))
                Text(
                    text = stringResource(R.string.settings_notifications_enable_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasPermission) {
                Switch(
                    checked = isNotificationReadingEnabled,
                    onCheckedChange = { settingsViewModel.setNotificationReadingEnabled(it) }
                )
            } else {
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.settings_notifications_permission_button))
                }
            }
        }

        if (isNotificationReadingEnabled && hasPermission) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.settings_notifications_apps),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            val apps = listOf(
                "com.whatsapp" to stringResource(R.string.settings_notifications_app_whatsapp),
                "org.thoughtcrime.securesms" to stringResource(R.string.settings_notifications_app_signal),
                "org.telegram.messenger" to stringResource(R.string.settings_notifications_app_telegram),
                "com.google.android.apps.messaging" to stringResource(R.string.settings_notifications_app_sms)
            )

            apps.forEach { (pkg, name) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            settingsViewModel.toggleMonitoredNotificationApp(pkg, !monitoredNotificationApps.contains(pkg))
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Text(text = name, modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = monitoredNotificationApps.contains(pkg),
                        onCheckedChange = { checked ->
                            settingsViewModel.toggleMonitoredNotificationApp(pkg, checked)
                        }
                    )
                }
            }
        }
    }
}
