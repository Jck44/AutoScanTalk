package com.andreas_kratzer.ghosttalk.core.services

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceSettings
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceTtsProxy
import com.andreas_kratzer.ghosttalk.core.actions.ScannerController
import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume

class PassiveNotificationReader(
    private val settings: ControlDeviceSettings,
    private val ttsProxy: ControlDeviceTtsProxy,
    private val scannerController: ScannerController,
    private val appStateRepository: AppStateRepository,
    private val notificationService: NotificationReaderService,
    private val context: Context
) {
    private val queue = ConcurrentLinkedQueue<StatusBarNotification>()
    private var isSpeaking = false
    private var speakJob: Job? = null
    private var intervalJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Start interval scheduling based on settings
        startIntervalScheduler()
    }

    private fun startIntervalScheduler() {
        intervalJob?.cancel()
        intervalJob = scope.launch {
            while (isActive) {
                val mode = settings.autoReadMode
                if (mode != AutoReadMode.OFF && mode != AutoReadMode.IMMEDIATE) {
                    val delayMs = mode.intervalMinutes * 60 * 1000L
                    delay(delayMs)
                    if (queue.isNotEmpty()) {
                        processQueue()
                    }
                } else {
                    delay(5000) // Check settings change periodically
                }
            }
        }
    }

    fun onNewNotification(sbn: StatusBarNotification) {
        if (!settings.isNotificationReadingEnabled) return

        val mode = settings.autoReadMode
        if (mode == AutoReadMode.OFF) return

        // Skip group summary notifications
        val isGroupSummary = (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) return

        // 1. App-Filterung prüfen
        if (!settings.monitoredNotificationApps.contains(sbn.packageName)) return

        // 2. Benutzermodus-Kriterium prüfen
        val isUserMode = appStateRepository.isUserModeActive.value
        if (settings.autoReadOnlyInUserMode && !isUserMode) return

        // 3. Standby-Kriterium prüfen (Screen Off)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!settings.autoReadInStandby && !pm.isInteractive) return

        // 4. In Queue einreihen und Sprech-Vorgang triggern
        queue.add(sbn)
        if (mode == AutoReadMode.IMMEDIATE) {
            processQueue()
        }
    }

    private fun processQueue() {
        if (isSpeaking || queue.isEmpty()) return
        isSpeaking = true

        speakJob = scope.launch {
            scannerController.pauseForNotification()

            while (queue.isNotEmpty()) {
                val sbn = queue.peek() ?: break
                val textToSpeak = extractText(sbn)

                if (textToSpeak != null) {
                    val completed = speakNotificationSuspended(textToSpeak)
                    if (completed) {
                        // Erfolgreich gesprochen -> Aus Queue entfernen & cancelNotification
                        queue.poll()
                        try {
                            notificationService.cancelNotification(sbn.key)
                        } catch (e: Exception) {
                            println("PassiveNotificationReader: Failed to cancel notification: ${e.message}")
                        }
                    } else {
                        // Abgebrochen -> NICHT aus Queue entfernen, nicht canceln, Schleife beenden
                        break
                    }
                } else {
                    queue.poll() // Kein Text vorhanden
                }
            }

            isSpeaking = false
            scannerController.resumeFromNotification()
        }
    }

    private suspend fun speakNotificationSuspended(text: String): Boolean = suspendCancellableCoroutine { continuation ->
        ttsProxy.isReadingNotification = true
        ttsProxy.speakRouted(text, settings.ttsAudioDeviceAddress) {
            ttsProxy.isReadingNotification = false
            if (continuation.isActive) {
                continuation.resume(true)
            }
        }

        continuation.invokeOnCancellation {
            ttsProxy.isReadingNotification = false
        }
    }

    fun cancelReading() {
        speakJob?.cancel()
        speakJob = null
        isSpeaking = false
        ttsProxy.isReadingNotification = false
        scannerController.resumeFromNotification()
    }

    private fun extractText(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        
        if (title.isBlank() && text.isBlank()) return null
        
        val packageManager = context.packageManager
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val baseText = if (title.isNotBlank()) {
            "$appName. $title: $text"
        } else {
            "$appName. $text"
        }

        return removeEmojis(baseText)
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
        if (codePoint in 0x1F600..0x1F64F) return true // Emoticons
        if (codePoint in 0x1F300..0x1F5FF) return true // Misc Symbols and Pictographs
        if (codePoint in 0x1F680..0x1F6FF) return true // Transport and Map
        if (codePoint in 0x2600..0x27BF) return true   // Misc Symbols & Dingbats
        if (codePoint in 0x1F900..0x1F9FF) return true // Supplemental Symbols and Pictographs
        if (codePoint in 0x1FA70..0x1FAFF) return true // Symbols and Pictographs Extended-A
        if (codePoint in 0x1F1E6..0x1F1FF) return true // Regional Indicator Symbols (Flags)
        if (codePoint in 0xE0020..0xE007F) return true // Tag Characters (Flags)
        if (codePoint in 0xFE00..0xFE0F) return true   // Variation Selectors
        if (codePoint in 0x1F000..0x1F0FF) return true // Mahjong / Domino / Playing Cards
        if (codePoint in 0x1F200..0x1F2FF) return true // Enclosed Ideographic Supplement
        
        if (codePoint > 0xFFFF && type == Character.OTHER_SYMBOL.toInt()) return true
        
        return false
    }

    fun destroy() {
        intervalJob?.cancel()
        speakJob?.cancel()
        scope.cancel()
    }
}
