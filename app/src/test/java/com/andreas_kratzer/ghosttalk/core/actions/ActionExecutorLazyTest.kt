package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
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
class ActionExecutorLazyTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(testDispatcher)
    private val actionCoordinator = mockk<ActionCoordinator>(relaxed = true)

    private val ttsHelper = mockk<com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper>(relaxed = true)

    @Before
    fun setup() {
    }

    @Test
    fun `navigation action does NOT throw if no handlers match`() = scope.runTest {
        val executor = ActionExecutor(
            scope = scope,
            settingsRepository = mockk(relaxed = true),
            buttonUsageRepository = mockk(relaxed = true),
            handlers = emptySet(),
            actionCoordinator = actionCoordinator,
            ttsHelper = ttsHelper,
            scanCoordinatorProvider = mockk(relaxed = true),
            firebaseAnalyticsManager = mockk(relaxed = true)
        )
        val action = NavigateToPageButtonAction("p2")
        val config = ButtonConfig(id = "b1", label = "Go", buttonAction = action, auditoryCue = null)

        // Should not crash
        executor.executeButtonAction(config)
    }

    @Test
    fun `executor handles handler failure gracefully`() = scope.runTest {
        val failingHandler = mockk<ActionHandler>(relaxed = true)
        every { failingHandler.canHandle(any()) } returns true
        every { failingHandler.handle(any(), any(), any(), any()) } throws RuntimeException("Execution Failed")
        
        val executor = ActionExecutor(
            scope = scope,
            settingsRepository = mockk(relaxed = true),
            buttonUsageRepository = mockk(relaxed = true),
            handlers = setOf(failingHandler),
            actionCoordinator = actionCoordinator,
            ttsHelper = ttsHelper,
            scanCoordinatorProvider = mockk(relaxed = true),
            firebaseAnalyticsManager = mockk(relaxed = true)
        )

        val action = GeminiButtonAction("Hello")
        val config = ButtonConfig(id = "b1", label = "Ask", buttonAction = action, auditoryCue = null)

        // This should not crash the app, but log an error via actionCoordinator
        executor.executeButtonAction(config)
        
        verify { actionCoordinator.error(any(), any()) }
    }
}
