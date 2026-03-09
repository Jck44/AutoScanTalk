package com.andreas_kratzer.ghosttalk.core.actions

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.view.KeyEvent
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.services.NotificationReaderService
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper

class ControlDeviceActionHandler(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper?,
    private val log: (String) -> Unit
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is ControlDeviceButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val deviceAction = action as ControlDeviceButtonAction
        when (deviceAction.actionType) {
            DeviceActionType.MEDIA_NEXT -> handleMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Nächstes Lied", executionId, onFinish)
            DeviceActionType.MEDIA_PREVIOUS -> handleMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Vorheriges Lied", executionId, onFinish)
            DeviceActionType.MEDIA_PLAY_PAUSE -> handleMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Start / Stop", executionId, onFinish)
            DeviceActionType.READ_NOTIFICATIONS -> handleReadNotifications(buttonConfig, deviceAction, executionId, onFinish)
            
            DeviceActionType.VOLUME_NOTIFICATION -> handleVolume(AudioManager.STREAM_NOTIFICATION, deviceAction, executionId, onFinish)
            DeviceActionType.VOLUME_ALARM -> handleVolume(AudioManager.STREAM_ALARM, deviceAction, executionId, onFinish)
            DeviceActionType.VOLUME_MEDIA -> handleVolume(AudioManager.STREAM_MUSIC, deviceAction, executionId, onFinish)
            DeviceActionType.VOLUME_CALL -> handleVolume(AudioManager.STREAM_VOICE_CALL, deviceAction, executionId, onFinish)
            
            DeviceActionType.STATUS_SILENT -> handleStatus(AudioManager.RINGER_MODE_SILENT, executionId, onFinish)
            DeviceActionType.STATUS_VIBRATE -> handleStatus(AudioManager.RINGER_MODE_VIBRATE, executionId, onFinish)
            DeviceActionType.STATUS_LOUD -> handleStatus(AudioManager.RINGER_MODE_NORMAL, executionId, onFinish)
            
            DeviceActionType.SEND_MESSAGE -> handleSendMessage(deviceAction, executionId, onFinish)
            
            DeviceActionType.READ_BATTERY -> handleReadBattery(buttonConfig, deviceAction, executionId, onFinish)
            DeviceActionType.READ_TIME -> handleReadTime(buttonConfig, deviceAction, executionId, onFinish)
            DeviceActionType.READ_DATE -> handleReadDate(buttonConfig, deviceAction, executionId, onFinish)

            DeviceActionType.CLEAR_NOTIFICATIONS -> {
                log("Benachrichtigungen löschen noch nicht unterstützt.")
                onFinish(executionId)
            }
        }
    }

    private fun handleMediaKey(keyCode: Int, description: String, executionId: Int, onFinish: (Int) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        log(description)
        onFinish(executionId)
    }

    private fun handleVolume(streamType: Int, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val valueStr = action.volumeValue ?: "50"
        
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        val currentVolume = audioManager.getStreamVolume(streamType)
        
        val isRelative = valueStr.startsWith("+") || valueStr.startsWith("-")
        val percentage = valueStr.removePrefix("+").toIntOrNull() ?: 50
        
        val targetVolume = if (isRelative) {
            val change = (maxVolume * (percentage / 100.0)).toInt()
            (currentVolume + change).coerceIn(0, maxVolume)
        } else {
            (maxVolume * (percentage / 100.0)).toInt().coerceIn(0, maxVolume)
        }
        
        audioManager.setStreamVolume(streamType, targetVolume, AudioManager.FLAG_SHOW_UI)
        log("Lautstärke auf ${((targetVolume.toDouble() / maxVolume) * 100).toInt()}% gesetzt")
        onFinish(executionId)
    }

    private fun handleStatus(ringerMode: Int, executionId: Int, onFinish: (Int) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (ringerMode == AudioManager.RINGER_MODE_SILENT && !notificationManager.isNotificationPolicyAccessGranted) {
            log("Berechtigung für 'Nicht stören' fehlt.")
        } else {
            audioManager.ringerMode = ringerMode
            val modeName = when(ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "Lautlos"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibration"
                else -> "Laut"
            }
            log("Modus auf $modeName gesetzt")
        }
        onFinish(executionId)
    }

    private fun handleSendMessage(action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val phone = action.contactPhone
        val message = action.messageText ?: ""

        if (phone.isNullOrBlank()) {
            log("Kein Kontakt ausgewählt.")
            onFinish(executionId)
            return
        }

        val sentAction = "com.andreas_kratzer.ghosttalk.SMS_SENT_${executionId}_${System.currentTimeMillis()}"
        val sentIntent = Intent(sentAction).apply {
            `package` = context.packageName
        }
        val sentPI = PendingIntent.getBroadcast(
            context,
            0,
            sentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val timeoutHandler = Handler(Looper.getMainLooper())
        val receiver = object : BroadcastReceiver() {
            private var isFinished = false
            override fun onReceive(arg0: Context?, arg1: Intent?) {
                if (isFinished) return
                isFinished = true
                timeoutHandler.removeCallbacksAndMessages(null)
                
                val result = when (resultCode) {
                    Activity.RESULT_OK -> "SMS erfolgreich versendet an $phone"
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "SMS-Fehler: Allgemeiner Fehler (Prüfe Netzempfang/Guthaben)"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "SMS-Fehler: Kein Dienst verfügbar"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "SMS-Fehler: Null PDU"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "SMS-Fehler: Funk aus / Flugmodus"
                    else -> "SMS-Fehler: Code $resultCode"
                }
                log(result)
                try {
                    context.unregisterReceiver(this)
                } catch (_: Exception) {
                    // Ignore
                }
                onFinish(executionId)
            }
        }

        // Timeout fallback if system never responds
        timeoutHandler.postDelayed({
            log("SMS-Timeout: Keine Rückmeldung vom System.")
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            onFinish(executionId)
        }, 15000) // 15 seconds timeout for multipart messages

        context.registerReceiver(receiver, IntentFilter(sentAction), Context.RECEIVER_NOT_EXPORTED)

        try {
            log("Sende SMS an $phone...")
            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(message)
            
            if (parts.size > 1) {
                // For long messages, we only track the last part's success for logging
                val sentIntents = ArrayList<PendingIntent>()
                for (i in 0 until parts.size) {
                    sentIntents.add(if (i == parts.size - 1) sentPI else 
                        PendingIntent.getBroadcast(context, i + 1000, Intent("DUMMY"), PendingIntent.FLAG_IMMUTABLE))
                }
                smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(phone, null, message, sentPI, null)
            }
        } catch (e: Exception) {
            timeoutHandler.removeCallbacksAndMessages(null)
            log("SMS-Sendeversuch fehlgeschlagen: ${e.message}")
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            onFinish(executionId)
        }
    }

    private fun handleReadNotifications(
        buttonConfig: ButtonConfig,
        action: ControlDeviceButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val service = NotificationReaderService.instance
        val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
            settingsRepository.cuesAudioDeviceAddress
        } else {
            settingsRepository.ttsAudioDeviceAddress
        }

        val tts = ttsHelper
        if (service == null || !settingsRepository.isNotificationReadingEnabled) {
            val msg = "Vorlesen von Benachrichtigungen nicht aktiv oder Berechtigung fehlt."
            log(msg)
            if (tts?.isReady == true) {
                tts.speakRouted(msg, targetDeviceAddress, action.ttsMode) {
                    onFinish(executionId)
                }
            } else onFinish(executionId)
            return
        }

        val activeNotifs = try {
            service.activeNotifications
        } catch (e: Exception) {
            log("Fehler beim Abrufen der Benachrichtigungen: ${e.message}")
            null
        }
        
        if (activeNotifs == null || activeNotifs.isEmpty()) {
            val msg = "Keine Benachrichtigungen vorhanden."
            log(msg)
            if (tts?.isReady == true) {
                tts.speakRouted(msg, targetDeviceAddress, action.ttsMode) {
                    onFinish(executionId)
                }
            } else onFinish(executionId)
            return
        }

        val allowedApps = settingsRepository.monitoredNotificationApps
        val filtered = activeNotifs.filter { sbn ->
            val pkg = sbn.packageName
            allowedApps.contains(pkg)
        }

        if (filtered.isEmpty()) {
            val msg = "Keine passenden Benachrichtigungen gefunden."
            log(msg)
            if (tts?.isReady == true) {
                tts.speakRouted(msg, targetDeviceAddress, action.ttsMode) {
                    onFinish(executionId)
                }
            } else onFinish(executionId)
            return
        }

        val messagesToRead = filtered.mapNotNull { sbn ->
            val extras = sbn.notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: "Unbekannt"
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            if (text.isNullOrBlank()) null else "Von $title: $text"
        }

        if (messagesToRead.isEmpty()) {
            val msg = "Benachrichtigungen enthalten keinen Text."
            log(msg)
            if (tts?.isReady == true) {
                tts.speakRouted(msg, targetDeviceAddress, action.ttsMode) {
                    onFinish(executionId)
                }
            } else onFinish(executionId)
            return
        }

        val combinedMessage = messagesToRead.joinToString(". ")
        log("Lese Benachrichtigungen: $combinedMessage")
        
        tts?.isReadingNotification = true
        if (tts?.isReady == true) {
            tts.speakRouted(combinedMessage, targetDeviceAddress, action.ttsMode) {
                tts.isReadingNotification = false
                onFinish(executionId)
            }
        } else {
            tts?.isReadingNotification = false
            onFinish(executionId)
        }
    }

    private fun handleReadBattery(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val percentage = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        val ssml = context.getString(R.string.action_battery_ssml, percentage)
        val plain = context.getString(R.string.action_battery_plain, percentage)
        
        speakRoutedWithLogging(ssml, plain, config, action, executionId, onFinish)
    }

    private fun handleReadTime(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val pattern = context.getString(R.string.time_format_pattern)
        val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        val time = sdf.format(java.util.Date())
        
        val ssml = context.getString(R.string.action_time_ssml, time)
        val plain = context.getString(R.string.action_time_plain, time)
        
        speakRoutedWithLogging(ssml, plain, config, action, executionId, onFinish)
    }

    private fun handleReadDate(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val pattern = context.getString(R.string.date_format_pattern)
        val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        val date = sdf.format(java.util.Date())
        
        val ssml = context.getString(R.string.action_date_ssml, date)
        val plain = context.getString(R.string.action_date_plain, date)
        
        speakRoutedWithLogging(ssml, plain, config, action, executionId, onFinish)
    }

    private fun speakRoutedWithLogging(
        ssml: String,
        plainText: String,
        config: ButtonConfig,
        action: ControlDeviceButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        log(plainText)
        val targetDeviceAddress = if (config.playActionAsAuditoryCue) {
            settingsRepository.cuesAudioDeviceAddress
        } else {
            settingsRepository.ttsAudioDeviceAddress
        }
        
        if (ttsHelper?.isReady == true) {
            ttsHelper.speakRouted(ssml, targetDeviceAddress, action.ttsMode) {
                onFinish(executionId)
            }
        } else {
            onFinish(executionId)
        }
    }
}
