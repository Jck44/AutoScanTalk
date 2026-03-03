package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionExecutorTest {
    private val pageRepository = mockk<PageRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
    private val geminiUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.GeminiUseCase>(relaxed = true)

    private var currentTimeMillis = 0L
    private val timeProvider: () -> Long = { currentTimeMillis }

    @Before
    fun setup() {
        // Haltezeit = 1000ms
        every { settingsRepository.holdingTimeMillis } returns 1000L
        every { ttsHelper.isReady } returns true
    }

    private fun createExecutor(scope: kotlinx.coroutines.CoroutineScope) = ActionExecutor(
        scope = scope,
        pageRepository = pageRepository,
        settingsRepository = settingsRepository,
        ttsHelper = ttsHelper,
        geminiUseCase = geminiUseCase,
        timeProvider = timeProvider
    )

    @Test
    fun testSyncScanningAndSpeechWithHoldingTime_Timeline() = runTest {
        val actionExecutor = createExecutor(this)
        val buttonConfig = ButtonConfig(
            id = "b1",
            label = "Test",
            auditoryCue = null,
            buttonAction = SpeakTextButtonAction("Hello")
        )

        val ttsCallback = slot<() -> Unit>()
        every { ttsHelper.speakRouted(any(), any(), any(), capture(ttsCallback)) } returns Unit

        val events = mutableListOf<ActionExecutor.ExecutionEvent>()
        val eventsJob = launch {
            actionExecutor.events.collect { events.add(it) }
        }

        // 200ms: Nutzer drückt das erste Mal
        currentTimeMillis = 200L
        actionExecutor.executeButtonAction(buttonConfig)
        runCurrent()

        // Verifiziere: isExecuting ist true
        assertTrue("Sollte ausführen", actionExecutor.isExecuting.value)
        verify(exactly = 1) { ttsHelper.speakRouted("Hello", any(), any(), any()) }

        // 310ms: Nutzer drückt nochmal -> Haltezeit (1000ms) ist noch aktiv
        currentTimeMillis = 310L
        actionExecutor.executeButtonAction(buttonConfig)
        runCurrent()

        // Verifiziere: Aktion ignoriert (Log-Event)
        assertTrue("Sollte Log-Event für Ignorieren haben", events.any { it is ActionExecutor.ExecutionEvent.Log && it.message.contains("ignoriert") })

        // 1250ms: Text ist fertig gesprochen
        currentTimeMillis = 1250L
        ttsCallback.captured.invoke()
        runCurrent()

        // Jetzt MUSS isExecuting wieder false sein
        assertEquals(false, actionExecutor.isExecuting.value)
        
        eventsJob.cancel()
    }

    @Test
    fun testInterleavedActions_PreventsPrematureResume() = runTest {
        val actionExecutor = createExecutor(this)
        val button1 = ButtonConfig(id = "1", label = "B1", auditoryCue = null, buttonAction = SpeakTextButtonAction("A1"))
        val button2 = ButtonConfig(id = "2", label = "B2", auditoryCue = null, buttonAction = SpeakTextButtonAction("A2"))

        val ttsCallback1 = slot<() -> Unit>()
        every { ttsHelper.speakRouted("A1", any(), any(), capture(ttsCallback1)) } returns Unit

        val events = mutableListOf<ActionExecutor.ExecutionEvent>()
        val eventsJob = launch {
            actionExecutor.events.collect { events.add(it) }
        }

        // 1. First action
        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(button1)
        runCurrent()
        assertTrue(actionExecutor.isExecuting.value)

        // 2. Wait past holding time
        currentTimeMillis = 1500L

        // 3. Second action (T=1500ms, past holding time, but speech A1 still active)
        actionExecutor.executeButtonAction(button2)
        runCurrent()
        
        // Verifiziere: Aktion 2 wird ignoriert, da A1 noch spricht
        assertTrue(events.any { it is ActionExecutor.ExecutionEvent.Log && it.message.contains("Sprachausgabe aktiv") })
        assertTrue(actionExecutor.isExecuting.value)

        // Simulate A1 finishing
        ttsCallback1.captured.invoke()
        runCurrent()

        // Jetzt MUSS isExecuting wieder false sein
        assertEquals(false, actionExecutor.isExecuting.value)
        
        eventsJob.cancel()
    }
}
