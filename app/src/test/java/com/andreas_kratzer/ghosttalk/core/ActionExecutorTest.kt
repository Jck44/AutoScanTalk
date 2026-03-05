import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor

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
import com.andreas_kratzer.ghosttalk.core.util.TestLogger
import com.andreas_kratzer.ghosttalk.core.util.Logger
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
    private val logger: Logger = TestLogger()

    private var currentTimeMillis = 0L
    private val timeProvider: () -> Long = { currentTimeMillis }

    @Before
    fun setup() {
        // Mock SettingsRepository values
        every { settingsRepository.holdingTimeMillis } returns 1000L
        every { settingsRepository.ttsVolumeMultiplier } returns 1.0f
        every { settingsRepository.cuesVolumeMultiplier } returns 1.0f
        every { ttsHelper.isReady } returns true
    }

    private fun createExecutor(scope: kotlinx.coroutines.CoroutineScope) = ActionExecutor(
        scope = scope,
        settingsRepository = settingsRepository,
        ttsHelper = ttsHelper,
        geminiUseCase = geminiUseCase,
        buttonUsageRepository = buttonUsageRepository,
        logger = logger,
        timeProvider = timeProvider,
        localIntentRouter = mockk(relaxed = true)
    )

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
        every { ttsHelper.speakRouted(any(), any(), any(), any(), any(), capture(ttsCallback)) } returns Unit

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
        verify(exactly = 1) { ttsHelper.speakRouted("Hello", any(), any(), any(), any(), any()) }

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
        every { ttsHelper.speakRouted("A1", any(), any(), any(), any(), capture(ttsCallback1)) } returns Unit

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
            spokenText = "Track",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        val ttsCallback = slot<() -> Unit>()
        every { ttsHelper.speakRouted(any(), any(), any(), any(), any(), capture(ttsCallback)) } returns Unit

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
            spokenText = "Test",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        every { ttsHelper.speakRouted(any(), any(), any(), any(), any(), any()) } returns Unit

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
            isActive = true,
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

        verify { ttsHelper.speakRouted("Wait 45s", any(), any(), any(), any(), any()) }
    }

    @Test
    fun testChangeVolumeButtonActionRelative() = runTest {
        val actionExecutor = createExecutor(this)
        val buttonConfig = ButtonConfig(
            id = "v1",
            label = "VolUp",
            auditoryCue = null,
            isActive = true,
            buttonAction = com.andreas_kratzer.ghosttalk.model.ChangeVolumeButtonAction(isAbsolute = false, amount = 0.2f, isForCues = false)
        )

        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(buttonConfig)
        runCurrent()

        // 1.0 + 0.2 = 1.2 => capped at 1.0
        verify { settingsRepository.ttsVolumeMultiplier = 1.0f }
        verify(exactly = 0) { settingsRepository.cuesVolumeMultiplier = any() }
    }

    @Test
    fun testChangeVolumeButtonActionAbsolute() = runTest {
        val actionExecutor = createExecutor(this)
        val buttonConfig = ButtonConfig(
            id = "v1",
            label = "VolMax",
            auditoryCue = null,
            isActive = true,
            buttonAction = com.andreas_kratzer.ghosttalk.model.ChangeVolumeButtonAction(isAbsolute = true, amount = 3.0f, isForCues = true)
        )

        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(buttonConfig)
        runCurrent()

        verify { settingsRepository.cuesVolumeMultiplier = 1.0f }
        verify(exactly = 0) { settingsRepository.ttsVolumeMultiplier = any() }
    }

    @Test
    fun testFinishExecution_staleId_doesNotUnlockExecuting() = runTest {
        val actionExecutor = createExecutor(this)
        val button1 = ButtonConfig(id = "1", label = "B1", spokenText = "A1", auditoryCue = null, isActive = true, buttonAction = SpeakTextButtonAction())
        val button2 = ButtonConfig(id = "2", label = "B2", spokenText = "A2", auditoryCue = null, isActive = true, buttonAction = SpeakTextButtonAction())

        val ttsCallback1 = slot<() -> Unit>()
        val ttsCallback2 = slot<() -> Unit>()
        every { ttsHelper.speakRouted("A1", any(), any(), any(), any(), capture(ttsCallback1)) } returns Unit
        every { ttsHelper.speakRouted("A2", any(), any(), any(), any(), capture(ttsCallback2)) } returns Unit

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
        every { ttsHelper.speakRouted(any(), any(), any(), any(), any(), capture(ttsCallback)) } returns Unit

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
        verify(exactly = 2) { ttsHelper.speakRouted("Hello", any(), any(), any(), any(), any()) }
        assertTrue(actionExecutor.isExecuting.value)

        // Cleanup
        ttsCallback.captured.invoke()
        runCurrent()
    }
}
