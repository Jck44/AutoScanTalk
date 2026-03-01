package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionExecutorTest {
    private val scope = TestScope()
    private val pageRepository = mockk<PageRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
    private val onLoadPage = mockk<(com.andreas_kratzer.ghosttalk.model.Page) -> Unit>(relaxed = true)
    private val onPauseScanning = mockk<() -> Unit>(relaxed = true)
    private val onResumeScanning = mockk<() -> Unit>(relaxed = true)
    private val onLogAction = mockk<(String) -> Unit>(relaxed = true)

    private var currentTimeMillis = 0L
    private val timeProvider: () -> Long = { currentTimeMillis }

    private lateinit var actionExecutor: ActionExecutor

    @Before
    fun setup() {
        actionExecutor = ActionExecutor(
            scope = scope,
            pageRepository = pageRepository,
            settingsRepository = settingsRepository,
            ttsHelper = ttsHelper,
            onLoadPage = onLoadPage,
            onPauseScanning = onPauseScanning,
            onResumeScanning = onResumeScanning,
            onLogAction = onLogAction,
            timeProvider = timeProvider
        )
        // Haltezeit = 1000ms
        every { settingsRepository.holdingTimeMillis } returns 1000L
        every { ttsHelper.isReady } returns true
    }

    @Test
    fun testSyncScanningAndSpeechWithHoldingTime_Timeline() = runTest {
        val buttonConfig = ButtonConfig(
            id = "b1",
            label = "Test",
            auditoryCue = null,
            buttonAction = SpeakTextButtonAction("Hello")
        )

        val ttsCallback = slot<() -> Unit>()
        every { ttsHelper.speakRouted(any(), any(), any(), capture(ttsCallback)) } returns Unit

        // 200ms: Nutzer drückt das erste Mal
        currentTimeMillis = 200L
        actionExecutor.executeButtonAction(buttonConfig)

        // Verifiziere: Scanning pausiert, Sprache gestartet
        verify(exactly = 1) { onPauseScanning() }
        verify(exactly = 1) { ttsHelper.speakRouted("Hello", any(), any(), any()) }
        verify(exactly = 0) { onResumeScanning() }

        // 310ms: Nutzer drückt nochmal -> Haltezeit (1000ms) ist noch aktiv (200 + 1000 = 1200)
        currentTimeMillis = 310L
        actionExecutor.executeButtonAction(buttonConfig)

        // Verifiziere: Aktion ignoriert, KEIN resume (da Sprache noch läuft), KEIN erneutes pausieren
        verify { onLogAction(match { it.contains("ignoriert") }) }
        verify(exactly = 1) { onPauseScanning() } 
        verify(exactly = 0) { onResumeScanning() }

        // 1200ms: Haltezeit ist zu Ende

        // 1250ms: Text ist fertig gesprochen (TTS Callback wird gefeuert)
        currentTimeMillis = 1250L
        ttsCallback.captured.invoke()

        // Jetzt MUSS das Scanning wieder starten
        verify(exactly = 1) { onResumeScanning() }
    }

    @Test
    fun testInterleavedActions_PreventsPrematureResume() = runTest {
        val button1 = ButtonConfig(id = "1", label = "B1", auditoryCue = null, buttonAction = SpeakTextButtonAction("A1"))
        val button2 = ButtonConfig(id = "2", label = "B2", auditoryCue = null, buttonAction = SpeakTextButtonAction("A2"))

        val ttsCallback1 = slot<() -> Unit>()
        val ttsCallback2 = slot<() -> Unit>()

        // Mock for first action
        every { ttsHelper.speakRouted("A1", any(), any(), capture(ttsCallback1)) } returns Unit
        // Mock for second action
        every { ttsHelper.speakRouted("A2", any(), any(), capture(ttsCallback2)) } returns Unit

        // 1. First action
        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(button1)
        verify(exactly = 1) { onPauseScanning() }

        // 2. Wait past holding time
        currentTimeMillis = 1500L

        // 3. Second action (T=1500ms, past 1000ms holding time, but speech A1 still active)
        actionExecutor.executeButtonAction(button2)
        
        // Verifiziere: Aktion 2 wird ignoriert, da A1 noch spricht
        verify { onLogAction(match { it.contains("Sprachausgabe aktiv") }) }
        // onPauseScanning wurde NICHT ein zweites Mal aufgerufen
        verify(exactly = 1) { onPauseScanning() }

        // Simulate TextToSpeechHelper calling the OLD callback for A1 because of the flush
        // (In the real app, we don't flush if it's ignored, but keep the test robust)
        ttsCallback1.captured.invoke()

        // Jetzt MUSS das Scanning wieder starten
        verify(exactly = 1) { onResumeScanning() }
    }

    @Test
    fun testSpeechProtection_EnsuresDynamicHoldingTime() = runTest {
        val button1 = ButtonConfig(id = "1", label = "B1", auditoryCue = null, buttonAction = SpeakTextButtonAction("A1"))
        val button2 = ButtonConfig(id = "2", label = "B2", auditoryCue = null, buttonAction = SpeakTextButtonAction("A2"))

        val ttsCallback1 = slot<() -> Unit>()
        every { ttsHelper.speakRouted("A1", any(), any(), capture(ttsCallback1)) } returns Unit

        // 1. Trigger Action 1 at T=0
        currentTimeMillis = 0L
        actionExecutor.executeButtonAction(button1)
        verify(exactly = 1) { onPauseScanning() }

        // 2. Wait 1500ms (Past the 1000ms configured holding time)
        currentTimeMillis = 1500L

        // 3. Trigger Action 2 while Action 1 is STILL SPEAKING
        actionExecutor.executeButtonAction(button2)

        // Verifiziere: Aktion 2 wird ignoriert, da isSpeaking noch true ist
        verify { onLogAction(match { it.contains("Sprachausgabe aktiv") }) }
        // Scanning bleibt pausiert, ttsHelper wurde NICHT erneut aufgerufen
        verify(exactly = 1) { ttsHelper.speakRouted("A1", any(), any(), any()) }
        verify(exactly = 0) { ttsHelper.speakRouted("A2", any(), any(), any()) }

        // 4. Action 1 finishes at T=2000ms
        currentTimeMillis = 2000L
        ttsCallback1.captured.invoke()

        // Verifiziere: Scanning resumed
        verify(exactly = 1) { onResumeScanning() }

        // 5. Trigger Action 2 again (now it should work)
        currentTimeMillis = 2100L
        actionExecutor.executeButtonAction(button2)
        verify(exactly = 2) { onPauseScanning() }
        verify(exactly = 1) { ttsHelper.speakRouted("A2", any(), any(), any()) }
    }
}
