package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionExecutorTest {
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
    private val geminiUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.GeminiUseCase>(relaxed = true)
    private val buttonUsageRepository = mockk<ButtonUsageRepository>(relaxed = true)

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
        settingsRepository = settingsRepository,
        ttsHelper = ttsHelper,
        geminiUseCase = geminiUseCase,
        buttonUsageRepository = buttonUsageRepository,
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
        actionExecutor.executeButtonAction(buttonConfig, bookId = "book1")
        runCurrent()

        // Verifiziere: isExecuting ist true
        assertTrue("Sollte ausführen", actionExecutor.isExecuting.value)
        verify(exactly = 1) { ttsHelper.speakRouted("Hello", any(), any(), any()) }

        // 310ms: Nutzer drückt nochmal -> Haltezeit (1000ms) ist noch aktiv
        currentTimeMillis = 310L
        actionExecutor.executeButtonAction(buttonConfig, bookId = "book1")
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
        actionExecutor.executeButtonAction(button1, bookId = "book1")
        runCurrent()
        assertTrue(actionExecutor.isExecuting.value)

        // 2. Wait past holding time
        currentTimeMillis = 1500L

        // 3. Second action (T=1500ms, past holding time, but speech A1 still active)
        actionExecutor.executeButtonAction(button2, bookId = "book1")
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

    @Test
    fun testUsageTrackingRecordsOnExecution() = runTest {
        val actionExecutor = createExecutor(this)
        val buttonConfig = ButtonConfig(
            id = "btn-track",
            label = "Track Me",
            auditoryCue = null,
            buttonAction = SpeakTextButtonAction("Track")
        )

        val ttsCallback = slot<() -> Unit>()
        every { ttsHelper.speakRouted(any(), any(), any(), capture(ttsCallback)) } returns Unit

        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(buttonConfig, bookId = "book1")
        runCurrent()

        // Verify usage was recorded
        coVerify(exactly = 1) { buttonUsageRepository.recordUsage("book1", buttonConfig) }
    }

    @Test
    fun testUsageTrackingSkippedWithoutBookId() = runTest {
        val actionExecutor = createExecutor(this)
        val buttonConfig = ButtonConfig(
            id = "btn-no-book",
            label = "No Book",
            auditoryCue = null,
            buttonAction = SpeakTextButtonAction("Test")
        )

        every { ttsHelper.speakRouted(any(), any(), any(), any()) } returns Unit

        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(buttonConfig) // No bookId
        runCurrent()

        // Verify usage was NOT recorded (no bookId)
        coVerify(exactly = 0) { buttonUsageRepository.recordUsage(any(), any()) }
    }

    @Test
    fun testGeminiQuotaReached_ProvidesLocalizedFeedback() = runTest {
        val actionExecutor = createExecutor(this)
        val buttonConfig = ButtonConfig(
            id = "g1",
            label = "Gemini",
            auditoryCue = null,
            buttonAction = com.andreas_kratzer.ghosttalk.model.GeminiButtonAction("Help")
        )

        val context = mockk<android.content.Context>(relaxed = true)
        every { ttsHelper.context } returns context
        every { ttsHelper.isReady } returns true
        every { settingsRepository.isGeminiEnabled } returns true
        
        // Return a localized string with a placeholder
        every { context.getString(com.andreas_kratzer.ghosttalk.R.string.error_gemini_quota_reached, 45) } returns "Wait 45s"

        // Mock GeminiUseCase to throw 429 with custom message
        coEvery { geminiUseCase.generateResponse(any()) } throws Exception("HTTP 429: wait 45 seconds")

        actionExecutor.executeButtonAction(buttonConfig)
        runCurrent()

        // Verify TTS spoke the "Wait 45s" message with 4 parameters to match signature
        verify { ttsHelper.speakRouted("Wait 45s", any(), any(), any()) }
    }
}
