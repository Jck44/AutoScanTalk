package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationActionHandlerTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var log: (String) -> Unit
    private lateinit var emitEvent: suspend (ActionExecutor.ExecutionEvent) -> Unit
    private lateinit var handler: NavigationActionHandler

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        log = mockk(relaxed = true)
        emitEvent = mockk(relaxed = true)
    }

    @Test
    fun `canHandle returns true for NavigateToPageButtonAction`() = runTest {
        handler = NavigationActionHandler(this, settingsRepository, object : dagger.Lazy<TextToSpeechHelper> {
            override fun get() = ttsHelper
        }, emitEvent, log)
        assert(handler.canHandle(NavigateToPageButtonAction("p1")))
    }

    @Test
    fun `handle navigates immediately if no feedback provided`() = runTest {
        handler = NavigationActionHandler(this, settingsRepository, object : dagger.Lazy<TextToSpeechHelper> {
            override fun get() = ttsHelper
        }, emitEvent, log)
        val action = NavigateToPageButtonAction("p2")
        val config = ButtonConfig(id = "b1", label = "Go", spokenText = null, buttonAction = action, auditoryCue = null)
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)

        handler.handle(config, action, 1, onFinish)
        runCurrent()

        coVerify { emitEvent(ActionExecutor.ExecutionEvent.NavigateToPage("p2")) }
        verify { onFinish(1) }
    }

    @Test
    fun `handle navigates immediately if feedback is blank`() = runTest {
        handler = NavigationActionHandler(this, settingsRepository, object : dagger.Lazy<TextToSpeechHelper> {
            override fun get() = ttsHelper
        }, emitEvent, log)
        val action = NavigateToPageButtonAction("p2")
        val config = ButtonConfig(id = "b1", label = "Go", spokenText = "", buttonAction = action, auditoryCue = null)
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)

        handler.handle(config, action, 1, onFinish)
        runCurrent()

        coVerify { emitEvent(ActionExecutor.ExecutionEvent.NavigateToPage("p2")) }
        verify { onFinish(1) }
    }

    @Test
    fun `handle navigates after tts speech if feedback provided`() = runTest {
        handler = NavigationActionHandler(this, settingsRepository, object : dagger.Lazy<TextToSpeechHelper> {
            override fun get() = ttsHelper
        }, emitEvent, log)
        val action = NavigateToPageButtonAction("p2")
        val config = ButtonConfig(id = "b1", label = "Go", spokenText = "Navigating", buttonAction = action, auditoryCue = null)
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)

        every { ttsHelper.isReady } returns true
        val onCompleteSlot = slot<() -> Unit>()
        every { ttsHelper.speakRouted(any(), any(), any(), any(), capture(onCompleteSlot)) } returns Unit

        handler.handle(config, action, 1, onFinish)
        runCurrent()

        // Navigation should NOT have happened yet
        coVerify(exactly = 0) { emitEvent(any()) }
        
        // Trigger completion
        onCompleteSlot.captured.invoke()
        runCurrent()

        coVerify { emitEvent(ActionExecutor.ExecutionEvent.NavigateToPage("p2")) }
        verify { onFinish(1) }
        verify { log("Navigations-Feedback: \"Navigating\"") }
    }

    @Test
    fun `handle navigates immediately and logs if feedback provided but tts not ready`() = runTest {
        handler = NavigationActionHandler(this, settingsRepository, object : dagger.Lazy<TextToSpeechHelper> {
            override fun get() = ttsHelper
        }, emitEvent, log)
        val action = NavigateToPageButtonAction("p2")
        val config = ButtonConfig(id = "b1", label = "Go", spokenText = "Navigating", buttonAction = action, auditoryCue = null)
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)

        every { ttsHelper.isReady } returns false

        handler.handle(config, action, 1, onFinish)
        runCurrent()

        coVerify { emitEvent(ActionExecutor.ExecutionEvent.NavigateToPage("p2")) }
        verify { log("Nav-Feedback (TTS nicht bereit): \"Navigating\"") }
        verify { onFinish(1) }
    }
}
