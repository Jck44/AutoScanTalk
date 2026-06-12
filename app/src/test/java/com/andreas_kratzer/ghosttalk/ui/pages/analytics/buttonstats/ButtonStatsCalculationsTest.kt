package com.andreas_kratzer.ghosttalk.ui.pages.analytics.buttonstats

import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class ButtonStatsCalculationsTest {

    @Test
    fun computeContextStats_emptyList_returnsNull() {
        assertNull(ButtonStatsCalculations.computeContextStats(emptyList()))
    }

    @Test
    fun computeContextStats_withEvents_returnsCorrectTopValues() {
        Locale.setDefault(Locale.GERMANY) // Set default locale for test consistency
        val cal = Calendar.getInstance()
        
        // Let's seed Monday (Monday is Montag in German)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 14)
        val time1 = cal.timeInMillis

        // Let's seed Monday 15:00
        cal.set(Calendar.HOUR_OF_DAY, 15)
        val time2 = cal.timeInMillis

        // Let's seed Tuesday 14:00
        cal.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
        cal.set(Calendar.HOUR_OF_DAY, 14)
        val time3 = cal.timeInMillis

        val events = listOf(
            ButtonUsageEvent(
                timestamp = time1,
                buttonId = "b1",
                pageId = "p1",
                label = "Test1",
                actionType = "SPEAK",
                geminiResponse = null
            ),
            ButtonUsageEvent(
                timestamp = time2,
                buttonId = "b1",
                pageId = "p1",
                label = "Test1",
                actionType = "SPEAK",
                geminiResponse = null
            ),
            ButtonUsageEvent(
                timestamp = time3,
                buttonId = "b1",
                pageId = "p1",
                label = "Test1",
                actionType = "SPEAK",
                geminiResponse = null
            )
        )

        val result = ButtonStatsCalculations.computeContextStats(events)
        org.junit.Assert.assertNotNull(result)
        assertEquals("Montag", result?.first)
        // 14:00 - 15:00 occurs twice (time1 and time3 both hour 14)
        assertEquals("14:00 - 15:00 Uhr", result?.second)
        assertEquals("Zu Hause / Indoor (Kein GPS)", result?.third)
    }
}
