package com.andreas_kratzer.ghosttalk.ui.pages

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleHomeManager
import com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@Composable
fun ButtonConfigDialog(
    buttonConfig: ButtonConfig,
    pages: List<Page>,
    templates: List<PageTemplate>,
    onSave: (ButtonConfig) -> Unit,
    onDismiss: () -> Unit,
    onTest: (ButtonConfig) -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToPage: ((String) -> Unit)? = null,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)? = null,
    currentPageId: String? = null,
    // AI Tools
    availableGeminiTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool> = emptyList(),
    // Google Home Support
    googleHomeManager: GoogleHomeManager? = null,
    googleHomeProjectId: String = "",
    featureGuard: FeatureGuard? = null
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(buttonConfig.label) }
    var spokenText by remember { mutableStateOf(buttonConfig.spokenText ?: "") }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (!granted) {
            Toast.makeText(context, R.string.permission_location_denied_weather, Toast.LENGTH_LONG).show()
        }
    }
    var auditoryCueText by remember { 
        mutableStateOf((buttonConfig.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text ?: "") 
    }
    var isActive by remember { mutableStateOf(buttonConfig.isActive) }
    var playActionAsAuditoryCue by remember { mutableStateOf(buttonConfig.playActionAsAuditoryCue) }
    
    var showMenu by remember { mutableStateOf(false) }

    val actionTypeSpeak = stringResource(R.string.button_action_speak_text)
    val actionTypeNavigate = stringResource(R.string.button_action_navigate_page)
    val actionTypeGemini = stringResource(R.string.button_action_gemini)
    val actionTypeGeminiSearch = stringResource(R.string.button_action_gemini_search)
    val actionTypeGeminiNano = stringResource(R.string.button_action_gemini_nano)
    val actionTypeFrequent = stringResource(R.string.button_action_frequent_action)
    val actionTypeSmart = stringResource(R.string.button_action_smart_prediction)
    val actionTypeDevice = stringResource(R.string.button_action_control_device)
    val actionTypeWeather = stringResource(R.string.button_action_weather)
    val actionTypeSmartHome = stringResource(R.string.button_action_smart_home)
    val actionTypeGeminiVision = "Gemini Vision (KI Auge)"

    var selectedActionType by remember {
        mutableStateOf(
            when (val action = buttonConfig.buttonAction) {
                is NavigateToPageButtonAction -> actionTypeNavigate
                is GeminiButtonAction -> actionTypeGemini
                is GeminiSearchButtonAction -> actionTypeGeminiSearch
                is GeminiNanoButtonAction -> actionTypeGeminiNano
                is com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction -> actionTypeGeminiVision
                is FrequentActionButtonAction -> actionTypeFrequent
                is SmartPredictionButtonAction -> actionTypeSmart
                is ControlDeviceButtonAction -> actionTypeDevice
                is WeatherButtonAction -> actionTypeWeather
                is SmartHomeButtonAction -> actionTypeSmartHome
                else -> actionTypeSpeak
            }
        )
    }

    // Navigation specific state
    var targetPageId by remember {
        mutableStateOf((buttonConfig.buttonAction as? NavigateToPageButtonAction)?.pageId ?: "")
    }

    // Gemini specific state
    var geminiPrompt by remember {
        mutableStateOf(
            when(val action = buttonConfig.buttonAction) {
                is GeminiButtonAction -> action.prompt
                is GeminiSearchButtonAction -> action.prompt
                is GeminiNanoButtonAction -> action.intent
                is com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction -> action.prompt
                else -> ""
            }
        )
    }

    var geminiVisionUseCloud by remember {
        mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction)?.useCloud ?: false)
    }

    var geminiVisionPlayShutterSound by remember {
        mutableStateOf((buttonConfig.buttonAction as? com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction)?.playShutterSound ?: true)
    }

    // Frequent/Smart specific state
    var rank by remember {
        mutableIntStateOf(
            when(val action = buttonConfig.buttonAction) {
                is FrequentActionButtonAction -> action.rank
                is SmartPredictionButtonAction -> action.rank
                else -> 1
            }
        )
    }

    // Device control specific state
    var deviceActionType by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.actionType ?: DeviceActionType.READ_TIME)
    }
    var volumeValue by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.volumeValue ?: "50")
    }
    var contactName by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.contactName ?: "")
    }
    var contactPhone by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.contactPhone ?: "")
    }
    var messageText by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.messageText ?: "")
    }
    var includeWeekday by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.includeWeekday ?: false)
    }
    var prefixText by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.prefixText ?: "")
    }
    var suffixText by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.suffixText ?: "")
    }
    var offsetValue by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.offsetValue?.toString() ?: "0")
    }

    // Smart Home specific state
    var smartHomeProvider by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.provider ?: SmartHomeProvider.GOOGLE_HOME)
    }
    var smartHomeDeviceId by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.deviceId ?: "")
    }
    var smartHomeDeviceName by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.deviceName ?: "")
    }
    var smartHomeIntent by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.intent ?: "")
    }
    var smartHomeValue by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.value ?: "")
    }
    
    var availableHomeDevices by remember { mutableStateOf<List<HomeDevice>>(emptyList()) }
    var isFetchingDevices by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.button_dialog_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.paddingSmall)
            ) {
                SettingsEditTextItem(
                    label = stringResource(R.string.button_label_field),
                    value = label,
                    onValueChange = { label = it }
                )
                
                SettingsEditTextItem(
                    label = stringResource(R.string.button_spoken_text_field),
                    value = spokenText,
                    onValueChange = { spokenText = it }
                )

                SettingsEditTextItem(
                    label = stringResource(R.string.button_auditory_cue_field),
                    value = auditoryCueText,
                    onValueChange = { auditoryCueText = it }
                )

                SettingsToggleItem(
                    label = stringResource(R.string.button_is_active_label),
                    checked = isActive,
                    onCheckedChange = { isActive = it }
                )

                SettingsToggleItem(
                    label = stringResource(R.string.button_play_as_cue),
                    checked = playActionAsAuditoryCue,
                    onCheckedChange = { playActionAsAuditoryCue = it }
                )

                featureGuard?.let { guard ->
                    val currentAction = buttonConfig.buttonAction
                    val isActionEnabled = guard.isActionEnabled(currentAction)
                    if (!isActionEnabled) {
                        val featureName = when (currentAction) {
                            is GeminiButtonAction, is GeminiSearchButtonAction -> "Gemini Cloud"
                            is GeminiNanoButtonAction -> "Gemini Nano"
                            is SmartHomeButtonAction -> "Smart Home"
                            is SmartPredictionButtonAction, is FrequentActionButtonAction -> "Smart Prediction"
                            is WeatherButtonAction -> "Wetter"
                            is ControlDeviceButtonAction -> {
                                if (currentAction.actionType == com.andreas_kratzer.ghosttalk.core.model.DeviceActionType.READ_NOTIFICATIONS) "Benachrichtigungen" else ""
                            }
                            else -> ""
                        }
                        if (featureName.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.feature_disabled_warning, featureName),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsDropdownItem(
                    label = stringResource(R.string.button_action_label),
                    selectedOption = selectedActionType,
                    options = listOf(
                        actionTypeSpeak to SpeakTextButtonAction(),
                        actionTypeNavigate to NavigateToPageButtonAction(),
                        actionTypeGemini to GeminiButtonAction(),
                        actionTypeGeminiSearch to GeminiSearchButtonAction(),
                        actionTypeGeminiNano to GeminiNanoButtonAction(),
                        actionTypeFrequent to FrequentActionButtonAction(),
                        actionTypeSmart to SmartPredictionButtonAction(),
                        actionTypeWeather to WeatherButtonAction(),
                        actionTypeSmartHome to SmartHomeButtonAction(),
                        actionTypeDevice to ControlDeviceButtonAction(),
                        actionTypeGeminiVision to com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction()
                    ).filter { (label, action) ->
                        featureGuard?.isActionEnabled(action) ?: true
                    }.map { (label, _) ->
                        label to { selectedActionType = label }
                    }
                )

                ActionConfigFields(
                    selectedActionType = selectedActionType,
                    pages = pages,
                    templates = templates,
                    targetPageId = targetPageId,
                    onTargetPageIdChange = { targetPageId = it },
                    geminiPrompt = geminiPrompt,
                    onGeminiPromptChange = { geminiPrompt = it },
                    rank = rank,
                    onRankChange = { rank = it },
                    // Gemini Tools
                    availableGeminiTools = availableGeminiTools,
                    deviceActionType = deviceActionType,
                    onDeviceActionTypeChange = { deviceActionType = it },
                    volumeValue = volumeValue,
                    onVolumeValueChange = { volumeValue = it },
                    contactName = contactName,
                    onContactNameChange = { contactName = it },
                    contactPhone = contactPhone,
                    onContactPhoneChange = { contactPhone = it },
                    messageText = messageText,
                    onMessageTextChange = { messageText = it },
                    includeWeekday = includeWeekday,
                    onIncludeWeekdayChange = { includeWeekday = it },
                    prefixText = prefixText,
                    onPrefixTextChange = { prefixText = it },
                    suffixText = suffixText,
                    onSuffixTextChange = { suffixText = it },
                    offsetValue = offsetValue,
                    onOffsetValueChange = { offsetValue = it },
                    // Smart Home
                    smartHomeProvider = smartHomeProvider,
                    onSmartHomeProviderChange = { smartHomeProvider = it },
                    smartHomeDeviceId = smartHomeDeviceId,
                    onSmartHomeDeviceIdChange = { smartHomeDeviceId = it },
                    smartHomeDeviceName = smartHomeDeviceName,
                    onSmartHomeDeviceNameChange = { smartHomeDeviceName = it },
                    smartHomeIntent = smartHomeIntent,
                    onSmartHomeIntentChange = { smartHomeIntent = it },
                    smartHomeValue = smartHomeValue,
                    onSmartHomeValueChange = { smartHomeValue = it },
                    availableHomeDevices = availableHomeDevices,
                    isFetchingDevices = isFetchingDevices,
                    onFetchDevices = {
                        if (googleHomeManager != null && googleHomeProjectId.isNotBlank()) {
                            scope.launch {
                                isFetchingDevices = true
                                availableHomeDevices = googleHomeManager.listDevices(googleHomeProjectId)
                                isFetchingDevices = false
                            }
                        }
                    },
                    onNavigateToPage = onNavigateToPage,
                    onCreatePage = onCreatePage,
                    onDismissDialog = onDismiss,
                    useCloud = geminiVisionUseCloud,
                    onUseCloudChange = { geminiVisionUseCloud = it },
                    isCloudEnabled = featureGuard?.isActionEnabled(GeminiButtonAction()) ?: true,
                    playShutterSound = geminiVisionPlayShutterSound,
                    onPlayShutterSoundChange = { geminiVisionPlayShutterSound = it }
                )
            }
        },
        confirmButton = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableWidth = maxWidth
                // Approximate button widths based on content + padding
                // Save (~80dp), Cancel (~80dp), Test (~80dp), Move (~100dp), Duplicate (~100dp), Delete (~90dp)
                // Thresholds are conservative to ensure they fit without wrapping or crowding
                val showTestAsButton = availableWidth > 400.dp
                val showMoveAsButton = availableWidth > 500.dp
                val showDuplicateAsButton = availableWidth > 600.dp
                val showDeleteAsButton = availableWidth > 700.dp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Always show Cancel (as Secondary)
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(CoreR.string.action_cancel))
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    // Test Button
                    if (showTestAsButton) {
                        OutlinedButton(onClick = {
                            val currentAction = when (selectedActionType) {
                                actionTypeNavigate -> NavigateToPageButtonAction(targetPageId)
                                actionTypeGemini -> GeminiButtonAction(geminiPrompt)
                                actionTypeGeminiSearch -> GeminiSearchButtonAction(geminiPrompt)
                                actionTypeGeminiNano -> GeminiNanoButtonAction(geminiPrompt)
                                actionTypeFrequent -> FrequentActionButtonAction(rank)
                                actionTypeSmart -> SmartPredictionButtonAction(rank)
                                actionTypeWeather -> WeatherButtonAction()
                                actionTypeDevice -> ControlDeviceButtonAction(
                                    actionType = deviceActionType,
                                    volumeValue = volumeValue,
                                    contactName = contactName,
                                    contactPhone = contactPhone,
                                    messageText = messageText,
                                    includeWeekday = includeWeekday,
                                    prefixText = prefixText.takeIf { it.isNotBlank() },
                                    suffixText = suffixText.takeIf { it.isNotBlank() },
                                    offsetValue = offsetValue.toIntOrNull() ?: 0
                                )
                                actionTypeSmartHome -> SmartHomeButtonAction(
                                    provider = smartHomeProvider,
                                    deviceId = smartHomeDeviceId,
                                    deviceName = smartHomeDeviceName,
                                    intent = smartHomeIntent,
                                    value = if (smartHomeValue.isNotBlank()) smartHomeValue else null
                                )
                                else -> SpeakTextButtonAction()
                            }
                            onTest(buttonConfig.copy(
                                label = label,
                                spokenText = if (spokenText.isNotBlank()) spokenText else null,
                                buttonAction = currentAction
                            ))
                            Toast.makeText(context, R.string.button_test_started, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(stringResource(R.string.button_action_test))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Move Button
                    if (showMoveAsButton) {
                        OutlinedButton(onClick = onMove) {
                            Text(stringResource(R.string.button_action_move))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Duplicate Button
                    if (showDuplicateAsButton) {
                        OutlinedButton(onClick = onDuplicate) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(stringResource(R.string.action_duplicate))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Delete Button
                    if (showDeleteAsButton) {
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(stringResource(CoreR.string.action_delete))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Save Button (Primary)
                    Button(
                        enabled = label.isNotBlank(),
                        onClick = {
                            val action = when (selectedActionType) {
                                actionTypeNavigate -> NavigateToPageButtonAction(targetPageId)
                                actionTypeGemini -> GeminiButtonAction(geminiPrompt)
                                actionTypeGeminiSearch -> GeminiSearchButtonAction(geminiPrompt)
                                actionTypeGeminiNano -> GeminiNanoButtonAction(geminiPrompt)
                                actionTypeGeminiVision -> com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction(geminiPrompt, geminiVisionUseCloud, geminiVisionPlayShutterSound)
                                actionTypeFrequent -> FrequentActionButtonAction(rank)
                                actionTypeSmart -> SmartPredictionButtonAction(rank)
                                actionTypeWeather -> WeatherButtonAction()
                                actionTypeDevice -> ControlDeviceButtonAction(
                                    actionType = deviceActionType,
                                    volumeValue = volumeValue,
                                    contactName = contactName,
                                    contactPhone = contactPhone,
                                    messageText = messageText,
                                    includeWeekday = includeWeekday,
                                    prefixText = prefixText.takeIf { it.isNotBlank() },
                                    suffixText = suffixText.takeIf { it.isNotBlank() },
                                    offsetValue = offsetValue.toIntOrNull() ?: 0
                                )
                                actionTypeSmartHome -> SmartHomeButtonAction(
                                    provider = smartHomeProvider,
                                    deviceId = smartHomeDeviceId,
                                    deviceName = smartHomeDeviceName,
                                    intent = smartHomeIntent,
                                    value = if (smartHomeValue.isNotBlank()) smartHomeValue else null
                                )
                                else -> SpeakTextButtonAction()
                            }

                            val config = buttonConfig.copy(
                                label = label,
                                spokenText = if (spokenText.isNotBlank()) spokenText else null,
                                auditoryCue = if (auditoryCueText.isNotBlank()) AuditoryCue.TextToSpeechCue(auditoryCueText) else null,
                                isActive = isActive,
                                playActionAsAuditoryCue = playActionAsAuditoryCue,
                                buttonAction = action
                            )

                            if (action is WeatherButtonAction) {
                                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (!hasFine && !hasCoarse) {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                }
                            }

                            onSave(config)
                        }
                    ) {
                        Text(stringResource(CoreR.string.action_save))
                    }

                    // Overflow Menu (only if items are hidden)
                    val hasHiddenItems = !showTestAsButton || !showMoveAsButton || !showDuplicateAsButton || !showDeleteAsButton
                    if (hasHiddenItems) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (!showTestAsButton) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.button_action_test)) },
                                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            val currentAction = when (selectedActionType) {
                                                actionTypeNavigate -> NavigateToPageButtonAction(targetPageId)
                                                actionTypeGemini -> GeminiButtonAction(geminiPrompt)
                                                actionTypeGeminiSearch -> GeminiSearchButtonAction(geminiPrompt)
                                                actionTypeGeminiNano -> GeminiNanoButtonAction(geminiPrompt)
                                                actionTypeFrequent -> FrequentActionButtonAction(rank)
                                                actionTypeSmart -> SmartPredictionButtonAction(rank)
                                                actionTypeWeather -> WeatherButtonAction()
                                                actionTypeDevice -> ControlDeviceButtonAction(
                                                    actionType = deviceActionType,
                                                    volumeValue = volumeValue,
                                                    contactName = contactName,
                                                    contactPhone = contactPhone,
                                                    messageText = messageText,
                                                    includeWeekday = includeWeekday,
                                                    prefixText = prefixText.takeIf { it.isNotBlank() },
                                                    suffixText = suffixText.takeIf { it.isNotBlank() },
                                                    offsetValue = offsetValue.toIntOrNull() ?: 0
                                                )
                                                actionTypeSmartHome -> SmartHomeButtonAction(
                                                    provider = smartHomeProvider,
                                                    deviceId = smartHomeDeviceId,
                                                    deviceName = smartHomeDeviceName,
                                                    intent = smartHomeIntent,
                                                    value = if (smartHomeValue.isNotBlank()) smartHomeValue else null
                                                )
                                                else -> SpeakTextButtonAction()
                                            }
                                            onTest(buttonConfig.copy(
                                                label = label,
                                                spokenText = if (spokenText.isNotBlank()) spokenText else null,
                                                buttonAction = currentAction
                                            ))
                                            Toast.makeText(context, R.string.button_test_started, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                if (!showMoveAsButton) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.button_action_move)) },
                                        onClick = {
                                            showMenu = false
                                            onMove()
                                        }
                                    )
                                }
                                if (!showDuplicateAsButton) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_duplicate)) },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onDuplicate()
                                        }
                                    )
                                }
                                if (!showDeleteAsButton) {
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                stringResource(CoreR.string.action_delete),
                                                color = MaterialTheme.colorScheme.error
                                            ) 
                                        },
                                        leadingIcon = { 
                                            Icon(
                                                Icons.Default.Delete, 
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            ) 
                                        },
                                        onClick = {
                                            showMenu = false
                                            onDelete()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = null
    )
}
