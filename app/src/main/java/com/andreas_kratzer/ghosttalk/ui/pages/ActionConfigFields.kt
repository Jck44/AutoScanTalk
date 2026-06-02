package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.pages.actions.DeviceActionFields
import com.andreas_kratzer.ghosttalk.ui.pages.actions.GeminiActionFields
import com.andreas_kratzer.ghosttalk.ui.pages.actions.GeminiVisionActionFields
import com.andreas_kratzer.ghosttalk.ui.pages.actions.PlayMediaActionFields
import com.andreas_kratzer.ghosttalk.ui.pages.actions.RankActionFields
import com.andreas_kratzer.ghosttalk.ui.pages.actions.SmartHomeActionFields
import com.andreas_kratzer.ghosttalk.ui.pages.actions.WeatherActionFields

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
    onContactSelected: (String, String) -> Unit = { _, _ -> },
    // New fields for Date/Time
    includeWeekday: Boolean = false,
    onIncludeWeekdayChange: (Boolean) -> Unit = {},
    prefixText: String = "",
    onPrefixTextChange: (String) -> Unit = {},
    suffixText: String = "",
    onSuffixTextChange: (String) -> Unit = {},
    offsetValue: String = "0",
    onOffsetValueChange: (String) -> Unit = {},
    // Smart Home specific state
    smartHomeProvider: SmartHomeProvider = SmartHomeProvider.PHILIPS_HUE,
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
    onPlayShutterSoundChange: (Boolean) -> Unit = {},
    // Play Media specific
    mediaProvider: MediaProvider = MediaProvider.SPOTIFY,
    onMediaProviderChange: (MediaProvider) -> Unit = {},
    mediaContentUri: String = "",
    onMediaContentUriChange: (String) -> Unit = {},
    mediaContentName: String = "",
    onMediaContentNameChange: (String) -> Unit = {},
    mediaReturnToAppDelaySec: String = "2",
    onMediaReturnToAppDelaySecChange: (String) -> Unit = {},
    mediaForcePlayViaMediaSession: Boolean = true,
    onMediaForcePlayViaMediaSessionChange: (Boolean) -> Unit = {},
    spotifyPlaylists: List<com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist> = emptyList(),
    isLoadingSpotifyPlaylists: Boolean = false,
    spotifyUserDisplayName: String? = null,
    onConnectSpotify: () -> Unit = {},
    onDisconnectSpotify: () -> Unit = {},
    onLoadSpotifyPlaylists: () -> Unit = {},
    ignoreEmojis: Boolean = false,
    onIgnoreEmojisChange: (Boolean) -> Unit = {},
    onAutoSave: () -> Unit = {}
) {
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

    val dimensions = LocalDimensions.current

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)) {
        when (selectedActionType) {
            actionTypeNavigate, actionTypeSpeak -> {
                // Speak and Navigate don't render config fields at the bottom
            }
            actionTypeGemini, actionTypeGeminiSearch -> {
                GeminiActionFields(
                    prompt = geminiPrompt,
                    onPromptChanged = onGeminiPromptChange,
                    availableTools = availableGeminiTools,
                    onAutoSave = onAutoSave
                )
            }
            actionTypeGeminiVision -> {
                GeminiVisionActionFields(
                    prompt = geminiPrompt,
                    useCloud = useCloud,
                    isCloudEnabled = isCloudEnabled,
                    playShutterSound = playShutterSound,
                    onPromptChanged = onGeminiPromptChange,
                    onUseCloudChanged = { 
                        onUseCloudChange(it)
                        onAutoSave()
                    },
                    onPlayShutterSoundChanged = { 
                        onPlayShutterSoundChange(it)
                        onAutoSave()
                    },
                    onAutoSave = onAutoSave
                )
            }
            actionTypeFrequent, actionTypeSmart -> {
                RankActionFields(
                    rank = rank.toString(),
                    onRankChanged = { onRankChange(it.toIntOrNull() ?: 1) },
                    onAutoSave = onAutoSave
                )
            }
            actionTypePrevious -> {
                RankActionFields(
                    rank = rank.toString(),
                    onRankChanged = { onRankChange(it.toIntOrNull() ?: 1) },
                    labelOverride = stringResource(R.string.action_previous_action_rank),
                    onAutoSave = onAutoSave
                )
            }
            actionTypeWeather -> {
                WeatherActionFields()
            }
            
            // Device Actions (mapped to flat strings)
            actionTypeReadNotifications,
            actionTypeClearNotifications,
            actionTypeSendMessage,
            actionTypeStartCall,
            actionTypeMediaPlayPause,
            actionTypeMediaNext,
            actionTypeMediaPrevious,
            actionTypeReadTime,
            actionTypeReadDate,
            actionTypeReadCalendarEntries,
            actionTypeReadBattery,
            actionTypeVolumeMedia,
            actionTypeVolumeNotification,
            actionTypeVolumeAlarm,
            actionTypeVolumeCall,
            actionTypeStatusSilent,
            actionTypeStatusVibrate,
            actionTypeStatusLoud,
            actionTypeToggleScanning,
            actionTypeInstallUpdate,
            actionTypeStartSync -> {
                val mappedDeviceActionType = when (selectedActionType) {
                    actionTypeReadNotifications -> DeviceActionType.READ_NOTIFICATIONS
                    actionTypeClearNotifications -> DeviceActionType.CLEAR_NOTIFICATIONS
                    actionTypeSendMessage -> DeviceActionType.SEND_MESSAGE
                    actionTypeStartCall -> DeviceActionType.START_CALL
                    actionTypeMediaPlayPause -> DeviceActionType.MEDIA_PLAY_PAUSE
                    actionTypeMediaNext -> DeviceActionType.MEDIA_NEXT
                    actionTypeMediaPrevious -> DeviceActionType.MEDIA_PREVIOUS
                    actionTypeReadTime -> DeviceActionType.READ_TIME
                    actionTypeReadDate -> DeviceActionType.READ_DATE
                    actionTypeReadCalendarEntries -> DeviceActionType.READ_CALENDAR_ENTRIES
                    actionTypeReadBattery -> DeviceActionType.READ_BATTERY
                    actionTypeVolumeMedia -> DeviceActionType.VOLUME_MEDIA
                    actionTypeVolumeNotification -> DeviceActionType.VOLUME_NOTIFICATION
                    actionTypeVolumeAlarm -> DeviceActionType.VOLUME_ALARM
                    actionTypeVolumeCall -> DeviceActionType.VOLUME_CALL
                    actionTypeStatusSilent -> DeviceActionType.STATUS_SILENT
                    actionTypeStatusVibrate -> DeviceActionType.STATUS_VIBRATE
                    actionTypeStatusLoud -> DeviceActionType.STATUS_LOUD
                    actionTypeToggleScanning -> DeviceActionType.TOGGLE_SCANNING
                    actionTypeInstallUpdate -> DeviceActionType.INSTALL_UPDATE
                    actionTypeStartSync -> DeviceActionType.START_SYNC
                    else -> DeviceActionType.READ_TIME
                }
                DeviceActionFields(
                    selectedType = mappedDeviceActionType,
                    onTypeSelected = {},
                    volumeValue = volumeValue,
                    onVolumeValueChange = onVolumeValueChange,
                    contactName = contactName,
                    contactPhone = contactPhone,
                    onContactSelected = onContactSelected,
                    messageText = messageText,
                    onMessageTextChange = onMessageTextChange,
                    includeWeekday = includeWeekday,
                    onIncludeWeekdayChange = onIncludeWeekdayChange,
                    prefixText = prefixText,
                    onPrefixTextChange = onPrefixTextChange,
                    suffixText = suffixText,
                    onSuffixTextChange = onSuffixTextChange,
                    offsetValue = offsetValue,
                    onOffsetValueChange = onOffsetValueChange,
                    ignoreEmojis = ignoreEmojis,
                    onIgnoreEmojisChange = onIgnoreEmojisChange,
                    onAutoSave = onAutoSave,
                    onlyShowConfig = true
                )
            }

            // Smart Home Actions (mapped to flat strings)
            actionTypePhilipsHue,
            actionTypeGoogleHome -> {
                val mappedSmartHomeProvider = when (selectedActionType) {
                    actionTypePhilipsHue -> SmartHomeProvider.PHILIPS_HUE
                    actionTypeGoogleHome -> SmartHomeProvider.GOOGLE_HOME
                    else -> SmartHomeProvider.PHILIPS_HUE
                }
                SmartHomeActionFields(
                    selectedProvider = mappedSmartHomeProvider,
                    onProviderSelected = {},
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
                    onRefresh = onFetchDevices,
                    onAutoSave = onAutoSave,
                    onlyShowConfig = true
                )
            }

            // Media Playback Actions (mapped to flat strings)
            actionTypeSpotify,
            actionTypeYoutube,
            actionTypeYoutubeMusic,
            actionTypeAudible -> {
                val mappedMediaProvider = when (selectedActionType) {
                    actionTypeSpotify -> MediaProvider.SPOTIFY
                    actionTypeYoutube -> MediaProvider.YOUTUBE
                    actionTypeYoutubeMusic -> MediaProvider.YOUTUBE_MUSIC
                    actionTypeAudible -> MediaProvider.AUDIBLE
                    else -> MediaProvider.SPOTIFY
                }
                PlayMediaActionFields(
                    selectedProvider = mappedMediaProvider,
                    onProviderSelected = {},
                    contentUri = mediaContentUri,
                    onContentUriChanged = onMediaContentUriChange,
                    contentName = mediaContentName,
                    onContentNameChanged = onMediaContentNameChange,
                    returnToAppDelaySec = mediaReturnToAppDelaySec,
                    onReturnToAppDelaySecChanged = onMediaReturnToAppDelaySecChange,
                    forcePlayViaMediaSession = mediaForcePlayViaMediaSession,
                    onForcePlayViaMediaSessionChanged = onMediaForcePlayViaMediaSessionChange,
                    spotifyPlaylists = spotifyPlaylists,
                    isLoadingSpotifyPlaylists = isLoadingSpotifyPlaylists,
                    spotifyUserDisplayName = spotifyUserDisplayName,
                    onConnectSpotify = onConnectSpotify,
                    onDisconnectSpotify = onDisconnectSpotify,
                    onLoadSpotifyPlaylists = onLoadSpotifyPlaylists,
                    onAutoSave = onAutoSave,
                    onlyShowConfig = true
                )
            }
        }
    }
}
