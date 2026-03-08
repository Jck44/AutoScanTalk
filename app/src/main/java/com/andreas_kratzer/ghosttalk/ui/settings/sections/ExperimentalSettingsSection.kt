package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel

@Composable
fun ExperimentalSettingsSection(viewModel: SettingsViewModel) {
    PreferenceCategory(stringResource(R.string.settings_category_experimental)) {

        val weatherTimeout by viewModel.weatherCacheTimeout.collectAsState(60L)
        SettingsEditTextItem(
            label = stringResource(R.string.settings_weather_cache_timeout_label),
            value = weatherTimeout.toString(),
            onValueChange = { viewModel.setWeatherCacheTimeoutInput(it) }
        )
    }
}
