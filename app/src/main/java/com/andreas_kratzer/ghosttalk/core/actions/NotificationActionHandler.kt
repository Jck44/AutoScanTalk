package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.services.NotificationReaderService
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NotificationButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper

class NotificationActionHandler(
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper?,
    private val log: (String) -> Unit
) : ActionHandler<NotificationButtonAction> {

    override fun canHandle(action: ButtonAction): Boolean = action is NotificationButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: NotificationButtonAction,
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
            val isAllowed = allowedApps.contains(pkg)
            val matchesTarget = action.targetApp == "ALL" || pkg == action.targetApp
            isAllowed && matchesTarget
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
