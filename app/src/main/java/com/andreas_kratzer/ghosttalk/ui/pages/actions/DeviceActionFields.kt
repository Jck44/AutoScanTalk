package com.andreas_kratzer.ghosttalk.ui.pages.actions

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.ui.res.pluralStringResource
import androidx.core.graphics.createBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.vector.ImageVector
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons

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
    onAutoSave: () -> Unit = {}
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
        DeviceActionType.TOGGLE_SCANNING to stringResource(R.string.button_device_control_toggle_scanning)
    )

    val currentLabel = types.find { it.first == selectedType }?.second ?: types.first().second

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
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

        // Messaging parameters
        if (selectedType == DeviceActionType.SEND_MESSAGE) {
            MessagingFields(
                contactName = contactName ?: stringResource(R.string.contact_picker_title),
                onContactSelected = onContactSelected,
                messageText = messageText ?: "",
                onMessageTextChange = onMessageTextChange,
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

        if (showNotificationPermissionDialog) {
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

@Composable
fun MessagingFields(
    contactName: String,
    onContactSelected: (String, String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onAutoSave: () -> Unit = {}
) {
    val dimensions = LocalDimensions.current
    val context = LocalContext.current

    val contactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            )
            context.contentResolver.query(it, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                    
                    // Now get the first phone number for this contact
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(id),
                        null
                    )?.use { phoneCursor ->
                        if (phoneCursor.moveToFirst()) {
                            val phone = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                            onContactSelected(name, phone)
                        } else {
                            onContactSelected(name, "")
                        }
                    }
                }
            }
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Proactive check
    fun checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    LaunchedEffect(Unit) {
        checkSmsPermission()
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        // Contact Selection
        OutlinedTextField(
            value = contactName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.contact_picker_title)) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(
                    onClick = { 
                        checkSmsPermission()
                        contactLauncher.launch(null)
                    }
                ) {
                    Icon(
                        imageVector = com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.Edit,
                        contentDescription = stringResource(R.string.contact_picker_title)
                    )
                }
            }
        )

        // Message Text (Filtering Emojis)
        OutlinedTextField(
            value = messageText,
            onValueChange = { newValue ->
                // Filter out Emojis / Surrogate pairs / Non-BMP characters
                val filtered = newValue.filter { char ->
                    char.code <= 0xFFFF && !char.isSurrogate()
                }
                onMessageTextChange(filtered)
            },
            label = { Text(stringResource(R.string.message_emojis_not_supported)) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if (!it.isFocused) onAutoSave()
            },
            supportingText = {
                Text(stringResource(R.string.message_emojis_hint))
            }
        )
    }
}

private data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: android.graphics.Bitmap?
)

private fun android.graphics.drawable.Drawable.toBitmapOrNull(): android.graphics.Bitmap? {
    try {
        val bitmap = createBitmap(
            intrinsicWidth.coerceAtLeast(1),
            intrinsicHeight.coerceAtLeast(1)
        )
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    } catch (e: Exception) {
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationAppPicker(
    selectedPackage: String?,
    selectedLabel: String?,
    onAppSelected: (String, String) -> Unit
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    var expanded by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val appsList = resolveInfos.mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = try {
                    resolveInfo.loadIcon(pm)?.toBitmapOrNull()
                } catch (e: Exception) {
                    null
                }
                if (packageName.isNotEmpty()) InstalledAppInfo(packageName, label, icon) else null
            }
            val sortedApps = appsList.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            withContext(Dispatchers.Main) {
                installedApps = sortedApps
            }
        }
    }

    val selectedPackages = remember(selectedPackage) {
        selectedPackage?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    val preferredAppsStr = stringResource(R.string.device_control_notifications_preferred_apps)
    val appsSelectedStr = pluralStringResource(
        R.plurals.device_control_notifications_apps_selected,
        selectedPackages.size,
        selectedPackages.size
    )

    val displayLabels = remember(selectedPackages, installedApps, selectedLabel) {
        if (selectedPackages.isEmpty() || installedApps.isEmpty()) {
            emptyList()
        } else {
            val selectedLabels = selectedPackages.mapNotNull { pkg ->
                installedApps.find { it.packageName == pkg }?.label
            }.filter { it.isNotEmpty() && !it.contains(".") }
            
            if (selectedLabels.size == selectedPackages.size) {
                selectedLabels
            } else {
                val splitLabels = selectedLabel?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && !it.contains(".") } ?: emptyList()
                if (splitLabels.size == selectedPackages.size) {
                    splitLabels
                } else {
                    selectedPackages.map { pkg ->
                        installedApps.find { it.packageName == pkg }?.label ?: pkg
                    }
                }
            }
        }
    }

    val firstThree = remember(displayLabels) {
        displayLabels.take(3).joinToString(", ")
    }
    val remaining = (displayLabels.size - 3).coerceAtLeast(0)

    val appsMoreFormatStr = pluralStringResource(
        R.plurals.device_control_notifications_apps_more_format,
        remaining,
        firstThree,
        remaining
    )

    val currentLabel = when {
        selectedPackages.isEmpty() -> preferredAppsStr
        installedApps.isEmpty() -> appsSelectedStr
        displayLabels.size <= 3 -> displayLabels.joinToString(", ")
        else -> appsMoreFormatStr
    }

    val singleSelectedApp = remember(selectedPackages, installedApps) {
        if (selectedPackages.size == 1) {
            installedApps.find { it.packageName == selectedPackages.first() }
        } else null
    }

    val onToggleApp: (String, String) -> Unit = { pkg, label ->
        val updated = selectedPackages.toMutableSet()
        if (updated.contains(pkg)) {
            updated.remove(pkg)
        } else {
            updated.add(pkg)
        }
        if (updated.isEmpty()) {
            onAppSelected("", "")
        } else {
            val newPhone = updated.joinToString(",")
            val newName = updated.map { p ->
                installedApps.find { it.packageName == p }?.label ?: p
            }.joinToString(", ")
            onAppSelected(newName, newPhone)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        Text(stringResource(R.string.device_control_notifications_read_from_app_label), style = MaterialTheme.typography.labelMedium)
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = currentLabel,
                onValueChange = { },
                leadingIcon = {
                    if (singleSelectedApp?.icon != null) {
                        Image(
                            bitmap = singleSelectedApp.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = getDeviceActionIcon(DeviceActionType.READ_NOTIFICATIONS),
                            contentDescription = null
                        )
                    }
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // Option for "Bevorzugte Apps (Einstellungen)"
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.device_control_notifications_preferred_apps)) },
                    leadingIcon = {
                        Icon(
                            imageVector = getDeviceActionIcon(DeviceActionType.READ_NOTIFICATIONS),
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        androidx.compose.material3.Checkbox(
                            checked = selectedPackages.isEmpty(),
                            onCheckedChange = {
                                if (selectedPackages.isNotEmpty()) {
                                    onAppSelected("", "")
                                }
                            }
                        )
                    },
                    onClick = {
                        onAppSelected("", "")
                    }
                )
                
                if (installedApps.isNotEmpty()) {
                    androidx.compose.material3.HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                val allPkgs = installedApps.map { it.packageName }.toSet()
                                val newPhone = allPkgs.joinToString(",")
                                val newName = allPkgs.map { p ->
                                    installedApps.find { it.packageName == p }?.label ?: p
                                }.joinToString(", ")
                                onAppSelected(newName, newPhone)
                            }
                        ) {
                            Text(stringResource(R.string.settings_notifications_select_all))
                        }
                        TextButton(
                            onClick = {
                                onAppSelected("", "")
                            }
                        ) {
                            Text(stringResource(R.string.settings_notifications_deselect_all))
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(bottom = dimensions.paddingSmall))
                }
                
                installedApps.forEach { app ->
                    DropdownMenuItem(
                        text = { Text(app.label) },
                        leadingIcon = {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = getDeviceActionIcon(DeviceActionType.READ_NOTIFICATIONS),
                                    contentDescription = null
                                )
                            }
                        },
                        trailingIcon = {
                            androidx.compose.material3.Checkbox(
                                checked = selectedPackages.contains(app.packageName),
                                onCheckedChange = { onToggleApp(app.packageName, app.label) }
                            )
                        },
                        onClick = {
                            onToggleApp(app.packageName, app.label)
                        }
                    )
                }
            }
        }
    }
}
