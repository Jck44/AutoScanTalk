package com.andreas_kratzer.ghosttalk.domain.executors

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidClockExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /**
     * Sets an alarm using the native Android AndroidClock API.
     * Returns true if the intent was fired successfully, false otherwise.
     */
    fun setAlarm(hour: Int, minute: Int, message: String = ""): Boolean {
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
}
