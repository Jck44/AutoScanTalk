package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@Composable
fun MaintenanceSection(
    viewModel: SettingsViewModel,
    isGlobal: Boolean,
    onLocalExport: () -> Unit,
    onLocalImport: () -> Unit
) {
    Column {
        PreferenceCategory(stringResource(R.string.settings_category_local_backup)) {
            if (!isGlobal) {
                Text(
                    text = stringResource(R.string.settings_local_backup_describe_create),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(LocalDimensions.current.paddingSmall))
                Button(
                    onClick = onLocalExport,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_local_backup_create))
                }
                Spacer(modifier = Modifier.height(LocalDimensions.current.paddingLarge))
            }
            
            Text(
                text = stringResource(if (isGlobal) R.string.settings_local_backup_describe_global_import else R.string.settings_local_backup_describe_restore),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(LocalDimensions.current.paddingSmall))
            OutlinedButton(
                onClick = onLocalImport,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (isGlobal) R.string.settings_local_backup_import else R.string.settings_local_backup_restore))
            }
        }

        if (!isGlobal) {
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
    }
}
