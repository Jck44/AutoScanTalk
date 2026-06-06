package com.andreas_kratzer.ghosttalk.ui.pages.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist
import com.andreas_kratzer.ghosttalk.core.model.*
import com.andreas_kratzer.ghosttalk.core.ui.components.DropdownGroup
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsGroupedDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.ui.pages.actions.NavigationActionFields
import com.andreas_kratzer.ghosttalk.ui.pages.ActionConfigFields
import java.io.File
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@Composable
fun ButtonSettingsTabContent(
    context: Context,
    uiState: ButtonSettingsUiState,
    actions: ButtonSettingsActions,
    buttonConfig: ButtonConfig,
    pages: List<Page>,
    templates: List<PageTemplate>,
    defaultStartPageId: String?,
    featureGuard: FeatureGuard?,
    availableGeminiTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool>,
    spotifyPlaylists: List<SpotifyPlaylist>,
    availableHomeDevices: List<HomeDevice>,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    micPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    onNavigateToPage: ((String) -> Unit)?,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)?,
    onDismiss: () -> Unit,
    onPlayTts: ((String, () -> Unit) -> Unit)?,
    onStopTts: (() -> Unit)?,
    isTtsElevenLabs: () -> Boolean,
    isTextCached: ((String) -> Boolean)?,
    onPrefetchText: ((String, () -> Unit) -> Unit)?,
    onSuggestLabel: ((ButtonConfig, onResult: (String) -> Unit) -> Unit)?,
    onRefreshHueCache: ((silentOnFailure: Boolean, onResult: (Boolean) -> Unit) -> Unit)?,
    onAvailableHomeDevicesChange: (List<HomeDevice>) -> Unit,
) {
    val isElevenLabs = remember { isTtsElevenLabs() }
    val resolver = remember(context) { ActionTypeResolver(context) }

    // Label cache state
    var isLabelPrefetching by remember { mutableStateOf(false) }
    var isLabelCached by remember(uiState.label, isTextCached) {
        mutableStateOf(if (isElevenLabs) (isTextCached?.invoke(uiState.label) ?: false) else false)
    }

    // SpokenText cache state
    var isSpokenTextPrefetching by remember { mutableStateOf(false) }
    var isSpokenTextCached by remember(uiState.spokenText, isTextCached) {
        mutableStateOf(if (isElevenLabs) (isTextCached?.invoke(uiState.spokenText) ?: false) else false)
    }

    // AuditoryCueText cache state
    var isAuditoryCueTextPrefetching by remember { mutableStateOf(false) }
    var isAuditoryCueTextCached by remember(uiState.auditoryCueText, isTextCached) {
        mutableStateOf(if (isElevenLabs) (isTextCached?.invoke(uiState.auditoryCueText) ?: false) else false)
    }

    var playingField by remember { mutableStateOf<String?>(null) }
    var isSuggestingLabel by remember { mutableStateOf(false) }

    fun handlePlayClick(
        fieldName: String,
        text: String,
        isCached: Boolean,
        setCached: (Boolean) -> Unit,
        setPrefetching: (Boolean) -> Unit,
        play: (String, () -> Unit) -> Unit
    ) {
        if (playingField == fieldName) {
            onStopTts?.invoke()
            playingField = null
        } else {
            onStopTts?.invoke()
            if (isElevenLabs && !isCached && text.isNotBlank()) {
                setPrefetching(true)
                onPrefetchText?.invoke(text) {
                    setPrefetching(false)
                    setCached(isTextCached?.invoke(text) ?: false)
                    playingField = fieldName
                    play(text) {
                        if (playingField == fieldName) {
                            playingField = null
                        }
                    }
                }
            } else {
                playingField = fieldName
                play(text) {
                    if (playingField == fieldName) {
                        playingField = null
                    }
                }
            }
        }
    }

    fun handleFocusLost(
        text: String,
        setCached: (Boolean) -> Unit,
        setPrefetching: (Boolean) -> Unit
    ) {
        actions.handleAutoSave()
        if (isElevenLabs && text.isNotBlank() && isTextCached?.invoke(text) == false) {
            setPrefetching(true)
            onPrefetchText?.invoke(text) {
                setPrefetching(false)
                setCached(isTextCached(text))
            }
        }
    }

    val rawGroups = listOf(
        ActionCategoryRegistry.GROUP_BASIS to listOf(
            resolver.actionTypeSpeak to SpeakTextButtonAction(),
            resolver.actionTypeNavigate to NavigateToPageButtonAction(),
            resolver.actionTypeNavigateToStartPage to NavigateToStartPageButtonAction(),
            resolver.actionTypeNavigateBack to NavigateBackButtonAction()
        ),
        ActionCategoryRegistry.GROUP_KI_ASSISTENZ to listOf(
            resolver.actionTypeGemini to GeminiButtonAction(),
            resolver.actionTypeGeminiSearch to GeminiSearchButtonAction(),
            resolver.actionTypeGeminiVision to GeminiVisionButtonAction(),
            resolver.actionTypeWeather to WeatherButtonAction()
        ),
        ActionCategoryRegistry.GROUP_KOMMUNIKATION to listOf(
            resolver.actionTypeReadNotifications to ControlDeviceButtonAction(DeviceActionType.READ_NOTIFICATIONS),
            resolver.actionTypeClearNotifications to ControlDeviceButtonAction(DeviceActionType.CLEAR_NOTIFICATIONS),
            resolver.actionTypeSendMessage to ControlDeviceButtonAction(DeviceActionType.SEND_MESSAGE),
            resolver.actionTypeSendLastSpokenSms to ControlDeviceButtonAction(DeviceActionType.SEND_LAST_SPOKEN_SMS),
            resolver.actionTypeStartCall to ControlDeviceButtonAction(DeviceActionType.START_CALL),
            resolver.actionTypeToggleAutoRead to ControlDeviceButtonAction(DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS)
        ),
        ActionCategoryRegistry.GROUP_MEDIEN_MUSIK to listOf(
            resolver.actionTypeSpotify to PlayMediaButtonAction(MediaProvider.SPOTIFY),
            resolver.actionTypeYoutube to PlayMediaButtonAction(MediaProvider.YOUTUBE),
            resolver.actionTypeYoutubeMusic to PlayMediaButtonAction(MediaProvider.YOUTUBE_MUSIC),
            resolver.actionTypeAudible to PlayMediaButtonAction(MediaProvider.AUDIBLE),
            resolver.actionTypeMediaPlayPause to ControlDeviceButtonAction(DeviceActionType.MEDIA_PLAY_PAUSE),
            resolver.actionTypeMediaNext to ControlDeviceButtonAction(DeviceActionType.MEDIA_NEXT),
            resolver.actionTypeMediaPrevious to ControlDeviceButtonAction(DeviceActionType.MEDIA_PREVIOUS)
        ),
        ActionCategoryRegistry.GROUP_GERAETE_EINSTELLUNGEN to listOf(
            resolver.actionTypeReadTime to ControlDeviceButtonAction(DeviceActionType.READ_TIME),
            resolver.actionTypeReadDate to ControlDeviceButtonAction(DeviceActionType.READ_DATE),
            resolver.actionTypeReadCalendarEntries to ControlDeviceButtonAction(DeviceActionType.READ_CALENDAR_ENTRIES),
            resolver.actionTypeReadBattery to ControlDeviceButtonAction(DeviceActionType.READ_BATTERY),
            resolver.actionTypeVolumeMedia to ControlDeviceButtonAction(DeviceActionType.VOLUME_MEDIA),
            resolver.actionTypeVolumeNotification to ControlDeviceButtonAction(DeviceActionType.VOLUME_NOTIFICATION),
            resolver.actionTypeVolumeAlarm to ControlDeviceButtonAction(DeviceActionType.VOLUME_ALARM),
            resolver.actionTypeVolumeCall to ControlDeviceButtonAction(DeviceActionType.VOLUME_CALL),
            resolver.actionTypeVolumeInAppTts to ControlDeviceButtonAction(DeviceActionType.VOLUME_IN_APP_TTS),
            resolver.actionTypeVolumeInAppCues to ControlDeviceButtonAction(DeviceActionType.VOLUME_IN_APP_CUES),
            resolver.actionTypeStatusSilent to ControlDeviceButtonAction(DeviceActionType.STATUS_SILENT),
            resolver.actionTypeStatusVibrate to ControlDeviceButtonAction(DeviceActionType.STATUS_VIBRATE),
            resolver.actionTypeStatusLoud to ControlDeviceButtonAction(DeviceActionType.STATUS_LOUD),
            resolver.actionTypeToggleScanning to ControlDeviceButtonAction(DeviceActionType.TOGGLE_SCANNING),
            resolver.actionTypeStartSync to ControlDeviceButtonAction(DeviceActionType.START_SYNC)
        ),
        ActionCategoryRegistry.GROUP_SMART_HOME to listOf(
            resolver.actionTypePhilipsHue to SmartHomeButtonAction(SmartHomeProvider.PHILIPS_HUE),
            resolver.actionTypeGoogleHome to SmartHomeButtonAction(SmartHomeProvider.GOOGLE_HOME)
        ),
        ActionCategoryRegistry.GROUP_VERLAUF_VORHERSAGE to listOf(
            resolver.actionTypeFrequent to FrequentActionButtonAction(),
            resolver.actionTypePrevious to PreviousActionButtonAction(),
            resolver.actionTypeSmart to SmartPredictionButtonAction()
        )
    )

    val dropdownGroups = rawGroups.map { (groupName, actionList) ->
        val enabledItems = actionList.filter { (_, action) ->
            featureGuard?.isActionEnabled(action) ?: true
        }.map { (actionLabel, action) ->
            actionLabel to {
                actions.onSelectedActionTypeChange(actionLabel)
                if (uiState.label.isBlank()) {
                    if (actionLabel == resolver.actionTypeNavigateToStartPage) {
                        actions.onLabelChange("Zu Startseite")
                    } else if (actionLabel == resolver.actionTypeNavigateBack) {
                        actions.onLabelChange("Vorherige Seite")
                    } else if (actionLabel == resolver.actionTypeNavigate && uiState.targetPageId.isNotEmpty()) {
                        val pageName = pages.find { it.id == uiState.targetPageId }?.name
                        if (pageName != null) {
                            actions.onLabelChange("Zu $pageName")
                        }
                    }
                }
                // Permission check for Weather
                if (action is WeatherButtonAction) {
                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!hasFine && !hasCoarse) {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                }
            }
        }
        DropdownGroup(name = groupName, items = enabledItems)
    }.filter { it.items.isNotEmpty() }

    SettingsGroupedDropdownItem(
        label = stringResource(R.string.button_action_label),
        selectedOption = uiState.selectedActionType,
        groups = dropdownGroups,
        iconProvider = { actionType ->
            if (actionType == resolver.actionTypeSpotify || actionType == resolver.actionTypeYoutube || actionType == resolver.actionTypeYoutubeMusic || actionType == resolver.actionTypeAudible) {
                val drawableRes = when (actionType) {
                    resolver.actionTypeSpotify -> CoreR.drawable.ic_spotify
                    resolver.actionTypeYoutube -> CoreR.drawable.ic_youtube
                    resolver.actionTypeYoutubeMusic -> CoreR.drawable.ic_youtube_music
                    resolver.actionTypeAudible -> CoreR.drawable.ic_audible
                    else -> CoreR.drawable.ic_spotify
                }
                Icon(
                    painter = painterResource(id = drawableRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                val icon = when (actionType) {
                    resolver.actionTypeSpeak -> Icons.Default.PlayArrow
                    resolver.actionTypeNavigate -> GhostTalkIcons.ArrowForward
                    resolver.actionTypeNavigateBack -> GhostTalkIcons.ArrowBack
                    resolver.actionTypeGemini, resolver.actionTypeGeminiSearch, resolver.actionTypeGeminiVision -> GhostTalkIcons.AutoAwesome
                    resolver.actionTypeWeather -> GhostTalkIcons.PartlyCloudy
                    resolver.actionTypeReadNotifications, resolver.actionTypeClearNotifications, resolver.actionTypeToggleAutoRead -> GhostTalkIcons.Notifications
                    resolver.actionTypeSendMessage -> GhostTalkIcons.Message
                    resolver.actionTypeStartCall -> GhostTalkIcons.Phone
                    resolver.actionTypeMediaPlayPause -> GhostTalkIcons.PlayPause
                    resolver.actionTypeMediaNext -> GhostTalkIcons.SkipNext
                    resolver.actionTypeMediaPrevious -> GhostTalkIcons.SkipPrevious
                    resolver.actionTypeReadTime -> GhostTalkIcons.AccessTime
                    resolver.actionTypeReadDate, resolver.actionTypeReadCalendarEntries -> GhostTalkIcons.DateRange
                    resolver.actionTypeReadBattery -> GhostTalkIcons.BatteryFull
                    resolver.actionTypeVolumeMedia, resolver.actionTypeVolumeNotification, resolver.actionTypeVolumeAlarm, resolver.actionTypeVolumeCall, resolver.actionTypeStatusLoud -> GhostTalkIcons.VolumeUp
                    resolver.actionTypeStatusSilent -> GhostTalkIcons.VolumeOff
                    resolver.actionTypeStatusVibrate -> GhostTalkIcons.Vibration
                    resolver.actionTypePhilipsHue, resolver.actionTypeGoogleHome, resolver.actionTypeNavigateToStartPage -> Icons.Default.Home
                    resolver.actionTypeFrequent, resolver.actionTypePrevious, resolver.actionTypeSmart -> GhostTalkIcons.History
                    else -> Icons.Default.Settings
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        onValueChangeFinished = actions.handleAutoSave
    )

    if (uiState.selectedActionType == resolver.actionTypeNavigate) {
        Spacer(modifier = Modifier.height(8.dp))
        NavigationActionFields(
            navigateToPageId = uiState.targetPageId,
            onPageSelected = { selectedId -> 
                actions.onTargetPageIdChange(selectedId)
                if (uiState.label.isBlank()) {
                    val pageName = pages.find { it.id == selectedId }?.name
                    if (pageName != null) {
                        actions.onLabelChange("Zu $pageName")
                    }
                }
                actions.handleAutoSave()
            },
            availablePages = pages.filter { it.id != defaultStartPageId },
            templates = templates,
            onNavigateToPage = onNavigateToPage,
            onCreatePage = onCreatePage,
            onDismissDialog = onDismiss,
            onAutoSave = actions.handleAutoSave
        )
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsEditTextItem(
        label = stringResource(R.string.button_label_field),
        value = uiState.label,
        onValueChange = actions.onLabelChange,
        onFocusLost = {
            handleFocusLost(uiState.label, { isLabelCached = it }, { isLabelPrefetching = it })
        },
        isPlaying = playingField == "label",
        isLoading = isLabelPrefetching,
        playPauseIconTint = if (isLabelCached) MaterialTheme.colorScheme.primary else null,
        onPlayPauseClick = onPlayTts?.let { play ->
            {
                handlePlayClick(
                    fieldName = "label",
                    text = uiState.label,
                    isCached = isLabelCached,
                    setCached = { isLabelCached = it },
                    setPrefetching = { isLabelPrefetching = it },
                    play = play
                )
            }
        }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSuggestingLabel) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.generating_suggestion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            val hasAi = onSuggestLabel != null
            TextButton(
                onClick = {
                    val currentConfig = buttonConfig.copy(
                        label = uiState.label,
                        spokenText = if (uiState.spokenText.isNotBlank()) uiState.spokenText else null,
                        spokenTextMode = uiState.spokenTextMode,
                        audioFileName = uiState.audioFileName,
                        auditoryCue = if (uiState.auditoryCueText.isNotBlank()) AuditoryCue.TextToSpeechCue(uiState.auditoryCueText) else null,
                        isActive = uiState.isActive,
                        playActionAsAuditoryCue = uiState.playActionAsAuditoryCue,
                        buttonAction = actions.buildCurrentAction()
                    )
                    if (onSuggestLabel != null) {
                        isSuggestingLabel = true
                        onSuggestLabel(currentConfig) { suggestion ->
                            isSuggestingLabel = false
                            if (suggestion.isNotBlank()) {
                                actions.onLabelChange(suggestion)
                                handleFocusLost(suggestion, { isLabelCached = it }, { isLabelPrefetching = it })
                            } else {
                                val localSuggest = getLocalLabelSuggestion(currentConfig, pages, context)
                                if (localSuggest.isNotBlank()) {
                                    actions.onLabelChange(localSuggest)
                                    handleFocusLost(localSuggest, { isLabelCached = it }, { isLabelPrefetching = it })
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.error_label_suggestion_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    } else {
                        val localSuggest = getLocalLabelSuggestion(currentConfig, pages, context)
                        if (localSuggest.isNotBlank()) {
                            actions.onLabelChange(localSuggest)
                            handleFocusLost(localSuggest, { isLabelCached = it }, { isLabelPrefetching = it })
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.error_label_suggestion_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = GhostTalkIcons.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(if (hasAi) R.string.ki_suggestion else R.string.local_suggestion_action),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    
    Text(
        text = stringResource(R.string.button_spoken_text_field),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LocalDimensions.current.paddingSmall),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modes = listOf(SpokenTextMode.TTS, SpokenTextMode.AUDIO)
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = uiState.spokenTextMode == mode,
                            onClick = { 
                                actions.onSpokenTextModeChange(mode)
                                actions.handleAutoSave()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            label = { 
                                Text(
                                    text = if (mode == SpokenTextMode.TTS) {
                                        stringResource(R.string.button_spoken_text_mode_tts)
                                    } else {
                                        stringResource(R.string.button_spoken_text_mode_audio)
                                    },
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.spokenTextMode == SpokenTextMode.TTS) {
                    SettingsEditTextItem(
                        label = "",
                        placeholder = stringResource(R.string.button_spoken_text_placeholder),
                        value = uiState.spokenText,
                        onValueChange = actions.onSpokenTextChange,
                        onFocusLost = {
                            handleFocusLost(uiState.spokenText, { isSpokenTextCached = it }, { isSpokenTextPrefetching = it })
                        },
                        isPlaying = playingField == "spokenText",
                        isLoading = isSpokenTextPrefetching,
                        playPauseIconTint = if (isSpokenTextCached) MaterialTheme.colorScheme.primary else null,
                        onPlayPauseClick = onPlayTts?.let { play ->
                            {
                                handlePlayClick(
                                    fieldName = "spokenText",
                                    text = uiState.spokenText,
                                    isCached = isSpokenTextCached,
                                    setCached = { isSpokenTextCached = it },
                                    setPrefetching = { isSpokenTextPrefetching = it },
                                    play = play
                                )
                            }
                        },
                        borderless = true
                    )
                } else {
                    val audioFileExists = remember(uiState.audioFileName) {
                        if (uiState.audioFileName.isNullOrBlank()) false
                        else File(context.filesDir.resolve("audio_recordings"), uiState.audioFileName).exists()
                    }

                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by if (uiState.isRecording) {
                        infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseAlpha"
                        )
                    } else {
                        remember { mutableFloatStateOf(1f) }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.isRecording) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = stringResource(R.string.button_audio_recording),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (audioFileExists) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.button_audio_saved),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.button_audio_ready),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (uiState.isRecording) {
                                        actions.onStopVoiceRecording()
                                    } else {
                                        val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (hasMicPermission) {
                                            actions.onStartVoiceRecording()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (uiState.isRecording) GhostTalkIcons.Stop else GhostTalkIcons.RecordVoiceOver,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp).size(20.dp)
                                )
                                Text(
                                    text = if (uiState.isRecording) stringResource(R.string.button_audio_stop) else stringResource(R.string.button_audio_record)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val file = File(context.filesDir.resolve("audio_recordings"), uiState.audioFileName ?: "")
                                    actions.onPlayRecording(file)
                                },
                                enabled = audioFileExists && !uiState.isRecording,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (uiState.isPlayingAudio) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPlayingAudio) GhostTalkIcons.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp).size(20.dp)
                                )
                                Text(
                                    text = if (uiState.isPlayingAudio) stringResource(R.string.button_audio_stop) else stringResource(R.string.button_audio_play)
                                )
                            }

                            IconButton(
                                onClick = { actions.onShowDeleteConfirmationChange(true) },
                                enabled = audioFileExists && !uiState.isRecording,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.button_audio_delete)
                                )
                            }
                        }
                    }

                    if (uiState.showDeleteConfirmation) {
                        AlertDialog(
                            onDismissRequest = { actions.onShowDeleteConfirmationChange(false) },
                            title = { Text(stringResource(R.string.button_audio_delete)) },
                            text = { Text(stringResource(R.string.button_audio_delete_confirm)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        actions.onShowDeleteConfirmationChange(false)
                                        val file = File(context.filesDir.resolve("audio_recordings"), uiState.audioFileName ?: "")
                                        if (file.exists()) {
                                            file.delete()
                                        }
                                        actions.onAudioFileNameChange(null)
                                        actions.handleAutoSave()
                                        Toast.makeText(context, "Aufnahme gelöscht", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.button_audio_delete))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { actions.onShowDeleteConfirmationChange(false) }) {
                                    Text(stringResource(CoreR.string.dialog_close))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    SettingsEditTextItem(
        label = stringResource(R.string.button_auditory_cue_field),
        value = uiState.auditoryCueText,
        onValueChange = actions.onAuditoryCueTextChange,
        onFocusLost = {
            handleFocusLost(uiState.auditoryCueText, { isAuditoryCueTextCached = it }, { isAuditoryCueTextPrefetching = it })
        },
        isPlaying = playingField == "auditoryCueText",
        isLoading = isAuditoryCueTextPrefetching,
        playPauseIconTint = if (isAuditoryCueTextCached) MaterialTheme.colorScheme.primary else null,
        onPlayPauseClick = onPlayTts?.let { play ->
            {
                handlePlayClick(
                    fieldName = "auditoryCueText",
                    text = uiState.auditoryCueText,
                    isCached = isAuditoryCueTextCached,
                    setCached = { isAuditoryCueTextCached = it },
                    setPrefetching = { isAuditoryCueTextPrefetching = it },
                    play = play
                )
            }
        }
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LocalDimensions.current.paddingSmall),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { 
                        actions.onIsActiveChange(!uiState.isActive)
                        actions.handleAutoSave()
                    }
                    .padding(vertical = 4.dp)
            ) {
                Switch(
                    checked = uiState.isActive,
                    onCheckedChange = { 
                        actions.onIsActiveChange(it)
                        actions.handleAutoSave()
                    },
                    thumbContent = if (uiState.isActive) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                    } else null
                )
                Text(
                    text = stringResource(R.string.button_is_active_label),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            VerticalDivider(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { 
                        actions.onPlayActionAsAuditoryCueChange(!uiState.playActionAsAuditoryCue)
                        actions.handleAutoSave()
                    }
                    .padding(vertical = 4.dp)
            ) {
                Switch(
                    checked = uiState.playActionAsAuditoryCue,
                    onCheckedChange = { 
                        actions.onPlayActionAsAuditoryCueChange(it)
                        actions.handleAutoSave()
                    }
                )
                Text(
                    text = stringResource(R.string.button_play_as_cue_short),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

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
                    if (currentAction.actionType == DeviceActionType.READ_NOTIFICATIONS) "Benachrichtigungen" else ""
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

    ActionConfigFields(
        selectedActionType = uiState.selectedActionType,
        pages = pages,
        templates = templates,
        targetPageId = uiState.targetPageId,
        onTargetPageIdChange = actions.onTargetPageIdChange,
        geminiPrompt = uiState.geminiPrompt,
        onGeminiPromptChange = actions.onGeminiPromptChange,
        rank = uiState.rank,
        onRankChange = { newRank ->
            val currentConfigWithOldRank = buttonConfig.copy(
                label = uiState.label,
                spokenText = if (uiState.spokenText.isNotBlank()) uiState.spokenText else null,
                spokenTextMode = uiState.spokenTextMode,
                audioFileName = uiState.audioFileName,
                auditoryCue = if (uiState.auditoryCueText.isNotBlank()) AuditoryCue.TextToSpeechCue(uiState.auditoryCueText) else null,
                isActive = uiState.isActive,
                playActionAsAuditoryCue = uiState.playActionAsAuditoryCue,
                buttonAction = actions.buildCurrentAction()
            )
            val oldSuggest = getLocalLabelSuggestion(currentConfigWithOldRank, pages, context)
            
            actions.onRankChange(newRank)
            
            val currentConfigWithNewRank = buttonConfig.copy(
                label = uiState.label,
                spokenText = if (uiState.spokenText.isNotBlank()) uiState.spokenText else null,
                spokenTextMode = uiState.spokenTextMode,
                audioFileName = uiState.audioFileName,
                auditoryCue = if (uiState.auditoryCueText.isNotBlank()) AuditoryCue.TextToSpeechCue(uiState.auditoryCueText) else null,
                isActive = uiState.isActive,
                playActionAsAuditoryCue = uiState.playActionAsAuditoryCue,
                buttonAction = actions.buildCurrentAction()
            )
            val newSuggest = getLocalLabelSuggestion(currentConfigWithNewRank, pages, context)
            
            if (uiState.label == oldSuggest || uiState.label.isBlank()) {
                actions.onLabelChange(newSuggest)
            }
            actions.handleAutoSave()
        },
        predictionType = uiState.predictionType,
        onPredictionTypeChange = { 
            actions.onPredictionTypeChange(it)
            actions.handleAutoSave()
        },
        availableGeminiTools = availableGeminiTools,
        deviceActionType = uiState.deviceActionType,
        onDeviceActionTypeChange = actions.onDeviceActionTypeChange,
        volumeValue = uiState.volumeValue,
        onVolumeValueChange = actions.onVolumeValueChange,
        contactName = uiState.contactName,
        onContactNameChange = actions.onContactNameChange,
        contactPhone = uiState.contactPhone,
        onContactPhoneChange = actions.onContactPhoneChange,
        onContactSelected = { name, phone ->
            actions.onContactNameChange(name)
            actions.onContactPhoneChange(phone)
            val updatedAction = ControlDeviceButtonAction(
                actionType = uiState.deviceActionType,
                volumeValue = uiState.volumeValue,
                contactName = name,
                contactPhone = phone,
                messageText = uiState.messageText,
                includeWeekday = uiState.includeWeekday,
                prefixText = uiState.prefixText.takeIf { it.isNotBlank() },
                suffixText = uiState.suffixText.takeIf { it.isNotBlank() },
                offsetValue = uiState.offsetValue.toIntOrNull() ?: 0,
                ignoreEmojis = uiState.ignoreEmojis
            )
            actions.saveWithAction(updatedAction)
        },
        messageText = uiState.messageText,
        onMessageTextChange = actions.onMessageTextChange,
        includeWeekday = uiState.includeWeekday,
        onIncludeWeekdayChange = actions.onIncludeWeekdayChange,
        prefixText = uiState.prefixText,
        onPrefixTextChange = actions.onPrefixTextChange,
        suffixText = uiState.suffixText,
        onSuffixTextChange = actions.onSuffixTextChange,
        offsetValue = uiState.offsetValue,
        onOffsetValueChange = actions.onOffsetValueChange,
        ignoreEmojis = uiState.ignoreEmojis,
        onIgnoreEmojisChange = {
            actions.onIgnoreEmojisChange(it)
            actions.handleAutoSave()
        },
        smartHomeProvider = uiState.smartHomeProvider,
        onSmartHomeProviderChange = actions.onSmartHomeProviderChange,
        smartHomeDeviceId = uiState.smartHomeDeviceId,
        onSmartHomeDeviceIdChange = actions.onSmartHomeDeviceIdChange,
        smartHomeDeviceName = uiState.smartHomeDeviceName,
        onSmartHomeDeviceNameChange = actions.onSmartHomeDeviceNameChange,
        smartHomeIntent = uiState.smartHomeIntent,
        onSmartHomeIntentChange = actions.onSmartHomeIntentChange,
        smartHomeValue = uiState.smartHomeValue,
        onSmartHomeValueChange = actions.onSmartHomeValueChange,
        availableHomeDevices = availableHomeDevices,
        isFetchingDevices = uiState.isFetchingDevices,
        onFetchDevices = {
            if (uiState.smartHomeProvider == SmartHomeProvider.PHILIPS_HUE) {
                if (onRefreshHueCache != null) {
                    actions.onIsFetchingDevicesChange(true)
                    onRefreshHueCache(false) { success ->
                        actions.onIsFetchingDevicesChange(false)
                    }
                }
            }
        },
        onNavigateToPage = onNavigateToPage,
        onCreatePage = onCreatePage,
        onDismissDialog = onDismiss,
        useCloud = uiState.geminiVisionUseCloud,
        onUseCloudChange = { 
            actions.onGeminiVisionUseCloudChange(it)
            actions.handleAutoSave()
        },
        isCloudEnabled = featureGuard?.isActionEnabled(GeminiButtonAction()) ?: true,
        playShutterSound = uiState.geminiVisionPlayShutterSound,
        onPlayShutterSoundChange = { 
            actions.onGeminiVisionPlayShutterSoundChange(it)
            actions.handleAutoSave()
        },
        mediaProvider = uiState.mediaProvider,
        onMediaProviderChange = {
            actions.onMediaProviderChange(it)
            actions.handleAutoSave()
        },
        mediaContentUri = uiState.mediaContentUri,
        onMediaContentUriChange = {
            actions.onMediaContentUriChange(it)
            actions.handleAutoSave()
        },
        mediaContentName = uiState.mediaContentName,
        onMediaContentNameChange = {
            actions.onMediaContentNameChange(it)
            actions.handleAutoSave()
        },
        mediaReturnToAppDelaySec = uiState.mediaReturnToAppDelaySec,
        onMediaReturnToAppDelaySecChange = {
            actions.onMediaReturnToAppDelaySecChange(it)
            actions.handleAutoSave()
        },
        mediaForcePlayViaMediaSession = uiState.mediaForcePlayViaMediaSession,
        onMediaForcePlayViaMediaSessionChange = {
            actions.onMediaForcePlayViaMediaSessionChange(it)
            actions.handleAutoSave()
        },
        spotifyPlaylists = spotifyPlaylists,
        isLoadingSpotifyPlaylists = uiState.isLoadingSpotifyPlaylists,
        spotifyUserDisplayName = uiState.spotifyUserDisplayName,
        onConnectSpotify = actions.onConnectSpotify,
        onDisconnectSpotify = actions.onDisconnectSpotify,
        onLoadSpotifyPlaylists = actions.onLoadSpotifyPlaylists,
        onAutoSave = actions.handleAutoSave
    )
}

private fun getLocalLabelSuggestion(
    config: ButtonConfig,
    pages: List<Page>,
    context: Context
): String {
    return when (val action = config.buttonAction) {
        is SpeakTextButtonAction -> {
            val text = config.spokenText ?: ""
            if (text.isNotBlank()) {
                val words = text.trim().split("\\s+".toRegex())
                if (words.size <= 3) text else words.take(3).joinToString(" ") + "…"
            } else ""
        }
        is NavigateToPageButtonAction -> {
            val pageName = pages.find { it.id == action.pageId }?.name
            if (!pageName.isNullOrBlank()) {
                context.getString(R.string.suggest_label_navigate, pageName)
            } else ""
        }
        is NavigateToStartPageButtonAction -> {
            context.getString(R.string.suggest_label_start_page)
        }
        is NavigateBackButtonAction -> {
            context.getString(R.string.suggest_label_back)
        }
        is WeatherButtonAction -> {
            context.getString(R.string.suggest_label_weather)
        }
        is ControlDeviceButtonAction -> {
            when (action.actionType) {
                DeviceActionType.READ_TIME -> context.getString(R.string.suggest_label_time)
                DeviceActionType.READ_DATE -> context.getString(R.string.suggest_label_date)
                DeviceActionType.READ_CALENDAR_ENTRIES -> context.getString(R.string.suggest_label_calendar)
                DeviceActionType.READ_BATTERY -> context.getString(R.string.suggest_label_battery)
                DeviceActionType.READ_NOTIFICATIONS -> context.getString(R.string.suggest_label_notifications)
                DeviceActionType.CLEAR_NOTIFICATIONS -> context.getString(R.string.suggest_label_clear_notifications)
                DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS -> context.getString(R.string.suggest_label_toggle_auto_read)
                DeviceActionType.MEDIA_PLAY_PAUSE -> context.getString(R.string.suggest_label_media_play_pause)
                DeviceActionType.MEDIA_NEXT -> context.getString(R.string.suggest_label_media_next)
                DeviceActionType.MEDIA_PREVIOUS -> context.getString(R.string.suggest_label_media_previous)
                DeviceActionType.VOLUME_MEDIA -> context.getString(R.string.suggest_label_volume_media, action.volumeValue ?: "")
                DeviceActionType.VOLUME_NOTIFICATION -> context.getString(R.string.suggest_label_volume_notification, action.volumeValue ?: "")
                DeviceActionType.VOLUME_ALARM -> context.getString(R.string.suggest_label_volume_alarm, action.volumeValue ?: "")
                DeviceActionType.VOLUME_CALL -> context.getString(R.string.suggest_label_volume_call, action.volumeValue ?: "")
                DeviceActionType.VOLUME_IN_APP_TTS -> context.getString(R.string.suggest_label_volume_tts, action.volumeValue ?: "")
                DeviceActionType.VOLUME_IN_APP_CUES -> context.getString(R.string.suggest_label_volume_cues, action.volumeValue ?: "")
                DeviceActionType.STATUS_SILENT -> context.getString(R.string.suggest_label_status_silent)
                DeviceActionType.STATUS_VIBRATE -> context.getString(R.string.suggest_label_status_vibrate)
                DeviceActionType.STATUS_LOUD -> context.getString(R.string.suggest_label_status_loud)
                DeviceActionType.TOGGLE_SCANNING -> context.getString(R.string.suggest_label_toggle_scanning)
                DeviceActionType.INSTALL_UPDATE -> context.getString(R.string.suggest_label_install_update)
                DeviceActionType.START_SYNC -> context.getString(R.string.suggest_label_start_sync)
                DeviceActionType.SEND_MESSAGE -> {
                    val contact = action.contactName ?: ""
                    if (contact.isNotBlank()) {
                        context.getString(R.string.suggest_label_send_message, contact)
                    } else ""
                }
                DeviceActionType.SEND_LAST_SPOKEN_SMS -> {
                    val contact = action.contactName ?: ""
                    if (contact.isNotBlank()) {
                        context.getString(R.string.suggest_label_send_last_spoken_sms, contact)
                    } else ""
                }
                DeviceActionType.START_CALL -> {
                    val contact = action.contactName ?: ""
                    if (contact.isNotBlank()) {
                        context.getString(R.string.suggest_label_start_call, contact)
                    } else ""
                }
            }
        }
        is PlayMediaButtonAction -> {
            val content = action.contentName
            if (content.isNotBlank()) {
                context.getString(R.string.suggest_label_play_media, content)
            } else ""
        }
        is SmartHomeButtonAction -> {
            val device = action.deviceName
            val intent = action.intent
            if (device.isNotBlank() && intent.isNotBlank()) {
                val localizedIntent = when (intent.lowercase()) {
                    "on", "turnon" -> context.getString(R.string.suggest_label_sh_on)
                    "off", "turnoff" -> context.getString(R.string.suggest_label_sh_off)
                    else -> intent
                }
                context.getString(R.string.suggest_label_smart_home, device, localizedIntent)
            } else ""
        }
        is FrequentActionButtonAction -> context.getString(R.string.suggest_label_frequent)
        is PreviousActionButtonAction -> context.getString(R.string.suggest_label_previous)
        is SmartPredictionButtonAction -> context.getString(R.string.suggest_label_smart)
        is GeminiButtonAction -> context.getString(R.string.suggest_label_gemini)
        is GeminiSearchButtonAction -> context.getString(R.string.suggest_label_gemini_search)
        is GeminiVisionButtonAction -> context.getString(R.string.suggest_label_gemini_vision)
        else -> ""
    }
}
