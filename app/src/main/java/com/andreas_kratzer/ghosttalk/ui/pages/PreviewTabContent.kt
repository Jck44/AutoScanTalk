package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.MediaProvider
import com.andreas_kratzer.ghosttalk.core.model.PredictionType

@Composable
fun PreviewTabContent(
    selectedActionType: String,
    spokenText: String,
    label: String,
    geminiPrompt: String,
    targetPageId: String,
    deviceActionType: DeviceActionType,
    includeWeekday: Boolean,
    offsetValue: String,
    prefixText: String,
    suffixText: String,
    contactName: String,
    contactPhone: String,
    messageText: String,
    smartHomeDeviceName: String,
    playActionAsAuditoryCue: Boolean,
    auditoryCueText: String,
    mediaProvider: MediaProvider = MediaProvider.SPOTIFY,
    mediaContentName: String = "",
    mediaReturnToAppDelaySec: String = "2",
    rank: Int = 1,
    predictionType: PredictionType = PredictionType.ALL,
    actionTypeSpeak: String = stringResource(R.string.button_action_speak_text),
    actionTypeNavigate: String = stringResource(R.string.button_action_navigate_page),
    actionTypeGemini: String = stringResource(R.string.button_action_gemini),
    actionTypeGeminiNano: String = stringResource(R.string.button_action_gemini_nano),
    actionTypeGeminiVision: String = stringResource(R.string.button_action_gemini_vision),
    actionTypeWeather: String = stringResource(R.string.button_action_weather),
    actionTypeDevice: String = stringResource(R.string.button_action_control_device),
    actionTypeSmartHome: String = stringResource(R.string.button_action_smart_home),
    actionTypePlayMedia: String = stringResource(R.string.button_action_play_media),
    actionTypeFrequent: String = stringResource(R.string.button_action_frequent_action),
    actionTypePrevious: String = stringResource(R.string.action_previous_action),
    actionTypeSmart: String = stringResource(R.string.button_action_smart_prediction)
) {
    val isSpeech = selectedActionType == actionTypeSpeak
    val speakTextToUse = if (isSpeech) {
        spokenText.takeIf { it.isNotBlank() } ?: label
    } else ""

    // Resolve string resources safely outside the remember block
    val frequentActionSpeak = stringResource(R.string.button_preview_frequent_action_speak, rank)
    val previousActionSpeak = stringResource(R.string.button_preview_previous_action_speak, rank)
    val smartTypeAll = stringResource(R.string.button_smart_prediction_type_all)
    val smartTypeAction = stringResource(R.string.button_smart_prediction_type_action)
    val smartTypeNav = stringResource(R.string.button_smart_prediction_type_navigation)
    val smartFilterText = when (predictionType) {
        PredictionType.ALL -> smartTypeAll
        PredictionType.ACTION -> smartTypeAction
        PredictionType.NAVIGATION -> smartTypeNav
    }
    val smartPredictionSpeak = stringResource(R.string.button_preview_smart_prediction_speak, rank, smartFilterText)

    val frequentActionCue1 = stringResource(R.string.button_preview_frequent_action_cue_1)
    val frequentActionCueN = stringResource(R.string.button_preview_frequent_action_cue_n, rank)
    val previousActionCue1 = stringResource(R.string.button_preview_previous_action_cue_1)
    val previousActionCueN = stringResource(R.string.button_preview_previous_action_cue_n, rank)
    val smartPredictionCue = stringResource(R.string.button_preview_smart_prediction_cue)

    val speakCustomFormat = stringResource(R.string.button_preview_speak_custom)
    val speakFallbackFormat = stringResource(R.string.button_preview_speak_fallback)
    val mediaDelayReturnFormat = stringResource(R.string.button_preview_media_delay_return)
    val mediaDelayStay = stringResource(R.string.button_preview_media_delay_stay)
    val playMediaFormat = stringResource(R.string.button_preview_play_media)
    val navigateWithFeedbackFormat = stringResource(R.string.button_preview_navigate_with_feedback)
    val navigateOnlyFormat = stringResource(R.string.button_preview_navigate_only)
    val geminiCloudFormat = stringResource(R.string.button_preview_gemini_cloud)
    val geminiNanoFormat = stringResource(R.string.button_preview_gemini_nano)
    val geminiVision = stringResource(R.string.button_preview_gemini_vision)
    val weatherAnnounce = stringResource(R.string.button_preview_weather)
    val smartHomeFormat = stringResource(R.string.button_preview_smart_home)
    val dynamicAction = stringResource(R.string.button_preview_dynamic_action)
    val cueSpeechFormat = stringResource(R.string.button_preview_cue_speech)
    val cueAction = stringResource(R.string.button_preview_cue_action)
    val cueCustomFormat = stringResource(R.string.button_preview_cue_custom)
    val cueFallbackFormat = stringResource(R.string.button_preview_cue_fallback)
    val spokenTextFormat = stringResource(R.string.button_preview_spoken_text_format)
    val calendarFormat = stringResource(R.string.button_preview_calendar_format)
    val batteryFormat = stringResource(R.string.button_preview_battery_format)
    val smsFormat = stringResource(R.string.button_preview_sms_format)
    val smsLastSpokenFormat = stringResource(R.string.button_preview_sms_last_spoken_format)
    val callFormat = stringResource(R.string.button_preview_call_format)
    val deviceFunctionFormat = stringResource(R.string.button_preview_device_function)

    // Device Action type display names
    val deviceActionReadTime = stringResource(R.string.device_action_read_time)
    val deviceActionReadDate = stringResource(R.string.device_action_read_date)
    val deviceActionReadBattery = stringResource(R.string.device_action_read_battery)
    val deviceActionReadCalendar = stringResource(R.string.device_action_read_calendar)
    val deviceActionSendMessage = stringResource(R.string.device_action_send_message)
    val deviceActionSendLastSpokenSms = stringResource(R.string.device_action_send_last_spoken_sms)
    val deviceActionStartCall = stringResource(R.string.device_action_start_call)
    val deviceActionVolumeMedia = stringResource(R.string.device_action_volume_media)
    val deviceActionVolumeInAppTts = stringResource(R.string.device_action_volume_in_app_tts)
    val deviceActionVolumeInAppCues = stringResource(R.string.device_action_volume_in_app_cues)
    val deviceActionVolumeNotification = stringResource(R.string.device_action_volume_notification)
    val deviceActionVolumeAlarm = stringResource(R.string.device_action_volume_alarm)
    val deviceActionVolumeCall = stringResource(R.string.device_action_volume_call)
    val deviceActionStatusSilent = stringResource(R.string.device_action_status_silent)
    val deviceActionStatusVibrate = stringResource(R.string.device_action_status_vibrate)
    val deviceActionStatusLoud = stringResource(R.string.device_action_status_loud)
    val deviceActionMediaPlayPause = stringResource(R.string.device_action_media_play_pause)
    val deviceActionMediaNext = stringResource(R.string.device_action_media_next)
    val deviceActionMediaPrevious = stringResource(R.string.device_action_media_previous)
    val deviceActionToggleScanning = stringResource(R.string.device_action_toggle_scanning)
    val deviceActionInstallUpdate = stringResource(R.string.device_action_install_update)
    val deviceActionStartSync = stringResource(R.string.device_action_start_sync)
    val deviceActionReadNotifications = stringResource(R.string.device_action_read_notifications)
    val deviceActionClearNotifications = stringResource(R.string.device_action_clear_notifications)
    val deviceActionToggleAutoReadNotifications = stringResource(R.string.device_action_toggle_auto_read_notifications)

    val speakDescription = remember(
        selectedActionType, spokenText, label, geminiPrompt, targetPageId,
        deviceActionType, includeWeekday, offsetValue, prefixText, suffixText,
        contactName, contactPhone, messageText, smartHomeDeviceName,
        mediaProvider, mediaContentName, mediaReturnToAppDelaySec,
        frequentActionSpeak, previousActionSpeak, smartPredictionSpeak,
        speakCustomFormat, speakFallbackFormat, mediaDelayReturnFormat, mediaDelayStay,
        playMediaFormat, navigateWithFeedbackFormat, navigateOnlyFormat, geminiCloudFormat,
        geminiNanoFormat, geminiVision, weatherAnnounce, smartHomeFormat, dynamicAction,
        spokenTextFormat, calendarFormat, batteryFormat, smsFormat, smsLastSpokenFormat, callFormat,
        deviceFunctionFormat, deviceActionReadTime, deviceActionReadDate, deviceActionReadBattery,
        deviceActionReadCalendar, deviceActionSendMessage, deviceActionSendLastSpokenSms, deviceActionStartCall,
        deviceActionVolumeMedia, deviceActionVolumeInAppTts, deviceActionVolumeInAppCues,
        deviceActionVolumeNotification, deviceActionVolumeAlarm, deviceActionVolumeCall,
        deviceActionStatusSilent, deviceActionStatusVibrate, deviceActionStatusLoud,
        deviceActionMediaPlayPause, deviceActionMediaNext, deviceActionMediaPrevious,
        deviceActionToggleScanning, deviceActionInstallUpdate, deviceActionStartSync,
        deviceActionReadNotifications, deviceActionClearNotifications,
        deviceActionToggleAutoReadNotifications
    ) {
        when {
            isSpeech -> {
                if (spokenText.isNotBlank()) {
                    speakCustomFormat.format(spokenText)
                } else {
                    speakFallbackFormat.format(label)
                }
            }
            else -> {
                when (selectedActionType) {
                    actionTypePlayMedia -> {
                        val nameStr = mediaContentName.ifBlank { mediaProvider.displayName }
                        val delayVal = mediaReturnToAppDelaySec.toIntOrNull() ?: 2
                        val delayDesc = if (delayVal > 0) mediaDelayReturnFormat.format(delayVal) else mediaDelayStay
                        playMediaFormat.format(mediaProvider.displayName, nameStr, delayDesc)
                    }
                    actionTypeNavigate -> {
                        if (spokenText.isNotBlank()) {
                            navigateWithFeedbackFormat.format(spokenText, targetPageId)
                        } else {
                            navigateOnlyFormat.format(targetPageId)
                        }
                    }
                    actionTypeGemini -> geminiCloudFormat.format(geminiPrompt)
                    actionTypeGeminiNano -> geminiNanoFormat.format(geminiPrompt)
                    actionTypeGeminiVision -> geminiVision
                    actionTypeDevice -> {
                        val actionName = when (deviceActionType) {
                            DeviceActionType.READ_TIME -> deviceActionReadTime
                            DeviceActionType.READ_DATE -> deviceActionReadDate
                            DeviceActionType.READ_BATTERY -> deviceActionReadBattery
                            DeviceActionType.READ_CALENDAR_ENTRIES -> deviceActionReadCalendar
                            DeviceActionType.SEND_MESSAGE -> deviceActionSendMessage
                            DeviceActionType.SEND_LAST_SPOKEN_SMS -> deviceActionSendLastSpokenSms
                            DeviceActionType.START_CALL -> deviceActionStartCall
                            DeviceActionType.VOLUME_MEDIA -> deviceActionVolumeMedia
                            DeviceActionType.VOLUME_IN_APP_TTS -> deviceActionVolumeInAppTts
                            DeviceActionType.VOLUME_IN_APP_CUES -> deviceActionVolumeInAppCues
                            DeviceActionType.VOLUME_NOTIFICATION -> deviceActionVolumeNotification
                            DeviceActionType.VOLUME_ALARM -> deviceActionVolumeAlarm
                            DeviceActionType.VOLUME_CALL -> deviceActionVolumeCall
                            DeviceActionType.STATUS_SILENT -> deviceActionStatusSilent
                            DeviceActionType.STATUS_VIBRATE -> deviceActionStatusVibrate
                            DeviceActionType.STATUS_LOUD -> deviceActionStatusLoud
                            DeviceActionType.MEDIA_PLAY_PAUSE -> deviceActionMediaPlayPause
                            DeviceActionType.MEDIA_NEXT -> deviceActionMediaNext
                            DeviceActionType.MEDIA_PREVIOUS -> deviceActionMediaPrevious
                            DeviceActionType.TOGGLE_SCANNING -> deviceActionToggleScanning
                            DeviceActionType.INSTALL_UPDATE -> deviceActionInstallUpdate
                            DeviceActionType.START_SYNC -> deviceActionStartSync
                            DeviceActionType.READ_NOTIFICATIONS -> deviceActionReadNotifications
                            DeviceActionType.CLEAR_NOTIFICATIONS -> deviceActionClearNotifications
                            DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS -> deviceActionToggleAutoReadNotifications
                        }
                        val specificText = try {
                            val calendar = java.util.Calendar.getInstance()
                            val offsetInt = offsetValue.toIntOrNull() ?: 0
                            if (deviceActionType == DeviceActionType.READ_TIME) {
                                if (offsetInt != 0) {
                                    calendar.add(java.util.Calendar.MINUTE, offsetInt)
                                }
                                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                val timeString = sdf.format(calendar.time)
                                val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                                val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                                spokenTextFormat.format("$prefix$timeString$suffix")
                            } else if (deviceActionType == DeviceActionType.READ_DATE) {
                                if (offsetInt != 0) {
                                    calendar.add(java.util.Calendar.DAY_OF_YEAR, offsetInt)
                                }
                                val pattern = if (includeWeekday) "EEEE, dd. MMMM yyyy" else "dd. MMMM yyyy"
                                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                                val dateString = sdf.format(calendar.time)
                                val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                                val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                                spokenTextFormat.format("$prefix$dateString$suffix")
                            } else if (deviceActionType == DeviceActionType.READ_CALENDAR_ENTRIES) {
                                val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                                val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                                calendarFormat.format("${prefix}[Termine]${suffix}", offsetInt)
                            } else if (deviceActionType == DeviceActionType.READ_BATTERY) {
                                batteryFormat
                            } else if (deviceActionType == DeviceActionType.SEND_MESSAGE) {
                                smsFormat.format(contactName, messageText)
                            } else if (deviceActionType == DeviceActionType.SEND_LAST_SPOKEN_SMS) {
                                smsLastSpokenFormat.format(contactName)
                            } else if (deviceActionType == DeviceActionType.START_CALL) {
                                callFormat.format(contactName, contactPhone)
                            } else {
                                ""
                            }
                        } catch (_: Exception) {
                            ""
                        }
                        deviceFunctionFormat.format(actionName, specificText)
                    }
                    actionTypeWeather -> weatherAnnounce
                    actionTypeSmartHome -> smartHomeFormat.format(smartHomeDeviceName)
                    actionTypeFrequent -> frequentActionSpeak
                    actionTypePrevious -> previousActionSpeak
                    actionTypeSmart -> smartPredictionSpeak
                    else -> dynamicAction
                }
            }
        }
    }

    val cueDescription = remember(
        playActionAsAuditoryCue, isSpeech, speakTextToUse, auditoryCueText, label, selectedActionType,
        frequentActionCue1, frequentActionCueN, previousActionCue1, previousActionCueN, smartPredictionCue,
        cueSpeechFormat, cueAction, cueCustomFormat, cueFallbackFormat
    ) {
        when {
            playActionAsAuditoryCue -> {
                if (isSpeech) {
                    cueSpeechFormat.format(speakTextToUse)
                } else {
                    cueAction
                }
            }
            auditoryCueText.isNotBlank() -> {
                cueCustomFormat.format(auditoryCueText)
            }
            else -> {
                when (selectedActionType) {
                    actionTypeFrequent -> {
                        if (rank == 1) {
                            "🔊 $frequentActionCue1"
                        } else {
                            "🔊 $frequentActionCueN"
                        }
                    }
                    actionTypePrevious -> {
                        if (rank == 1) {
                            "🔊 $previousActionCue1"
                        } else {
                            "🔊 $previousActionCueN"
                        }
                    }
                    actionTypeSmart -> {
                        "🔊 $smartPredictionCue"
                    }
                    else -> {
                        cueFallbackFormat.format(label)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.button_preview_header_speak),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = speakDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.button_preview_header_cue),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = cueDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

