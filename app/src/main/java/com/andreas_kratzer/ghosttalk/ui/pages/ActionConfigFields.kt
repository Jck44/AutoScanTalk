package com.andreas_kratzer.ghosttalk.ui.pages

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.getDisplayName

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
    onPromptChanged: (String) -> Unit,
    availableTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool> = emptyList()
) {
    val dimensions = LocalDimensions.current
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChanged,
            label = { Text(stringResource(R.string.button_gemini_prompt_field)) },
            placeholder = { Text(stringResource(R.string.button_gemini_prompt_hint)) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        )

        if (availableTools.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.button_gemini_available_tools_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                for (tool in availableTools) {
                    ToolItem(tool)
                }
            }
        } else {
             Text(
                text = stringResource(R.string.button_gemini_no_tools_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ToolItem(tool: com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (tool.description.isNotBlank()) {
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
@Composable
fun GeminiNanoActionFields() {
    val dimensions = LocalDimensions.current
    Text(
        text = "Gemini Nano arbeitet aktuell ohne externe Tools.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun GeminiVisionActionFields(
    prompt: String,
    useCloud: Boolean,
    isCloudEnabled: Boolean = true,
    playShutterSound: Boolean = true,
    onPromptChanged: (String) -> Unit,
    onUseCloudChanged: (Boolean) -> Unit,
    onPlayShutterSoundChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, R.string.permission_camera_denied, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val dimensions = LocalDimensions.current
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChanged,
            label = { Text(stringResource(R.string.button_gemini_vision_prompt_label)) },
            placeholder = { Text(stringResource(R.string.button_gemini_vision_prompt_hint)) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        )

        com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem(
            label = stringResource(R.string.button_gemini_vision_use_cloud_label),
            checked = useCloud,
            enabled = isCloudEnabled,
            onCheckedChange = onUseCloudChanged
        )
        
        com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem(
            label = stringResource(R.string.button_gemini_vision_shutter_sound_label),
            checked = playShutterSound,
            onCheckedChange = onPlayShutterSoundChanged
        )
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
    onMessageTextChange: (String) -> Unit = {},
    // New fields
    includeWeekday: Boolean = false,
    onIncludeWeekdayChange: (Boolean) -> Unit = {},
    prefixText: String = "",
    onPrefixTextChange: (String) -> Unit = {},
    suffixText: String = "",
    onSuffixTextChange: (String) -> Unit = {},
    offsetValue: String = "0",
    onOffsetValueChange: (String) -> Unit = {}
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

        // Date & Time parameters
        if (selectedType == DeviceActionType.READ_DATE || selectedType == DeviceActionType.READ_TIME) {
            OutlinedTextField(
                value = prefixText,
                onValueChange = onPrefixTextChange,
                label = { Text(stringResource(R.string.button_device_control_prefix_label)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = suffixText,
                onValueChange = onSuffixTextChange,
                label = { Text(stringResource(R.string.button_device_control_suffix_label)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )

            if (selectedType == DeviceActionType.READ_DATE) {
                com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem(
                    label = stringResource(R.string.button_device_control_weekday_label),
                    checked = includeWeekday,
                    onCheckedChange = onIncludeWeekdayChange
                )
                
                OutlinedTextField(
                    value = offsetValue,
                    onValueChange = { if (it.isEmpty() || it == "-" || it.all { c -> c.isDigit() || c == '-' }) onOffsetValueChange(it) },
                    label = { Text(stringResource(R.string.button_device_control_offset_days_label)) },
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = offsetValue,
                    onValueChange = { if (it.isEmpty() || it == "-" || it.all { c -> c.isDigit() || c == '-' }) onOffsetValueChange(it) },
                    label = { Text(stringResource(R.string.button_device_control_offset_minutes_label)) },
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
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

@Composable
fun WeatherActionFields() {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (!granted) {
            Toast.makeText(context, R.string.permission_location_denied_weather, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            permissionLauncher.launch(permissions)
        }
    }
}

@Composable
fun ActionConfigFields(
    selectedActionType: String,
    pages: List<Page>,
    templates: List<PageTemplate>,
    targetPageId: String,
    onTargetPageIdChange: (String) -> Unit,
    geminiPrompt: String,
    onGeminiPromptChange: (String) -> Unit,
    rank: Int,
    onRankChange: (Int) -> Unit,
    deviceActionType: DeviceActionType,
    onDeviceActionTypeChange: (DeviceActionType) -> Unit,
    volumeValue: String,
    onVolumeValueChange: (String) -> Unit,
    contactName: String,
    onContactNameChange: (String) -> Unit,
    contactPhone: String,
    onContactPhoneChange: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    // New fields for Date/Time
    includeWeekday: Boolean = false,
    onIncludeWeekdayChange: (Boolean) -> Unit = {},
    prefixText: String = "",
    onPrefixTextChange: (String) -> Unit = {},
    suffixText: String = "",
    onSuffixTextChange: (String) -> Unit = {},
    offsetValue: String = "0",
    onOffsetValueChange: (String) -> Unit = {},
    // Smart Home specific
    smartHomeProvider: SmartHomeProvider = SmartHomeProvider.GOOGLE_HOME,
    onSmartHomeProviderChange: (SmartHomeProvider) -> Unit = {},
    smartHomeDeviceId: String = "",
    onSmartHomeDeviceIdChange: (String) -> Unit = {},
    smartHomeDeviceName: String = "",
    onSmartHomeDeviceNameChange: (String) -> Unit = {},
    smartHomeIntent: String = "",
    onSmartHomeIntentChange: (String) -> Unit = {},
    smartHomeValue: String? = null,
    onSmartHomeValueChange: (String) -> Unit = {},
    availableHomeDevices: List<HomeDevice> = emptyList(),
    isFetchingDevices: Boolean = false,
    onFetchDevices: () -> Unit = {},

    onNavigateToPage: ((String) -> Unit)? = null,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)? = null,
    onDismissDialog: () -> Unit,
    availableGeminiTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool> = emptyList(),
    // Vision specific
    useCloud: Boolean = false,
    isCloudEnabled: Boolean = true,
    playShutterSound: Boolean = true,
    onUseCloudChange: (Boolean) -> Unit = {},
    onPlayShutterSoundChange: (Boolean) -> Unit = {}
) {
    val actionTypeNavigate = stringResource(R.string.button_action_navigate_page)
    val actionTypeGemini = stringResource(R.string.button_action_gemini)
    val actionTypeGeminiSearch = stringResource(R.string.button_action_gemini_search)
    val actionTypeGeminiNano = stringResource(R.string.button_action_gemini_nano)
    val actionTypeGeminiVision = stringResource(R.string.button_action_gemini_vision)
    val actionTypeFrequent = stringResource(R.string.button_action_frequent_action)
    val actionTypeSmart = stringResource(R.string.button_action_smart_prediction)
    val actionTypeDevice = stringResource(R.string.button_action_control_device)
    val actionTypeSmartHome = stringResource(R.string.button_action_smart_home)
    val actionTypeWeather = stringResource(R.string.button_action_weather)

    when (selectedActionType) {
        actionTypeNavigate -> {
            NavigationActionFields(
                navigateToPageId = targetPageId,
                onPageSelected = onTargetPageIdChange,
                availablePages = pages,
                templates = templates,
                onNavigateToPage = onNavigateToPage,
                onCreatePage = onCreatePage,
                onDismissDialog = onDismissDialog
            )
        }
        actionTypeGemini, actionTypeGeminiSearch -> {
            GeminiActionFields(
                prompt = geminiPrompt,
                onPromptChanged = onGeminiPromptChange,
                availableTools = availableGeminiTools
            )
        }
        actionTypeGeminiNano -> {
            GeminiNanoActionFields()
        }
        actionTypeGeminiVision -> {
            GeminiVisionActionFields(
                prompt = geminiPrompt,
                useCloud = useCloud,
                isCloudEnabled = isCloudEnabled,
                playShutterSound = playShutterSound,
                onPromptChanged = onGeminiPromptChange,
                onUseCloudChanged = onUseCloudChange,
                onPlayShutterSoundChanged = onPlayShutterSoundChange
            )
        }
        actionTypeFrequent, actionTypeSmart -> {
            RankActionFields(
                rank = rank.toString(),
                onRankChanged = { onRankChange(it.toIntOrNull() ?: 1) }
            )
        }
        actionTypeDevice -> {
            ControlDeviceActionFields(
                selectedType = deviceActionType,
                onTypeSelected = onDeviceActionTypeChange,
                volumeValue = volumeValue,
                onVolumeValueChange = onVolumeValueChange,
                contactName = contactName,
                onContactSelected = { name, phone ->
                    onContactNameChange(name)
                    onContactPhoneChange(phone)
                },
                messageText = messageText,
                onMessageTextChange = onMessageTextChange,
                includeWeekday = includeWeekday,
                onIncludeWeekdayChange = onIncludeWeekdayChange,
                prefixText = prefixText,
                onPrefixTextChange = onPrefixTextChange,
                suffixText = suffixText,
                onSuffixTextChange = onSuffixTextChange,
                offsetValue = offsetValue,
                onOffsetValueChange = onOffsetValueChange
            )
        }
        actionTypeSmartHome -> {
            SmartHomeActionFields(
                selectedProvider = smartHomeProvider,
                onProviderSelected = onSmartHomeProviderChange,
                deviceId = smartHomeDeviceId,
                onDeviceSelected = { device ->
                    onSmartHomeDeviceIdChange(device.id)
                    onSmartHomeDeviceNameChange(device.name)
                },
                deviceName = smartHomeDeviceName,
                selectedIntent = smartHomeIntent,
                onIntentSelected = onSmartHomeIntentChange,
                value = smartHomeValue ?: "",
                onValueChange = onSmartHomeValueChange,
                devices = availableHomeDevices,
                isFetching = isFetchingDevices,
                onRefresh = onFetchDevices
            )
        }
        actionTypeWeather -> {
            WeatherActionFields()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHomeActionFields(
    selectedProvider: SmartHomeProvider,
    onProviderSelected: (SmartHomeProvider) -> Unit,
    deviceId: String,
    onDeviceSelected: (HomeDevice) -> Unit,
    deviceName: String,
    selectedIntent: String,
    onIntentSelected: (String) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    devices: List<HomeDevice>,
    isFetching: Boolean,
    onRefresh: () -> Unit
) {
    val dimensions = LocalDimensions.current
    var expandedProvider by remember { mutableStateOf(false) }
    var expandedDevice by remember { mutableStateOf(false) }
    var expandedIntent by remember { mutableStateOf(false) }

    val selectedDevice = devices.find { it.id == deviceId }
    
    val availableIntents = remember(selectedDevice, selectedProvider) {
        if (selectedProvider == SmartHomeProvider.GOOGLE_HOME) {
            selectedDevice?.traits?.flatMap { trait ->
                when (trait) {
                    "sdm.devices.traits.OnOff" -> listOf("sdm.devices.commands.OnOff.On", "sdm.devices.commands.OnOff.Off")
                    "sdm.devices.traits.Brightness" -> listOf("sdm.devices.commands.Brightness.Brightness")
                    "sdm.devices.traits.TemperatureSetting" -> listOf("sdm.devices.commands.TemperatureSetting.SetPoint")
                    else -> emptyList()
                }
            }?.map { it to it.substringAfterLast(".") } ?: emptyList()
        } else {
            // Skeleton for Hue
            listOf("action.on" to "An", "action.off" to "Aus")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        // Step 1: Provider
        Text(stringResource(R.string.button_smart_home_provider_label), style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(
            expanded = expandedProvider,
            onExpandedChange = { expandedProvider = !expandedProvider }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedProvider.getDisplayName(),
                onValueChange = { },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expandedProvider,
                onDismissRequest = { expandedProvider = false }
            ) {
                SmartHomeProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.getDisplayName()) },
                        onClick = {
                            onProviderSelected(provider)
                            expandedProvider = false
                        }
                    )
                }
            }
        }

        // Step 2: Device
        Text(stringResource(R.string.button_google_home_device_label), style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(
            expanded = expandedDevice,
            onExpandedChange = { expandedDevice = !expandedDevice }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = if (isFetching) stringResource(R.string.button_google_home_loading_devices) else deviceName.ifEmpty { stringResource(R.string.button_google_home_no_devices) },
                onValueChange = { },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDevice) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expandedDevice,
                onDismissRequest = { expandedDevice = false }
            ) {
                if (devices.isEmpty() && !isFetching) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.button_google_home_no_devices)) }, onClick = { onRefresh(); expandedDevice = false })
                }
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name) },
                        onClick = {
                            onDeviceSelected(device)
                            expandedDevice = false
                        }
                    )
                }
            }
        }

        // Step 3: Intent/Command
        if (deviceId.isNotEmpty()) {
            Text(stringResource(R.string.button_google_home_command_label), style = MaterialTheme.typography.labelMedium)
            ExposedDropdownMenuBox(
                expanded = expandedIntent,
                onExpandedChange = { expandedIntent = !expandedIntent }
            ) {
                val currentIntentLabel = availableIntents.find { it.first == selectedIntent }?.second ?: ""
                OutlinedTextField(
                    readOnly = true,
                    value = currentIntentLabel,
                    onValueChange = { },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIntent) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedIntent,
                    onDismissRequest = { expandedIntent = false }
                ) {
                    availableIntents.forEach { (intent, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onIntentSelected(intent)
                                expandedIntent = false
                            }
                        )
                    }
                }
            }

            // Step 4: Value (Optional)
            if (selectedIntent.contains("Brightness") || selectedIntent.contains("SetPoint")) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(stringResource(R.string.button_google_home_value_label)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    LaunchedEffect(selectedProvider) {
        if (selectedProvider == SmartHomeProvider.GOOGLE_HOME && devices.isEmpty()) onRefresh()
    }
}
