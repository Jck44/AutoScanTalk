package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionExecutorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(testDispatcher)
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    private lateinit var actionCoordinator: ActionCoordinator
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var mockHandler: ActionHandler

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        buttonUsageRepository = mockk(relaxed = true)
        actionCoordinator = mockk(relaxed = true)
        mockHandler = mockk(relaxed = true)
        
        actionExecutor = ActionExecutor(
            scope = scope,
            settingsRepository = settingsRepository,
            buttonUsageRepository = buttonUsageRepository,
            handlers = setOf(mockHandler),
            actionCoordinator = actionCoordinator
        )
    }

    @Test
    fun `executeButtonAction ignores if holding time not passed`() {
        val action = SpeakTextButtonAction()
        val button = ButtonConfig(id = "1", label = "Test", buttonAction = action, auditoryCue = null)
        every { mockHandler.canHandle(action) } returns true
        every { settingsRepository.holdingTimeMillis } returns 1000L
        
        actionExecutor.setTimeProviderForTest { 0L }
        actionExecutor.executeButtonAction(button)
        
        actionExecutor.setTimeProviderForTest { 500L }
        actionExecutor.executeButtonAction(button)
        
        verify(exactly = 1) { mockHandler.handle(button, action, any(), any()) }
        verify { actionCoordinator.log(match { it.contains("Haltezeit aktiv") }) }
    }

    @Test
    fun `executeButtonAction ignores if already executing`() {
        every { settingsRepository.holdingTimeMillis } returns 0L
        actionExecutor.setExecutingStateForTest(true)
        
        val button = ButtonConfig(id = "1", label = "Test", buttonAction = SpeakTextButtonAction(), auditoryCue = null)
        actionExecutor.executeButtonAction(button)
        
        verify(exactly = 0) { mockHandler.handle(any(), any(), any(), any()) }
        verify { actionCoordinator.log(match { it.contains("Aktion läuft bereits") }) }
    }

    @Test
    fun `executeButtonAction calls handler if it can handle action`() {
        val action = SpeakTextButtonAction()
        val button = ButtonConfig(id = "1", label = "Test", buttonAction = action, auditoryCue = null)
        every { mockHandler.canHandle(action) } returns true
        
        actionExecutor.executeButtonAction(button)
        
        verify { mockHandler.handle(button, action, any(), any()) }
    }

    @Test
    fun `executeButtonAction logs if no handler found`() {
        val action = SpeakTextButtonAction()
        val button = ButtonConfig(id = "1", label = "Test", buttonAction = action, auditoryCue = null)
        every { mockHandler.canHandle(any()) } returns false
        
        actionExecutor.executeButtonAction(button)
        
        verify { actionCoordinator.log(match { it.contains("Kein Handler für Aktion gefunden") }) }
    }

    @Test
    fun `isExecuting flow updates correctly`() = scope.runTest {
        val action = SpeakTextButtonAction()
        val button = ButtonConfig(id = "1", label = "Test", buttonAction = action, auditoryCue = null)
        every { mockHandler.canHandle(any()) } returns true
        
        var finishCallback: ((Int) -> Unit)? = null
        every { mockHandler.handle(any(), any(), any(), any()) } answers {
            finishCallback = it.invocation.args[3] as (Int) -> Unit
        }

        assertFalse(actionExecutor.isExecuting.value)
        
        actionExecutor.executeButtonAction(button)
        assertTrue(actionExecutor.isExecuting.value)
        
        finishCallback?.invoke(1)
        assertFalse(actionExecutor.isExecuting.value)
    }

    @Test
    fun `usage is recorded when bookId and index are provided`() = scope.runTest {
        val button = ButtonConfig(id = "1", label = "Test", buttonAction = SpeakTextButtonAction(), auditoryCue = null)
        
        actionExecutor.executeButtonAction(button, bookId = "b1", index = 5)
        
        coVerify { buttonUsageRepository.recordUsage("b1", button, 1, 1, 5) }
    }
}
