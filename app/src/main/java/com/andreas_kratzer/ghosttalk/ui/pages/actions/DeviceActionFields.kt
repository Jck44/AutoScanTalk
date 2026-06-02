package com.andreas_kratzer.ghosttalk.ui.pages.actions

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

private fun getDeviceActionIcon(type: DeviceActionType): ImageVector {
    return when (type) {
        DeviceActionType.READ_NOTIFICATIONS -> GhostTalkIcons.Notifications
        DeviceActionType.CLEAR_NOTIFICATIONS -> Icons.Default.Delete
        DeviceActionType.MEDIA_PLAY_PAUSE -> Icons.Default.PlayArrow
        DeviceActionType.MEDIA_NEXT -> Icons.AutoMirrored.Filled.ArrowForward
        DeviceActionType.MEDIA_PREVIOUS -> Icons.AutoMirrored.Filled.ArrowBack
        DeviceActionType.VOLUME_MEDIA -> GhostTalkIcons.VolumeUp
        DeviceActionType.VOLUME_NOTIFICATION -> GhostTalkIcons.VolumeDown
        DeviceActionType.VOLUME_ALARM -> GhostTalkIcons.VolumeUp
        DeviceActionType.VOLUME_CALL -> Icons.Default.Phone
        DeviceActionType.STATUS_SILENT -> GhostTalkIcons.VolumeOff
        DeviceActionType.STATUS_VIBRATE -> GhostTalkIcons.Vibration
        DeviceActionType.STATUS_LOUD -> GhostTalkIcons.VolumeUp
        DeviceActionType.SEND_MESSAGE -> Icons.AutoMirrored.Filled.Send
        DeviceActionType.READ_BATTERY -> GhostTalkIcons.BatteryFull
        DeviceActionType.READ_DATE -> GhostTalkIcons.DateRange
        DeviceActionType.READ_TIME -> GhostTalkIcons.AccessTime
        DeviceActionType.READ_CALENDAR_ENTRIES -> GhostTalkIcons.DateRange
        DeviceActionType.TOGGLE_SCANNING -> Icons.Default.Refresh
        DeviceActionType.START_CALL -> Icons.Default.Phone
        DeviceActionType.INSTALL_UPDATE -> GhostTalkIcons.CloudDownload
        DeviceActionType.START_SYNC -> Icons.Default.Refresh
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun DeviceActionFields(
    selectedType: DeviceActionType,
    onTypeSelected: (DeviceActionType) -> Unit,
    volumeValue: String? = null,
    onVolumeValueChange: (String) -> Unit = {},
    contactName: String? = null,
    contactPhone: String? = null,
    onContactSelected: (name: String, phone: String) -> Unit = { _, _ -> },
    messageText: String? = null,
    onMessageTextChange: (String) -> Unit = {},
    includeWeekday: Boolean = false,
    onIncludeWeekdayChange: (Boolean) -> Unit = {},
    prefixText: String = "",
    onPrefixTextChange: (String) -> Unit = {},
    suffixText: String = "",
    onSuffixTextChange: (String) -> Unit = {},
    offsetValue: String = "0",
    onOffsetValueChange: (String) -> Unit = {},
    ignoreEmojis: Boolean = false,
    onIgnoreEmojisChange: (Boolean) -> Unit = {},
    onAutoSave: () -> Unit = {},
    onlyShowSelector: Boolean = false,
    onlyShowConfig: Boolean = false
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    var expandedType by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    val types = listOf(
        DeviceActionType.READ_NOTIFICATIONS to stringResource(R.string.button_action_notification),
        DeviceActionType.CLEAR_NOTIFICATIONS to stringResource(R.string.button_action_clear_notifications),
        DeviceActionType.MEDIA_PLAY_PAUSE to stringResource(R.string.button_device_control_media_play_pause),
        DeviceActionType.MEDIA_NEXT to stringResource(R.string.button_device_control_media_next),
        DeviceActionType.MEDIA_PREVIOUS to stringResource(R.string.button_device_control_media_previous),
        DeviceActionType.VOLUME_MEDIA to stringResource(R.string.volume_media),
        DeviceActionType.VOLUME_NOTIFICATION to stringResource(R.string.volume_notification),
        DeviceActionType.VOLUME_ALARM to stringResource(R.string.volume_alarm),
        DeviceActionType.VOLUME_CALL to stringResource(R.string.volume_call),
        DeviceActionType.STATUS_SILENT to stringResource(R.string.status_silent),
        DeviceActionType.STATUS_VIBRATE to stringResource(R.string.status_vibrate),
        DeviceActionType.STATUS_LOUD to stringResource(R.string.status_loud),
        DeviceActionType.SEND_MESSAGE to stringResource(R.string.action_send_message),
        DeviceActionType.READ_BATTERY to stringResource(R.string.button_device_control_battery),
        DeviceActionType.READ_DATE to stringResource(R.string.button_device_control_date),
        DeviceActionType.READ_TIME to stringResource(R.string.button_device_control_time),
        DeviceActionType.READ_CALENDAR_ENTRIES to stringResource(R.string.button_device_control_calendar),
        DeviceActionType.TOGGLE_SCANNING to stringResource(R.string.button_device_control_toggle_scanning),
        DeviceActionType.START_CALL to stringResource(R.string.action_start_call),
        DeviceActionType.INSTALL_UPDATE to stringResource(R.string.button_device_control_install_update),
        DeviceActionType.START_SYNC to stringResource(R.string.button_device_control_start_sync)
    )

    val currentLabel = types.find { it.first == selectedType }?.second ?: types.first().second

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        if (!onlyShowConfig) {
            Text(stringResource(R.string.button_device_control_type_label), style = MaterialTheme.typography.labelMedium)

            ExposedDropdownMenuBox(
                expanded = expandedType,
                onExpandedChange = { expandedType = !expandedType }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = currentLabel,
                    onValueChange = { },
                    leadingIcon = {
                        Icon(
                            imageVector = getDeviceActionIcon(selectedType),
                            contentDescription = null
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedType,
                    onDismissRequest = { expandedType = false }
                ) {
                    types.forEach { (type, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = getDeviceActionIcon(type),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                onTypeSelected(type)
                                expandedType = false
                                onAutoSave()
                                
                                // Permission check for Notification Access
                                if (type == DeviceActionType.READ_NOTIFICATIONS || type == DeviceActionType.CLEAR_NOTIFICATIONS) {
                                    if (!androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
                                        showNotificationPermissionDialog = true
                                    }
                                }

                                // Permission check for Silent mode
                                if (type == DeviceActionType.STATUS_SILENT) {
                                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    if (!nm.isNotificationPolicyAccessGranted) {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                }

                                // Permission check for Messaging
                                if (type == DeviceActionType.SEND_MESSAGE) {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
                                    )
                                }

                                // Permission check for Call
                                if (type == DeviceActionType.START_CALL) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.CALL_PHONE,
                                            Manifest.permission.READ_CONTACTS,
                                            Manifest.permission.READ_CALL_LOG
                                        )
                                    )
                                }

                                // Permission check for Calendar
                                if (type == DeviceActionType.READ_CALENDAR_ENTRIES) {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.READ_CALENDAR)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        if (!onlyShowSelector) {
            // Volume parameters
            if (selectedType.name.startsWith("VOLUME_")) {
                OutlinedTextField(
                    value = volumeValue ?: "50",
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            onVolumeValueChange(newValue)
                        }
                    },
                    label = { Text(stringResource(R.string.volume_label)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { 
                        if (!it.isFocused) onAutoSave()
                    }
                )
            }

            // Notification parameters (Target App Selection)
            if (selectedType == DeviceActionType.READ_NOTIFICATIONS || selectedType == DeviceActionType.CLEAR_NOTIFICATIONS) {
                NotificationAppPicker(
                    selectedPackage = contactPhone,
                    selectedLabel = contactName,
                    onAppSelected = { label, pkg ->
                        onContactSelected(label, pkg)
                        onAutoSave()
                    }
                )
            }

            if (selectedType == DeviceActionType.READ_NOTIFICATIONS) {
                com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem(
                    label = stringResource(R.string.button_device_control_ignore_emojis_label),
                    checked = ignoreEmojis,
                    onCheckedChange = onIgnoreEmojisChange,
                    onValueChangeFinished = onAutoSave
                )
            }

            // Messaging parameters
            if (selectedType == DeviceActionType.SEND_MESSAGE) {
                MessagingFields(
                    contactName = contactName ?: stringResource(R.string.contact_picker_title),
                    contactPhone = contactPhone ?: "",
                    onContactSelected = onContactSelected,
                    messageText = messageText ?: "",
                    onMessageTextChange = onMessageTextChange,
                    onAutoSave = onAutoSave
                )
            }

            // Call parameters
            if (selectedType == DeviceActionType.START_CALL) {
                CallFields(
                    contactName = contactName ?: stringResource(R.string.contact_picker_title),
                    contactPhone = contactPhone ?: "",
                    onContactSelected = onContactSelected,
                    onAutoSave = onAutoSave
                )
            }

            // Date & Time parameters
            if (selectedType == DeviceActionType.READ_DATE || selectedType == DeviceActionType.READ_TIME || selectedType == DeviceActionType.READ_CALENDAR_ENTRIES) {
                
                // Preset Suggestion Chips
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedType == DeviceActionType.READ_TIME) {
                        SuggestionChip(
                            onClick = {
                                onPrefixTextChange(context.getString(R.string.device_control_time_prefix_std))
                                onSuffixTextChange(context.getString(R.string.device_control_time_suffix_std))
                                onOffsetValueChange("0")
                                onAutoSave()
                            },
                            label = { Text(stringResource(R.string.device_control_time_preset_standard)) }
                        )
                        SuggestionChip(
                            onClick = {
                                onPrefixTextChange("")
                                onSuffixTextChange("")
                                onOffsetValueChange("0")
                                onAutoSave()
                            },
                            label = { Text(stringResource(R.string.device_control_time_preset_short)) }
                        )
                        SuggestionChip(
                            onClick = {
                                onPrefixTextChange(context.getString(R.string.device_control_time_prefix_plus5))
                                onSuffixTextChange(context.getString(R.string.device_control_time_suffix_std))
                                onOffsetValueChange("5")
                                onAutoSave()
                            },
                            label = { Text(stringResource(R.string.device_control_time_preset_plus5)) }
                        )
                        SuggestionChip(
                            onClick = {
                                onPrefixTextChange(context.getString(R.string.device_control_time_prefix_minus5))
                                onSuffixTextChange(context.getString(R.string.device_control_time_suffix_std))
                                onOffsetValueChange("-5")
                                onAutoSave()
                            },
                            label = { Text(stringResource(R.string.device_control_time_preset_minus5)) }
                        )
                    } else if (selectedType == DeviceActionType.READ_DATE) {
                        SuggestionChip(
                            onClick = {
                                onPrefixTextChange(context.getString(R.string.device_control_date_prefix_weekday_date))
                                onSuffixTextChange("")
                                onIncludeWeekdayChange(true)
                                onOffsetValueChange("0")
                                onAutoSave()
                            },
                            label = { Text(stringResource(R.string.device_control_date_preset_weekday_date)) }
                        )
                        SuggestionChip(
                            onClick = {
                                onPrefixTextChange(context.getString(R.string.device_control_date_prefix_only_date))
                                onSuffixTextChange("")
                                onIncludeWeekdayChange(false)
                                onOffsetValueChange("0")
                                onAutoSave()
                            },
                            label = { Text(stringResource(R.string.device_control_date_preset_only_date)) }
                        )
                        SuggestionChip(
                            onClick = {
                                onPrefixTextChange(context.getString(R.string.device_control_date_prefix_tomorrow))
                                onSuffixTextChange("")
                                onIncludeWeekdayChange(true)
                                onOffsetValueChange("1")
                                onAutoSave()
                            },
                            label = { Text(stringResource(R.string.device_control_date_preset_tomorrow)) }
                        )
                        SuggestionChip(
                            onClick = {
                                onPrefixTextChange(context.getString(R.string.device_control_date_prefix_yesterday))
                                onSuffixTextChange("")
                                onIncludeWeekdayChange(true)
                                onOffsetValueChange("-1")
                                onAutoSave()
                            },
                            label = { Text(stringResource(R.string.device_control_date_preset_yesterday)) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
                ) {
                    OutlinedTextField(
                        value = prefixText,
                        onValueChange = onPrefixTextChange,
                        label = { Text(stringResource(R.string.button_device_control_prefix_label)) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f).onFocusChanged { 
                            if (!it.isFocused) onAutoSave()
                        }
                    )

                    OutlinedTextField(
                        value = suffixText,
                        onValueChange = onSuffixTextChange,
                        label = { Text(stringResource(R.string.button_device_control_suffix_label)) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f).onFocusChanged { 
                            if (!it.isFocused) onAutoSave()
                        }
                    )
                }

                if (selectedType == DeviceActionType.READ_DATE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
                    ) {
                        Box(modifier = Modifier.weight(1.2f)) {
                            com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem(
                                label = stringResource(R.string.button_device_control_weekday_label),
                                checked = includeWeekday,
                                onCheckedChange = onIncludeWeekdayChange,
                                onValueChangeFinished = onAutoSave
                            )
                        }
                        
                        OutlinedTextField(
                            value = offsetValue,
                            onValueChange = { if (it.isEmpty() || it == "-" || it.all { c -> c.isDigit() || c == '-' }) onOffsetValueChange(it) },
                            label = { Text(stringResource(R.string.button_device_control_offset_days_label)) },
                            shape = MaterialTheme.shapes.large,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.8f).onFocusChanged { 
                                if (!it.isFocused) onAutoSave()
                            }
                        )
                    }
                } else if (selectedType == DeviceActionType.READ_TIME) {
                    OutlinedTextField(
                        value = offsetValue,
                        onValueChange = { if (it.isEmpty() || it == "-" || it.all { c -> c.isDigit() || c == '-' }) onOffsetValueChange(it) },
                        label = { Text(stringResource(R.string.button_device_control_offset_minutes_label)) },
                        shape = MaterialTheme.shapes.large,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { 
                            if (!it.isFocused) onAutoSave()
                        }
                    )
                } else {
                    // Calendar Entries Count
                    OutlinedTextField(
                        value = offsetValue,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) onOffsetValueChange(it) },
                        label = { Text(stringResource(R.string.button_device_control_calendar_count_label)) },
                        placeholder = { Text("1") },
                        shape = MaterialTheme.shapes.large,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { 
                            if (!it.isFocused) onAutoSave()
                        }
                    )
                }

                val appointmentsPlaceholder = stringResource(R.string.device_control_calendar_appointments_placeholder)
                val offsetInt = offsetValue.toIntOrNull() ?: 0
                val count = offsetInt.coerceAtLeast(1)
                val countSuffix = pluralStringResource(R.plurals.device_control_calendar_read_count_suffix, count, count)

                // Spoken text preview
                val previewText = remember(selectedType, prefixText, suffixText, includeWeekday, offsetValue, appointmentsPlaceholder, countSuffix) {
                    try {
                        val calendar = java.util.Calendar.getInstance()
                        if (selectedType == DeviceActionType.READ_TIME) {
                            if (offsetInt != 0) {
                                calendar.add(java.util.Calendar.MINUTE, offsetInt)
                            }
                            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            val timeString = sdf.format(calendar.time)
                            val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                            val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                            "$prefix$timeString$suffix"
                        } else if (selectedType == DeviceActionType.READ_DATE) {
                            if (offsetInt != 0) {
                                calendar.add(java.util.Calendar.DAY_OF_YEAR, offsetInt)
                            }
                            val pattern = if (includeWeekday) "EEEE, dd. MMMM yyyy" else "dd. MMMM yyyy"
                            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                            val dateString = sdf.format(calendar.time)
                            val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                            val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                            "$prefix$dateString$suffix"
                        } else if (selectedType == DeviceActionType.READ_CALENDAR_ENTRIES) {
                            val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                            val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                            "$prefix$appointmentsPlaceholder$suffix$countSuffix"
                        } else {
                            ""
                        }
                    } catch (e: Exception) {
                        ""
                    }
                }

                if (previewText.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
                            Text(
                                text = "Gesprochener Text (Vorschau):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(dimensions.paddingSmall))
                            Text(
                                text = "\"$previewText\"",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }

        if (showNotificationPermissionDialog && !onlyShowConfig) {
            AlertDialog(
                onDismissRequest = { showNotificationPermissionDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.notification_permission_dialog_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.notification_permission_dialog_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNotificationPermissionDialog = false
                            val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(R.string.notification_permission_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showNotificationPermissionDialog = false }
                    ) {
                        Text(stringResource(R.string.notification_permission_dialog_dismiss))
                    }
                }
            )
        }
    }
}
