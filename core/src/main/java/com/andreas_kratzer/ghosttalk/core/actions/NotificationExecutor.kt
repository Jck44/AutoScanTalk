package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.services.NotificationReaderService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actionLogger: ActionLogger,
    private val settings: ControlDeviceSettings,
    private val ttsProxyLazy: dagger.Lazy<ControlDeviceTtsProxy>
) {
    fun handleReadNotifications(
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
        if (service == null) {
            val msg = "Berechtigung für Benachrichtigungs-Zugriff fehlt."
            actionLogger.log(msg, action, buttonConfig.label)
            tts.speakRouted(msg, targetDeviceAddress) {
                onFinish(executionId)
            }
            return
        }

        val activeNotifs = try {
            service.activeNotifications
        } catch (e: Exception) {
            actionLogger.log("Fehler beim Abrufen der Benachrichtigungen: ${e.message}", action, buttonConfig.label)
            null
        }
        
        if (activeNotifs == null || activeNotifs.isEmpty()) {
            val msg = "Keine Benachrichtigungen vorhanden."
            actionLogger.log(msg, action, buttonConfig.label)
            tts.speakRouted(msg, targetDeviceAddress) {
                onFinish(executionId)
            }
            return
        }

        val allowedApps = settings.monitoredNotificationApps
        val targetAppPackage = action.contactPhone
        val filtered = activeNotifs.filter { sbn ->
            val isGroupSummary = (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
            if (isGroupSummary) return@filter false

            val pkg = sbn.packageName
            if (!targetAppPackage.isNullOrBlank()) {
                val targetApps = targetAppPackage.split(",").filter { it.isNotBlank() }
                targetApps.contains(pkg)
            } else {
                allowedApps.contains(pkg)
            }
        }

        if (filtered.isEmpty()) {
            val msg = "Keine passenden Benachrichtigungen gefunden."
            actionLogger.log(msg, action, buttonConfig.label)
            tts.speakRouted(msg, targetDeviceAddress) {
                onFinish(executionId)
            }
            return
        }

        val packageManager = context.packageManager
        val groupedByApp = filtered.groupBy { sbn ->
            try {
                val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                sbn.packageName
            }
        }

        val appMessages = mutableListOf<String>()
        for ((appName, sbns) in groupedByApp) {
            val messagesForApp = sbns.mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: "Unbekannt"
                val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                if (text.isNullOrBlank()) null else "$title: $text"
            }
            if (messagesForApp.isNotEmpty()) {
                val appPrefix = if (appName.isNotBlank()) "$appName: " else ""
                val appBody = messagesForApp.joinToString(". ")
                appMessages.add("$appPrefix$appBody")
            }
        }

        if (appMessages.isEmpty()) {
            val msg = "Benachrichtigungen enthalten keinen Text."
            actionLogger.log(msg, action, buttonConfig.label)
            tts.speakRouted(msg, targetDeviceAddress) {
                onFinish(executionId)
            }
            return
        }

        var combinedMessage = appMessages.joinToString(". ")
        if (action.ignoreEmojis) {
            combinedMessage = removeEmojis(combinedMessage)
        }
        actionLogger.log("Lese Benachrichtigungen: $combinedMessage", action, buttonConfig.label)
        
        tts.isReadingNotification = true
        tts.speakRouted(combinedMessage, targetDeviceAddress) {
            tts.isReadingNotification = false
            onFinish(executionId)
        }
    }

    fun handleClearNotifications(action: ControlDeviceButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        val service = NotificationReaderService.instance
        if (service == null) {
            actionLogger.log("Benachrichtigungsservice nicht verbunden.", action, label)
            onFinish(executionId)
            return
        }

        val contactPhone = action.contactPhone
        val targetPackages = if (!contactPhone.isNullOrBlank()) {
            contactPhone.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } else {
            settings.monitoredNotificationApps
        }

        try {
            val activeNotifications = service.activeNotifications
            if (activeNotifications != null) {
                var clearedCount = 0
                for (sbn in activeNotifications) {
                    val pkg = sbn.packageName
                    if (targetPackages.isEmpty() || targetPackages.contains(pkg)) {
                        service.cancelNotification(sbn.key)
                        clearedCount++
                    }
                }
                if (clearedCount > 0) {
                    actionLogger.log("$clearedCount Benachrichtigungen als gelesen markiert.", action, label)
                } else {
                    actionLogger.log("Keine passenden Benachrichtigungen zum Löschen gefunden.", action, label)
                }
            } else {
                actionLogger.log("Keine aktiven Benachrichtigungen gefunden.", action, label)
            }
        } catch (e: Exception) {
            actionLogger.log("Fehler beim Löschen der Benachrichtigungen: ${e.message}", action, label)
        }

        onFinish(executionId)
    }

    fun handleToggleAutoRead(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val current = settings.isNotificationReadingEnabled
        settings.isNotificationReadingEnabled = !current
        val msg = if (!current) "Automatisches Vorlesen aktiviert" else "Automatisches Vorlesen deaktiviert"
        actionLogger.log(msg, action, config.label)
        
        val targetDeviceAddress = if (config.playActionAsAuditoryCue) settings.cuesAudioDeviceAddress else settings.ttsAudioDeviceAddress
        ttsProxyLazy.get().speakRouted(msg, targetDeviceAddress) {
            onFinish(executionId)
        }
    }

    private fun removeEmojis(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val type = Character.getType(codePoint)
            if (!isEmojiCodePoint(codePoint, type)) {
                sb.appendCodePoint(codePoint)
            }
            i += Character.charCount(codePoint)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun isEmojiCodePoint(codePoint: Int, type: Int): Boolean {
        if (codePoint in 0x1F600..0x1F64F) return true
        if (codePoint in 0x1F300..0x1F5FF) return true
        if (codePoint in 0x1F680..0x1F6FF) return true
        if (codePoint in 0x2600..0x27BF) return true
        if (codePoint in 0x1F900..0x1F9FF) return true
        if (codePoint in 0x1FA70..0x1FAFF) return true
        if (codePoint in 0x1F1E6..0x1F1FF) return true
        if (codePoint in 0xE0020..0xE007F) return true
        if (codePoint in 0xFE00..0xFE0F) return true
        if (codePoint in 0x1F000..0x1F0FF) return true
        if (codePoint in 0x1F200..0x1F2FF) return true
        
        if (codePoint > 0xFFFF && type == Character.OTHER_SYMBOL.toInt()) return true
        
        return false
    }
}
