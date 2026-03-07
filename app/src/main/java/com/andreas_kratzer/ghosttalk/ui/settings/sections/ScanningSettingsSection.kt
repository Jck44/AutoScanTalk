package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel

@Composable
fun ScanningSettingsSection(viewModel: SettingsViewModel) {
    val autoStart by viewModel.autoStartScanning.collectAsState(true)
    val scanDelay by viewModel.scanDelayMillis.collectAsState(1000L)
    val resumeFromStart by viewModel.resumeScanningFromStart.collectAsState(true)
    val scanPattern by viewModel.defaultScanPattern.collectAsState("linear")
    val holdingTime by viewModel.holdingTimeMillis.collectAsState(0L)
    val bluetoothDelay by viewModel.bluetoothDelay.collectAsState(1500L)

    var expandedPattern by remember { mutableStateOf(false) }

    PreferenceCategory(stringResource(R.string.settings_category_scanning)) {
        SettingsToggleItem(stringResource(R.string.settings_auto_scan), autoStart) { viewModel.setAutoStartScanning(it) }
        SettingsEditTextItem(
            label = stringResource(R.string.settings_scan_delay), 
            value = scanDelay.toString(),
            onValueChange = { viewModel.setScanDelayInput(it) }
        )
        SettingsToggleItem(stringResource(R.string.settings_restart_scan), resumeFromStart) { viewModel.setResumeScanningFromStart(it) }
        
        Box(modifier = Modifier.fillMaxWidth()) {
            val patternLabel = when (scanPattern) {
                "linear" -> stringResource(R.string.settings_pattern_linear)
                "row_column" -> stringResource(R.string.settings_pattern_row_by_row)
                else -> scanPattern
            }
            SettingsClickableItem(stringResource(R.string.settings_scan_pattern), patternLabel) { expandedPattern = true }
            DropdownMenu(expanded = expandedPattern, onDismissRequest = { expandedPattern = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_pattern_linear), style = MaterialTheme.typography.bodyLarge) },
                    onClick = { viewModel.setDefaultScanPattern("linear"); expandedPattern = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_pattern_row_by_row), style = MaterialTheme.typography.bodyLarge) },
                    onClick = { viewModel.setDefaultScanPattern("row_column"); expandedPattern = false }
                )
            }
        }
    }

    PreferenceCategory(stringResource(R.string.settings_category_hardware)) {
        val switchKey by viewModel.switchActivationKey.collectAsState("Space")
        SettingsEditTextItem(
            label = stringResource(R.string.settings_switch_key), 
            value = switchKey,
            onValueChange = { viewModel.setSwitchActivationKey(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
    }

    PreferenceCategory(stringResource(R.string.settings_category_advanced)) {
        SettingsEditTextItem(
            label = stringResource(R.string.settings_holding_time), 
            value = holdingTime.toString(),
            onValueChange = { viewModel.setHoldingTimeInput(it) }
        )
        SettingsEditTextItem(
            label = stringResource(R.string.settings_bluetooth_delay), 
            value = bluetoothDelay.toString(),
            onValueChange = { viewModel.setBluetoothDelay(it) }
        )
    }
}
