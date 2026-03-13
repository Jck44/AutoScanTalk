package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

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
