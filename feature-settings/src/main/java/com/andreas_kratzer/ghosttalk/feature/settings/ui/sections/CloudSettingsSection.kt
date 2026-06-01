package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.BackupSelectionDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.DriveFolderPickerDialog
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.SyncLogDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CloudSettingsSection(
    viewModel: SettingsViewModel,
    isGlobal: Boolean,
    onLocalExport: () -> Unit,
    onLocalImport: () -> Unit
) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    
    val syncMode by viewModel.syncMode.collectAsState()
    val syncModeBook by viewModel.syncModeBook.collectAsState()
    val syncModeTts by viewModel.syncModeTts.collectAsState()
    val syncModeStats by viewModel.syncModeStats.collectAsState()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsState()
    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsState()
    val lastSyncTime by viewModel.lastSuccessfulSyncTime.collectAsState()

    val googleDriveFolderId by viewModel.googleDriveFolderId.collectAsState(null)
    val googleDriveFolderName by viewModel.googleDriveFolderName.collectAsState(null)
    val driveFolders by viewModel.driveFolders.collectAsState()
    val isBrowsingFolders by viewModel.isBrowsingFolders.collectAsState()
    var showFolderPicker by remember { mutableStateOf(false) }
    var showManualUrlDialog by remember { mutableStateOf(false) }

    val availableBackups by viewModel.availableBackups.collectAsState()
    val showBackupSelectionDialog by viewModel.showBackupSelectionDialog.collectAsState()
    var showImportFolderPicker by remember { mutableStateOf(false) }
    var showManualImportUrlDialog by remember { mutableStateOf(false) }
    val syncLogs by viewModel.syncLogs.collectAsState()
    var showSyncLogDialog by remember { mutableStateOf(false) }
    val spotifyUserDisplayName by viewModel.spotifyUserDisplayName.collectAsState(null)
    
    val dimensions = LocalDimensions.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd.MM.yyyy HH:mm", locale) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensions.paddingMedium)
    ) {
        if (isSyncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        val elevenLabsApiKey by viewModel.elevenLabsApiKey.collectAsState("")

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
                    Text("Konto wechseln")
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
            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

            PreferenceCategory(stringResource(R.string.settings_category_elevenlabs)) {
                com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem(
                    label = stringResource(R.string.settings_elevenlabs_api_key),
                    value = elevenLabsApiKey ?: "",
                    onValueChange = { viewModel.setElevenLabsApiKey(it) }
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
                    val activity = context as? android.app.Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                    
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

        if (isGlobal) {
            // Global Mode: Only show "Import as New Book"
            PreferenceCategory(stringResource(R.string.settings_category_cloud_import)) {
                Button(
                    onClick = { 
                        showImportFolderPicker = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = userEmail != null && !isSyncing
                ) {
                    Icon(GhostTalkIcons.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_cloud_import_as_new))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { 
                        showManualImportUrlDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = userEmail != null && !isSyncing
                ) {
                    Icon(GhostTalkIcons.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aus geteiltem Ordner importieren (Link)")
                }
                Text(
                    text = stringResource(R.string.settings_cloud_import_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

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
        } else {
            // Book-Scoped Mode: Show Sync Settings and manual buttons
            PreferenceCategory("Backup-Ordner") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Speicherort im Google Drive",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = googleDriveFolderName ?: "Standard (GhosTTalk_Sync)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { showFolderPicker = true }, enabled = userEmail != null) {
                        Text("Ändern")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showManualUrlDialog = true }, enabled = userEmail != null) {
                        Text("Link eingeben")
                    }
                }
                if (googleDriveFolderId != null) {
                    TextButton(
                        onClick = { viewModel.selectDriveFolder(null, null) },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("Auf Standard zurücksetzen", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimensions.paddingMedium))

            PreferenceCategory(stringResource(R.string.settings_category_cloud_sync)) {
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

                Spacer(modifier = Modifier.height(dimensions.paddingMedium))

                OutlinedButton(
                    onClick = { 
                        viewModel.loadSyncLogs()
                        showSyncLogDialog = true 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(GhostTalkIcons.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_cloud_logs_view))
                }
            }
        }

        Spacer(modifier = Modifier.height(dimensions.paddingLarge))

        PreferenceCategory(stringResource(R.string.settings_category_local_backup)) {
            if (!isGlobal) {
                Text(
                    text = stringResource(R.string.settings_local_backup_describe_create),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(dimensions.paddingSmall))
                Button(
                    onClick = onLocalExport,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_local_backup_create))
                }
                Spacer(modifier = Modifier.height(dimensions.paddingLarge))
            }

            Text(
                text = stringResource(if (isGlobal) R.string.settings_local_backup_describe_global_import else R.string.settings_local_backup_describe_restore),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(dimensions.paddingSmall))
            OutlinedButton(
                onClick = onLocalImport,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (isGlobal) R.string.settings_local_backup_import else R.string.settings_local_backup_restore))
            }
        }

        if (!isGlobal) {
            Spacer(modifier = Modifier.height(dimensions.paddingMedium))
            
            val statsRetentionDays by viewModel.statsRetentionDays.collectAsState()
            val statsAggregationHours by viewModel.statsAggregationHours.collectAsState()
            
            PreferenceCategory(stringResource(R.string.settings_category_stats_privacy)) {
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
                    androidx.compose.material3.Slider(
                        value = statsRetentionDays.toFloat(),
                        onValueChange = { viewModel.setStatsRetentionDays(it.toInt()) },
                        valueRange = 7f..365f,
                        steps = 358 // 365 - 7
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
    }

    if (showFolderPicker) {
        DriveFolderPickerDialog(
            folders = driveFolders,
            isLoading = isBrowsingFolders,
            onFetchFolders = { parentId -> viewModel.fetchDriveFolders(parentId) },
            onFolderSelected = { id, name ->
                viewModel.selectDriveFolder(id, name)
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false }
        )
    }

    if (showImportFolderPicker) {
        DriveFolderPickerDialog(
            folders = driveFolders,
            isLoading = isBrowsingFolders,
            onFetchFolders = { parentId -> viewModel.fetchDriveFolders(parentId) },
            onFolderSelected = { id, _ ->
                viewModel.fetchAvailableBackupsForImport(id)
                showImportFolderPicker = false
            },
            onDismiss = { showImportFolderPicker = false }
        )
    }

    if (showSyncLogDialog) {
        SyncLogDialog(
            logs = syncLogs,
            onDismiss = { showSyncLogDialog = false },
            onClearLogs = { viewModel.clearSyncLogs() }
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

    if (showManualUrlDialog) {
        var urlOrIdInput by remember { mutableStateOf("") }
        var isVerifying by remember { mutableStateOf(false) }
        var verificationError by remember { mutableStateOf<String?>(null) }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!isVerifying) showManualUrlDialog = false },
            title = { Text("Freigabe-Link oder Ordner-ID eingeben") },
            text = {
                Column {
                    Text(
                        text = "Füge den Google Drive Link zum geteilten Ordner oder die Ordner-ID hier ein. Der Ordner muss von der GhostTalk-App (z. B. auf dem Patientengerät) erstellt worden sein.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = urlOrIdInput,
                        onValueChange = { urlOrIdInput = it },
                        label = { Text("Link oder ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isVerifying
                    )
                    if (verificationError != null) {
                        Text(
                            text = verificationError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (isVerifying) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isVerifying = true
                        verificationError = null
                        viewModel.selectDriveFolderByUrlOrId(urlOrIdInput) { success, folderName ->
                            isVerifying = false
                            if (success) {
                                showManualUrlDialog = false
                            } else {
                                verificationError = folderName ?: "Unbekannter Fehler beim Verifizieren"
                            }
                        }
                    },
                    enabled = urlOrIdInput.isNotBlank() && !isVerifying
                ) {
                    Text("Verknüpfen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualUrlDialog = false },
                    enabled = !isVerifying
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showManualImportUrlDialog) {
        var urlOrIdInput by remember { mutableStateOf("") }

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showManualImportUrlDialog = false },
            title = { Text("Aus geteiltem Ordner importieren") },
            text = {
                Column {
                    Text(
                        text = "Füge den Google Drive Link zum geteilten Ordner oder die Ordner-ID hier ein, um nach verfügbaren Backups zu suchen.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = urlOrIdInput,
                        onValueChange = { urlOrIdInput = it },
                        label = { Text("Link oder ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.fetchAvailableBackupsForImportByUrlOrId(urlOrIdInput)
                        showManualImportUrlDialog = false
                    },
                    enabled = urlOrIdInput.isNotBlank()
                ) {
                    Text("Nach Backups suchen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualImportUrlDialog = false }
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
