package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.ui.pages.ScanCoordinator
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ActivateButtonUseCaseTest {

    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var resolveSmartPredictionUseCase: ResolveSmartPredictionUseCase
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var scanCoordinator: ScanCoordinator
    private lateinit var useCase: ActivateButtonUseCase

    @Before
    fun setup() {
        ttsHelper = mockk(relaxed = true)
        resolveSmartPredictionUseCase = mockk(relaxed = true)
        actionExecutor = mockk(relaxed = true)
        scanCoordinator = mockk(relaxed = true)
        useCase = ActivateButtonUseCase(ttsHelper, resolveSmartPredictionUseCase)
        
        every { actionExecutor.isExecuting } returns MutableStateFlow(false)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
    }

    @Test
    fun `execute ignores click if actionExecutor is executing`() = runTest {
        every { actionExecutor.isExecuting } returns MutableStateFlow(true)

        useCase.execute(0, null, null, true, emptyList(), actionExecutor, scanCoordinator)

        verify(exactly = 0) { ttsHelper.stopNotificationTTS() }
    }

    @Test
    fun `execute stops notification tts and sets focus`() = runTest {
        val testButton = ButtonConfig(id = "btn1", label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val testPage = Page(id = "p1", bookId = "b1", name = "P1", buttonConfigs = MutableList(36) { if (it == 0) testButton else null }, rows = 2, columns = 2)

        useCase.execute(0, testPage, "b1", true, emptyList(), actionExecutor, scanCoordinator)

        verify { ttsHelper.stopNotificationTTS() }
        verify { scanCoordinator.setFocusedIndex(0) }
    }

    @Test
    fun `execute calls actionExecutor`() = runTest {
        val execButton = ButtonConfig(id = "btn1", label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val execPage = Page(id = "p1", bookId = "b1", name = "P1", buttonConfigs = MutableList(36) { if (it == 0) execButton else null }, rows = 2, columns = 2)

        useCase.execute(0, execPage, "b1", true, emptyList(), actionExecutor, scanCoordinator)

        verify { actionExecutor.executeButtonAction(execButton, bookId = "b1", rows = 2, columns = 2, index = 0) }
    }

    @Test
    fun `execute smart prediction button delegates to resolveSmartPredictionUseCase`() = runTest {
        val smartAction = SmartPredictionButtonAction(rank = 1)
        val button = ButtonConfig(id = "smart", label = "Smart", auditoryCue = null, buttonAction = smartAction)
        val page = Page(id = "p1", bookId = "b1", name = "P1", buttonConfigs = MutableList(36) { if (it == 0) button else null }, rows = 2, columns = 2)
        val predictions = listOf("pred1")

        useCase.execute(0, page, "b1", true, predictions, actionExecutor, scanCoordinator)

        coVerify { 
            resolveSmartPredictionUseCase.execute("pred1", page, "b1", true, actionExecutor) 
        }
    }
}
