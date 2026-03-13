package com.andreas_kratzer.ghosttalk.domain.executors

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.andreas_kratzer.ghosttalk.core.ai.ClockExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidClockExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ClockExecutor {
    /**
     * Sets an alarm using the native Android AndroidClock API.
     * Returns true if the intent was fired successfully, false otherwise.
     */
    override fun setAlarm(hour: Int, minute: Int, message: String): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                // Set to true to bypass the alarm creation UI and just set it
                putExtra(AlarmClock.EXTRA_SKIP_UI, true) 
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns the next scheduled alarm as a human readable string.
     */
    override fun getNextAlarm(): String {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val nextAlarm = alarmManager.nextAlarmClock
            if (nextAlarm != null) {
                val sdf = java.text.SimpleDateFormat("H:mm", java.util.Locale.getDefault())
                val time = sdf.format(java.util.Date(nextAlarm.triggerTime))
                "Der nächste Wecker ist für $time Uhr gestellt."
            } else {
                "Es ist aktuell kein Wecker gestellt."
            }
        } catch (_: Exception) {
            "Weckerinformationen konnten nicht abgerufen werden."
        }
    }
}
