package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@Composable
fun ExperimentalSettingsSection(viewModel: SettingsViewModel) {
    PreferenceCategory(stringResource(R.string.settings_category_experimental)) {

        val weatherTimeout by viewModel.weatherCacheTimeout.collectAsState(60L)
        SettingsEditTextItem(
            label = stringResource(R.string.settings_weather_cache_timeout_label),
            value = weatherTimeout.toString(),
            onValueChange = { viewModel.setWeatherCacheTimeoutInput(it) },
            numericOnly = true
        )

        val backgroundLocationEnabled by viewModel.backgroundLocationEnabled.collectAsState(false)
        val backgroundLocationInterval by viewModel.backgroundLocationInterval.collectAsState(1L)

        SettingsToggleItem(
            label = stringResource(R.string.settings_background_location_enabled_label),
            checked = backgroundLocationEnabled,
            onCheckedChange = { viewModel.setBackgroundLocationEnabled(it) }
        )

        if (backgroundLocationEnabled) {
            val locationIntervalLabel = stringResource(
                if (backgroundLocationInterval == 1L) R.string.settings_interval_hour_single else R.string.settings_interval_hours_plural,
                backgroundLocationInterval
            )
            val locationOptions = (1L..6L).map { hour ->
                val label = stringResource(
                    if (hour == 1L) R.string.settings_interval_hour_single else R.string.settings_interval_hours_plural,
                    hour
                )
                label to { viewModel.setBackgroundLocationInterval(hour) }
            }
            com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem(
                label = stringResource(R.string.settings_background_location_interval_label),
                selectedOption = locationIntervalLabel,
                options = locationOptions
            )
        }

        val backgroundWeatherEnabled by viewModel.backgroundWeatherEnabled.collectAsState(false)
        val backgroundWeatherInterval by viewModel.backgroundWeatherInterval.collectAsState(6L)

        SettingsToggleItem(
            label = stringResource(R.string.settings_background_weather_enabled_label),
            checked = backgroundWeatherEnabled,
            onCheckedChange = { viewModel.setBackgroundWeatherEnabled(it) }
        )

        if (backgroundWeatherEnabled) {
            val weatherIntervalLabel = stringResource(
                if (backgroundWeatherInterval == 1L) R.string.settings_interval_hour_single else R.string.settings_interval_hours_plural,
                backgroundWeatherInterval
            )
            val weatherOptions = (1L..6L).map { hour ->
                val label = stringResource(
                    if (hour == 1L) R.string.settings_interval_hour_single else R.string.settings_interval_hours_plural,
                    hour
                )
                label to { viewModel.setBackgroundWeatherInterval(hour) }
            }
            com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem(
                label = stringResource(R.string.settings_background_weather_interval_label),
                selectedOption = weatherIntervalLabel,
                options = weatherOptions
            )
        }
    }
}
