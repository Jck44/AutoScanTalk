package com.andreas_kratzer.ghosttalk.ui.pages

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationActionFields(
    navigateToPageId: String,
    onPageSelected: (String) -> Unit,
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    onNavigateToPage: ((String) -> Unit)?,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)?,
    onBeforeCreatePage: (() -> Boolean)? = null,

    onDismissDialog: () -> Unit
) {
    var expandedPageSelect by remember { mutableStateOf(false) }
    var pageSearchQuery by remember { mutableStateOf("") }
    val showAddPageDialogState = remember { mutableStateOf(false) }

    val dimensions = LocalDimensions.current
    val focusManager = LocalFocusManager.current

    val filteredPages = remember(pageSearchQuery, availablePages) {
        val trimmedQuery = pageSearchQuery.trim()
        if (trimmedQuery.isBlank()) availablePages
        else availablePages.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)) {
        ExposedDropdownMenuBox(
            expanded = expandedPageSelect,
            onExpandedChange = { expandedPageSelect = !expandedPageSelect }
        ) {
            val selectedPageName = availablePages.find { it.id == navigateToPageId }?.name 
                ?: stringResource(R.string.button_no_page_selected)
            
            OutlinedTextField(
                value = if (expandedPageSelect) pageSearchQuery else selectedPageName,
                onValueChange = { if (expandedPageSelect) pageSearchQuery = it },
                readOnly = !expandedPageSelect,
                label = { Text(stringResource(R.string.button_target_page_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPageSelect) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
            )
            
            ExposedDropdownMenu(
                expanded = expandedPageSelect,
                onDismissRequest = { 
                    expandedPageSelect = false
                    pageSearchQuery = ""
                }
            ) {
                filteredPages.forEach { pageOption ->
                    DropdownMenuItem(
                        text = { Text(pageOption.name, style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            onPageSelected(pageOption.id)
                            focusManager.clearFocus()
                            expandedPageSelect = false
                            pageSearchQuery = ""
                        }
                    )
                }
                if (filteredPages.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.page_none_found), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { },
                        enabled = false
                    )
                }
            }
        }

        if (navigateToPageId.isNotEmpty() && onNavigateToPage != null) {
            OutlinedButton(
                onClick = {
                    onDismissDialog()
                    onNavigateToPage(navigateToPageId)
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.page_manage_target))
            }
        }

        if (onCreatePage != null) {
            OutlinedButton(
                onClick = { 
                    if (onBeforeCreatePage == null || onBeforeCreatePage()) {
                        showAddPageDialogState.value = true 
                    }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(stringResource(R.string.page_create_new_target))
            }
        }
    }

    if (showAddPageDialogState.value) {
        AddPageDialog(
            templates = templates,
            onDismiss = { showAddPageDialogState.value = false },
            onConfirm = { name, rows, cols, templateId ->
                onCreatePage?.invoke(name, rows, cols, templateId) { newId ->
                    onPageSelected(newId)
                    showAddPageDialogState.value = false
                }
            }
        )
    }
}

@Composable
fun GeminiActionFields(
    prompt: String,
    onPromptChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChanged,
        label = { Text(stringResource(R.string.button_gemini_prompt_field)) },
        placeholder = { Text(stringResource(R.string.button_gemini_prompt_hint)) },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiNanoActionFields(
    selectedIntent: String,
    onIntentSelected: (String) -> Unit
) {
    val dimensions = LocalDimensions.current
    var expanded by remember { mutableStateOf(false) }

    val intentAlarm = "alarm"

    val intentLabels = mapOf(
        intentAlarm to stringResource(R.string.button_gemini_nano_intent_alarm)
    )

    val currentLabel = intentLabels[selectedIntent] ?: stringResource(R.string.button_gemini_nano_intent_alarm)

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        Text(stringResource(R.string.button_gemini_nano_intent_label), style = MaterialTheme.typography.labelMedium)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = currentLabel,
                onValueChange = { },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                intentLabels.forEach { (intent, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onIntentSelected(intent)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RankActionFields(
    rank: String,
    onRankChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = rank,
        onValueChange = { newValue ->
            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                onRankChanged(newValue)
            }
        },
        label = { Text(stringResource(R.string.button_smart_prediction_rank_label)) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlDeviceActionFields(
    selectedType: DeviceActionType,
    onTypeSelected: (DeviceActionType) -> Unit,
    volumeValue: String? = null,
    onVolumeValueChange: (String) -> Unit = {},
    contactName: String? = null,
    onContactSelected: (name: String, phone: String) -> Unit = { _, _ -> },
    messageText: String? = null,
    onMessageTextChange: (String) -> Unit = {}
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
        DeviceActionType.READ_TIME to stringResource(R.string.button_device_control_time)
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
                        }
                    )
                }
            }
        }

        // Volume parameters
        if (selectedType.name.startsWith("VOLUME_")) {
            OutlinedTextField(
                value = volumeValue ?: "50",
                onValueChange = onVolumeValueChange,
                label = { Text(stringResource(R.string.volume_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Messaging parameters
        if (selectedType == DeviceActionType.SEND_MESSAGE) {
            MessagingFields(
                contactName = contactName ?: stringResource(R.string.contact_picker_title),
                onContactSelected = onContactSelected,
                messageText = messageText ?: "",
                onMessageTextChange = onMessageTextChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingFields(
    contactName: String,
    onContactSelected: (String, String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit
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
        OutlinedButton(
            onClick = { 
                checkSmsPermission()
                contactLauncher.launch(null)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(contactName)
        }

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
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(stringResource(R.string.message_emojis_hint))
            }
        )
    }
}
