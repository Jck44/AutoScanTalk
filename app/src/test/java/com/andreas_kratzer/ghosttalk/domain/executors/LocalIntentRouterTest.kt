package com.andreas_kratzer.ghosttalk.domain.executors

import com.andreas_kratzer.ghosttalk.core.util.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LocalIntentRouterTest {

    private lateinit var systemTimeExecutor: SystemTimeExecutor
    private lateinit var androidClockExecutor: AndroidClockExecutor
    private lateinit var logger: Logger
    private lateinit var router: LocalIntentRouter

    @Before
    fun setup() {
        systemTimeExecutor = mockk(relaxed = true)
        androidClockExecutor = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        
        router = LocalIntentRouter(systemTimeExecutor, androidClockExecutor, logger)
    }

    @Test
    fun `handleJsonIntent parses time query correctly`() {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        every { systemTimeExecutor.getCurrentTimeOutput() } returns "Es ist 14:00 Uhr"
        
        // Use reflection to test the private parsing method
        val method = LocalIntentRouter::class.java.getDeclaredMethod("handleJsonIntent", String::class.java, Function1::class.java)
        method.isAccessible = true
        method.invoke(router, """{"intent": "time", "query": "time"}""", onSpeak)
        
        verify(exactly = 1) { systemTimeExecutor.getCurrentTimeOutput() }
        assert(spokenText == "Es ist 14:00 Uhr")
    }

    @Test
    fun `handleJsonIntent parses date query correctly`() {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        every { systemTimeExecutor.getCurrentDateOutput() } returns "Heute ist Montag"
        
        val method = LocalIntentRouter::class.java.getDeclaredMethod("handleJsonIntent", String::class.java, Function1::class.java)
        method.isAccessible = true
        method.invoke(router, """{"intent": "time", "query": "date"}""", onSpeak)
        
        verify(exactly = 1) { systemTimeExecutor.getCurrentDateOutput() }
        assert(spokenText == "Heute ist Montag")
    }

    @Test
    fun `handleJsonIntent parses alarm set correctly`() {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        every { androidClockExecutor.setAlarm(7, 30, any()) } returns true
        
        val method = LocalIntentRouter::class.java.getDeclaredMethod("handleJsonIntent", String::class.java, Function1::class.java)
        method.isAccessible = true
        method.invoke(router, """{"intent": "alarm", "action": "set", "hour": 7, "minute": 30}""", onSpeak)
        
        verify(exactly = 1) { androidClockExecutor.setAlarm(7, 30, any()) }
        assert(spokenText == "Wecker wurde gestellt.")
    }

    @Test
    fun `handleJsonIntent handles invalid alarm time gracefully`() {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        val method = LocalIntentRouter::class.java.getDeclaredMethod("handleJsonIntent", String::class.java, Function1::class.java)
        method.isAccessible = true
        method.invoke(router, """{"intent": "alarm", "action": "set", "hour": 25, "minute": 0}""", onSpeak)
        
        verify(exactly = 0) { androidClockExecutor.setAlarm(any(), any(), any()) }
        assert(spokenText == "Ungültige Uhrzeit für den Wecker.")
    }
    
    @Test
    fun `handleJsonIntent safely catches malformed JSON`() {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        val method = LocalIntentRouter::class.java.getDeclaredMethod("handleJsonIntent", String::class.java, Function1::class.java)
        method.isAccessible = true
        method.invoke(router, """{not valid json}""", onSpeak)
        
        assert(spokenText == "Konnte das JSON nicht verarbeiten.")
    }

    @Test
    fun `extractJson handles markdown and conversational text`() {
        val method = LocalIntentRouter::class.java.getDeclaredMethod("extractJson", String::class.java)
        method.isAccessible = true
        
        val input1 = """Hier ist das JSON: {"intent": "unknown"} Viel Spaß!"""
        val result1 = method.invoke(router, input1) as String
        assert(result1 == """{"intent": "unknown"}""")
        
        val input2 = """```json
            {"intent": "time"}
            ```"""
        val result2 = method.invoke(router, input2) as String
        assert(result2.trim() == """{"intent": "time"}""")
    }
}
