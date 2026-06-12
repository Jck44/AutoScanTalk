package com.andreas_kratzer.ghosttalk.ui.pages.components

import android.content.Context
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction

internal fun getLocalLabelSuggestion(
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
