package com.andreas_kratzer.ghosttalk.ui.pages.components

import android.content.Context
import androidx.compose.runtime.Immutable
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.PredictionType
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode

@Immutable
data class ButtonSettingsUiState(
    val label: String = "",
    val spokenText: String = "",
    val spokenTextMode: SpokenTextMode = SpokenTextMode.TTS,
    val audioFileName: String? = null,
    val auditoryCueText: String = "",
    val isActive: Boolean = true,
    val playActionAsAuditoryCue: Boolean = false,
    val selectedActionType: String = "",
    val targetPageId: String = "",
    val geminiPrompt: String = "",
    val geminiVisionUseCloud: Boolean = false,
    val geminiVisionPlayShutterSound: Boolean = false,
    val rank: Int = 1,
    val predictionType: PredictionType = PredictionType.ALL,
    val deviceActionType: DeviceActionType = DeviceActionType.READ_TIME,
    val volumeValue: String = "50",
    val contactName: String = "",
    val contactPhone: String = "",
    val messageText: String = "",
    val includeWeekday: Boolean = false,
    val prefixText: String = "",
    val suffixText: String = "",
    val offsetValue: String = "0",
    val ignoreEmojis: Boolean = false,
    val smartHomeProvider: SmartHomeProvider = SmartHomeProvider.PHILIPS_HUE,
    val smartHomeDeviceId: String = "",
    val smartHomeDeviceName: String = "",
    val smartHomeIntent: String = "",
    val smartHomeValue: String = "",
    val mediaProvider: MediaProvider = MediaProvider.SPOTIFY,
    val mediaContentUri: String = "",
    val mediaContentName: String = "",
    val mediaReturnToAppDelaySec: String = "0",
    val mediaForcePlayViaMediaSession: Boolean = false,
    val isRecording: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isFetchingDevices: Boolean = false,
    val isLoadingSpotifyPlaylists: Boolean = false,
    val spotifyUserDisplayName: String? = null
)

@Immutable
data class ButtonSettingsActions(
    val onLabelChange: (String) -> Unit,
    val onSpokenTextChange: (String) -> Unit,
    val onSpokenTextModeChange: (SpokenTextMode) -> Unit,
    val onAudioFileNameChange: (String?) -> Unit,
    val onAuditoryCueTextChange: (String) -> Unit,
    val onIsActiveChange: (Boolean) -> Unit,
    val onPlayActionAsAuditoryCueChange: (Boolean) -> Unit,
    val onSelectedActionTypeChange: (String) -> Unit,
    val onTargetPageIdChange: (String) -> Unit,
    val onGeminiPromptChange: (String) -> Unit,
    val onGeminiVisionUseCloudChange: (Boolean) -> Unit,
    val onGeminiVisionPlayShutterSoundChange: (Boolean) -> Unit,
    val onRankChange: (Int) -> Unit,
    val onPredictionTypeChange: (PredictionType) -> Unit,
    val onDeviceActionTypeChange: (DeviceActionType) -> Unit,
    val onVolumeValueChange: (String) -> Unit,
    val onContactNameChange: (String) -> Unit,
    val onContactPhoneChange: (String) -> Unit,
    val onMessageTextChange: (String) -> Unit,
    val onIncludeWeekdayChange: (Boolean) -> Unit,
    val onPrefixTextChange: (String) -> Unit,
    val onSuffixTextChange: (String) -> Unit,
    val onOffsetValueChange: (String) -> Unit,
    val onIgnoreEmojisChange: (Boolean) -> Unit,
    val onSmartHomeProviderChange: (SmartHomeProvider) -> Unit,
    val onSmartHomeDeviceIdChange: (String) -> Unit,
    val onSmartHomeDeviceNameChange: (String) -> Unit,
    val onSmartHomeIntentChange: (String) -> Unit,
    val onSmartHomeValueChange: (String) -> Unit,
    val onMediaProviderChange: (MediaProvider) -> Unit,
    val onMediaContentUriChange: (String) -> Unit,
    val onMediaContentNameChange: (String) -> Unit,
    val onMediaReturnToAppDelaySecChange: (String) -> Unit,
    val onMediaForcePlayViaMediaSessionChange: (Boolean) -> Unit,
    val onIsRecordingChange: (Boolean) -> Unit,
    val onIsPlayingAudioChange: (Boolean) -> Unit,
    val onShowDeleteConfirmationChange: (Boolean) -> Unit,
    val onIsFetchingDevicesChange: (Boolean) -> Unit,
    val onConnectSpotify: () -> Unit,
    val onDisconnectSpotify: () -> Unit,
    val onLoadSpotifyPlaylists: () -> Unit,
    val onStartVoiceRecording: () -> Unit,
    val onStopVoiceRecording: () -> Unit,
    val onPlayRecording: (java.io.File) -> Unit,
    val buildCurrentAction: () -> ButtonAction,
    val handleAutoSave: () -> Unit,
    val saveWithAction: (ButtonAction) -> Unit
)

class ActionTypeResolver(context: Context) {
    val actionTypeSpeak = context.getString(R.string.button_action_speak_text)
    val actionTypeNavigate = context.getString(R.string.button_action_navigate_page)
    val actionTypeNavigateBack = context.getString(R.string.button_action_navigate_back)
    val actionTypeNavigateToStartPage = context.getString(R.string.button_action_navigate_to_start_page)
    val actionTypeGemini = context.getString(R.string.button_action_gemini)
    val actionTypeGeminiSearch = context.getString(R.string.button_action_gemini_search)
    val actionTypeGeminiVision = context.getString(R.string.button_action_gemini_vision)
    val actionTypeWeather = context.getString(R.string.button_action_weather)
    val actionTypeReadNotifications = context.getString(R.string.button_action_notification)
    val actionTypeClearNotifications = context.getString(R.string.button_action_clear_notifications)
    val actionTypeSendMessage = context.getString(R.string.action_send_message)
    val actionTypeSendLastSpokenSms = context.getString(R.string.device_action_send_last_spoken_sms)
    val actionTypeStartCall = context.getString(R.string.action_start_call)
    val actionTypeMediaPlayPause = context.getString(R.string.button_device_control_media_play_pause)
    val actionTypeMediaNext = context.getString(R.string.button_device_control_media_next)
    val actionTypeMediaPrevious = context.getString(R.string.button_device_control_media_previous)
    val actionTypeReadTime = context.getString(R.string.button_device_control_time)
    val actionTypeReadDate = context.getString(R.string.button_device_control_date)
    val actionTypeReadCalendarEntries = context.getString(R.string.button_device_control_calendar)
    val actionTypeReadBattery = context.getString(R.string.button_device_control_battery)
    val actionTypeVolumeMedia = context.getString(R.string.volume_media)
    val actionTypeVolumeNotification = context.getString(R.string.volume_notification)
    val actionTypeVolumeAlarm = context.getString(R.string.volume_alarm)
    val actionTypeVolumeCall = context.getString(R.string.volume_call)
    val actionTypeVolumeInAppTts = context.getString(R.string.volume_in_app_tts)
    val actionTypeVolumeInAppCues = context.getString(R.string.volume_in_app_cues)
    val actionTypeStatusSilent = context.getString(R.string.status_silent)
    val actionTypeStatusVibrate = context.getString(R.string.status_vibrate)
    val actionTypeStatusLoud = context.getString(R.string.status_loud)
    val actionTypeToggleScanning = context.getString(R.string.button_device_control_toggle_scanning)
    val actionTypeStartSync = context.getString(R.string.button_device_control_start_sync)
    val actionTypeToggleAutoRead = "Automatisches Vorlesen umschalten"
    val actionTypeSpotify = "Spotify abspielen"
    val actionTypeYoutube = "YouTube abspielen"
    val actionTypeYoutubeMusic = "YouTube Music abspielen"
    val actionTypeAudible = "Audible abspielen"
    val actionTypePhilipsHue = "Philips Hue steuern"
    val actionTypeGoogleHome = "Google Home steuern"
    val actionTypeFrequent = context.getString(R.string.button_action_frequent_action)
    val actionTypePrevious = context.getString(R.string.action_previous_action)
    val actionTypeSmart = context.getString(R.string.button_action_smart_prediction)
}
