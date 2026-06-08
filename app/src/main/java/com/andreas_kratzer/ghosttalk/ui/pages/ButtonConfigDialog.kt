@file:Suppress("DEPRECATION", "UNUSED_PARAMETER")
package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
import com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PredictionType
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.ui.pages.components.ButtonSettingsActions
import com.andreas_kratzer.ghosttalk.ui.pages.components.ButtonSettingsUiState
import java.io.File

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
    onSuggestLabel: ((ButtonConfig, onResult: (String) -> Unit) -> Unit)? = null,
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
    onApplyRecommendation: ((com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation) -> Unit)? = null,
    defaultStartPageId: String? = null
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(buttonConfig.label) }
    var spokenText by remember { mutableStateOf(buttonConfig.spokenText ?: "") }
    var spokenTextMode by remember { mutableStateOf(buttonConfig.spokenTextMode) }
    var audioFileNameState by remember { mutableStateOf(buttonConfig.audioFileName) }

    val audioRecorder = remember(context) { com.andreas_kratzer.ghosttalk.core.audio.AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }

    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    DisposableEffect(isRecording) {
        if (isRecording) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = true
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
        }
    }
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

    // SpokenText cache state

    // AuditoryCueText cache state


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
    val actionTypeNavigateBack = stringResource(R.string.button_action_navigate_back)
    val actionTypeNavigateToStartPage = stringResource(R.string.button_action_navigate_to_start_page)
    val actionTypeGemini = stringResource(R.string.button_action_gemini)
    val actionTypeGeminiSearch = stringResource(R.string.button_action_gemini_search)
    val actionTypeGeminiVision = stringResource(R.string.button_action_gemini_vision)
    val actionTypeWeather = stringResource(R.string.button_action_weather)

    // Kommunikation
    val actionTypeReadNotifications = stringResource(R.string.button_action_notification)
    val actionTypeToggleAutoRead = "Automatisches Vorlesen umschalten"
    val actionTypeClearNotifications = stringResource(R.string.button_action_clear_notifications)
    val actionTypeSendMessage = stringResource(R.string.action_send_message)
    val actionTypeSendLastSpokenSms = stringResource(R.string.device_action_send_last_spoken_sms)
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
    val actionTypeVolumeInAppTts = stringResource(R.string.volume_in_app_tts)
    val actionTypeVolumeInAppCues = stringResource(R.string.volume_in_app_cues)
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
                is NavigateBackButtonAction -> actionTypeNavigateBack
                is NavigateToStartPageButtonAction -> actionTypeNavigateToStartPage
                is GeminiButtonAction -> actionTypeGemini
                is GeminiSearchButtonAction -> actionTypeGeminiSearch
                is GeminiNanoButtonAction -> actionTypeGemini
                is com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction -> actionTypeGeminiVision
                is WeatherButtonAction -> actionTypeWeather
                is ControlDeviceButtonAction -> {
                    when (action.actionType) {
                        DeviceActionType.READ_NOTIFICATIONS -> actionTypeReadNotifications
                        DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS -> actionTypeToggleAutoRead
                        DeviceActionType.CLEAR_NOTIFICATIONS -> actionTypeClearNotifications
                        DeviceActionType.SEND_MESSAGE -> actionTypeSendMessage
                        DeviceActionType.SEND_LAST_SPOKEN_SMS -> actionTypeSendLastSpokenSms
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
                        DeviceActionType.VOLUME_IN_APP_TTS -> actionTypeVolumeInAppTts
                        DeviceActionType.VOLUME_IN_APP_CUES -> actionTypeVolumeInAppCues
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
    rememberCoroutineScope()

    val buildCurrentAction = {
        when (selectedActionType) {
            actionTypeNavigate -> {
                NavigateToPageButtonAction(targetPageId)
            }
            actionTypeNavigateBack -> NavigateBackButtonAction()
            actionTypeNavigateToStartPage -> NavigateToStartPageButtonAction()
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
            actionTypeToggleAutoRead -> ControlDeviceButtonAction(
                actionType = DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS
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
            actionTypeSendLastSpokenSms -> ControlDeviceButtonAction(
                actionType = DeviceActionType.SEND_LAST_SPOKEN_SMS,
                contactName = contactName,
                contactPhone = contactPhone
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
            actionTypeVolumeInAppTts -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_IN_APP_TTS, volumeValue = volumeValue)
            actionTypeVolumeInAppCues -> ControlDeviceButtonAction(actionType = DeviceActionType.VOLUME_IN_APP_CUES, volumeValue = volumeValue)
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
                actionTypeNavigate, actionTypeNavigateBack -> "Nav"
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
                            val settingsUiState = ButtonSettingsUiState(
                                label = label,
                                spokenText = spokenText,
                                spokenTextMode = spokenTextMode,
                                audioFileName = audioFileNameState,
                                auditoryCueText = auditoryCueText,
                                isActive = isActive,
                                playActionAsAuditoryCue = playActionAsAuditoryCue,
                                selectedActionType = selectedActionType,
                                targetPageId = targetPageId,
                                geminiPrompt = geminiPrompt,
                                geminiVisionUseCloud = geminiVisionUseCloud,
                                geminiVisionPlayShutterSound = geminiVisionPlayShutterSound,
                                rank = rank,
                                predictionType = predictionType,
                                deviceActionType = deviceActionType,
                                volumeValue = volumeValue,
                                contactName = contactName,
                                contactPhone = contactPhone,
                                messageText = messageText,
                                includeWeekday = includeWeekday,
                                prefixText = prefixText,
                                suffixText = suffixText,
                                offsetValue = offsetValue,
                                ignoreEmojis = ignoreEmojis,
                                smartHomeProvider = smartHomeProvider,
                                smartHomeDeviceId = smartHomeDeviceId,
                                smartHomeDeviceName = smartHomeDeviceName,
                                smartHomeIntent = smartHomeIntent,
                                smartHomeValue = smartHomeValue,
                                mediaProvider = mediaProvider,
                                mediaContentUri = mediaContentUri,
                                mediaContentName = mediaContentName,
                                mediaReturnToAppDelaySec = mediaReturnToAppDelaySec,
                                mediaForcePlayViaMediaSession = mediaForcePlayViaMediaSession,
                                isRecording = isRecording,
                                isPlayingAudio = isPlayingAudio,
                                showDeleteConfirmation = showDeleteConfirmation,
                                isFetchingDevices = isFetchingDevices,
                                isLoadingSpotifyPlaylists = isLoadingSpotifyPlaylists,
                                spotifyUserDisplayName = spotifyUserDisplayName
                            )

                            val settingsActions = ButtonSettingsActions(
                                onLabelChange = { label = it },
                                onSpokenTextChange = { spokenText = it },
                                onSpokenTextModeChange = { spokenTextMode = it },
                                onAudioFileNameChange = { audioFileNameState = it },
                                onAuditoryCueTextChange = { auditoryCueText = it },
                                onIsActiveChange = { isActive = it },
                                onPlayActionAsAuditoryCueChange = { playActionAsAuditoryCue = it },
                                onSelectedActionTypeChange = { selectedActionType = it },
                                onTargetPageIdChange = { targetPageId = it },
                                onGeminiPromptChange = { geminiPrompt = it },
                                onGeminiVisionUseCloudChange = { geminiVisionUseCloud = it },
                                onGeminiVisionPlayShutterSoundChange = { geminiVisionPlayShutterSound = it },
                                onRankChange = { rank = it },
                                onPredictionTypeChange = { predictionType = it },
                                onDeviceActionTypeChange = { deviceActionType = it },
                                onVolumeValueChange = { volumeValue = it },
                                onContactNameChange = { contactName = it },
                                onContactPhoneChange = { contactPhone = it },
                                onMessageTextChange = { messageText = it },
                                onIncludeWeekdayChange = { includeWeekday = it },
                                onPrefixTextChange = { prefixText = it },
                                onSuffixTextChange = { suffixText = it },
                                onOffsetValueChange = { offsetValue = it },
                                onIgnoreEmojisChange = { ignoreEmojis = it },
                                onSmartHomeProviderChange = { smartHomeProvider = it },
                                onSmartHomeDeviceIdChange = { smartHomeDeviceId = it },
                                onSmartHomeDeviceNameChange = { smartHomeDeviceName = it },
                                onSmartHomeIntentChange = { smartHomeIntent = it },
                                onSmartHomeValueChange = { smartHomeValue = it },
                                onMediaProviderChange = { mediaProvider = it },
                                onMediaContentUriChange = { mediaContentUri = it },
                                onMediaContentNameChange = { mediaContentName = it },
                                onMediaReturnToAppDelaySecChange = { mediaReturnToAppDelaySec = it },
                                onMediaForcePlayViaMediaSessionChange = { mediaForcePlayViaMediaSession = it },
                                onIsRecordingChange = { isRecording = it },
                                onIsPlayingAudioChange = { isPlayingAudio = it },
                                onShowDeleteConfirmationChange = { showDeleteConfirmation = it },
                                onIsFetchingDevicesChange = { isFetchingDevices = it },
                                onConnectSpotify = onConnectSpotify,
                                onDisconnectSpotify = onDisconnectSpotify,
                                onLoadSpotifyPlaylists = onLoadSpotifyPlaylists,
                                onStartVoiceRecording = { startVoiceRecording() },
                                onStopVoiceRecording = { stopVoiceRecording() },
                                onPlayRecording = { file -> playRecording(file) },
                                buildCurrentAction = buildCurrentAction,
                                handleAutoSave = handleAutoSave,
                                saveWithAction = saveWithAction
                            )

                            com.andreas_kratzer.ghosttalk.ui.pages.components.ButtonSettingsTabContent(
                                context = context,
                                uiState = settingsUiState,
                                actions = settingsActions,
                                buttonConfig = buttonConfig,
                                pages = pages,
                                templates = templates,
                                defaultStartPageId = defaultStartPageId,
                                featureGuard = featureGuard,
                                availableGeminiTools = availableGeminiTools,
                                spotifyPlaylists = spotifyPlaylists,
                                availableHomeDevices = availableHomeDevices,
                                permissionLauncher = permissionLauncher,
                                micPermissionLauncher = micPermissionLauncher,
                                onNavigateToPage = onNavigateToPage,
                                onCreatePage = onCreatePage,
                                onDismiss = onDismiss,
                                onPlayTts = onPlayTts,
                                onStopTts = onStopTts,
                                isTtsElevenLabs = isTtsElevenLabs,
                                isTextCached = isTextCached,
                                onPrefetchText = onPrefetchText,
                                onSuggestLabel = onSuggestLabel,
                                onRefreshHueCache = onRefreshHueCache,
                                onAvailableHomeDevicesChange = { availableHomeDevices = it }
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
                                mediaReturnToAppDelaySec = mediaReturnToAppDelaySec,
                                rank = rank,
                                predictionType = predictionType
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

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
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
        is com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction -> context.getString(R.string.suggest_label_gemini_vision)
        else -> ""
    }
}
