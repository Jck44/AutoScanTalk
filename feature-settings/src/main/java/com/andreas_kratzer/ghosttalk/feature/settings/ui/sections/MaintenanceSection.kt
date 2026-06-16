package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.BackupSelectionDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.DriveFolderPickerDialog

@Suppress("UNUSED_PARAMETER", "UNUSED_VALUE")
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun MaintenanceSection(
    viewModel: SettingsViewModel,
    isGlobal: Boolean,
    onLocalExport: () -> Unit,
    onLocalImport: () -> Unit,
    onSelectSafFolderForImport: () -> Unit = {}
) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val showImportFolderPicker = remember { mutableStateOf(false) }
    val showManualImportUrlDialog = remember { mutableStateOf(false) }
    val updateErrorFormat = stringResource(R.string.settings_maintenance_check_update_error)
    val showDeleteEmptyButtonsConfirmation = remember { mutableStateOf(false) }
    val updateStatus by viewModel.updateCheckStatus.collectAsState()

    LaunchedEffect(updateStatus) {
        when (val status = updateStatus) {
            is SettingsViewModel.UpdateCheckStatus.UpToDate -> {
                Toast.makeText(context, R.string.settings_maintenance_check_update_none, Toast.LENGTH_SHORT).show()
                viewModel.setUpdateCheckStatus(null)
            }
            is SettingsViewModel.UpdateCheckStatus.UpdateFound -> {
                Toast.makeText(context, R.string.settings_maintenance_check_update_found, Toast.LENGTH_LONG).show()
                viewModel.setUpdateCheckStatus(null)
            }
            is SettingsViewModel.UpdateCheckStatus.NotFromPlayStore -> {
                Toast.makeText(context, R.string.settings_maintenance_check_update_not_play_store, Toast.LENGTH_LONG).show()
                viewModel.setUpdateCheckStatus(null)
            }
            is SettingsViewModel.UpdateCheckStatus.Error -> {
                val message = String.format(updateErrorFormat, status.message)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                viewModel.setUpdateCheckStatus(null)
            }
            else -> {}
        }
    }

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
                    onClick = { viewModel.openUsageStatistics() },
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
                    onClick = { showDeleteEmptyButtonsConfirmation.value = true },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_maintenance_delete_empty_buttons_title))
                }

                Spacer(modifier = Modifier.height(LocalDimensions.current.paddingLarge))

                Text(
                    text = stringResource(R.string.settings_maintenance_check_update_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(LocalDimensions.current.paddingSmall))
                Button(
                    onClick = { viewModel.checkManualUpdate() },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = updateStatus !is SettingsViewModel.UpdateCheckStatus.Checking
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (updateStatus is SettingsViewModel.UpdateCheckStatus.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp).padding(end = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Text(stringResource(R.string.settings_maintenance_check_update_started))
                        } else {
                            Text(stringResource(R.string.settings_maintenance_check_update_title))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(LocalDimensions.current.paddingLarge))

            PreferenceCategory(stringResource(R.string.settings_category_cloud_import)) {
                Button(
                    onClick = { showImportFolderPicker.value = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = userEmail != null && !isSyncing
                ) {
                    Icon(GhostTalkIcons.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_cloud_import_drive_api))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showManualImportUrlDialog.value = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = userEmail != null && !isSyncing
                ) {
                    Icon(GhostTalkIcons.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_cloud_import_shared_link))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onSelectSafFolderForImport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing
                ) {
                    Icon(GhostTalkIcons.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_cloud_import_saf))
                }

                Text(
                    text = stringResource(R.string.settings_cloud_import_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(LocalDimensions.current.paddingLarge))

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
    }

    if (showDeleteEmptyButtonsConfirmation.value) {
        GhostTalkDialog(
            title = stringResource(R.string.settings_maintenance_delete_empty_buttons_confirm_title),
            confirmText = stringResource(R.string.settings_maintenance_delete_empty_buttons_title),
            dismissText = stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.dialog_close),
            isDestructive = true,
            onConfirm = {
                showDeleteEmptyButtonsConfirmation.value = false
                viewModel.deleteEmptyButtons { count ->
                    Toast.makeText(
                        context,
                        context.applicationContext.resources.getQuantityString(R.plurals.settings_maintenance_delete_empty_buttons_success, count, count),
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = { showDeleteEmptyButtonsConfirmation.value = false },
            content = {
                Text(stringResource(R.string.settings_maintenance_delete_empty_buttons_confirm_text))
            }
        )
    }

    val driveFolders by viewModel.driveFolders.collectAsState()
    val isBrowsingFolders by viewModel.isBrowsingFolders.collectAsState()
    val availableBackups by viewModel.availableBackups.collectAsState()
    val showBackupSelectionDialog by viewModel.showBackupSelectionDialog.collectAsState()

    if (showImportFolderPicker.value) {
        DriveFolderPickerDialog(
            folders = driveFolders,
            isLoading = isBrowsingFolders,
            onFetchFolders = { parentId -> viewModel.fetchDriveFolders(parentId) },
            onFolderSelected = { id, _ ->
                viewModel.fetchAvailableBackupsForImport(id)
                showImportFolderPicker.value = false
            },
            onDismiss = { showImportFolderPicker.value = false }
        )
    }

    if (showBackupSelectionDialog) {
        BackupSelectionDialog(
            backups = availableBackups,
            onDismiss = { viewModel.dismissBackupSelectionDialog() },
            onBackupSelected = { backupInfo ->
                viewModel.importCloudBackup(backupInfo)
            }
        )
    }

    if (showManualImportUrlDialog.value) {
        val urlOrIdInput = remember { mutableStateOf("") }

        GhostTalkDialog(
            title = stringResource(R.string.settings_cloud_import_dialog_title),
            confirmText = stringResource(R.string.settings_cloud_import_search_backups),
            dismissText = stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.action_cancel),
            onConfirm = {
                viewModel.fetchAvailableBackupsForImportByUrlOrId(urlOrIdInput.value)
                showManualImportUrlDialog.value = false
            },
            onDismiss = { showManualImportUrlDialog.value = false },
            confirmEnabled = urlOrIdInput.value.isNotBlank(),
            content = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_cloud_import_dialog_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = urlOrIdInput.value,
                        onValueChange = { urlOrIdInput.value = it },
                        label = { Text(stringResource(R.string.settings_cloud_import_link_or_id_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        )
    }
}
