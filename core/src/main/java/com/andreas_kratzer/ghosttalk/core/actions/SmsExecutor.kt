package com.andreas_kratzer.ghosttalk.core.actions

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actionLogger: ActionLogger,
    private val settings: ControlDeviceSettings,
    private val ttsProxyLazy: dagger.Lazy<ControlDeviceTtsProxy>
) {
    fun handleSendMessage(action: ControlDeviceButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        sendSmsInternal(action.contactPhone, action.messageText ?: "", action, label, executionId, onFinish)
    }

    fun handleSendLastSpokenSms(
        config: ButtonConfig,
        action: ControlDeviceButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val lastSpoken = actionLogger.lastSpokenText
        if (lastSpoken.isBlank()) {
            val msg = "Es wurde noch kein Text gesprochen."
            actionLogger.log(msg, action, config.label)
            val targetDeviceAddress = if (config.playActionAsAuditoryCue) {
                settings.cuesAudioDeviceAddress
            } else {
                settings.ttsAudioDeviceAddress
            }
            val tts = ttsProxyLazy.get()
            if (tts.isReady) {
                tts.speakRouted(msg, targetDeviceAddress) {
                    onFinish(executionId)
                }
            } else {
                onFinish(executionId)
            }
            return
        }
        sendSmsInternal(action.contactPhone, lastSpoken, action, config.label, executionId, onFinish)
    }

    private fun sendSmsInternal(
        phone: String?,
        message: String,
        action: ControlDeviceButtonAction,
        label: String?,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        if (phone.isNullOrBlank()) {
            actionLogger.log("Kein Kontakt ausgewählt.", action, label)
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
                actionLogger.log(result, action, label)
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
            actionLogger.log("SMS-Timeout: Keine Rückmeldung vom System.", action, label)
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            onFinish(executionId)
        }, 15000) // 15 seconds timeout for multipart messages

        context.registerReceiver(receiver, IntentFilter(sentAction), Context.RECEIVER_NOT_EXPORTED)

        try {
            actionLogger.log("Sende SMS an $phone...", action, label)
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
            actionLogger.log("SMS-Sendeversuch fehlgeschlagen: ${e.message}", action, label)
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            onFinish(executionId)
        }
    }
}
