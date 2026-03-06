package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel

@Composable
fun MaintenanceSection(viewModel: SettingsViewModel) {
    PreferenceCategory(stringResource(R.string.settings_category_maintenance)) {
        Button(
            onClick = { viewModel.clearButtonUsageStats(viewModel.activeBookId) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_clear_usage_stats))
        }
    }
}
