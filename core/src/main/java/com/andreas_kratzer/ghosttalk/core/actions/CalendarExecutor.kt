package com.andreas_kratzer.ghosttalk.core.actions

import android.annotation.SuppressLint
import android.content.Context
import com.andreas_kratzer.ghosttalk.core.audio.SsmlFactory
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val actionLogger: ActionLogger,
    private val settings: ControlDeviceSettings,
    private val ttsProxyLazy: dagger.Lazy<ControlDeviceTtsProxy>
) {
    fun handleReadCalendarEntries(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
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
            speakRoutedWithLogging(SsmlFactory.wrap(errorMsg), errorMsg, config, action, executionId, onFinish)
        } else if (messages.isEmpty()) {
            val msg = "Keine anstehenden Kalendereinträge gefunden."
            speakRoutedWithLogging(SsmlFactory.wrap(msg), msg, config, action, executionId, onFinish)
        } else {
            val prefix = action.prefixText?.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
            val suffix = action.suffixText?.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
            
            val plain = prefix + messages.joinToString(". ") + suffix
            val ssml = SsmlFactory.wrap(plain)
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
}
