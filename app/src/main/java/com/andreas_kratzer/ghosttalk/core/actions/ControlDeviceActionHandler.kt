package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
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
            else -> {
                log("Aktion ${action.actionType} noch nicht implementiert.")
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
}
