package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel

@Composable
fun ExperimentalSettingsSection(viewModel: SettingsViewModel) {
    val manualSorting by viewModel.experimentalManualSorting.collectAsState(false)
    val smartEnabled by viewModel.isSmartPredictionEnabled.collectAsState(false)

    PreferenceCategory(stringResource(R.string.settings_category_experimental)) {
        SettingsToggleItem(
            label = stringResource(R.string.settings_experimental_manual_sorting),
            checked = manualSorting,
            onCheckedChange = { viewModel.setExperimentalManualSorting(it) }
        )
        
        SettingsToggleItem(
            label = stringResource(R.string.settings_smart_prediction_enable),
            checked = smartEnabled,
            onCheckedChange = { viewModel.setSmartPredictionEnabled(it) }
        )
    }
}
