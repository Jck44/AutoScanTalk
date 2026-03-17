package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanningSettingsSection(viewModel: SettingsViewModel, isGlobal: Boolean) {
    val autoStart by viewModel.autoStartScanning.collectAsState(true)
    val scanDelay by viewModel.scanDelayMillis.collectAsState(1000L)
    val resumeFromStart by viewModel.resumeScanningFromStart.collectAsState(true)
    val scanPattern by viewModel.defaultScanPattern.collectAsState("linear")
    val holdingTime by viewModel.holdingTimeMillis.collectAsState(0L)
    val bluetoothDelay by viewModel.bluetoothDelay.collectAsState(1500L)

    val dimensions = LocalDimensions.current

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        PreferenceCategory(stringResource(R.string.settings_category_scanning), modifier = Modifier.weight(1f)) {
            SettingsToggleItem(stringResource(R.string.settings_auto_scan), autoStart) { viewModel.setAutoStartScanning(it) }
            SettingsEditTextItem(
                label = stringResource(R.string.settings_scan_delay), 
                value = scanDelay.toString(),
                onValueChange = { viewModel.setScanDelayInput(it) }
            )
            SettingsToggleItem(stringResource(R.string.settings_restart_scan), resumeFromStart) { viewModel.setResumeScanningFromStart(it) }
        }

        PreferenceCategory(stringResource(R.string.settings_category_hardware), modifier = Modifier.weight(1f)) {
            val switchKey by viewModel.switchActivationKey.collectAsState("Space")
            SettingsEditTextItem(
                label = stringResource(R.string.settings_switch_key), 
                value = switchKey,
                onValueChange = { viewModel.setSwitchActivationKey(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            val patternLabel = when (scanPattern) {
                "linear" -> stringResource(R.string.settings_pattern_linear)
                "row_column" -> stringResource(R.string.settings_pattern_row_by_row)
                else -> scanPattern
            }
            
            com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem(
                label = stringResource(R.string.settings_scan_pattern),
                selectedOption = patternLabel,
                options = listOf(
                    stringResource(R.string.settings_pattern_linear) to { viewModel.setDefaultScanPattern("linear") },
                    stringResource(R.string.settings_pattern_row_by_row) to { viewModel.setDefaultScanPattern("row_column") }
                )
            )
        }

        PreferenceCategory(stringResource(R.string.settings_category_advanced), modifier = Modifier.weight(1f)) {
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

        if (!isGlobal) {
            val limitScanCycles by viewModel.limitScanCycles.collectAsState(false)
            val scanCycleLimit by viewModel.scanCycleLimit.collectAsState(2)

            PreferenceCategory(stringResource(R.string.settings_category_limits), modifier = Modifier.weight(1f)) {
                SettingsToggleItem(
                    label = stringResource(R.string.settings_limit_scan_cycles),
                    checked = limitScanCycles,
                    onCheckedChange = { viewModel.setLimitScanCycles(it) }
                )
                if (limitScanCycles) {
                    SettingsEditTextItem(
                        label = stringResource(R.string.settings_scan_cycle_limit),
                        value = scanCycleLimit.toString(),
                        onValueChange = { viewModel.setScanCycleLimitInput(it) }
                    )
                }
            }
        }
    }
}
