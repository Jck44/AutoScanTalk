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
}
