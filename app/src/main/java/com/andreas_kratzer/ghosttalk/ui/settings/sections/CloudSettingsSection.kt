package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsClickableItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel

@Composable
fun CloudSettingsSection(viewModel: SettingsViewModel) {
    val isEnabled by viewModel.isCloudSyncEnabled.collectAsState(false)
    val syncMode by viewModel.syncMode.collectAsState("TWO_WAY")
    val userEmail by viewModel.userEmail.collectAsState(null)
    val isSyncing by viewModel.isSyncing.collectAsState(false)
    val lastSync by viewModel.lastSuccessfulSyncTime.collectAsState(0L)
    val context = LocalContext.current

    var expandedMode by remember { mutableStateOf(false) }

    PreferenceCategory(stringResource(R.string.settings_category_google_account)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = userEmail ?: stringResource(R.string.settings_google_account_status_not_signed_in),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (userEmail == null) {
                Button(onClick = { viewModel.signIn(context) }) { Text(stringResource(R.string.settings_google_account_sign_in)) }
            } else {
                OutlinedButton(onClick = { viewModel.signOut() }) { Text(stringResource(R.string.settings_google_account_sign_out)) }
            }
        }
    }

    if (userEmail != null) {
        PreferenceCategory(stringResource(R.string.settings_category_cloud)) {
            SettingsToggleItem(stringResource(R.string.settings_cloud_sync_enabled), isEnabled) { viewModel.setCloudSyncEnabled(it) }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                val modeLabel = when (syncMode) {
                    "BACKUP_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_backup)
                    "RESTORE_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_restore)
                    else -> stringResource(R.string.settings_cloud_sync_mode_two_way)
                }
                SettingsClickableItem(stringResource(R.string.settings_cloud_sync_mode), modeLabel) { expandedMode = true }
                DropdownMenu(expanded = expandedMode, onDismissRequest = { expandedMode = false }) {
                    listOf("TWO_WAY", "BACKUP_ONLY", "RESTORE_ONLY").forEach { mode ->
                        val itemLabel = when (mode) {
                            "BACKUP_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_backup)
                            "RESTORE_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_restore)
                            else -> stringResource(R.string.settings_cloud_sync_mode_two_way)
                        }
                        DropdownMenuItem(text = { Text(itemLabel) }, onClick = { viewModel.setSyncMode(mode); expandedMode = false })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.syncNow() }, enabled = !isSyncing) { Text(stringResource(R.string.action_search).replace("…", "")) } // Reuse Search or similar? Let's use fixed for now
                OutlinedButton(onClick = { viewModel.backupNow() }, enabled = !isSyncing) { Text(stringResource(R.string.settings_cloud_backup_now)) }
            }
        }
    }
}
