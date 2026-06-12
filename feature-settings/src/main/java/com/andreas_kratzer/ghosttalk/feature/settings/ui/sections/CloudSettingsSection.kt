@file:Suppress("UNUSED_VALUE")
package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.DriveFolderPickerDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.SyncLogDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("UNUSED_VALUE", "AssignedValueDoubleCheck")
@Composable
fun CloudSettingsSection(
    viewModel: SettingsViewModel,
    showSyncSettings: Boolean
) {
    val context = LocalContext.current
    val isSyncing by viewModel.isSyncing.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val driveFolders by viewModel.driveFolders.collectAsState()
    val isBrowsingFolders by viewModel.isBrowsingFolders.collectAsState()
    val showFolderPicker = remember { mutableStateOf(false) }
    val showManualUrlDialog = remember { mutableStateOf(false) }
    val showSyncLogDialog = remember { mutableStateOf(false) }
    val syncLogs by viewModel.syncLogs.collectAsState()
    val dimensions = LocalDimensions.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensions.paddingMedium)
    ) {
        if (isSyncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!showSyncSettings) {
            CloudAccountSettingsCategory(viewModel = viewModel)
            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
            ElevenLabsSettingsCategory(viewModel = viewModel)
            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
            SpotifySettingsCategory(viewModel = viewModel)
        } else {
            SyncTargetSettingsCategory(
                viewModel = viewModel,
                showFolderPicker = showFolderPicker,
                showManualUrlDialog = showManualUrlDialog
            )
            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
            CloudSyncSettingsCategory(
                viewModel = viewModel,
                showSyncLogDialog = showSyncLogDialog
            )
            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
            StatsPrivacySettingsCategory(viewModel = viewModel)
        }
    }

    if (showFolderPicker.value) {
        DriveFolderPickerDialog(
            folders = driveFolders,
            isLoading = isBrowsingFolders,
            onFetchFolders = { parentId -> viewModel.fetchDriveFolders(parentId) },
            onFolderSelected = { id, name ->
                viewModel.selectDriveFolder(id, name)
                showFolderPicker.value = false
            },
            onDismiss = { showFolderPicker.value = false }
        )
    }

    if (showSyncLogDialog.value) {
        SyncLogDialog(
            logs = syncLogs,
            onDismiss = { showSyncLogDialog.value = false },
            onClearLogs = { viewModel.clearSyncLogs() }
        )
    }

    if (showManualUrlDialog.value) {
        ManualUrlDialog(
            viewModel = viewModel,
            showManualUrlDialog = showManualUrlDialog
        )
    }
}

@Composable
private fun CloudAccountSettingsCategory(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsState()

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
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.switchAccount(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_cloud_switch_account))
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
}

@Composable
private fun ElevenLabsSettingsCategory(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsState()
    val elevenLabsApiKey by viewModel.elevenLabsApiKey.collectAsState()
    val dimensions = LocalDimensions.current

    PreferenceCategory(stringResource(R.string.settings_category_elevenlabs)) {
        SettingsEditTextItem(
            label = stringResource(R.string.settings_elevenlabs_api_key),
            value = elevenLabsApiKey ?: "",
            onValueChange = { viewModel.setElevenLabsApiKey(it) },
            isPassword = true
        )
        Text(
            text = stringResource(R.string.settings_elevenlabs_api_key_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { viewModel.testElevenLabsConnection() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(GhostTalkIcons.RecordVoiceOver, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_elevenlabs_test_button))
        }

        Spacer(modifier = Modifier.height(dimensions.paddingSmall))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
        ) {
            val activity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
            
            OutlinedButton(
                onClick = { activity?.let { viewModel.saveApiKeyToGoogle(it) } },
                modifier = Modifier.weight(1f),
                enabled = userEmail != null && !elevenLabsApiKey.isNullOrEmpty()
            ) {
                Icon(GhostTalkIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.settings_cloud_backup_now), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { activity?.let { viewModel.importApiKeyFromGoogle(it) } },
                modifier = Modifier.weight(1f),
                enabled = userEmail != null
            ) {
                Icon(GhostTalkIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.settings_cloud_restore_now), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SpotifySettingsCategory(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val spotifyUserDisplayName by viewModel.spotifyUserDisplayName.collectAsState()

    PreferenceCategory(stringResource(R.string.settings_category_spotify)) {
        if (spotifyUserDisplayName != null) {
            Text(
                text = stringResource(R.string.settings_spotify_status_connected, spotifyUserDisplayName!!),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = { viewModel.disconnectSpotify() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_spotify_disconnect))
            }
        } else {
            Text(
                text = stringResource(R.string.settings_spotify_status_disconnected),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = { viewModel.connectSpotify(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_spotify_connect))
            }
        }
    }
}

@Composable
private fun SyncTargetSettingsCategory(
    viewModel: SettingsViewModel,
    showFolderPicker: MutableState<Boolean>,
    showManualUrlDialog: MutableState<Boolean>
) {
    val userEmail by viewModel.userEmail.collectAsState()
    val googleDriveFolderId by viewModel.googleDriveFolderId.collectAsState()
    val googleDriveFolderName by viewModel.googleDriveFolderName.collectAsState()

    PreferenceCategory(stringResource(R.string.settings_sync_target_category)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_sync_drive_location),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = googleDriveFolderName ?: stringResource(R.string.settings_sync_drive_default_folder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(onClick = { showFolderPicker.value = true }, enabled = userEmail != null) {
                Text(stringResource(R.string.settings_sync_change))
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { showManualUrlDialog.value = true }, enabled = userEmail != null) {
                Text(stringResource(R.string.settings_sync_enter_link))
            }
        }
        if (googleDriveFolderId != null) {
            TextButton(
                onClick = { viewModel.selectDriveFolder(null, null) },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(stringResource(R.string.settings_sync_reset_default), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CloudSyncSettingsCategory(
    viewModel: SettingsViewModel,
    showSyncLogDialog: MutableState<Boolean>
) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncModeBook by viewModel.syncModeBook.collectAsState()
    val syncModeTts by viewModel.syncModeTts.collectAsState()
    val syncModeStats by viewModel.syncModeStats.collectAsState()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsState()
    val foregroundSyncIntervalMinutes by viewModel.foregroundSyncIntervalMinutes.collectAsState()
    val isDataCloudSyncEnabled by viewModel.isDataCloudSyncEnabled.collectAsState()
    val lastSyncTime by viewModel.lastSuccessfulSyncTime.collectAsState()
    val syncModeLogs by viewModel.syncModeLogs.collectAsState()
    val syncLogsIntervalHours by viewModel.syncLogsIntervalHours.collectAsState()
    val lastLogsSyncTime by viewModel.lastLogsSyncTime.collectAsState()
    
    val logDateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val dimensions = LocalDimensions.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd.MM.yyyy HH:mm", locale) }

    PreferenceCategory(stringResource(R.string.settings_category_cloud_sync)) {
        SettingsToggleItem(
            label = stringResource(R.string.settings_cloud_sync_enabled),
            checked = isDataCloudSyncEnabled,
            onCheckedChange = { viewModel.setCloudSyncEnabled(context, it) },
            enabled = userEmail != null
        )

        Text(
            text = stringResource(R.string.settings_cloud_last_sync, if (lastSyncTime > 0) dateFormat.format(Date(lastSyncTime)) else "-"),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        val syncModeBookLabel = when (syncModeBook) {
            "OFF" -> stringResource(R.string.settings_cloud_sync_mode_off)
            "BACKUP_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_backup)
            "RESTORE_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_restore)
            else -> stringResource(R.string.settings_cloud_sync_mode_two_way)
        }

        SettingsDropdownItem(
            label = stringResource(R.string.settings_cloud_sync_mode_book),
            selectedOption = syncModeBookLabel,
            options = listOf(
                "TWO_WAY" to R.string.settings_cloud_sync_mode_two_way,
                "BACKUP_ONLY" to R.string.settings_cloud_sync_mode_backup,
                "RESTORE_ONLY" to R.string.settings_cloud_sync_mode_restore,
                "OFF" to R.string.settings_cloud_sync_mode_off
            ).map { (mode, resId) ->
                stringResource(resId) to { viewModel.setSyncModeBook(mode) }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        val syncModeTtsLabel = when (syncModeTts) {
            "OFF" -> stringResource(R.string.settings_cloud_sync_mode_off)
            "BACKUP_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_backup)
            "RESTORE_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_restore)
            else -> stringResource(R.string.settings_cloud_sync_mode_two_way)
        }

        SettingsDropdownItem(
            label = stringResource(R.string.settings_cloud_sync_mode_tts),
            selectedOption = syncModeTtsLabel,
            options = listOf(
                "TWO_WAY" to R.string.settings_cloud_sync_mode_two_way,
                "BACKUP_ONLY" to R.string.settings_cloud_sync_mode_backup,
                "RESTORE_ONLY" to R.string.settings_cloud_sync_mode_restore,
                "OFF" to R.string.settings_cloud_sync_mode_off
            ).map { (mode, resId) ->
                stringResource(resId) to { viewModel.setSyncModeTts(mode) }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        val syncModeStatsLabel = when (syncModeStats) {
            "BACKUP_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_backup)
            "RESTORE_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_restore)
            else -> stringResource(R.string.settings_cloud_sync_mode_off)
        }

        SettingsDropdownItem(
            label = stringResource(R.string.settings_cloud_sync_mode_stats),
            selectedOption = syncModeStatsLabel,
            options = listOf(
                "BACKUP_ONLY" to R.string.settings_cloud_sync_mode_backup,
                "RESTORE_ONLY" to R.string.settings_cloud_sync_mode_restore,
                "OFF" to R.string.settings_cloud_sync_mode_off
            ).map { (mode, resId) ->
                stringResource(resId) to { viewModel.setSyncModeStats(mode) }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Profiles/Settings are always synchronized, so we display a text instead of the dropdown
        Text(
            text = stringResource(R.string.settings_cloud_sync_profiles_always_synced),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- Log Sync ---
        val syncModeLogsLabel = when (syncModeLogs) {
            "BACKUP_ONLY" -> stringResource(R.string.settings_cloud_sync_mode_backup)
            else -> stringResource(R.string.settings_cloud_sync_mode_off)
        }
        SettingsDropdownItem(
            label = stringResource(R.string.settings_cloud_sync_mode_logs),
            selectedOption = syncModeLogsLabel,
            options = listOf(
                "OFF" to R.string.settings_cloud_sync_mode_off,
                "BACKUP_ONLY" to R.string.settings_cloud_sync_mode_backup
            ).map { (mode, resId) ->
                stringResource(resId) to { viewModel.setSyncModeLogs(mode) }
            }
        )

        if (syncModeLogs == "BACKUP_ONLY") {
            Spacer(modifier = Modifier.height(4.dp))
            val intervalLabel = when (syncLogsIntervalHours) {
                3L -> stringResource(R.string.settings_interval_hours_plural, 3)
                6L -> stringResource(R.string.settings_interval_hours_plural, 6)
                24L -> stringResource(R.string.settings_interval_hours_plural, 24)
                else -> stringResource(R.string.settings_interval_hours_plural, 12)
            }
            SettingsDropdownItem(
                label = stringResource(R.string.settings_logs_sync_interval),
                selectedOption = intervalLabel,
                options = listOf(3L, 6L, 12L, 24L).map { hours ->
                    val label = stringResource(R.string.settings_interval_hours_plural, hours.toInt())
                    label to { viewModel.setSyncLogsIntervalHours(hours) }
                }
            )
        }

        // Last log upload timestamp
        val lastLogSyncLabel = if (lastLogsSyncTime > 0L) {
            stringResource(R.string.settings_logs_last_sync, logDateFormat.format(Date(lastLogsSyncTime)))
        } else {
            stringResource(R.string.settings_logs_last_sync, stringResource(R.string.settings_logs_last_sync_never))
        }
        Text(
            text = lastLogSyncLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Manual log actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
        ) {
            OutlinedButton(
                onClick = { viewModel.shareLogs(context) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(R.string.settings_logs_share_report),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (syncModeLogs == "BACKUP_ONLY") {
                OutlinedButton(
                    onClick = { viewModel.uploadLogsNow() },
                    modifier = Modifier.weight(1f),
                    enabled = userEmail != null && !isSyncing
                ) {
                    Text(
                        stringResource(R.string.settings_logs_upload_report),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (isDataCloudSyncEnabled) {
            SettingsDropdownItem(
                label = stringResource(R.string.settings_cloud_sync_interval),
                selectedOption = syncIntervalMinutes.toString(),
                options = listOf("15", "30", "60", "120", "360", "1440").map { interval ->
                    interval to { viewModel.setSyncIntervalMinutes(interval.toLong()) }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsDropdownItem(
                label = stringResource(R.string.settings_foreground_sync_interval),
                selectedOption = foregroundSyncIntervalMinutes.toString(),
                options = listOf("1", "2", "5", "10", "15", "30").map { interval ->
                    interval to { viewModel.setForegroundSyncIntervalMinutes(interval.toLong()) }
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

        Spacer(modifier = Modifier.height(dimensions.paddingMedium))

        OutlinedButton(
            onClick = { 
                viewModel.loadSyncLogs()
                showSyncLogDialog.value = true 
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(GhostTalkIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_cloud_logs_view))
        }
    }
}

@Composable
private fun StatsPrivacySettingsCategory(viewModel: SettingsViewModel) {
    val statsRetentionDays by viewModel.statsRetentionDays.collectAsState()
    val statsAggregationHours by viewModel.statsAggregationHours.collectAsState()
    val onlyRecordHardwareStats by viewModel.onlyRecordHardwareStats.collectAsState()
    val firebaseAnalyticsEnabled by viewModel.firebaseAnalyticsEnabled.collectAsState()

    PreferenceCategory(stringResource(R.string.settings_category_stats_privacy)) {
        SettingsToggleItem(
            label = stringResource(R.string.settings_stats_only_hardware_label),
            checked = onlyRecordHardwareStats,
            onCheckedChange = { viewModel.setOnlyRecordHardwareStats(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsToggleItem(
            label = stringResource(R.string.settings_firebase_analytics_label),
            checked = firebaseAnalyticsEnabled,
            onCheckedChange = { viewModel.setFirebaseAnalyticsEnabled(it) }
        )
        Text(
            text = stringResource(R.string.settings_firebase_analytics_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.settings_stats_retention_label),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.settings_stats_retention_days_format, statsRetentionDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            val retentionSteps = listOf(7, 14, 30, 90, 180, 365)
            val currentIndex = remember(statsRetentionDays) {
                retentionSteps.mapIndexed { index, value -> index to kotlin.math.abs(value - statsRetentionDays) }
                    .minByOrNull { it.second }?.first ?: 2
            }
            androidx.compose.material3.Slider(
                value = currentIndex.toFloat(),
                onValueChange = { index -> 
                    viewModel.setStatsRetentionDays(retentionSteps[index.toInt()]) 
                },
                valueRange = 0f..(retentionSteps.size - 1).toFloat(),
                steps = retentionSteps.size - 2
            )
            Text(
                text = stringResource(R.string.settings_stats_retention_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val aggregationOptions = listOf(1, 3, 6, 12, 24)
        val selectedLabel = when (statsAggregationHours) {
            1 -> stringResource(R.string.settings_stats_interval_1h)
            3 -> stringResource(R.string.settings_stats_interval_3h)
            6 -> stringResource(R.string.settings_stats_interval_6h)
            12 -> stringResource(R.string.settings_stats_interval_12h)
            else -> stringResource(R.string.settings_stats_interval_24h)
        }
        
        SettingsDropdownItem(
            label = stringResource(R.string.settings_stats_aggregation_label),
            selectedOption = selectedLabel,
            options = aggregationOptions.map { hours ->
                val label = when (hours) {
                    1 -> stringResource(R.string.settings_stats_interval_1h)
                    3 -> stringResource(R.string.settings_stats_interval_3h)
                    6 -> stringResource(R.string.settings_stats_interval_6h)
                    12 -> stringResource(R.string.settings_stats_interval_12h)
                    else -> stringResource(R.string.settings_stats_interval_24h)
                }
                label to { viewModel.setStatsAggregationHours(hours) }
            }
        )
        
        Text(
            text = stringResource(R.string.settings_stats_aggregation_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ManualUrlDialog(
    viewModel: SettingsViewModel,
    showManualUrlDialog: MutableState<Boolean>
) {
    val urlOrIdInput = remember { mutableStateOf("") }
    val isVerifying = remember { mutableStateOf(false) }
    val verificationError = remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isVerifying.value) showManualUrlDialog.value = false },
        title = { Text(stringResource(R.string.settings_sync_manual_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_sync_manual_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                androidx.compose.material3.OutlinedTextField(
                    value = urlOrIdInput.value,
                    onValueChange = { urlOrIdInput.value = it },
                    label = { Text(stringResource(R.string.settings_sync_manual_dialog_input_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isVerifying.value
                )
                if (verificationError.value != null) {
                    Text(
                        text = verificationError.value!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (isVerifying.value) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isVerifying.value = true
                    verificationError.value = null
                    viewModel.selectDriveFolderByUrlOrId(urlOrIdInput.value) { success, folderName ->
                        isVerifying.value = false
                        if (success) {
                            showManualUrlDialog.value = false
                        } else {
                            verificationError.value = folderName ?: "Unbekannter Fehler beim Verifizieren"
                        }
                    }
                },
                enabled = urlOrIdInput.value.isNotBlank() && !isVerifying.value
            ) {
                Text(stringResource(R.string.settings_sync_manual_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showManualUrlDialog.value = false },
                enabled = !isVerifying.value
            ) {
                Text(stringResource(com.andreas_kratzer.ghosttalk.core.ui.R.string.action_cancel))
            }
        }
    )
}
