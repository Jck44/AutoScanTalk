package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.domain.actions.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.domain.actions.ActivateButtonUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.HandleActionExecutionEventUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InteractionDelegateTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(testDispatcher)
    
    private val application = mockk<Application>(relaxed = true)
    private val actionLogUseCase = mockk<ActionLogUseCase>(relaxed = true)
    private val ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
    private val activateButtonUseCase = mockk<ActivateButtonUseCase>(relaxed = true)
    private val handleActionExecutionEventUseCase = mockk<HandleActionExecutionEventUseCase>(relaxed = true)
    private val locationExecutor = mockk<com.andreas_kratzer.ghosttalk.domain.executors.LocationExecutor>(relaxed = true)
    private val appStateRepository = mockk<AppStateRepository>(relaxed = true)
    private val bookRepository = mockk<BookRepository>(relaxed = true)
    private val actionExecutor = mockk<ActionExecutor>(relaxed = true)
    private val scanCoordinator = mockk<ScanCoordinator>(relaxed = true)

    private lateinit var delegate: InteractionDelegate

    @Before
    fun setup() {
        every { appStateRepository.isUserModeActive } returns MutableStateFlow(true)
        every { appStateRepository.activeBookId } returns MutableStateFlow("b1")
        every { actionExecutor.events } returns MutableSharedFlow()
        every { actionExecutor.isExecuting } returns MutableStateFlow(false)
        
        delegate = InteractionDelegate(
            application, actionLogUseCase, ttsHelper, activateButtonUseCase,
            handleActionExecutionEventUseCase, locationExecutor, appStateRepository, bookRepository
        )
        delegate.scanCoordinator = scanCoordinator
    }

    @Test
    fun `activateButtonAtIndex should call ActivateButtonUseCase`() = runTest(testDispatcher) {
        val page = Page(id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1, buttonConfigs = emptyList())
        val smartPredictions = MutableStateFlow<List<String>?>(null)
        
        delegate.init(scope, actionExecutor, {}, smartPredictions, MutableStateFlow("b1"))
        
        delegate.activateButtonAtIndex(5, page, "b1")
        
        coVerify { activateButtonUseCase.execute(
            index = 5,
            currentPage = page,
            activeBookId = "b1",
            isUserModeActive = true,
            smartPredictions = any(),
            actionExecutor = actionExecutor,
            scanCoordinator = scanCoordinator
        ) }
    }

    @Test
    fun `setUserModeActive(false) should stop notification TTS and actions`() {
        delegate.init(scope, actionExecutor, {}, MutableStateFlow(null), MutableStateFlow("b1"))
        
        delegate.setUserModeActive(false)
        
        verify { ttsHelper.stopNotificationTTS() }
        verify { actionExecutor.stopActions() }
        verify { appStateRepository.setUserModeActive(false) }
    }
}
