package com.andreas_kratzer.ghosttalk.domain.executors

import com.andreas_kratzer.ghosttalk.core.util.Logger
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class LocalIntentRouterTest {

    private lateinit var androidClockExecutor: AndroidClockExecutor
    private lateinit var logger: Logger
    private lateinit var router: LocalIntentRouter

    @Before
    fun setup() {
        androidClockExecutor = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        
        router = spyk(LocalIntentRouter(androidClockExecutor, logger))
    }

    @Test
    fun `executeIntent calls speak with natural response for alarm`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        every { androidClockExecutor.getNextAlarm() } returns "Wecker um 08:00 Uhr"
        coEvery { router.generateRawResponse(any(), any()) } returns "Dein nächster Wecker klingelt um 8 Uhr morgens."
        
        router.executeIntent("alarm", onSpeak)
        
        assert(spokenText == "Dein nächster Wecker klingelt um 8 Uhr morgens.")
    }

    @Test
    fun `executeIntent falls back to system executor if AI response is empty for alarm`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        every { androidClockExecutor.getNextAlarm() } returns "Wecker um 08:00 Uhr"
        coEvery { router.generateRawResponse(any(), any()) } returns ""
        
        router.executeIntent("alarm", onSpeak)
        
        assert(spokenText == "Wecker um 08:00 Uhr")
    }
}
