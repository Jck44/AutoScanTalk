package com.andreas_kratzer.ghosttalk.ui.pages.actions

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
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
        DeviceActionType.SEND_LAST_SPOKEN_SMS -> Icons.AutoMirrored.Filled.Send
        DeviceActionType.READ_BATTERY -> GhostTalkIcons.BatteryFull
        DeviceActionType.READ_DATE -> GhostTalkIcons.DateRange
        DeviceActionType.READ_TIME -> GhostTalkIcons.AccessTime
        DeviceActionType.READ_CALENDAR_ENTRIES -> GhostTalkIcons.DateRange
        DeviceActionType.TOGGLE_SCANNING -> Icons.Default.Refresh
        DeviceActionType.START_CALL -> Icons.Default.Phone
        DeviceActionType.INSTALL_UPDATE -> GhostTalkIcons.CloudDownload
        DeviceActionType.START_SYNC -> Icons.Default.Refresh
        DeviceActionType.VOLUME_IN_APP_TTS -> GhostTalkIcons.VolumeUp
        DeviceActionType.VOLUME_IN_APP_CUES -> GhostTalkIcons.VolumeUp
        DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS -> GhostTalkIcons.Notifications
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
        DeviceActionType.SEND_LAST_SPOKEN_SMS to stringResource(R.string.device_action_send_last_spoken_sms),
        DeviceActionType.READ_BATTERY to stringResource(R.string.button_device_control_battery),
        DeviceActionType.READ_DATE to stringResource(R.string.button_device_control_date),
        DeviceActionType.READ_TIME to stringResource(R.string.button_device_control_time),
        DeviceActionType.READ_CALENDAR_ENTRIES to stringResource(R.string.button_device_control_calendar),
        DeviceActionType.TOGGLE_SCANNING to stringResource(R.string.button_device_control_toggle_scanning),
        DeviceActionType.START_CALL to stringResource(R.string.action_start_call),
        DeviceActionType.START_SYNC to stringResource(R.string.button_device_control_start_sync),
        DeviceActionType.VOLUME_IN_APP_TTS to stringResource(R.string.volume_in_app_tts),
        DeviceActionType.VOLUME_IN_APP_CUES to stringResource(R.string.volume_in_app_cues),
        DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS to stringResource(R.string.button_action_toggle_auto_read_notifications)
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
                                if (type == DeviceActionType.READ_NOTIFICATIONS || 
                                    type == DeviceActionType.CLEAR_NOTIFICATIONS ||
                                    type == DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS) {
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
                                if (type == DeviceActionType.SEND_MESSAGE || type == DeviceActionType.SEND_LAST_SPOKEN_SMS) {
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
                VolumeActionFields(
                    volumeValue = volumeValue,
                    onVolumeValueChange = onVolumeValueChange,
                    onAutoSave = onAutoSave
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
            if (selectedType == DeviceActionType.SEND_MESSAGE || selectedType == DeviceActionType.SEND_LAST_SPOKEN_SMS) {
                MessagingFields(
                    contactName = contactName ?: stringResource(R.string.contact_picker_title),
                    contactPhone = contactPhone ?: "",
                    onContactSelected = onContactSelected,
                    messageText = messageText ?: "",
                    onMessageTextChange = onMessageTextChange,
                    onAutoSave = onAutoSave,
                    showTextField = selectedType == DeviceActionType.SEND_MESSAGE
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
                DateTimeReadFields(
                    selectedType = selectedType,
                    includeWeekday = includeWeekday,
                    onIncludeWeekdayChange = onIncludeWeekdayChange,
                    prefixText = prefixText,
                    onPrefixTextChange = onPrefixTextChange,
                    suffixText = suffixText,
                    onSuffixTextChange = onSuffixTextChange,
                    offsetValue = offsetValue,
                    onOffsetValueChange = onOffsetValueChange,
                    onAutoSave = onAutoSave
                )
            }
        }

        if (showNotificationPermissionDialog && !onlyShowConfig) {
            GhostTalkDialog(
                title = stringResource(R.string.notification_permission_dialog_title),
                onDismiss = { showNotificationPermissionDialog = false },
                confirmText = stringResource(R.string.notification_permission_dialog_confirm),
                onConfirm = {
                    showNotificationPermissionDialog = false
                    val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                },
                dismissText = stringResource(R.string.notification_permission_dialog_dismiss)
            ) {
                Text(
                    text = stringResource(R.string.notification_permission_dialog_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
