package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.ai.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
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
    private val ttsProxy = mockk<ActionTtsProxy>(relaxed = true)
    
    private val events = mutableListOf<ActionExecutor.ExecutionEvent>()

    private lateinit var handler: GeminiActionHandler

    @Before
    fun setup() {
        handler = GeminiActionHandler(
            scope = scope,
            settingsRepository = settingsRepository,
            geminiUseCaseLazy = object : dagger.Lazy<GeminiUseCase> {
                override fun get() = geminiUseCase
            },
            localIntentRouter = localIntentRouter,
            ttsProxyLazy = object : dagger.Lazy<ActionTtsProxy> {
                override fun get() = ttsProxy
            },
            emitEvent = { _ -> },
            log = { _ -> },
            error = { _, _ -> },
            getString = { _, args -> "Wait ${args[0]}s" }
        )
        every { ttsProxy.isReady } returns true
    }

    @Test
    fun `NanoAction should execute even if Cloud is disabled`() = scope.runTest {
        // GIVEN
        val action = GeminiNanoButtonAction("alarm")
        val config = ButtonConfig(
            id = "1", 
            label = "Alarm", 
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
        coVerify { localIntentRouter.executeIntent("alarm", capture(callback)) }
        
        // Simulate response
        callback.captured.invoke("12:00")
        runCurrent()
        
        val ttsCallback = slot<() -> Unit>()
        verify { ttsProxy.speakRouted(text = "12:00", deviceAddress = any(), onDone = capture(ttsCallback)) }
        
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
        coVerify(exactly = 0) { localIntentRouter.executeIntent(any<String>(), any<(String) -> Unit>()) } // Must NOT use Nano
        coVerify { geminiUseCase.generateResponse("What is AI?", any()) }
        
        verify { ttsProxy.speakRouted(text = "Cloud Response", deviceAddress = any<String>(), onDone = any<() -> Unit>()) }
    }

    @Test
    fun `NanoAction should speak error if Nano is disabled`() = scope.runTest {
        // GIVEN
        val action = GeminiNanoButtonAction("alarm")
        val config = ButtonConfig(
            id = "1", 
            label = "Alarm", 
            auditoryCue = null,
            buttonAction = action
        )
        
        every { settingsRepository.useLocalGenerativeAi } returns false
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val ttsCallback = slot<() -> Unit>()
        every { ttsProxy.speakRouted(text = any<String>(), deviceAddress = any<String>(), onDone = capture(ttsCallback)) } returns Unit
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        coVerify(exactly = 0) { localIntentRouter.executeIntent(any<String>(), any<(String) -> Unit>()) }
        verify { ttsProxy.speakRouted(text = any<String>(), deviceAddress = any<String>(), onDone = any<() -> Unit>()) } 
        
        ttsCallback.captured.invoke()
        runCurrent()
        
        verify { onFinish(1) }
    }
    
    @Test
    fun `CloudAction should speak Wait 45s when 429 occurs`() = scope.runTest {
        // GIVEN
        val action = GeminiButtonAction("Help")
        val config = ButtonConfig(id = "1", label = "Gemini", auditoryCue = null, buttonAction = action)
        
        every { settingsRepository.isGeminiEnabled } returns true
        coEvery { geminiUseCase.generateResponse(any(), any()) } throws Exception("HTTP 429: wait 45 seconds")
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val ttsCallback = slot<() -> Unit>()
        every { ttsProxy.speakRouted(text = "Wait 45s", deviceAddress = any(), onDone = capture(ttsCallback)) } returns Unit
        
        // WHEN
        handler.handle(config, action, 1, onFinish)
        runCurrent()
        
        // THEN
        verify { ttsProxy.speakRouted(text = "Wait 45s", deviceAddress = any(), onDone = any()) }
        
        ttsCallback.captured.invoke()
        runCurrent()
        verify { onFinish(1) }
    }

    @Test
    fun `GeminiActionHandler regression - should extract wait time correctly from 429 error and skip status code`() = scope.runTest {
        // GIVEN
        every { settingsRepository.isGeminiEnabled } returns true
        every { ttsProxy.isReady } returns true
        
        // Mock 429 with both code and wait time
        coEvery { geminiUseCase.generateResponse(any(), any()) } throws Exception("HTTP 429: Rate limit exceeded. Wait 45 seconds.")

        val button = ButtonConfig(id = "1", label = "G", buttonAction = GeminiButtonAction("Hi"), auditoryCue = null)
        
        // WHEN
        handler.handle(button, button.buttonAction!!, 1) {}
        runCurrent()

        // THEN
        // Verify it extracts 45, not 429 or 42945
        verify { ttsProxy.speakRouted("Wait 45s", any(), any(), any(), any()) }
    }
}
