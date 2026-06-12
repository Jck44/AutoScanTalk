package com.andreas_kratzer.ghosttalk.core.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallDurationAnnouncerTest {

    @Test
    fun testFormatDurationAnnouncement_IntervalHandling() {
        assertNull(CallDurationAnnouncer.formatDurationAnnouncement(5, 10, isEnglish = true))
        assertNull(CallDurationAnnouncer.formatDurationAnnouncement(15, 10, isEnglish = true))
        
        assertEquals(
            "Call duration is 10 seconds.",
            CallDurationAnnouncer.formatDurationAnnouncement(10, 10, isEnglish = true)
        )
    }

    @Test
    fun testFormatDurationAnnouncement_English() {
        assertEquals(
            "Call duration is 30 seconds.",
            CallDurationAnnouncer.formatDurationAnnouncement(30, 30, isEnglish = true)
        )
        assertEquals(
            "Call has been active for 1 minute.",
            CallDurationAnnouncer.formatDurationAnnouncement(60, 60, isEnglish = true)
        )
        assertEquals(
            "Call has been active for 2 minutes.",
            CallDurationAnnouncer.formatDurationAnnouncement(120, 60, isEnglish = true)
        )
        assertEquals(
            "Call has been active for 1 minute and 15 seconds.",
            CallDurationAnnouncer.formatDurationAnnouncement(75, 15, isEnglish = true)
        )
        assertEquals(
            "Call has been active for 2 minutes and 10 seconds.",
            CallDurationAnnouncer.formatDurationAnnouncement(130, 10, isEnglish = true)
        )
    }

    @Test
    fun testFormatDurationAnnouncement_German() {
        assertEquals(
            "Telefonat dauert seit 30 Sekunden.",
            CallDurationAnnouncer.formatDurationAnnouncement(30, 30, isEnglish = false)
        )
        assertEquals(
            "Telefonat dauert seit 1 Minute.",
            CallDurationAnnouncer.formatDurationAnnouncement(60, 60, isEnglish = false)
        )
        assertEquals(
            "Telefonat dauert seit 2 Minuten.",
            CallDurationAnnouncer.formatDurationAnnouncement(120, 60, isEnglish = false)
        )
        assertEquals(
            "Telefonat dauert seit 1 Minute und 15 Sekunden.",
            CallDurationAnnouncer.formatDurationAnnouncement(75, 15, isEnglish = false)
        )
        assertEquals(
            "Telefonat dauert seit 2 Minuten und 10 Sekunden.",
            CallDurationAnnouncer.formatDurationAnnouncement(130, 10, isEnglish = false)
        )
    }

    @Test
    fun testMaxDurationReachedText() {
        assertEquals(
            "Maximum call duration reached. Ending the call.",
            CallDurationAnnouncer.maxDurationReachedText(isEnglish = true)
        )
        assertEquals(
            "Maximale Anrufdauer erreicht. Der Anruf wird beendet.",
            CallDurationAnnouncer.maxDurationReachedText(isEnglish = false)
        )
    }
}
