@file:Suppress("DEPRECATION", "UNUSED_PARAMETER")
package com.andreas_kratzer.ghosttalk.ui.pages

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.PredictionType
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.components.DropdownGroup
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsGroupedDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.ui.pages.actions.NavigationActionFields
import kotlinx.coroutines.launch
import java.io.File
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
    isTextCached: ((String) -> Boolean)? = null,
    onPrefetchText: ((String, () -> Unit) -> Unit)? = null,
    // AI Tools
    availableGeminiTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool> = emptyList(),
    // Philips Hue Support
    philipsHueManager: PhilipsHueManager? = null,
    hueBridgeIp: String = "",
    hueUsername: String = "",
    hueCachedDevices: String = "",
    onRefreshHueCache: ((silentOnFailure: Boolean, onResult: (Boolean) -> Unit) -> Unit)? = null,
    featureGuard: FeatureGuard? = null,
    onPlayTts: ((String, () -> Unit) -> Unit)? = null,
    onStopTts: (() -> Unit)? = null,
    isTtsElevenLabs: () -> Boolean = { false },
    spotifyPlaylists: List<com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist> = emptyList(),
    isLoadingSpotifyPlaylists: Boolean = false,
    spotifyUserDisplayName: String? = null,
    onConnectSpotify: () -> Unit = {},
    onDisconnectSpotify: () -> Unit = {},
    onLoadSpotifyPlaylists: () -> Unit = {},
    onSaveAsTemplate: ((ButtonConfig) -> Unit)? = null,
    metrics: ButtonEffortMetrics? = null,
    historyEvents: List<ButtonUsageEvent> = emptyList(),
    recommendations: List<com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation> = emptyList(),
    loadMarkovSuccessors: suspend (String) -> List<Pair<String, Int>> = { emptyList() },
    onApplyRecommendation: ((com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation) -> Unit)? = null
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(buttonConfig.label) }
    var spokenText by remember { mutableStateOf(buttonConfig.spokenText ?: "") }
    var spokenTextMode by remember { mutableStateOf(buttonConfig.spokenTextMode) }
    var audioFileNameState by remember { mutableStateOf(buttonConfig.audioFileName) }

    val audioRecorder = remember(context) { com.andreas_kratzer.ghosttalk.core.audio.AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val dir = context.filesDir.resolve("audio_recordings")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val recordingFile = File(dir, "audio_${buttonConfig.id}.ogg")
                audioRecorder.startRecording(recordingFile)
                isRecording = true
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler bei der Aufnahme: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, R.string.error_microphone_permission_missing, Toast.LENGTH_LONG).show()
        }
    }
    
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
    var playingField by remember { mutableStateOf<String?>(null) }

    val isElevenLabs = remember { isTtsElevenLabs() }

    // Label cache state
    var isLabelPrefetching by remember { mutableStateOf(false) }
    var isLabelCached by remember(label, isTextCached) {
        mutableStateOf(if (isElevenLabs) (isTextCached?.invoke(label) ?: false) else false)
    }

    // SpokenText cache state
    var isSpokenTextPrefetching by remember { mutableStateOf(false) }
    var isSpokenTextCached by remember(spokenText, isTextCached) {
        mutableStateOf(if (isElevenLabs) (isTextCached?.invoke(spokenText) ?: false) else false)
    }

    // AuditoryCueText cache state
    var isAuditoryCueTextPrefetching by remember { mutableStateOf(false) }
    var isAuditoryCueTextCached by remember(auditoryCueText, isTextCached) {
        mutableStateOf(if (isElevenLabs) (isTextCached?.invoke(auditoryCueText) ?: false) else false)
    }


    DisposableEffect(Unit) {
        onDispose {
            onStopTts?.invoke()
            audioRecorder.stopRecording()
            mediaPlayer?.release()
        }
    }

    var currentTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Einstellungen", "Vorschau", "Statistiken")

    val actionTypeSpeak = stringResource(R.string.button_action_speak_text)
    val actionTypeNavigate = stringResource(R.string.button_action_navigate_page)
    val actionTypeGemini = stringResource(R.string.button_action_gemini)
    val actionTypeGeminiSearch = stringResource(R.string.button_action_gemini_search)
    val actionTypeGeminiVision = stringResource(R.string.button_action_gemini_vision)
    val actionTypeWeather = stringResource(R.string.button_action_weather)

    // Kommunikation
    val actionTypeReadNotifications = stringResource(R.string.button_action_notification)
    val actionTypeClearNotifications = stringResource(R.string.button_action_clear_notifications)
    val actionTypeSendMessage = stringResource(R.string.action_send_message)
    val actionTypeStartCall = stringResource(R.string.action_start_call)

    // Medien & Musik
    val actionTypeSpotify = "Spotify abspielen"
    val actionTypeYoutube = "YouTube abspielen"
    val actionTypeYoutubeMusic = "YouTube Music abspielen"
    val actionTypeAudible = "Audible abspielen"
    val actionTypeMediaPlayPause = stringResource(R.string.button_device_control_media_play_pause)
    val actionTypeMediaNext = stringResource(R.string.button_device_control_media_next)
    val actionTypeMediaPrevious = stringResource(R.string.button_device_control_media_previous)

    // Geräte & Einstellungen
    val actionTypeReadTime = stringResource(R.string.button_device_control_time)
    val actionTypeReadDate = stringResource(R.string.button_device_control_date)
    val actionTypeReadCalendarEntries = stringResource(R.string.button_device_control_calendar)
    val actionTypeReadBattery = stringResource(R.string.button_device_control_battery)
    val actionTypeVolumeMedia = stringResource(R.string.volume_media)
    val actionTypeVolumeNotification = stringResource(R.string.volume_notification)
    val actionTypeVolumeAlarm = stringResource(R.string.volume_alarm)
    val actionTypeVolumeCall = stringResource(R.string.volume_call)
    val actionTypeStatusSilent = stringResource(R.string.status_silent)
    val actionTypeStatusVibrate = stringResource(R.string.status_vibrate)
    val actionTypeStatusLoud = stringResource(R.string.status_loud)
    val actionTypeToggleScanning = stringResource(R.string.button_device_control_toggle_scanning)
    val actionTypeInstallUpdate = stringResource(R.string.button_device_control_install_update)
    val actionTypeStartSync = stringResource(R.string.button_device_control_start_sync)

    // Smart Home
    val actionTypePhilipsHue = "Philips Hue steuern"
    val actionTypeGoogleHome = "Google Home steuern"

    // Verlauf & Vorhersage
    val actionTypeFrequent = stringResource(R.string.button_action_frequent_action)
    val actionTypePrevious = stringResource(R.string.action_previous_action)
    val actionTypeSmart = stringResource(R.string.button_action_smart_prediction)

    var selectedActionType by remember {
        mutableStateOf(
            when (val action = buttonConfig.buttonAction) {
                is NavigateToPageButtonAction -> actionTypeNavigate
                is GeminiButtonAction -> actionTypeGemini
                is GeminiSearchButtonAction -> actionTypeGeminiSearch
                is GeminiNanoButtonAction -> actionTypeGemini
                is com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction -> actionTypeGeminiVision
                is WeatherButtonAction -> actionTypeWeather
                is ControlDeviceButtonAction -> {
                    when (action.actionType) {
                        DeviceActionType.READ_NOTIFICATIONS -> actionTypeReadNotifications
                        DeviceActionType.CLEAR_NOTIFICATIONS -> actionTypeClearNotifications
                        DeviceActionType.SEND_MESSAGE -> actionTypeSendMessage
                        DeviceActionType.START_CALL -> actionTypeStartCall
                        DeviceActionType.MEDIA_PLAY_PAUSE -> actionTypeMediaPlayPause
                        DeviceActionType.MEDIA_NEXT -> actionTypeMediaNext
                        DeviceActionType.MEDIA_PREVIOUS -> actionTypeMediaPrevious
                        DeviceActionType.READ_TIME -> actionTypeReadTime
                        DeviceActionType.READ_DATE -> actionTypeReadDate
                        DeviceActionType.READ_CALENDAR_ENTRIES -> actionTypeReadCalendarEntries
                        DeviceActionType.READ_BATTERY -> actionTypeReadBattery
                        DeviceActionType.VOLUME_MEDIA -> actionTypeVolumeMedia
                        DeviceActionType.VOLUME_NOTIFICATION -> actionTypeVolumeNotification
                        DeviceActionType.VOLUME_ALARM -> actionTypeVolumeAlarm
                        DeviceActionType.VOLUME_CALL -> actionTypeVolumeCall
                        DeviceActionType.STATUS_SILENT -> actionTypeStatusSilent
                        DeviceActionType.STATUS_VIBRATE -> actionTypeStatusVibrate
                        DeviceActionType.STATUS_LOUD -> actionTypeStatusLoud
                        DeviceActionType.TOGGLE_SCANNING -> actionTypeToggleScanning
                        DeviceActionType.INSTALL_UPDATE -> actionTypeInstallUpdate
                        DeviceActionType.START_SYNC -> actionTypeStartSync
                    }
                }
                is PlayMediaButtonAction -> {
                    when (action.provider) {
                        MediaProvider.SPOTIFY -> actionTypeSpotify
                        MediaProvider.YOUTUBE -> actionTypeYoutube
                        MediaProvider.YOUTUBE_MUSIC -> actionTypeYoutubeMusic
                        MediaProvider.AUDIBLE -> actionTypeAudible
                    }
                }
                is SmartHomeButtonAction -> {
                    when (action.provider) {
                        SmartHomeProvider.PHILIPS_HUE -> actionTypePhilipsHue
                        SmartHomeProvider.GOOGLE_HOME -> actionTypeGoogleHome
                    }
                }
                is FrequentActionButtonAction -> actionTypeFrequent
                is SmartPredictionButtonAction -> actionTypeSmart
                is PreviousActionButtonAction -> actionTypePrevious
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
                is PreviousActionButtonAction -> action.rank
                else -> 1
            }
        )
    }

    var predictionType by remember {
        mutableStateOf(
            (buttonConfig.buttonAction as? SmartPredictionButtonAction)?.predictionType ?: PredictionType.ALL
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
    var ignoreEmojis by remember {
        mutableStateOf((buttonConfig.buttonAction as? ControlDeviceButtonAction)?.ignoreEmojis ?: false)
    }

    // Smart Home specific state
    var smartHomeProvider by remember {
        mutableStateOf((buttonConfig.buttonAction as? SmartHomeButtonAction)?.provider ?: SmartHomeProvider.PHILIPS_HUE)
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

    // Play Media specific state
    var mediaProvider by remember {
        mutableStateOf((buttonConfig.buttonAction as? PlayMediaButtonAction)?.provider ?: MediaProvider.SPOTIFY)
    }
    var mediaContentUri by remember {
        mutableStateOf((buttonConfig.buttonAction as? PlayMediaButtonAction)?.contentUri ?: "")
    }
    var mediaContentName by remember {
        mutableStateOf((buttonConfig.buttonAction as? PlayMediaButtonAction)?.contentName ?: "")
    }
    var mediaReturnToAppDelaySec by remember {
        mutableStateOf(((buttonConfig.buttonAction as? PlayMediaButtonAction)?.returnToAppDelayMs ?: 2000L).div(1000L).toString())
    }
    var mediaForcePlayViaMediaSession by remember {
        mutableStateOf((buttonConfig.buttonAction as? PlayMediaButtonAction)?.forcePlayViaMediaSession ?: true)
    }
    
    val parsedCachedDevices = remember(hueCachedDevices) {
        val list = mutableListOf<HomeDevice>()
        if (hueCachedDevices.isNotBlank()) {
            try {
                val array = org.json.JSONArray(hueCachedDevices)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        HomeDevice(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            type = obj.optString("type", "LIGHT")
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        list
    }

    var availableHomeDevices by remember { mutableStateOf<List<HomeDevice>>(parsedCachedDevices) }
    var isFetchingDevices by remember { mutableStateOf(false) }

    LaunchedEffect(parsedCachedDevices) {
        if (parsedCachedDevices.isNotEmpty() || availableHomeDevices.isEmpty()) {
            availableHomeDevices = parsedCachedDevices
        }
    }

    LaunchedEffect(smartHomeProvider, selectedActionType) {
        if (selectedActionType == actionTypePhilipsHue && onRefreshHueCache != null) {
            onRefreshHueCache(true) { success ->
                // Silently refreshed cache if bridge is reachable
            }
        }
    }
    val scope = rememberCoroutineScope()

    val buildCurrentAction = {
        when (selectedActionType) {
            actionTypeNavigate -> NavigateToPageButtonAction(targetPageId)
            actionTypeGemini -> GeminiButtonAction(geminiPrompt)
            actionTypeGeminiSearch -> GeminiSearchButtonAction(geminiPrompt)
            actionTypeGeminiVision -> com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction(geminiPrompt, geminiVisionUseCloud, geminiVisionPlayShutterSound)
            actionTypeFrequent -> FrequentActionButtonAction(rank)
            actionTypePrevious -> PreviousActionButtonAction(rank)
            actionTypeSmart -> SmartPredictionButtonAction(rank, predictionType)
            actionTypeWeather -> WeatherButtonAction()
            
            // Kommunikation
            actionTypeReadNotifications -> ControlDeviceButtonAction(
                actionType = DeviceActionType.READ_NOTIFICATIONS,
                contactName = contactName,
                contactPhone = contactPhone,
                ignoreEmojis = ignoreEmojis
            )
            actionTypeClearNotifications -> ControlDeviceButtonAction(
                actionType = DeviceActionType.CLEAR_NOTIFICATIONS,
                contactName = contactName,
                contactPhone = contactPhone
            )
            actionTypeSendMessage -> ControlDeviceButtonAction(
                actionType = DeviceActionType.SEND_MESSAGE,
                contactName = contactName,
                contactPhone = contactPhone,
                messageText = messageText
            )
            actionTypeStartCall -> ControlDeviceButtonAction(
                actionType = DeviceActionType.START_CALL,
                contactName = contactName,
                contactPhone = contactPhone
            )

            // Medien & Musik
            actionTypeSpotify -> PlayMediaButtonAction(
                provider = MediaProvider.SPOTIFY,
                contentUri = mediaContentUri,
                contentName = mediaContentName,
                returnToAppDelayMs = (mediaReturnToAppDelaySec.toLongOrNull() ?: 2L) * 1000L,
                forcePlayViaMediaSession = mediaForcePlayViaMediaSession
            )
            actionTypeYoutube -> PlayMediaButtonAction(
                provider = MediaProvider.YOUTUBE,
                contentUri = mediaContentUri,
                contentName = mediaContentName,
                returnToAppDelayMs = (mediaReturnToAppDelaySec.toLongOrNull() ?: 2L) * 1000L,
                forcePlayViaMediaSession = mediaForcePlayViaMediaSession
            )
            actionTypeYoutubeMusic -> PlayMediaButtonAction(
                provider = MediaProvider.YOUTUBE_MUSIC,
                contentUri = mediaContentUri,
                contentName = mediaContentName,
                returnToAppDelayMs = (mediaReturnToAppDelaySec.toLongOrNull() ?: 2L) * 1000L,
                forcePlayViaMediaSession = mediaForcePlayViaMediaSession
            )
            actionTypeAudible -> PlayMediaButtonAction(
                provider = MediaProvider.AUDIBLE,
                contentUri = mediaContentUri,
                contentName = mediaContentName,
                returnToAppDelayMs = (mediaReturnToAppDelaySec.toLongOrNull() ?: 2L) * 1000L,
                forcePlayViaMediaSession = mediaForcePlayViaMediaSession
            )
            actionTypeMediaPlayPause -> ControlDeviceButtonAction(actionType = DeviceActionType.MEDIA_PLAY_PAUSE)
            actionTypeMediaNext -> ControlDeviceButtonAction(actionType = DeviceActionType.MEDIA_NEXT)
            actionTypeMediaPrevious -> ControlDeviceButtonAction(actionType = DeviceActionType.MEDIA_PREVIOUS)

            // Geräte & Einstellungen
            actionTypeReadTime -> ControlDeviceButtonAction(
                actionType = DeviceActionType.READ_TIME,
                prefixText = prefixText.takeIf { it.isNotBlank() },
                suffixText = suffixText.takeIf { it.isNotBlank() },
                offsetValue = offsetValue.toIntOrNull() ?: 0
            )
            actionTypeReadDate -> ControlDeviceButtonAction(
                actionType = DeviceActionType.READ_DATE,
                includeWeekday = includeWeekday,
                prefixText = prefixText.takeIf { it.isNotBlank() },
                suffixText = suffixText.takeIf { it.isNotBlank() },
                offsetValue = offsetValue.toIntOrNull() ?: 0
            )
            actionTypeReadCalendarEntries -> ControlDeviceButtonAction(
                actionType = DeviceActionType.READ_CALENDAR_ENTRIES,
                prefixText = prefixText.takeIf { it.isNotBlank() },
                suffixText = suffixText.takeIf { it.isNotBlank() },
                offsetValue = offsetValue.toIntOrNull() ?: 0
            )
            actionTypeReadBattery -> ControlDeviceButtonAction(actionType = DeviceActionType.READ_BATTERY)
            actionTypeVolumeMedia -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_MEDIA, volumeValue = volumeValue)
            actionTypeVolumeNotification -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_NOTIFICATION, volumeValue = volumeValue)
            actionTypeVolumeAlarm -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_ALARM, volumeValue = volumeValue)
            actionTypeVolumeCall -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_CALL, volumeValue = volumeValue)
            actionTypeStatusSilent -> ControlDeviceButtonAction(actionType = DeviceActionType.STATUS_SILENT)
            actionTypeStatusVibrate -> ControlDeviceButtonAction(actionType = DeviceActionType.STATUS_VIBRATE)
            actionTypeStatusLoud -> ControlDeviceButtonAction(actionType = DeviceActionType.STATUS_LOUD)
            actionTypeToggleScanning -> ControlDeviceButtonAction(actionType = DeviceActionType.TOGGLE_SCANNING)
            actionTypeInstallUpdate -> ControlDeviceButtonAction(actionType = DeviceActionType.INSTALL_UPDATE)
            actionTypeStartSync -> ControlDeviceButtonAction(actionType = DeviceActionType.START_SYNC)

            // Smart Home
            actionTypePhilipsHue -> SmartHomeButtonAction(
                provider = SmartHomeProvider.PHILIPS_HUE,
                deviceId = smartHomeDeviceId,
                deviceName = smartHomeDeviceName,
                intent = smartHomeIntent,
                value = if (smartHomeValue.isNotBlank()) smartHomeValue else null
            )
            actionTypeGoogleHome -> SmartHomeButtonAction(
                provider = SmartHomeProvider.GOOGLE_HOME,
                deviceId = smartHomeDeviceId,
                deviceName = smartHomeDeviceName,
                intent = smartHomeIntent,
                value = if (smartHomeValue.isNotBlank()) smartHomeValue else null
            )
            
            else -> SpeakTextButtonAction()
        }
    }

    val handleAutoSave: () -> Unit = {
        if (label.isNotBlank()) {
            val action = buildCurrentAction()

            val config = buttonConfig.copy(
                label = label,
                spokenText = if (spokenText.isNotBlank()) spokenText else null,
                spokenTextMode = spokenTextMode,
                audioFileName = audioFileNameState,
                auditoryCue = if (auditoryCueText.isNotBlank()) AuditoryCue.TextToSpeechCue(auditoryCueText) else null,
                isActive = isActive,
                playActionAsAuditoryCue = playActionAsAuditoryCue,
                buttonAction = action
            )
            onSave(config)
        }
    }

    val saveWithAction: (ButtonAction) -> Unit = { action ->
        if (label.isNotBlank()) {
            val config = buttonConfig.copy(
                label = label,
                spokenText = if (spokenText.isNotBlank()) spokenText else null,
                spokenTextMode = spokenTextMode,
                audioFileName = audioFileNameState,
                auditoryCue = if (auditoryCueText.isNotBlank()) AuditoryCue.TextToSpeechCue(auditoryCueText) else null,
                isActive = isActive,
                playActionAsAuditoryCue = playActionAsAuditoryCue,
                buttonAction = action
            )
            onSave(config)
        }
    }

    fun startVoiceRecording() {
        try {
            val dir = context.filesDir.resolve("audio_recordings")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val recordingFile = File(dir, "audio_${buttonConfig.id}.ogg")
            audioRecorder.startRecording(recordingFile)
            isRecording = true
        } catch (e: Exception) {
            Toast.makeText(context, "Fehler bei der Aufnahme: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun stopVoiceRecording() {
        try {
            audioRecorder.stopRecording()
            isRecording = false
            audioFileNameState = "audio_${buttonConfig.id}.ogg"
            handleAutoSave()
            Toast.makeText(context, R.string.button_audio_saved, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Fehler beim Stoppen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun playRecording(file: File) {
        if (isPlayingAudio) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlayingAudio = false
            return
        }

        try {
            val player = android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    isPlayingAudio = false
                    it.release()
                    mediaPlayer = null
                }
                start()
            }
            mediaPlayer = player
            isPlayingAudio = true
        } catch (e: Exception) {
            android.util.Log.e("ButtonConfigDialog", "Error playing recording", e)
            Toast.makeText(context, "Fehler beim Abspielen: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

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
        handleAutoSave()
        if (isElevenLabs && text.isNotBlank() && isTextCached?.invoke(text) == false) {
            setPrefetching(true)
            onPrefetchText?.invoke(text) {
                setPrefetching(false)
                setCached(isTextCached(text))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 800.dp)
            .fillMaxWidth(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            val actionBadgeText = when (selectedActionType) {
                actionTypeNavigate -> "Nav"
                actionTypeGemini, actionTypeGeminiSearch, actionTypeGeminiVision -> "KI"
                actionTypeFrequent, actionTypePrevious, actionTypeSmart -> "Verlauf"
                actionTypeWeather -> "Wetter"
                actionTypeReadNotifications, actionTypeClearNotifications, actionTypeSendMessage, actionTypeStartCall -> "Komm."
                actionTypeSpotify, actionTypeYoutube, actionTypeYoutubeMusic, actionTypeAudible, actionTypeMediaPlayPause, actionTypeMediaNext, actionTypeMediaPrevious -> "Medien"
                actionTypePhilipsHue, actionTypeGoogleHome -> "Home"
                actionTypeSpeak -> "Sprechen"
                else -> "Gerät"
            }
            Text("[$actionBadgeText] Bearbeiten")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        SegmentedButton(
                            selected = currentTab == index,
                            onClick = { currentTab = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabTitles.size),
                            label = { Text(title, maxLines = 1) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.paddingSmall)
                ) {
                    when (currentTab) {
                        0 -> {
                            val rawGroups = listOf(
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_BASIS to listOf(
                                    actionTypeSpeak to SpeakTextButtonAction(),
                                    actionTypeNavigate to NavigateToPageButtonAction()
                                ),
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_KI_ASSISTENZ to listOf(
                                    actionTypeGemini to GeminiButtonAction(),
                                    actionTypeGeminiSearch to GeminiSearchButtonAction(),
                                    actionTypeGeminiVision to com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction(),
                                    actionTypeWeather to WeatherButtonAction()
                                ),
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_KOMMUNIKATION to listOf(
                                    actionTypeReadNotifications to ControlDeviceButtonAction(DeviceActionType.READ_NOTIFICATIONS),
                                    actionTypeClearNotifications to ControlDeviceButtonAction(DeviceActionType.CLEAR_NOTIFICATIONS),
                                    actionTypeSendMessage to ControlDeviceButtonAction(DeviceActionType.SEND_MESSAGE),
                                    actionTypeStartCall to ControlDeviceButtonAction(DeviceActionType.START_CALL)
                                ),
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_MEDIEN_MUSIK to listOf(
                                    actionTypeSpotify to PlayMediaButtonAction(MediaProvider.SPOTIFY),
                                    actionTypeYoutube to PlayMediaButtonAction(MediaProvider.YOUTUBE),
                                    actionTypeYoutubeMusic to PlayMediaButtonAction(MediaProvider.YOUTUBE_MUSIC),
                                    actionTypeAudible to PlayMediaButtonAction(MediaProvider.AUDIBLE),
                                    actionTypeMediaPlayPause to ControlDeviceButtonAction(DeviceActionType.MEDIA_PLAY_PAUSE),
                                    actionTypeMediaNext to ControlDeviceButtonAction(DeviceActionType.MEDIA_NEXT),
                                    actionTypeMediaPrevious to ControlDeviceButtonAction(DeviceActionType.MEDIA_PREVIOUS)
                                ),
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_GERAETE_EINSTELLUNGEN to listOf(
                                    actionTypeReadTime to ControlDeviceButtonAction(DeviceActionType.READ_TIME),
                                    actionTypeReadDate to ControlDeviceButtonAction(DeviceActionType.READ_DATE),
                                    actionTypeReadCalendarEntries to ControlDeviceButtonAction(DeviceActionType.READ_CALENDAR_ENTRIES),
                                    actionTypeReadBattery to ControlDeviceButtonAction(DeviceActionType.READ_BATTERY),
                                    actionTypeVolumeMedia to ControlDeviceButtonAction(DeviceActionType.VOLUME_MEDIA),
                                    actionTypeVolumeNotification to ControlDeviceButtonAction(DeviceActionType.VOLUME_NOTIFICATION),
                                    actionTypeVolumeAlarm to ControlDeviceButtonAction(DeviceActionType.VOLUME_ALARM),
                                    actionTypeVolumeCall to ControlDeviceButtonAction(DeviceActionType.VOLUME_CALL),
                                    actionTypeStatusSilent to ControlDeviceButtonAction(DeviceActionType.STATUS_SILENT),
                                    actionTypeStatusVibrate to ControlDeviceButtonAction(DeviceActionType.STATUS_VIBRATE),
                                    actionTypeStatusLoud to ControlDeviceButtonAction(DeviceActionType.STATUS_LOUD),
                                    actionTypeToggleScanning to ControlDeviceButtonAction(DeviceActionType.TOGGLE_SCANNING),
                                    actionTypeInstallUpdate to ControlDeviceButtonAction(DeviceActionType.INSTALL_UPDATE),
                                    actionTypeStartSync to ControlDeviceButtonAction(DeviceActionType.START_SYNC)
                                ),
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_SMART_HOME to listOf(
                                    actionTypePhilipsHue to SmartHomeButtonAction(SmartHomeProvider.PHILIPS_HUE),
                                    actionTypeGoogleHome to SmartHomeButtonAction(SmartHomeProvider.GOOGLE_HOME)
                                ),
                                com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry.GROUP_VERLAUF_VORHERSAGE to listOf(
                                    actionTypeFrequent to FrequentActionButtonAction(),
                                    actionTypePrevious to PreviousActionButtonAction(),
                                    actionTypeSmart to SmartPredictionButtonAction()
                                )
                            )

                            val dropdownGroups = rawGroups.map { (groupName, actionList) ->
                                val enabledItems = actionList.filter { (_, action) ->
                                    featureGuard?.isActionEnabled(action) ?: true
                                }.map { (label, action) ->
                                    label to {
                                        selectedActionType = label
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
                                selectedOption = selectedActionType,
                                groups = dropdownGroups,
                                iconProvider = { actionType ->
                                    if (actionType == actionTypeSpotify || actionType == actionTypeYoutube || actionType == actionTypeYoutubeMusic || actionType == actionTypeAudible) {
                                        val drawableRes = when (actionType) {
                                            actionTypeSpotify -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_spotify
                                            actionTypeYoutube -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_youtube
                                            actionTypeYoutubeMusic -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_youtube_music
                                            actionTypeAudible -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_audible
                                            else -> com.andreas_kratzer.ghosttalk.core.ui.R.drawable.ic_spotify
                                        }
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = drawableRes),
                                            contentDescription = null,
                                            tint = androidx.compose.ui.graphics.Color.Unspecified,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        val icon = when (actionType) {
                                            actionTypeSpeak -> Icons.Default.PlayArrow
                                            actionTypeNavigate -> GhostTalkIcons.ArrowForward
                                            actionTypeGemini, actionTypeGeminiSearch, actionTypeGeminiVision -> GhostTalkIcons.AutoAwesome
                                            actionTypeWeather -> GhostTalkIcons.PartlyCloudy
                                            actionTypeReadNotifications, actionTypeClearNotifications -> GhostTalkIcons.Notifications
                                            actionTypeSendMessage -> GhostTalkIcons.Message
                                            actionTypeStartCall -> GhostTalkIcons.Phone
                                            actionTypeMediaPlayPause -> GhostTalkIcons.PlayPause
                                            actionTypeMediaNext -> GhostTalkIcons.SkipNext
                                            actionTypeMediaPrevious -> GhostTalkIcons.SkipPrevious
                                            actionTypeReadTime -> GhostTalkIcons.AccessTime
                                            actionTypeReadDate, actionTypeReadCalendarEntries -> GhostTalkIcons.DateRange
                                            actionTypeReadBattery -> GhostTalkIcons.BatteryFull
                                            actionTypeVolumeMedia, actionTypeVolumeNotification, actionTypeVolumeAlarm, actionTypeVolumeCall, actionTypeStatusLoud -> GhostTalkIcons.VolumeUp
                                            actionTypeStatusSilent -> GhostTalkIcons.VolumeOff
                                            actionTypeStatusVibrate -> GhostTalkIcons.Vibration
                                            actionTypePhilipsHue, actionTypeGoogleHome -> Icons.Default.Home
                                            actionTypeFrequent, actionTypePrevious, actionTypeSmart -> GhostTalkIcons.History
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
                                onValueChangeFinished = handleAutoSave
                            )

                            if (selectedActionType == actionTypeNavigate) {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                                NavigationActionFields(
                                    navigateToPageId = targetPageId,
                                    onPageSelected = { 
                                        targetPageId = it
                                        handleAutoSave()
                                    },
                                    availablePages = pages,
                                    templates = templates,
                                    onNavigateToPage = onNavigateToPage,
                                    onCreatePage = onCreatePage,
                                    onDismissDialog = onDismiss,
                                    onAutoSave = handleAutoSave
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            SettingsEditTextItem(
                                label = stringResource(R.string.button_label_field),
                                value = label,
                                onValueChange = { label = it },
                                onFocusLost = {
                                    handleFocusLost(label, { isLabelCached = it }, { isLabelPrefetching = it })
                                },
                                isPlaying = playingField == "label",
                                isLoading = isLabelPrefetching,
                                playPauseIconTint = if (isLabelCached) MaterialTheme.colorScheme.primary else null,
                                onPlayPauseClick = onPlayTts?.let { play ->
                                    {
                                        handlePlayClick(
                                            fieldName = "label",
                                            text = label,
                                            isCached = isLabelCached,
                                            setCached = { isLabelCached = it },
                                            setPrefetching = { isLabelPrefetching = it },
                                            play = play
                                        )
                                    }
                                }
                            )
                            
                            Text(
                                text = stringResource(R.string.button_spoken_text_field),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                            androidx.compose.material3.OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = LocalDimensions.current.paddingSmall),
                                colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
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
                                                    selected = spokenTextMode == mode,
                                                    onClick = { 
                                                        spokenTextMode = mode
                                                        handleAutoSave()
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
                                        if (spokenTextMode == SpokenTextMode.TTS) {
                                            SettingsEditTextItem(
                                                label = "",
                                                placeholder = stringResource(R.string.button_spoken_text_placeholder),
                                                value = spokenText,
                                                onValueChange = { spokenText = it },
                                                onFocusLost = {
                                                    handleFocusLost(spokenText, { isSpokenTextCached = it }, { isSpokenTextPrefetching = it })
                                                },
                                                isPlaying = playingField == "spokenText",
                                                isLoading = isSpokenTextPrefetching,
                                                playPauseIconTint = if (isSpokenTextCached) MaterialTheme.colorScheme.primary else null,
                                                onPlayPauseClick = onPlayTts?.let { play ->
                                                    {
                                                        handlePlayClick(
                                                            fieldName = "spokenText",
                                                            text = spokenText,
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
                                            val audioFileExists = remember(audioFileNameState) {
                                                if (audioFileNameState.isNullOrBlank()) false
                                                else File(context.filesDir.resolve("audio_recordings"), audioFileNameState!!).exists()
                                            }

                                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                            val pulseAlpha by if (isRecording) {
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
                                                    if (isRecording) {
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
                                                            if (isRecording) {
                                                                stopVoiceRecording()
                                                            } else {
                                                                val hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                                                if (hasMicPermission) {
                                                                    startVoiceRecording()
                                                                } else {
                                                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isRecording) GhostTalkIcons.Stop else GhostTalkIcons.RecordVoiceOver,
                                                            contentDescription = null,
                                                            modifier = Modifier.padding(end = 4.dp).size(20.dp)
                                                        )
                                                        Text(
                                                            text = if (isRecording) stringResource(R.string.button_audio_stop) else stringResource(R.string.button_audio_record)
                                                        )
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            val file = File(context.filesDir.resolve("audio_recordings"), audioFileNameState ?: "")
                                                            playRecording(file)
                                                        },
                                                        enabled = audioFileExists && !isRecording,
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = if (isPlayingAudio) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isPlayingAudio) GhostTalkIcons.Stop else Icons.Default.PlayArrow,
                                                            contentDescription = null,
                                                            modifier = Modifier.padding(end = 4.dp).size(20.dp)
                                                        )
                                                        Text(
                                                            text = if (isPlayingAudio) stringResource(R.string.button_audio_stop) else stringResource(R.string.button_audio_play)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { showDeleteConfirmation = true },
                                                        enabled = audioFileExists && !isRecording,
                                                        colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
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

                                            if (showDeleteConfirmation) {
                                                AlertDialog(
                                                    onDismissRequest = { showDeleteConfirmation = false },
                                                    title = { Text(stringResource(R.string.button_audio_delete)) },
                                                    text = { Text(stringResource(R.string.button_audio_delete_confirm)) },
                                                    confirmButton = {
                                                        TextButton(
                                                            onClick = {
                                                                showDeleteConfirmation = false
                                                                val file = File(context.filesDir.resolve("audio_recordings"), audioFileNameState ?: "")
                                                                if (file.exists()) {
                                                                    file.delete()
                                                                }
                                                                audioFileNameState = null
                                                                handleAutoSave()
                                                                Toast.makeText(context, "Aufnahme gelöscht", Toast.LENGTH_SHORT).show()
                                                            },
                                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                        ) {
                                                            Text(stringResource(R.string.button_audio_delete))
                                                        }
                                                    },
                                                    dismissButton = {
                                                        TextButton(onClick = { showDeleteConfirmation = false }) {
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
                                value = auditoryCueText,
                                onValueChange = { auditoryCueText = it },
                                onFocusLost = {
                                    handleFocusLost(auditoryCueText, { isAuditoryCueTextCached = it }, { isAuditoryCueTextPrefetching = it })
                                },
                                isPlaying = playingField == "auditoryCueText",
                                isLoading = isAuditoryCueTextPrefetching,
                                playPauseIconTint = if (isAuditoryCueTextCached) MaterialTheme.colorScheme.primary else null,
                                onPlayPauseClick = onPlayTts?.let { play ->
                                    {
                                        handlePlayClick(
                                            fieldName = "auditoryCueText",
                                            text = auditoryCueText,
                                            isCached = isAuditoryCueTextCached,
                                            setCached = { isAuditoryCueTextCached = it },
                                            setPrefetching = { isAuditoryCueTextPrefetching = it },
                                            play = play
                                        )
                                    }
                                }
                            )

                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = LocalDimensions.current.paddingSmall),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
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
                                                isActive = !isActive 
                                                handleAutoSave()
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        androidx.compose.material3.Switch(
                                            checked = isActive,
                                            onCheckedChange = { 
                                                isActive = it
                                                handleAutoSave()
                                            },
                                            thumbContent = if (isActive) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize)) }
                                            } else null
                                        )
                                        Text(
                                            text = stringResource(R.string.button_is_active_label),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.VerticalDivider(modifier = Modifier.height(32.dp))
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { 
                                                playActionAsAuditoryCue = !playActionAsAuditoryCue
                                                handleAutoSave()
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        androidx.compose.material3.Switch(
                                            checked = playActionAsAuditoryCue,
                                            onCheckedChange = { 
                                                playActionAsAuditoryCue = it
                                                handleAutoSave()
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
                                selectedActionType = selectedActionType,
                                pages = pages,
                                templates = templates,
                                targetPageId = targetPageId,
                                onTargetPageIdChange = { targetPageId = it },
                                geminiPrompt = geminiPrompt,
                                onGeminiPromptChange = { geminiPrompt = it },
                                rank = rank,
                                onRankChange = { rank = it },
                                predictionType = predictionType,
                                onPredictionTypeChange = { 
                                    predictionType = it
                                    handleAutoSave()
                                },
                                availableGeminiTools = availableGeminiTools,
                                deviceActionType = deviceActionType,
                                onDeviceActionTypeChange = { deviceActionType = it },
                                volumeValue = volumeValue,
                                onVolumeValueChange = { volumeValue = it },
                                contactName = contactName,
                                onContactNameChange = { contactName = it },
                                contactPhone = contactPhone,
                                onContactPhoneChange = { contactPhone = it },
                                onContactSelected = { name, phone ->
                                    contactName = name
                                    contactPhone = phone
                                    val updatedAction = ControlDeviceButtonAction(
                                        actionType = deviceActionType,
                                        volumeValue = volumeValue,
                                        contactName = name,
                                        contactPhone = phone,
                                        messageText = messageText,
                                        includeWeekday = includeWeekday,
                                        prefixText = prefixText.takeIf { it.isNotBlank() },
                                        suffixText = suffixText.takeIf { it.isNotBlank() },
                                        offsetValue = offsetValue.toIntOrNull() ?: 0,
                                        ignoreEmojis = ignoreEmojis
                                    )
                                    saveWithAction(updatedAction)
                                },
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
                                ignoreEmojis = ignoreEmojis,
                                onIgnoreEmojisChange = {
                                    ignoreEmojis = it
                                    handleAutoSave()
                                },
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
                                    if (smartHomeProvider == SmartHomeProvider.PHILIPS_HUE) {
                                        if (onRefreshHueCache != null) {
                                            isFetchingDevices = true
                                            onRefreshHueCache(false) { success ->
                                                isFetchingDevices = false
                                            }
                                        } else if (philipsHueManager != null) {
                                            scope.launch {
                                                isFetchingDevices = true
                                                val list = philipsHueManager.getLocalLights(hueBridgeIp, hueUsername)
                                                if (list.isNotEmpty()) {
                                                    availableHomeDevices = list
                                                }
                                                isFetchingDevices = false
                                            }
                                        }
                                    }
                                },
                                onNavigateToPage = onNavigateToPage,
                                onCreatePage = onCreatePage,
                                onDismissDialog = onDismiss,
                                useCloud = geminiVisionUseCloud,
                                onUseCloudChange = { 
                                    geminiVisionUseCloud = it
                                    handleAutoSave()
                                },
                                isCloudEnabled = featureGuard?.isActionEnabled(GeminiButtonAction()) ?: true,
                                playShutterSound = geminiVisionPlayShutterSound,
                                onPlayShutterSoundChange = { 
                                    geminiVisionPlayShutterSound = it
                                    handleAutoSave()
                                },
                                mediaProvider = mediaProvider,
                                onMediaProviderChange = {
                                    mediaProvider = it
                                    handleAutoSave()
                                },
                                mediaContentUri = mediaContentUri,
                                onMediaContentUriChange = {
                                    mediaContentUri = it
                                    handleAutoSave()
                                },
                                mediaContentName = mediaContentName,
                                onMediaContentNameChange = {
                                    mediaContentName = it
                                    handleAutoSave()
                                },
                                mediaReturnToAppDelaySec = mediaReturnToAppDelaySec,
                                onMediaReturnToAppDelaySecChange = {
                                    mediaReturnToAppDelaySec = it
                                    handleAutoSave()
                                },
                                mediaForcePlayViaMediaSession = mediaForcePlayViaMediaSession,
                                onMediaForcePlayViaMediaSessionChange = {
                                    mediaForcePlayViaMediaSession = it
                                    handleAutoSave()
                                },
                                spotifyPlaylists = spotifyPlaylists,
                                isLoadingSpotifyPlaylists = isLoadingSpotifyPlaylists,
                                spotifyUserDisplayName = spotifyUserDisplayName,
                                onConnectSpotify = onConnectSpotify,
                                onDisconnectSpotify = onDisconnectSpotify,
                                onLoadSpotifyPlaylists = onLoadSpotifyPlaylists,
                                onAutoSave = handleAutoSave
                            )
                        }
                        1 -> {
                            PreviewTabContent(
                                selectedActionType = selectedActionType,
                                spokenText = spokenText,
                                label = label,
                                geminiPrompt = geminiPrompt,
                                targetPageId = targetPageId,
                                deviceActionType = deviceActionType,
                                includeWeekday = includeWeekday,
                                offsetValue = offsetValue,
                                prefixText = prefixText,
                                suffixText = suffixText,
                                contactName = contactName,
                                contactPhone = contactPhone,
                                messageText = messageText,
                                smartHomeDeviceName = smartHomeDeviceName,
                                playActionAsAuditoryCue = playActionAsAuditoryCue,
                                auditoryCueText = auditoryCueText,
                                mediaProvider = mediaProvider,
                                mediaContentName = mediaContentName,
                                mediaReturnToAppDelaySec = mediaReturnToAppDelaySec
                            )
                        }
                        2 -> {
                            ButtonStatisticsTabContent(
                                metrics = metrics,
                                historyEvents = historyEvents,
                                recommendations = recommendations,
                                onApplyRecommendation = onApplyRecommendation,
                                buttonId = buttonConfig.id,
                                loadMarkovSuccessors = loadMarkovSuccessors,
                                allPages = pages,
                                onNavigateToPage = onNavigateToPage,
                                onDismissDialog = onDismiss
                            )
                        }
                    }
                }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

            // Action Bar (Fixed at the bottom)
            DialogActionBar(
                                buttonConfig = buttonConfig,
                                label = label,
                                spokenText = spokenText,
                                spokenTextMode = spokenTextMode,
                                audioFileName = audioFileNameState,
                                buildCurrentAction = buildCurrentAction,
                                onDismiss = onDismiss,
                                onTest = onTest,
                                onMove = onMove,
                                onDuplicate = onDuplicate,
                                onDelete = onDelete,
                                onSaveAsTemplate = onSaveAsTemplate
            )
        }
    },
    confirmButton = { },
    dismissButton = { }
)
}


