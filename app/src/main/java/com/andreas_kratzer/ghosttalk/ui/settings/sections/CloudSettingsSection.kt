package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsClickableItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSettingsSection(viewModel: SettingsViewModel) {
    val isEnabled by viewModel.isCloudSyncEnabled.collectAsState(false)
    val syncMode by viewModel.syncMode.collectAsState("TWO_WAY")
    val lastSyncTime by viewModel.lastSuccessfulSyncTime.collectAsState(0L)
    val syncInterval by viewModel.syncIntervalMinutes.collectAsState(15L)
    val userEmail by viewModel.userEmail.collectAsState(null)
    val isSyncing by viewModel.isSyncing.collectAsState(false)
    val context = LocalContext.current
    val dimensions = LocalDimensions.current

    var expandedMode by remember { mutableStateOf(false) }

    PreferenceCategory(stringResource(R.string.settings_category_cloud)) {
        SettingsToggleItem(stringResource(R.string.settings_cloud_sync_enabled), isEnabled) { viewModel.setCloudSyncEnabled(context, it) }
        
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

        if (isEnabled) {
            com.andreas_kratzer.ghosttalk.ui.settings.SettingsEditTextItem(
                label = "Sync-Intervall (Minuten)",
                value = syncInterval.toString(),
                onValueChange = { newValue ->
                    newValue.toLongOrNull()?.let { viewModel.setSyncIntervalMinutes(it) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            val lastSyncTimeValue = lastSyncTime
            val lastSyncText = if (lastSyncTimeValue > 0) {
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                sdf.format(Date(lastSyncTimeValue))
            } else {
                "Nie"
            }
            Text(
                text = "Letzter Sync: $lastSyncText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = dimensions.paddingSmall)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
            modifier = Modifier.padding(top = dimensions.paddingMedium)
        ) {
            Button(
                onClick = { viewModel.syncNow() },
                enabled = !isSyncing && userEmail != null,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.action_search).replace("…", ""))
            }
            OutlinedButton(
                onClick = { viewModel.backupNow() },
                enabled = !isSyncing && userEmail != null,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.settings_cloud_backup_now))
            }
        }
    }
}
