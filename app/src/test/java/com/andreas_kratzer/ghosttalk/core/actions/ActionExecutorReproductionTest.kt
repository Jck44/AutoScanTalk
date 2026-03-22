package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionExecutorReproductionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(testDispatcher)
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    private lateinit var actionCoordinator: ActionCoordinator
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var mockHandler: ActionHandler
    private lateinit var ttsHelper: TextToSpeechHelper

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        buttonUsageRepository = mockk(relaxed = true)
        actionCoordinator = mockk(relaxed = true)
        mockHandler = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        
        actionExecutor = ActionExecutor(
            scope = scope,
            settingsRepository = settingsRepository,
            buttonUsageRepository = buttonUsageRepository,
            handlers = setOf(mockHandler),
            actionCoordinator = actionCoordinator,
            ttsHelper = ttsHelper
        )
    }

    @Test
    fun `reproduce double click issue`() = runTest {
        val action = SpeakTextButtonAction()
        val button = ButtonConfig(id = "1", label = "Test", buttonAction = action, auditoryCue = null)
        every { mockHandler.canHandle(action) } returns true
        every { settingsRepository.holdingTimeMillis } returns 1000L
        
        // Simulating the case where onFinish might be called quickly or something else happens
        // First click
        actionExecutor.setTimeProviderForTest { 0L }
        actionExecutor.executeButtonAction(button)
        
        // Let's say it finishes immediately (some handlers do this if not ready)
        // actionExecutor.finishExecution is usually called by handler. 
        // In the real code it's a callback.
        
        // Second click at T=100ms
        actionExecutor.setTimeProviderForTest { 100L }
        actionExecutor.executeButtonAction(button)
        
        // Verify only 1 execution
        verify(exactly = 1) { mockHandler.handle(button, action, any(), any()) }
    }
}
