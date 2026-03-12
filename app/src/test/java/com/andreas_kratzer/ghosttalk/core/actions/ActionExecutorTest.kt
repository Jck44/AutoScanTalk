package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.util.TestLogger
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
    private val application = mockk<android.app.Application>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
    private val geminiUseCase = mockk<com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase>(relaxed = true)
    private val buttonUsageRepository = mockk<ButtonUsageRepository>(relaxed = true)
    private val logger: Logger = TestLogger()

    private var currentTimeMillis = 0L
    private val timeProvider: () -> Long = { currentTimeMillis }

    @Before
    fun setup() {
        // Mock SettingsRepository values
        every { settingsRepository.holdingTimeMillis } returns 1000L
        every { ttsHelper.isReady } returns true
    }

    private fun createExecutor(scope: kotlinx.coroutines.CoroutineScope): ActionExecutor {
        val executor = ActionExecutor(
            application = application,
            scope = scope,
            settingsRepository = settingsRepository,
            logger = logger,
            localIntentRouter = mockk(relaxed = true),
            weatherExecutor = mockk(relaxed = true),
            buttonUsageRepository = buttonUsageRepository,
            geminiUseCaseLazy = object : dagger.Lazy<com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase> {
                override fun get() = geminiUseCase
            },
            ttsHelperLazy = object : dagger.Lazy<TextToSpeechHelper> {
                override fun get() = ttsHelper
            }
        )
        executor.setTimeProviderForTest(timeProvider)
        return executor
    }

    @Test
    fun testSyncScanningAndSpeechWithHoldingTime_Timeline() = runTest {
        val actionExecutor = createExecutor(this)
        val buttonConfig = ButtonConfig(
            id = "b1",
            label = "Test",
            spokenText = "Hello",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        val ttsCallback = slot<() -> Unit>()
        every { ttsHelper.speakRouted(any(), any(), any(), any(), capture(ttsCallback)) } returns Unit

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
        verify(exactly = 1) { ttsHelper.speakRouted("Hello", any(), any(), any(), any()) }

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
        val button1 = ButtonConfig(id = "1", label = "B1", spokenText = "A1", auditoryCue = null, isActive = true, buttonAction = SpeakTextButtonAction())
        val button2 = ButtonConfig(id = "2", label = "B2", spokenText = "A2", auditoryCue = null, isActive = true, buttonAction = SpeakTextButtonAction())

        val ttsCallback1 = slot<() -> Unit>()
        every { ttsHelper.speakRouted("A1", any(), any(), any(), capture(ttsCallback1)) } returns Unit

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
        
        // Verifiziere: Aktion 2 wird ignoriert, da A1 noch läuft
        assertTrue(events.any { it is ActionExecutor.ExecutionEvent.Log && it.message.contains("läuft bereits") })
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
            spokenText = "Track",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        val ttsCallback = slot<() -> Unit>()
        every { ttsHelper.speakRouted(any(), any(), any(), any(), capture(ttsCallback)) } returns Unit

        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(buttonConfig, bookId = "book1", rows = 6, columns = 6, index = 10)
        runCurrent()

        // Verify usage was recorded
        coVerify(exactly = 1) { buttonUsageRepository.recordUsage("book1", buttonConfig, 6, 6, 10) }
    }

    @Test
    fun testUsageTrackingSkippedWithoutBookId() = runTest {
        val actionExecutor = createExecutor(this)
        val buttonConfig = ButtonConfig(
            id = "btn-no-book",
            label = "No Book",
            spokenText = "Test",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        every { ttsHelper.speakRouted(any(), any(), any(), any(), any()) } returns Unit

        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(buttonConfig) // No bookId
        runCurrent()

        // Verify usage was NOT recorded (no bookId)
        coVerify(exactly = 0) { buttonUsageRepository.recordUsage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun testGeminiQuotaReached_ProvidesLocalizedFeedback() = runTest {
        val actionExecutor = createExecutor(this)
        val buttonConfig = ButtonConfig(
            id = "g1",
            label = "Gemini",
            auditoryCue = null,
            isActive = true,
            buttonAction = com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction("Help")
        )

        val context = mockk<android.content.Context>(relaxed = true)
        every { ttsHelper.context } returns context
        every { ttsHelper.isReady } returns true
        every { settingsRepository.isGeminiEnabled } returns true
        
        // Return a localized string with a placeholder
        every { application.getString(com.andreas_kratzer.ghosttalk.R.string.error_gemini_quota_reached, 45) } returns "Wait 45s"

        // Mock GeminiUseCase to throw 429 with custom message
        coEvery { geminiUseCase.generateResponse(any()) } throws Exception("HTTP 429: wait 45 seconds")

        actionExecutor.executeButtonAction(buttonConfig)
        runCurrent()

        verify { ttsHelper.speakRouted("Wait 45s", any(), any(), any(), any()) }
    }


    @Test
    fun testFinishExecution_staleId_doesNotUnlockExecuting() = runTest {
        val actionExecutor = createExecutor(this)
        val button1 = ButtonConfig(id = "1", label = "B1", spokenText = "A1", auditoryCue = null, isActive = true, buttonAction = SpeakTextButtonAction())
        val button2 = ButtonConfig(id = "2", label = "B2", spokenText = "A2", auditoryCue = null, isActive = true, buttonAction = SpeakTextButtonAction())

        val ttsCallback1 = slot<() -> Unit>()
        val ttsCallback2 = slot<() -> Unit>()
        every { ttsHelper.speakRouted("A1", any(), any(), any(), capture(ttsCallback1)) } returns Unit
        every { ttsHelper.speakRouted("A2", any(), any(), any(), capture(ttsCallback2)) } returns Unit

        // Execute button 1
        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(button1, bookId = "book1")
        runCurrent()
        assertTrue("Should be executing after B1", actionExecutor.isExecuting.value)

        // Simulate TTS callback for B1 finishes execution
        ttsCallback1.captured.invoke()
        runCurrent()
        assertEquals(false, actionExecutor.isExecuting.value)

        // Execute button 2
        currentTimeMillis = 2000L
        actionExecutor.executeButtonAction(button2, bookId = "book1")
        runCurrent()
        assertTrue("Should be executing after B2", actionExecutor.isExecuting.value)

        // Now B1's callback fires AGAIN (stale!) — this should NOT unlock the executor
        ttsCallback1.captured.invoke()
        runCurrent()
        assertTrue("Should still be executing — stale callback must not unlock", actionExecutor.isExecuting.value)

        // Only B2's callback should unlock
        ttsCallback2.captured.invoke()
        runCurrent()
        assertEquals(false, actionExecutor.isExecuting.value)
    }

    @Test
    fun testExecuteButtonAction_afterHoldingTimeExpires_succeeds() = runTest {
        val actionExecutor = createExecutor(this)
        val buttonConfig = ButtonConfig(
            id = "b1",
            label = "Test",
            spokenText = "Hello",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        val ttsCallback = slot<() -> Unit>()
        every { ttsHelper.speakRouted(any(), any(), any(), any(), capture(ttsCallback)) } returns Unit

        // First press at T=0
        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(buttonConfig, bookId = "book1")
        runCurrent()
        assertTrue(actionExecutor.isExecuting.value)

        // TTS finishes at T=500ms
        currentTimeMillis = 500L
        ttsCallback.captured.invoke()
        runCurrent()
        assertEquals(false, actionExecutor.isExecuting.value)

        // Second press at T=1500ms — past holding time (1000ms), should succeed
        currentTimeMillis = 1500L
        actionExecutor.executeButtonAction(buttonConfig, bookId = "book1")
        runCurrent()

        // Verify: action was executed again (2 total calls)
        verify(exactly = 2) { ttsHelper.speakRouted("Hello", any(), any(), any(), any()) }
        assertTrue(actionExecutor.isExecuting.value)

        // Cleanup
        ttsCallback.captured.invoke()
        runCurrent()
    }

    @Test
    fun testExecuteButonAction_WithinHoldingTime_IgnoresAction() = runTest {
        val actionExecutor = createExecutor(this)
        val button = ButtonConfig(id = "1", label = "B1", buttonAction = SpeakTextButtonAction(), auditoryCue = null)
        
        // First press at T=200
        currentTimeMillis = 200L
        actionExecutor.executeButtonAction(button)
        runCurrent()
        verify(exactly = 1) { ttsHelper.speakRouted(any(), any(), any(), any(), any()) }

        // Second press at T=300 (holding time 1000ms active)
        currentTimeMillis = 300L
        actionExecutor.executeButtonAction(button)
        runCurrent()
        
        // Still only 1 execution
        verify(exactly = 1) { ttsHelper.speakRouted(any(), any(), any(), any(), any()) }
    }

    @Test
    fun testExecuteButtonAction_WhileExecuting_IgnoresAction() = runTest {
        val actionExecutor = createExecutor(this)
        val button = ButtonConfig(id = "1", label = "B1", buttonAction = SpeakTextButtonAction(), auditoryCue = null)
        
        // Force executing state
        actionExecutor.setExecutingStateForTest(true)
        
        // Past holding time, but still executing
        currentTimeMillis = 2000L
        actionExecutor.executeButtonAction(button)
        runCurrent()
        
        // No execution should happen
        verify(exactly = 0) { ttsHelper.speakRouted(any(), any(), any(), any(), any()) }
    }

    @Test
    fun testExecuteButtonAction_MissingHandler_LogsAndFinishes() = runTest {
        val actionExecutor = createExecutor(this)
        // Clear handlers to simulate missing handler
        actionExecutor.handlers = emptyList()
        
        val button = ButtonConfig(id = "1", label = "B1", buttonAction = SpeakTextButtonAction(), auditoryCue = null)
        
        val events = mutableListOf<ActionExecutor.ExecutionEvent>()
        val eventsJob = launch {
            actionExecutor.events.collect { events.add(it) }
        }

        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(button)
        runCurrent()
        
        // Should log and NOT be executing anymore
        assertTrue(events.any { it is ActionExecutor.ExecutionEvent.Log && it.message.contains("Kein Handler") })
        assertEquals(false, actionExecutor.isExecuting.value)
        
        eventsJob.cancel()
    }
}
