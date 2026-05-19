package com.andreas_kratzer.ghosttalk.ui.pages.actions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActionFields(
    selectedType: DeviceActionType,
    onTypeSelected: (DeviceActionType) -> Unit,
    volumeValue: String? = null,
    onVolumeValueChange: (String) -> Unit = {},
    contactName: String? = null,
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    val types = listOf(
        DeviceActionType.READ_NOTIFICATIONS to stringResource(R.string.button_action_notification),
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
                        onClick = {
                            onTypeSelected(type)
                            expandedType = false
                            onAutoSave()
                            
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
                            onPrefixTextChange("Es ist")
                            onSuffixTextChange("Uhr")
                            onOffsetValueChange("0")
                            onAutoSave()
                        },
                        label = { Text("Standard (\"Es ist ... Uhr\")") }
                    )
                    SuggestionChip(
                        onClick = {
                            onPrefixTextChange("")
                            onSuffixTextChange("")
                            onOffsetValueChange("0")
                            onAutoSave()
                        },
                        label = { Text("Kurz (\"14:30\")") }
                    )
                    SuggestionChip(
                        onClick = {
                            onPrefixTextChange("In fünf Minuten ist es")
                            onSuffixTextChange("Uhr")
                            onOffsetValueChange("5")
                            onAutoSave()
                        },
                        label = { Text("+5 Min.") }
                    )
                    SuggestionChip(
                        onClick = {
                            onPrefixTextChange("Vor fünf Minuten war es")
                            onSuffixTextChange("Uhr")
                            onOffsetValueChange("-5")
                            onAutoSave()
                        },
                        label = { Text("-5 Min.") }
                    )
                } else if (selectedType == DeviceActionType.READ_DATE) {
                    SuggestionChip(
                        onClick = {
                            onPrefixTextChange("Heute ist")
                            onSuffixTextChange("")
                            onIncludeWeekdayChange(true)
                            onOffsetValueChange("0")
                            onAutoSave()
                        },
                        label = { Text("Wochentag & Datum") }
                    )
                    SuggestionChip(
                        onClick = {
                            onPrefixTextChange("Heute ist der")
                            onSuffixTextChange("")
                            onIncludeWeekdayChange(false)
                            onOffsetValueChange("0")
                            onAutoSave()
                        },
                        label = { Text("Nur Datum") }
                    )
                    SuggestionChip(
                        onClick = {
                            onPrefixTextChange("Morgen ist")
                            onSuffixTextChange("")
                            onIncludeWeekdayChange(true)
                            onOffsetValueChange("1")
                            onAutoSave()
                        },
                        label = { Text("Morgen") }
                    )
                    SuggestionChip(
                        onClick = {
                            onPrefixTextChange("Gestern war")
                            onSuffixTextChange("")
                            onIncludeWeekdayChange(true)
                            onOffsetValueChange("-1")
                            onAutoSave()
                        },
                        label = { Text("Gestern") }
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

            // Spoken text preview
            val previewText = remember(selectedType, prefixText, suffixText, includeWeekday, offsetValue) {
                try {
                    val calendar = java.util.Calendar.getInstance()
                    val offsetInt = offsetValue.toIntOrNull() ?: 0
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
                        val count = offsetInt.coerceAtLeast(1)
                        val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                        val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                        "$prefix[Termine]$suffix (Liest $count Kalendereinträge vor)"
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
