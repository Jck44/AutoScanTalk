package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.ai.ClockExecutor

import com.andreas_kratzer.ghosttalk.core.util.Logger
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class LocalIntentRouterTest {

    private lateinit var clockExecutor: ClockExecutor
    private lateinit var logger: Logger
    private lateinit var router: LocalIntentRouterImpl

    @Before
    fun setup() {
        clockExecutor = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        
        router = spyk(LocalIntentRouterImpl(clockExecutor, logger))
    }

    @Test
    fun `executeIntent calls speak with natural response for alarm`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        every { clockExecutor.getNextAlarm() } returns "Wecker um 08:00 Uhr"
        coEvery { router.generateRawResponse(any(), any()) } returns "Dein nächster Wecker klingelt um 8 Uhr morgens."
        
        router.executeIntent("alarm", onSpeak)
        
        assert(spokenText == "Dein nächster Wecker klingelt um 8 Uhr morgens.")
    }

    @Test
    fun `executeIntent falls back to system executor if AI response is empty for alarm`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        every { clockExecutor.getNextAlarm() } returns "Wecker um 08:00 Uhr"
        coEvery { router.generateRawResponse(any(), any()) } returns ""
        
        router.executeIntent("alarm", onSpeak)
        
        assert(spokenText == "Wecker um 08:00 Uhr")
    }
}
