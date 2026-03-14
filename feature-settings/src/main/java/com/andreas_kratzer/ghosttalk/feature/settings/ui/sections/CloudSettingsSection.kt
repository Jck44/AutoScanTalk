package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.BackupSelectionDialog
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhosTTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSettingsSection(
    viewModel: SettingsViewModel,
    isGlobal: Boolean
) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    
    val syncMode by viewModel.syncMode.collectAsState()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsState()
    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsState()
    val lastSyncTime by viewModel.lastSuccessfulSyncTime.collectAsState()

    val availableBackups by viewModel.availableBackups.collectAsState()
    val showBackupSelectionDialog by viewModel.showBackupSelectionDialog.collectAsState()
    
    val dimensions = LocalDimensions.current
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensions.paddingMedium)
    ) {
        if (isSyncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        PreferenceCategory(stringResource(R.string.settings_category_cloud_account)) {
            if (userEmail != null) {
                Text(
                    text = stringResource(R.string.settings_cloud_signed_in_as, userEmail!!),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_cloud_sign_out))
                }
            } else {
                Button(
                    onClick = { viewModel.signIn(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_cloud_sign_in))
                }
            }
        }

        if (isGlobal) {
            // Global Mode: Only show "Import as New Book"
            PreferenceCategory(stringResource(R.string.settings_category_cloud_import)) {
                Button(
                    onClick = { 
                        viewModel.fetchAvailableBackupsForImport() 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = userEmail != null && !isSyncing
                ) {
                    Icon(GhosTTalkIcons.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_cloud_import_as_new))
                }
                Text(
                    text = stringResource(R.string.settings_cloud_import_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            // Book-Scoped Mode: Show Sync Settings and manual buttons
            PreferenceCategory(stringResource(R.string.settings_category_cloud)) {
                SettingsToggleItem(
                    label = stringResource(R.string.settings_cloud_sync_enabled),
                    checked = isCloudSyncEnabled,
                    onCheckedChange = { viewModel.setCloudSyncEnabled(context, it) },
                    enabled = userEmail != null
                )

                Text(
                    text = stringResource(R.string.settings_cloud_last_sync, if (lastSyncTime > 0) dateFormat.format(Date(lastSyncTime)) else "-"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                val syncModeLabel = when (syncMode) {
                    "BACKUP_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_backup)
                    "RESTORE_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_restore)
                    else -> stringResource(R.string.settings_cloud_sync_mode_two_way)
                }

                SettingsDropdownItem(
                    label = stringResource(R.string.settings_cloud_sync_mode),
                    selectedOption = syncModeLabel,
                    options = listOf(
                        "TWO_WAY" to R.string.settings_cloud_sync_mode_two_way,
                        "BACKUP_ONLY" to R.string.settings_cloud_sync_mode_backup,
                        "RESTORE_ONLY" to R.string.settings_cloud_sync_mode_restore
                    ).map { (mode, resId) ->
                        stringResource(resId) to { viewModel.setSyncMode(mode) }
                    }
                )

                if (isCloudSyncEnabled) {
                    SettingsDropdownItem(
                        label = stringResource(R.string.settings_cloud_sync_interval),
                        selectedOption = syncIntervalMinutes.toString(),
                        options = listOf("15", "30", "60", "120", "360", "1440").map { interval ->
                            interval to { viewModel.setSyncIntervalMinutes(interval.toLong()) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(dimensions.paddingMedium))

                Button(
                    onClick = { viewModel.syncNow() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing && userEmail != null
                ) {
                    Text(stringResource(R.string.settings_cloud_sync_now))
                }

                Spacer(modifier = Modifier.height(dimensions.paddingSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.backupNow() },
                        modifier = Modifier.weight(1f),
                        enabled = !isSyncing && userEmail != null
                    ) {
                        Text(stringResource(R.string.settings_cloud_backup_now))
                    }
                    OutlinedButton(
                        onClick = { 
                            viewModel.restoreNow() 
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSyncing && userEmail != null
                    ) {
                        Text(stringResource(R.string.settings_cloud_restore_now))
                    }
                }
            }
        }

        PreferenceCategory(stringResource(R.string.settings_category_cloud)) { 
             val projectId by viewModel.googleHomeProjectId.collectAsState("")
             com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem(
                 label = stringResource(R.string.settings_google_home_project_id),
                 value = projectId,
                 onValueChange = { viewModel.setGoogleHomeProjectId(it) }
             )
             Text(
                 text = stringResource(R.string.settings_google_home_project_id_desc),
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.padding(start = 16.dp, top = 4.dp)
             )
        }
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
}
