package com.andreas_kratzer.ghosttalk.ui.pages.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.components.DropdownGroup
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsGroupedDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.ui.pages.actions.NavigationActionFields
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@Composable
fun ActionTypeDropdownSection(
    state: ButtonConfigDialogState,
    context: Context,
    pages: List<Page>,
    templates: List<PageTemplate>,
    defaultStartPageId: String?,
    featureGuard: FeatureGuard?,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onNavigateToPage: ((String) -> Unit)?,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)?,
    onDismiss: () -> Unit,
    onAutoSave: () -> Unit
) {
    val resolver = remember(context) { ActionTypeResolver(context) }

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
                state.selectedActionType = ButtonActionFactory.actionTypeIdOf(action)
                if (state.label.isBlank()) {
                    if (actionLabel == resolver.actionTypeNavigateToStartPage) {
                        state.label = "Zu Startseite"
                    } else if (actionLabel == resolver.actionTypeNavigateBack) {
                        state.label = "Vorherige Seite"
                    } else if (actionLabel == resolver.actionTypeNavigate && state.targetPageId.isNotEmpty()) {
                        val pageName = pages.find { it.id == state.targetPageId }?.name
                        if (pageName != null) {
                            state.label = "Zu $pageName"
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
        selectedOption = resolver.getLabel(state.selectedActionType),
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
        onValueChangeFinished = onAutoSave
    )

    if (state.selectedActionType == ActionTypeId.NAVIGATE) {
        Spacer(modifier = Modifier.height(8.dp))
        NavigationActionFields(
            navigateToPageId = state.targetPageId,
            onPageSelected = { selectedId -> 
                state.targetPageId = selectedId
                if (state.label.isBlank()) {
                    val pageName = pages.find { it.id == selectedId }?.name
                    if (pageName != null) {
                        state.label = "Zu $pageName"
                    }
                }
                onAutoSave()
            },
            availablePages = pages.filter { it.id != defaultStartPageId },
            templates = templates,
            onNavigateToPage = onNavigateToPage,
            onCreatePage = onCreatePage,
            onDismissDialog = onDismiss,
            onAutoSave = onAutoSave
        )
    }
}
