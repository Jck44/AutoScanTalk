package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel

@Composable
fun GeminiNanoSettingsSection(viewModel: SettingsViewModel) {
    val smartEnabled by viewModel.isSmartPredictionEnabled.collectAsState(false)
    val geminiTimeout by viewModel.geminiTimeout.collectAsState(6000L)
    val redoPrediction by viewModel.geminiRedoPrediction.collectAsState(false)

    PreferenceCategory(stringResource(R.string.settings_category_gemini_nano)) {
        SettingsToggleItem(
            label = stringResource(R.string.settings_smart_prediction_enable),
            checked = smartEnabled,
            onCheckedChange = { viewModel.setSmartPredictionEnabled(it) }
        )
        
        SettingsToggleItem(
            label = stringResource(R.string.settings_gemini_redo_prediction),
            checked = redoPrediction,
            onCheckedChange = { viewModel.setGeminiRedoPrediction(it) }
        )

        SettingsEditTextItem(
            label = "Gemini Timeout (ms)",
            value = geminiTimeout.toString(),
            onValueChange = { newValue -> viewModel.setGeminiTimeoutInput(newValue) }
        )
    }
}
