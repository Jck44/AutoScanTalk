package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocalSwitchSettingsSection(
    viewModel: SettingsViewModel,
    onNavigateToVocalTraining: () -> Unit
) {
    val isVocalSwitchEnabled by viewModel.isVocalSwitchEnabled.collectAsState()
    val dimensions = LocalDimensions.current

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        PreferenceCategory(stringResource(R.string.settings_category_vocal_switch), modifier = Modifier.weight(1f)) {
            SettingsToggleItem(
                label = stringResource(R.string.settings_vocal_switch_activate),
                checked = isVocalSwitchEnabled,
                onCheckedChange = { viewModel.scanningDelegate.setVocalSwitchEnabled(it) }
            )
            com.andreas_kratzer.ghosttalk.core.ui.components.SettingsClickableItem(
                label = stringResource(R.string.settings_vocal_profile_manage),
                value = stringResource(R.string.settings_vocal_profile_manage_desc),
                onClick = onNavigateToVocalTraining
            )
        }
    }
}
