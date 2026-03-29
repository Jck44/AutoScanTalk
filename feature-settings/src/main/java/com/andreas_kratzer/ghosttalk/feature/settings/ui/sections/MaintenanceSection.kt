package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var showDeleteEmptyButtonsConfirmation by remember { mutableStateOf(false) }

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
                Spacer(modifier = Modifier.height(LocalDimensions.current.paddingSmall))
                Button(
                    onClick = { viewModel.setShowPrefetchDialog(true) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_prefetch_tts_title))
                }
            }
        }

        if (isGlobal) {
            PreferenceCategory(stringResource(R.string.settings_category_maintenance)) {
                Text(
                    text = stringResource(R.string.settings_maintenance_delete_empty_buttons_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(LocalDimensions.current.paddingSmall))
                Button(
                    onClick = { showDeleteEmptyButtonsConfirmation = true },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_maintenance_delete_empty_buttons_title))
                }
            }
        }
    }

    if (showDeleteEmptyButtonsConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteEmptyButtonsConfirmation = false },
            title = { Text(stringResource(R.string.settings_maintenance_delete_empty_buttons_confirm_title)) },
            text = { Text(stringResource(R.string.settings_maintenance_delete_empty_buttons_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteEmptyButtonsConfirmation = false
                        viewModel.deleteEmptyButtons { count ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_maintenance_delete_empty_buttons_success, count),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.settings_maintenance_delete_empty_buttons_title),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteEmptyButtonsConfirmation = false }) {
                    Text(stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.dialog_close))
                }
            }
        )
    }
}
