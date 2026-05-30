package com.andreas_kratzer.ghosttalk.core.actions

import android.annotation.SuppressLint
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
    private val scanControllerLazy: dagger.Lazy<ScannerController>,
    private val callActionProxy: dagger.Lazy<CallActionProxy>,
    private val actionLogger: ActionLogger
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
            DeviceActionType.MEDIA_NEXT -> handleMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Nächstes Lied", action, buttonConfig.label, executionId, onFinish)
            DeviceActionType.MEDIA_PREVIOUS -> handleMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Vorheriges Lied", action, buttonConfig.label, executionId, onFinish)
            DeviceActionType.MEDIA_PLAY_PAUSE -> handleMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Start / Stop", action, buttonConfig.label, executionId, onFinish)
            DeviceActionType.READ_NOTIFICATIONS -> handleReadNotifications(buttonConfig, deviceAction, executionId, onFinish)
            
            DeviceActionType.VOLUME_NOTIFICATION -> handleVolume(AudioManager.STREAM_NOTIFICATION, deviceAction, buttonConfig.label, executionId, onFinish)
            DeviceActionType.VOLUME_ALARM -> handleVolume(AudioManager.STREAM_ALARM, deviceAction, buttonConfig.label, executionId, onFinish)
            DeviceActionType.VOLUME_MEDIA -> handleVolume(AudioManager.STREAM_MUSIC, deviceAction, buttonConfig.label, executionId, onFinish)
            DeviceActionType.VOLUME_CALL -> handleVolume(AudioManager.STREAM_VOICE_CALL, deviceAction, buttonConfig.label, executionId, onFinish)
            
            DeviceActionType.STATUS_SILENT -> handleStatus(AudioManager.RINGER_MODE_SILENT, action, buttonConfig.label, executionId, onFinish)
            DeviceActionType.STATUS_VIBRATE -> handleStatus(AudioManager.RINGER_MODE_VIBRATE, action, buttonConfig.label, executionId, onFinish)
            DeviceActionType.STATUS_LOUD -> handleStatus(AudioManager.RINGER_MODE_NORMAL, action, buttonConfig.label, executionId, onFinish)
            
            DeviceActionType.SEND_MESSAGE -> handleSendMessage(deviceAction, buttonConfig.label, executionId, onFinish)
            
            DeviceActionType.READ_BATTERY -> handleReadBattery(buttonConfig, deviceAction, executionId, onFinish)
            DeviceActionType.READ_TIME -> handleReadTime(buttonConfig, deviceAction, executionId, onFinish)
            DeviceActionType.READ_DATE -> handleReadDate(buttonConfig, deviceAction, executionId, onFinish)
            DeviceActionType.READ_CALENDAR_ENTRIES -> handleReadCalendarEntries(buttonConfig, deviceAction, executionId, onFinish)

            DeviceActionType.TOGGLE_SCANNING -> {
                handleToggleScanning(action, buttonConfig.label, executionId, onFinish)
            }

            DeviceActionType.CLEAR_NOTIFICATIONS -> {
                handleClearNotifications(deviceAction, buttonConfig.label, executionId, onFinish)
            }

            DeviceActionType.START_CALL -> {
                val phone = deviceAction.contactPhone ?: ""
                val name = deviceAction.contactName ?: ""
                if (settings.simulateCallsEnabled) {
                    callActionProxy.get().simulateOutgoingCall(name, phone)
                    actionLogger.log("Anruf simulieren an $name ($phone)", action, buttonConfig.label)
                } else {
                    callActionProxy.get().startCall(name, phone)
                    actionLogger.log("Anruf starten an $name ($phone)", action, buttonConfig.label)
                }
                onFinish(executionId)
            }
        }
    }

    private fun handleMediaKey(keyCode: Int, description: String, action: ButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        actionLogger.log(description, action, label)
        onFinish(executionId)
    }

    private fun handleVolume(streamType: Int, action: ControlDeviceButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
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
        actionLogger.log("Lautstärke auf ${((targetVolume.toDouble() / maxVolume) * 100).toInt()}% gesetzt", action, label)
        onFinish(executionId)
    }

    private fun handleStatus(ringerMode: Int, action: ButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (ringerMode == AudioManager.RINGER_MODE_SILENT && !notificationManager.isNotificationPolicyAccessGranted) {
            actionLogger.log("Berechtigung für 'Nicht stören' fehlt.", action, label)
        } else {
            audioManager.ringerMode = ringerMode
            val modeName = when(ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "Lautlos"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibration"
                else -> "Laut"
            }
            actionLogger.log("Modus auf $modeName gesetzt", action, label)
        }
        onFinish(executionId)
    }

    private fun handleSendMessage(action: ControlDeviceButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        val phone = action.contactPhone
        val message = action.messageText ?: ""

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
            // Skip group summary notifications
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

        val messagesToRead = filtered.mapNotNull { sbn ->
            val extras = sbn.notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: "Unbekannt"
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            if (text.isNullOrBlank()) null else "$title: $text"
        }

        if (messagesToRead.isEmpty()) {
            val msg = "Benachrichtigungen enthalten keinen Text."
            actionLogger.log(msg, action, buttonConfig.label)
            tts.speakRouted(msg, targetDeviceAddress) {
                onFinish(executionId)
            }
            return
        }

        var combinedMessage = messagesToRead.joinToString(". ")
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
        val calendar = java.util.Calendar.getInstance()
        if (action.offsetValue != 0) {
            calendar.add(java.util.Calendar.MINUTE, action.offsetValue)
        }
        
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val timeString = sdf.format(calendar.time)
        
        val prefix = action.prefixText?.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
        val suffix = action.suffixText?.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
        
        val plain = "$prefix$timeString$suffix"
        val ssml = "<speak>$plain</speak>"
        
        speakRoutedWithLogging(ssml, plain, config, action, executionId, onFinish)
    }

    private fun handleReadDate(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val calendar = java.util.Calendar.getInstance()
        if (action.offsetValue != 0) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, action.offsetValue)
        }
        
        val mainPattern = "dd. MMMM yyyy"
        val fullPattern = if (action.includeWeekday) "EEEE, dd. MMMM yyyy" else mainPattern
        
        val sdfDisplay = java.text.SimpleDateFormat(fullPattern, java.util.Locale.getDefault())
        val dateString = sdfDisplay.format(calendar.time)
        
        val prefix = action.prefixText?.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
        val suffix = action.suffixText?.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
        
        val plain = "$prefix$dateString$suffix"
        
        // Use the naturally formatted date string even in SSML, as most TTS engines 
        // handle "22. März 2026" better than say-as with numeric strings.
        val ssml = "<speak>$plain</speak>"
        
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
        actionLogger.log(plainText, action, config.label)
        val targetDeviceAddress = if (config.playActionAsAuditoryCue) {
            settings.cuesAudioDeviceAddress
        } else {
            settings.ttsAudioDeviceAddress
        }
        
        val tts = ttsProxyLazy.get()
        tts.speakRouted(ssml, targetDeviceAddress) {
            onFinish(executionId)
        }
    }

    private fun handleToggleScanning(action: ButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        val scannerController = scanControllerLazy.get()
        scannerController.togglePause()
        
        val isPaused = scannerController.isPausedManually.value
        val msg = if (isPaused) "Scannen pausiert" else "Scannen fortgesetzt"
        actionLogger.log(msg, action, label)
        
        onFinish(executionId)
    }

    private fun handleReadCalendarEntries(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val count = action.offsetValue.coerceAtLeast(1)
        val resolver = context.contentResolver
        
        // Calculate start of today and tomorrow in default local timezone
        val todayCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfToday = todayCal.timeInMillis
        todayCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val startOfTomorrow = todayCal.timeInMillis
        
        val endOfRange = startOfToday + 7 * 24 * 60 * 60 * 1000L // 7 days range
        
        val contentUri = android.provider.CalendarContract.Instances.CONTENT_URI
        val uri = if (contentUri != null) {
            val builder = contentUri.buildUpon()
            android.content.ContentUris.appendId(builder, startOfToday)
            android.content.ContentUris.appendId(builder, endOfRange)
            builder.build()
        } else {
            android.net.Uri.EMPTY
        }
        
        val projection = arrayOf(
            android.provider.CalendarContract.Instances.TITLE,
            android.provider.CalendarContract.Instances.BEGIN,
            android.provider.CalendarContract.Instances.END,
            android.provider.CalendarContract.Instances.ALL_DAY
        )
        
        val sortOrder = "${android.provider.CalendarContract.Instances.BEGIN} ASC"
        
        val messages = mutableListOf<String>()
        var errorMsg: String? = null
        
        // Check permission first
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            errorMsg = "Berechtigung für den Kalender ist nicht erteilt."
        } else {
            val entriesByDay = LinkedHashMap<String, MutableList<String>>()
            val sdfDate = java.text.SimpleDateFormat("d. MMMM", java.util.Locale.getDefault())
            
            fun getDayKey(startMillis: Long): String {
                val eventCal = java.util.Calendar.getInstance().apply { timeInMillis = startMillis }
                eventCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                eventCal.set(java.util.Calendar.MINUTE, 0)
                eventCal.set(java.util.Calendar.SECOND, 0)
                eventCal.set(java.util.Calendar.MILLISECOND, 0)
                val eventDayStart = eventCal.timeInMillis

                return when (eventDayStart) {
                    startOfToday -> getAppString("calendar_today")
                    startOfTomorrow -> getAppString("calendar_tomorrow")
                    else -> getAppString("calendar_on_date", sdfDate.format(java.util.Date(startMillis)))
                }
            }

            try {
                resolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                    var found = 0
                    while (cursor.moveToNext() && found < count) {
                        val titleIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.TITLE)
                        val startIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN)
                        val endIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.END)
                        val allDayIdx = cursor.getColumnIndex(android.provider.CalendarContract.Instances.ALL_DAY)
                        
                        val title = if (titleIdx >= 0) cursor.getString(titleIdx) else "Unbekannt"
                        val start = if (startIdx >= 0) cursor.getLong(startIdx) else 0L
                        val end = if (endIdx >= 0) cursor.getLong(endIdx) else 0L
                        val allDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false
                        
                        val dayKey = getDayKey(start)
                        val timeStr = if (allDay) {
                             getAppString("calendar_all_day")
                        } else {
                            val stTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(start))
                            val enTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(end))
                            getAppString("calendar_time_range", stTime, enTime)
                        }
                        
                        val itemText = "$timeStr: $title"
                        entriesByDay.getOrPut(dayKey) { mutableListOf() }.add(itemText)
                        found++
                    }
                }
                
                val calendarAnd = getAppString("calendar_and")
                val onDatePrefix = getAppString("calendar_on_date", "").trim()
                
                for ((day, items) in entriesByDay) {
                    val itemsText = items.joinToString(calendarAnd)
                    if (onDatePrefix.isNotEmpty() && day.startsWith(onDatePrefix)) {
                        messages.add("$day, $itemsText")
                    } else {
                        messages.add("$day $itemsText")
                    }
                }
            } catch (e: SecurityException) {
                errorMsg = "Kalenderberechtigung nicht erteilt."
                actionLogger.log("Sicherheitsfehler beim Kalenderzugriff: ${e.message}", action, config.label)
            } catch (e: Exception) {
                errorMsg = "Fehler beim Kalenderzugriff."
                actionLogger.log("Fehler beim Kalenderzugriff: ${e.message}", action, config.label)
            }
        }

        if (errorMsg != null) {
            speakRoutedWithLogging("<speak>$errorMsg</speak>", errorMsg, config, action, executionId, onFinish)
        } else if (messages.isEmpty()) {
            val msg = "Keine anstehenden Kalendereinträge gefunden."
            speakRoutedWithLogging("<speak>$msg</speak>", msg, config, action, executionId, onFinish)
        } else {
            val prefix = action.prefixText?.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
            val suffix = action.suffixText?.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
            
            val plain = prefix + messages.joinToString(". ") + suffix
            val ssml = "<speak>$plain</speak>"
            speakRoutedWithLogging(ssml, plain, config, action, executionId, onFinish)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun getAppString(name: String, vararg args: Any?): String {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
        return if (resId != 0) {
            context.getString(resId, *args)
        } else {
            val isEn = java.util.Locale.getDefault().language == "en"
            when (name) {
                "calendar_today" -> if (isEn) "Today" else "Heute"
                "calendar_tomorrow" -> if (isEn) "Tomorrow" else "Morgen"
                "calendar_all_day" -> if (isEn) "all day" else "ganztägig"
                "calendar_and" -> if (isEn) " and " else " und "
                "calendar_on_date" -> {
                    val dateArg = args.getOrNull(0)?.toString() ?: ""
                    if (isEn) "On $dateArg" else "Am $dateArg"
                }
                "calendar_time_range" -> {
                    val start = args.getOrNull(0)?.toString() ?: ""
                    val end = args.getOrNull(1)?.toString() ?: ""
                    if (isEn) "from $start to $end" else "von $start bis $end Uhr"
                }
                else -> ""
            }
        }
    }

    private fun handleClearNotifications(action: ControlDeviceButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
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
}
