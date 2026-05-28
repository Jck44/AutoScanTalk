package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.ai.ClockExecutor
import com.andreas_kratzer.ghosttalk.core.util.Logger
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
        logger = mockk(relaxed = true)
        router = spyk(LocalIntentRouterImpl(logger))
    }

    @Test
    fun `executeIntent returns not available message`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        router.executeIntent("alarm", onSpeak)
        
        assert(spokenText == "Dieses Tool ist für die lokale Verarbeitung aktuell nicht verfügbar.")
    }

    @Test
    fun `routeIntent returns unknown message`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        router.routeIntent(onSpeak)
        
        assert(spokenText == "Befehl konnte nicht verarbeitet werden.")
    }
}
