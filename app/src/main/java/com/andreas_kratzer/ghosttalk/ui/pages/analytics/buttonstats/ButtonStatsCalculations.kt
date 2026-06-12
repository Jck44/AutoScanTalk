package com.andreas_kratzer.ghosttalk.ui.pages.analytics.buttonstats

import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ButtonStatsCalculations {
    fun computeContextStats(historyEvents: List<ButtonUsageEvent>): Triple<String?, String?, String>? {
        if (historyEvents.isEmpty()) return null
        
        val sdfDay = SimpleDateFormat("EEEE", Locale.getDefault())
        val sdfHour = SimpleDateFormat("H", Locale.getDefault())
        
        val dayCounts = historyEvents.groupBy { sdfDay.format(Date(it.timestamp)) }
            .mapValues { it.value.size }
        val topDay = dayCounts.maxByOrNull { it.value }?.key
        
        val hourCounts = historyEvents.groupBy { 
            val hour = sdfHour.format(Date(it.timestamp)).toIntOrNull() ?: 0
            val start = hour
            val end = hour + 1
            "$start:00 - $end:00 Uhr"
        }.mapValues { it.value.size }
        val topHour = hourCounts.maxByOrNull { it.value }?.key
        
        val locationEvents = historyEvents.filter { 
            try {
                val latField = it.javaClass.getDeclaredField("latitude")
                latField.isAccessible = true
                latField.get(it) != null
            } catch(e: Exception) {
                false
            }
        }
        val topLocation = if (locationEvents.isNotEmpty()) {
            "GPS Koordinaten vorhanden"
        } else {
            "Zu Hause / Indoor (Kein GPS)"
        }
        
        return Triple(topDay, topHour, topLocation)
    }
}
