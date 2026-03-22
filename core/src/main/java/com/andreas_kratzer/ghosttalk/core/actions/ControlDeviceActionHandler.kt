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
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.services.NotificationReaderService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface ControlDeviceTtsProxy {
    val isReady: Boolean
    var isReadingNotification: Boolean
    fun speakRouted(text: String, deviceAddress: String?, onDone: (() -> Unit)? = null)
}


class ControlDeviceActionHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: ControlDeviceSettings,
    private val ttsProxyLazy: dagger.Lazy<ControlDeviceTtsProxy>,
    private val actionLogger: ActionLogger,
    private val actionEventEmitter: ActionEventEmitter
) : ActionHandler {
    
    private val getString: (Int, Array<out Any?>) -> String = { id, args -> 
        // Fallback for battery/time/date which used hardcoded negative IDs in the previous version
        when (id) {
            -1001 -> "Batteriestand ist bei ${args.getOrNull(0) ?: ""} Prozent"
            -1002 -> "Batteriestand ist bei ${args.getOrNull(0) ?: ""} Prozent" 
            else -> try { context.getString(id, *args) } catch(_: Exception) { "" }
        }
    }

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
                actionLogger.log("Benachrichtigungen löschen noch nicht unterstützt.")
                onFinish(executionId)
            }
        }
    }

    private fun handleMediaKey(keyCode: Int, description: String, executionId: Int, onFinish: (Int) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        actionLogger.log(description)
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
        actionLogger.log("Lautstärke auf ${((targetVolume.toDouble() / maxVolume) * 100).toInt()}% gesetzt")
        onFinish(executionId)
    }

    private fun handleStatus(ringerMode: Int, executionId: Int, onFinish: (Int) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (ringerMode == AudioManager.RINGER_MODE_SILENT && !notificationManager.isNotificationPolicyAccessGranted) {
            actionLogger.log("Berechtigung für 'Nicht stören' fehlt.")
        } else {
            audioManager.ringerMode = ringerMode
            val modeName = when(ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "Lautlos"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibration"
                else -> "Laut"
            }
            actionLogger.log("Modus auf $modeName gesetzt")
        }
        onFinish(executionId)
    }

    private fun handleSendMessage(action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val phone = action.contactPhone
        val message = action.messageText ?: ""

        if (phone.isNullOrBlank()) {
            actionLogger.log("Kein Kontakt ausgewählt.")
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
                actionLogger.log(result)
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
            actionLogger.log("SMS-Timeout: Keine Rückmeldung vom System.")
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            onFinish(executionId)
        }, 15000) // 15 seconds timeout for multipart messages

        context.registerReceiver(receiver, IntentFilter(sentAction), Context.RECEIVER_NOT_EXPORTED)

        try {
            actionLogger.log("Sende SMS an $phone...")
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
            actionLogger.log("SMS-Sendeversuch fehlgeschlagen: ${e.message}")
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
            settings.cuesAudioDeviceAddress
        } else {
            settings.ttsAudioDeviceAddress
        }

        val tts = ttsProxyLazy.get()
        if (service == null || !settings.isNotificationReadingEnabled) {
            val msg = "Vorlesen von Benachrichtigungen nicht aktiv oder Berechtigung fehlt."
            actionLogger.log(msg)
            if (tts.isReady) {
                tts.speakRouted(msg, targetDeviceAddress) {
                    onFinish(executionId)
                }
            } else onFinish(executionId)
            return
        }

        val activeNotifs = try {
            service.activeNotifications
        } catch (e: Exception) {
            actionLogger.log("Fehler beim Abrufen der Benachrichtigungen: ${e.message}")
            null
        }
        
        if (activeNotifs == null || activeNotifs.isEmpty()) {
            val msg = "Keine Benachrichtigungen vorhanden."
            actionLogger.log(msg)
            if (tts.isReady) {
                tts.speakRouted(msg, targetDeviceAddress) {
                    onFinish(executionId)
                }
            } else onFinish(executionId)
            return
        }

        val allowedApps = settings.monitoredNotificationApps
        val filtered = activeNotifs.filter { sbn ->
            val pkg = sbn.packageName
            allowedApps.contains(pkg)
        }

        if (filtered.isEmpty()) {
            val msg = "Keine passenden Benachrichtigungen gefunden."
            actionLogger.log(msg)
            if (tts.isReady) {
                tts.speakRouted(msg, targetDeviceAddress) {
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
            actionLogger.log(msg)
            if (tts.isReady) {
                tts.speakRouted(msg, targetDeviceAddress) {
                    onFinish(executionId)
                }
            } else onFinish(executionId)
            return
        }

        val combinedMessage = messagesToRead.joinToString(". ")
        actionLogger.log("Lese Benachrichtigungen: $combinedMessage")
        
        tts.isReadingNotification = true
        if (tts.isReady) {
            tts.speakRouted(combinedMessage, targetDeviceAddress) {
                tts.isReadingNotification = false
                onFinish(executionId)
            }
        } else {
            tts.isReadingNotification = false
            onFinish(executionId)
        }
    }

    private fun handleReadBattery(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val percentage = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        // We use the provided getString lambda (likely IDs from GoSTalk app's R.string)
        val ssml = getString(-1001, arrayOf(percentage)) 
        val plain = getString(-1002, arrayOf(percentage)) 
        
        speakRoutedWithLogging(ssml, plain, config, action, executionId, onFinish)
    }

    // Temporary simplification: Instead of resources, we pass a logic to get them
    private fun handleReadTime(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val time = sdf.format(java.util.Date())
        speakRoutedWithLogging("Es ist $time", "Es ist $time", config, action, executionId, onFinish)
    }

    private fun handleReadDate(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        val date = sdf.format(java.util.Date())
        speakRoutedWithLogging("Heute ist der $date", "Heute ist der $date", config, action, executionId, onFinish)
    }

    private fun speakRoutedWithLogging(
        ssml: String,
        plainText: String,
        config: ButtonConfig,
        action: ControlDeviceButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        actionLogger.log(plainText)
        val targetDeviceAddress = if (config.playActionAsAuditoryCue) {
            settings.cuesAudioDeviceAddress
        } else {
            settings.ttsAudioDeviceAddress
        }
        
        val tts = ttsProxyLazy.get()
        if (tts.isReady) {
            tts.speakRouted(ssml, targetDeviceAddress) {
                onFinish(executionId)
            }
        } else {
            onFinish(executionId)
        }
    }
}
