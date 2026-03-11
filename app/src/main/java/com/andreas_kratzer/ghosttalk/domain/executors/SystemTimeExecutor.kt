package com.andreas_kratzer.ghosttalk.domain.executors

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class SystemTimeExecutor @Inject constructor() {

    fun getCurrentTimeOutput(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = formatter.format(Date())
        return "Es ist $timeStr Uhr."
    }

    fun getCurrentDateOutput(): String {
        val formatter = SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.getDefault())
        val dateStr = formatter.format(Date())
        return "Heute ist $dateStr."
    }

    /**
     * Returns a raw timestamp string (e.g. "2026-03-05T10:52:00") to be used as context for AI.
     */
    fun getRawTimestampContext(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
