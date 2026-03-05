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
fun TestSettingsSection(viewModel: SettingsViewModel) {
    val showTestButtons by viewModel.showTestButtons.collectAsState(false)
    val showPageId by viewModel.showPageIdInLog.collectAsState(true)
    val volumeKeysActivate by viewModel.volumeKeysActivate.collectAsState(false)

    PreferenceCategory(stringResource(R.string.settings_category_test)) {
        SettingsToggleItem(
            label = stringResource(R.string.settings_show_test_buttons),
            checked = showTestButtons,
            onCheckedChange = { viewModel.setShowTestButtons(it) }
        )
        SettingsToggleItem(
            label = stringResource(R.string.settings_show_page_id_in_log),
            checked = showPageId,
            onCheckedChange = { viewModel.setShowPageIdInLog(it) }
        )
        SettingsToggleItem(
            label = stringResource(R.string.settings_volume_keys_trigger),
            checked = volumeKeysActivate,
            onCheckedChange = { viewModel.setVolumeKeysActivate(it) }
        )
    }
}
