package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiActionHandlerTest {
    private val scope = TestScope()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val geminiUseCase = mockk<GeminiUseCase>(relaxed = true)
    private val localIntentRouter = mockk<LocalIntentRouter>(relaxed = true)
    private val ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
    
    private val events = mutableListOf<ActionExecutor.ExecutionEvent>()

    private lateinit var handler: GeminiActionHandler

    @Before
    fun setup() {
        handler = GeminiActionHandler(
            scope = scope,
            settingsRepository = settingsRepository,
            geminiUseCase = geminiUseCase,
            localIntentRouter = localIntentRouter,
            ttsHelper = ttsHelper,
            emitEvent = { events.add(it) },
            log = { println(it) }
        )
        every { ttsHelper.isReady } returns true
    }

    @Test
    fun `NanoAction should execute even if Cloud is disabled`() = scope.runTest {
        // GIVEN
        val action = GeminiNanoButtonAction("What time is it?")
        val config = ButtonConfig(
            id = "1", 
            label = "Time", 
            auditoryCue = null,
            buttonAction = action
        )
        
        every { settingsRepository.useLocalGenerativeAi } returns true
        every { settingsRepository.isGeminiEnabled } returns false // Cloud disabled
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        val callback = slot<(String) -> Unit>()
        coVerify { localIntentRouter.routeIntent("What time is it?", capture(callback)) }
        
        // Simulate response
        callback.captured.invoke("12:00")
        runCurrent()
        
        val ttsCallback = slot<() -> Unit>()
        verify { ttsHelper.speakRouted(text = "12:00", deviceAddress = any(), onDone = capture(ttsCallback)) }
        
        ttsCallback.captured.invoke()
        runCurrent()
        
        verify { onFinish(1) }
    }

    @Test
    fun `CloudAction should not use Nano even if Nano is enabled`() = scope.runTest {
        // GIVEN
        val action = GeminiButtonAction("What is AI?")
        val config = ButtonConfig(
            id = "1", 
            label = "Nano Check", 
            auditoryCue = null,
            buttonAction = action
        )
        
        every { settingsRepository.useLocalGenerativeAi } returns true // Nano enabled globally
        every { settingsRepository.isGeminiEnabled } returns true
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        coEvery { geminiUseCase.generateResponse(any(), any()) } returns "Cloud Response"
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        coVerify(exactly = 0) { localIntentRouter.routeIntent(any(), any()) } // Must NOT use Nano
        coVerify { geminiUseCase.generateResponse("What is AI?", any()) }
        
        verify { ttsHelper.speakRouted(text = "Cloud Response", deviceAddress = any(), onDone = any()) }
    }

    @Test
    fun `NanoAction should speak error if Nano is disabled`() = scope.runTest {
        // GIVEN
        val action = GeminiNanoButtonAction("Help")
        val config = ButtonConfig(
            id = "1", 
            label = "Help", 
            auditoryCue = null,
            buttonAction = action
        )
        
        every { settingsRepository.useLocalGenerativeAi } returns false
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val ttsCallback = slot<() -> Unit>()
        every { ttsHelper.speakRouted(text = any(), deviceAddress = any(), onDone = capture(ttsCallback)) } returns Unit
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        coVerify(exactly = 0) { localIntentRouter.routeIntent(any(), any()) }
        verify { ttsHelper.speakRouted(text = any(), deviceAddress = any(), onDone = any()) } 
        
        ttsCallback.captured.invoke()
        runCurrent()
        
        verify { onFinish(1) }
    }
}
