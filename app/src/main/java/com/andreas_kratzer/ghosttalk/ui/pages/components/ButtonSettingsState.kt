package com.andreas_kratzer.ghosttalk.ui.pages.components

import android.content.Context
import com.andreas_kratzer.ghosttalk.R

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
    val actionTypeInstallUpdate = context.getString(R.string.button_device_control_install_update)
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

    fun getLabel(id: ActionTypeId): String {
        return when (id) {
            ActionTypeId.SPEAK -> actionTypeSpeak
            ActionTypeId.NAVIGATE -> actionTypeNavigate
            ActionTypeId.NAVIGATE_BACK -> actionTypeNavigateBack
            ActionTypeId.NAVIGATE_TO_START_PAGE -> actionTypeNavigateToStartPage
            ActionTypeId.GEMINI -> actionTypeGemini
            ActionTypeId.GEMINI_SEARCH -> actionTypeGeminiSearch
            ActionTypeId.GEMINI_VISION -> actionTypeGeminiVision
            ActionTypeId.WEATHER -> actionTypeWeather
            ActionTypeId.READ_NOTIFICATIONS -> actionTypeReadNotifications
            ActionTypeId.TOGGLE_AUTO_READ -> actionTypeToggleAutoRead
            ActionTypeId.CLEAR_NOTIFICATIONS -> actionTypeClearNotifications
            ActionTypeId.SEND_MESSAGE -> actionTypeSendMessage
            ActionTypeId.SEND_LAST_SPOKEN_SMS -> actionTypeSendLastSpokenSms
            ActionTypeId.START_CALL -> actionTypeStartCall
            ActionTypeId.SPOTIFY -> actionTypeSpotify
            ActionTypeId.YOUTUBE -> actionTypeYoutube
            ActionTypeId.YOUTUBE_MUSIC -> actionTypeYoutubeMusic
            ActionTypeId.AUDIBLE -> actionTypeAudible
            ActionTypeId.MEDIA_PLAY_PAUSE -> actionTypeMediaPlayPause
            ActionTypeId.MEDIA_NEXT -> actionTypeMediaNext
            ActionTypeId.MEDIA_PREVIOUS -> actionTypeMediaPrevious
            ActionTypeId.READ_TIME -> actionTypeReadTime
            ActionTypeId.READ_DATE -> actionTypeReadDate
            ActionTypeId.READ_CALENDAR_ENTRIES -> actionTypeReadCalendarEntries
            ActionTypeId.READ_BATTERY -> actionTypeReadBattery
            ActionTypeId.VOLUME_MEDIA -> actionTypeVolumeMedia
            ActionTypeId.VOLUME_NOTIFICATION -> actionTypeVolumeNotification
            ActionTypeId.VOLUME_ALARM -> actionTypeVolumeAlarm
            ActionTypeId.VOLUME_CALL -> actionTypeVolumeCall
            ActionTypeId.VOLUME_IN_APP_TTS -> actionTypeVolumeInAppTts
            ActionTypeId.VOLUME_IN_APP_CUES -> actionTypeVolumeInAppCues
            ActionTypeId.STATUS_SILENT -> actionTypeStatusSilent
            ActionTypeId.STATUS_VIBRATE -> actionTypeStatusVibrate
            ActionTypeId.STATUS_LOUD -> actionTypeStatusLoud
            ActionTypeId.TOGGLE_SCANNING -> actionTypeToggleScanning
            ActionTypeId.INSTALL_UPDATE -> actionTypeInstallUpdate
            ActionTypeId.START_SYNC -> actionTypeStartSync
            ActionTypeId.PHILIPS_HUE -> actionTypePhilipsHue
            ActionTypeId.GOOGLE_HOME -> actionTypeGoogleHome
            ActionTypeId.FREQUENT -> actionTypeFrequent
            ActionTypeId.PREVIOUS -> actionTypePrevious
            ActionTypeId.SMART -> actionTypeSmart
        }
    }
}
