package com.example.gostalk.ui

import android.app.Application
import com.example.gostalk.core.PageImportExportManager
import com.example.gostalk.core.ScannerEngine
import com.example.gostalk.core.util.TestLogger
import com.example.gostalk.data.PageRepository
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.domain.GetPagesUseCase
import com.example.gostalk.domain.ActionLogUseCase
import com.example.gostalk.domain.CreatePageUseCase
import com.example.gostalk.model.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var pageRepository: PageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var importExportManager: PageImportExportManager
    private lateinit var getPagesUseCase: GetPagesUseCase
    private lateinit var actionLogUseCase: ActionLogUseCase
    private lateinit var createPageUseCase: CreatePageUseCase
    private lateinit var viewModel: PageViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        pageRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        importExportManager = mockk<PageImportExportManager>(relaxed = true)
        getPagesUseCase = mockk<GetPagesUseCase>(relaxed = true)
        actionLogUseCase = mockk<ActionLogUseCase>(relaxed = true)
        createPageUseCase = mockk<CreatePageUseCase>(relaxed = true)
        
        // Mock default flows
        every { settingsRepository.ttsLanguageFlow } returns MutableStateFlow("default")
        every { settingsRepository.ttsVoiceNameFlow } returns MutableStateFlow(null)
        every { settingsRepository.scanDelayFlow } returns MutableStateFlow(1000L)
        every { settingsRepository.scanDelayMillis } returns 1000L
        every { settingsRepository.autoStartScanning } returns false
        every { settingsRepository.persistActionLogs } returns false
        every { settingsRepository.persistActionLogsFlow } returns MutableStateFlow(false)
        every { settingsRepository.actionLogsStorage } returns null
        every { settingsRepository.actionLogsStorageFlow } returns MutableStateFlow(null)
        
        // Mock UseCase behavior
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow(emptyList())
        every { actionLogUseCase.loadSavedLogs() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createNewPage delegates to CreatePageUseCase`() = runTest {
        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)
        viewModel = PageViewModel(
            application, pageRepository, settingsRepository, ttsHelper = mockTts, 
            logger = TestLogger, importExportManager = importExportManager,
            getPagesUseCase = getPagesUseCase, actionLogUseCase = actionLogUseCase,
            createPageUseCase = createPageUseCase
        )
        
        viewModel.createNewPage("Test Page", rows = 2, columns = 2, bookId = "test-book-id")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { createPageUseCase.execute("Test Page", 2, 2, "test-book-id", any()) }
    }

    @Test
    fun `init loads action logs via ActionLogUseCase`() = runTest {
        val mockLogs = listOf("[12:00:00] Log 1")
        every { actionLogUseCase.loadSavedLogs() } returns mockLogs
        
        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)
        viewModel = PageViewModel(
            application, pageRepository, settingsRepository, ttsHelper = mockTts, 
            logger = TestLogger, importExportManager = importExportManager,
            getPagesUseCase = getPagesUseCase, actionLogUseCase = actionLogUseCase,
            createPageUseCase = createPageUseCase
        )
        
        assertEquals(mockLogs, viewModel.lastActions.value)
    }

    @Test
    fun `logAction calls formatAndAddEntry on ActionLogUseCase`() = runTest {
        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)
        viewModel = PageViewModel(
            application, pageRepository, settingsRepository, ttsHelper = mockTts, 
            logger = TestLogger, importExportManager = importExportManager,
            getPagesUseCase = getPagesUseCase, actionLogUseCase = actionLogUseCase,
            createPageUseCase = createPageUseCase
        )
        
        val button = ButtonConfig(label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction("Hey"))
        viewModel.loadPage(Page(id = "p1", bookId = "b1", name = "T", rows = 1, columns = 1, buttonConfigs = listOf(button)))
        
        viewModel.activateButtonAtIndex(0)
        verify { actionLogUseCase.formatAndAddEntry(match { it.contains("Hey") }, any()) }
    }

    @Test
    fun `clearActionLog calls clearLogs on ActionLogUseCase`() = runTest {
        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)
        viewModel = PageViewModel(
            application, pageRepository, settingsRepository, ttsHelper = mockTts, 
            logger = TestLogger, importExportManager = importExportManager,
            getPagesUseCase = getPagesUseCase, actionLogUseCase = actionLogUseCase,
            createPageUseCase = createPageUseCase
        )
        
        viewModel.clearActionLogs()
        verify { actionLogUseCase.clearLogs() }
    }

    @Test
    fun `importFromJson delegates to importExportManager`() = runTest {
        val jsonString = "{}"
        coEvery { importExportManager.importFromJson(any(), any()) } returns Result.success(1)

        var successCalled = false
        val mockTts = mockk<com.example.gostalk.tts.TextToSpeechHelper>(relaxed = true)
        viewModel = PageViewModel(
            application, pageRepository, settingsRepository, ttsHelper = mockTts, 
            logger = TestLogger, importExportManager = importExportManager,
            getPagesUseCase = getPagesUseCase, actionLogUseCase = actionLogUseCase,
            createPageUseCase = createPageUseCase
        )
        viewModel.importFromJson(jsonString, "test_book", onSuccess = { successCalled = true }, onError = {})
        
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Success callback should be called", successCalled)
        coVerify { importExportManager.importFromJson(jsonString, "test_book") }
    }
}
