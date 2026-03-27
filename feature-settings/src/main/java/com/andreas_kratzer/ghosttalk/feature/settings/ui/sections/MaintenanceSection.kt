package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@Composable
fun MaintenanceSection(
    viewModel: SettingsViewModel,
    isGlobal: Boolean,
    onLocalExport: () -> Unit,
    onLocalImport: () -> Unit
) {
    Column {

        if (!isGlobal) {
            PreferenceCategory(stringResource(R.string.settings_category_maintenance)) {
                Button(
                    onClick = { viewModel.setShowActionHistoryDialog(true) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_action_history_title))
                }
                Spacer(modifier = Modifier.height(LocalDimensions.current.paddingSmall))
                Button(
                    onClick = { viewModel.setShowUsageStatsDialog(true) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_usage_stats_title))
                }
            }
        }
    }
}
