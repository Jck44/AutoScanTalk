package com.andreas_kratzer.ghosttalk.ui

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.FrequentActionResolver
import com.andreas_kratzer.ghosttalk.core.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.TestLogger
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.domain.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.domain.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.PredictNextActionUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private lateinit var templateRepository: TemplateRepository
    private val actionLogUseCase = mockk<ActionLogUseCase>(relaxed = true)
    private val createPageUseCase = mockk<CreatePageUseCase>(relaxed = true)
    private val frequentActionResolver = mockk<FrequentActionResolver>(relaxed = true)
    private val buttonUsageRepository = mockk<ButtonUsageRepository>(relaxed = true)
    private lateinit var driveAuthManager: com.andreas_kratzer.ghosttalk.core.cloud.DriveAuthManager
    private lateinit var geminiUseCaseFactory: com.andreas_kratzer.ghosttalk.domain.GeminiUseCaseFactory
    private lateinit var ttsHelper: com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
    private val predictNextActionUseCase = mockk<PredictNextActionUseCase>(relaxed = true)
    private lateinit var viewModel: PageViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        pageRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        importExportManager = mockk<PageImportExportManager>(relaxed = true)
        getPagesUseCase = mockk<GetPagesUseCase>(relaxed = true)
        driveAuthManager = mockk(relaxed = true)
        geminiUseCaseFactory = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        templateRepository = mockk(relaxed = true) {
            every { getAllTemplates() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        }
        
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
        coEvery { frequentActionResolver.resolve(any(), any()) } answers { firstArg() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PageViewModel {
        return PageViewModel(
            application = application,
            pageRepository = pageRepository,
            settingsRepository = settingsRepository,
            logger = TestLogger,
            importExportManager = importExportManager,
            getPagesUseCase = getPagesUseCase,
            actionLogUseCase = actionLogUseCase,
            createPageUseCase = createPageUseCase,
            frequentActionResolver = frequentActionResolver,
            buttonUsageRepository = buttonUsageRepository,
            driveAuthManager = driveAuthManager,
            templateRepository = templateRepository,
            geminiUseCaseFactory = geminiUseCaseFactory,
            ttsHelper = ttsHelper,
            predictNextActionUseCase = predictNextActionUseCase
        )
    }

    @Test
    fun `createNewPage delegates to CreatePageUseCase`() = runTest {
        viewModel = createViewModel()
        
        viewModel.createNewPage("Test Page", rows = 2, columns = 2, bookId = "test-book-id", templateId = null, onCreated = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { createPageUseCase.execute("Test Page", 2, 2, "test-book-id", any(), null) }
    }

    @Test
    fun `init loads action logs via ActionLogUseCase`() = runTest {
        val mockLogs = listOf("[12:00:00] Log 1")
        every { actionLogUseCase.loadSavedLogs() } returns mockLogs
        
        viewModel = createViewModel()
        
        assertEquals(mockLogs, viewModel.lastActions.value)
    }

    @Test
    fun `logAction calls formatAndAddEntry on ActionLogUseCase`() = runTest {
        viewModel = createViewModel()
        
        val button = ButtonConfig(label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction("Hey"))
        viewModel.loadPage(Page(id = "p1", bookId = "b1", name = "T", rows = 1, columns = 1, buttonConfigs = listOf(button)))
        testDispatcher.scheduler.runCurrent()
        
        viewModel.activateButtonAtIndex(0)
        testDispatcher.scheduler.runCurrent()
        verify { actionLogUseCase.formatAndAddEntry(match { it.contains("Hey") }, any()) }
    }

    @Test
    fun `clearActionLog calls clearLogs on ActionLogUseCase`() = runTest {
        viewModel = createViewModel()
        
        viewModel.clearActionLogs()
        verify { actionLogUseCase.clearLogs() }
    }

    @Test
    fun `importFromJson delegates to importExportManager`() = runTest {
        val jsonString = "{}"
        coEvery { importExportManager.importFromJson(any(), any()) } returns Result.success(1)

        var successCalled = false
        viewModel = createViewModel()
        viewModel.importFromJson(jsonString, "test_book", onSuccess = { successCalled = true }, onError = {})
        
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Success callback should be called", successCalled)
        coVerify { importExportManager.importFromJson(jsonString, "test_book") }
    }

    @Test
    fun `startScanning delegates to ScannerEngine`() = runTest {
        viewModel = createViewModel()
        
        val button = ButtonConfig(label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction("Hey"), isActive = true)
        val page = Page(id = "p1", bookId = "b1", name = "T", rows = 1, columns = 1, buttonConfigs = listOf(button))
        viewModel.loadPage(page)
        testDispatcher.scheduler.runCurrent()
        
        viewModel.startScanning(0)
        testDispatcher.scheduler.advanceTimeBy(1)
        val focused = viewModel.focusedButtonIndex.value
        viewModel.stopScanning()
        
        assertEquals(0, focused)
    }

    @Test
    fun `activateButtonAtIndex navigation loads new page`() = runTest {
        val action = NavigateToPageButtonAction(pageId = "p2")
        val config = ButtonConfig(label = "Nav", auditoryCue = null, buttonAction = action)
        val p1 = Page(id = "p1", bookId = "b1", name = "P1", rows = 1, columns = 1, buttonConfigs = listOf(config))
        val p2 = Page(id = "p2", bookId = "b1", name = "P2", rows = 1, columns = 1, buttonConfigs = emptyList())
        
        coEvery { pageRepository.getPageById("p2") } returns p2

        viewModel = createViewModel()
        
        viewModel.loadPage(p1)
        testDispatcher.scheduler.runCurrent()
        assertEquals("p1", viewModel.currentPage.value?.id)
        
        // Trigger action via simulating setting a focused index
        // or directly calling it
        viewModel.activateButtonAtIndex(0)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals("p2", viewModel.currentPage.value?.id)
    }

    @Test
    fun `loadPage with same page ID pauses scanning`() = runTest {
        viewModel = createViewModel()
        val page = Page(id = "p1", bookId = "b1", name = "P1", rows = 1, columns = 1, buttonConfigs = emptyList())
        
        // Initial load
        viewModel.loadPage(page)
        testDispatcher.scheduler.runCurrent()
        
        // Mock scanning active by setting a focus
        viewModel.scannerEngine.setFocusedIndex(0)
        assertEquals(0, viewModel.focusedButtonIndex.value)
        
        // Load same page again
        viewModel.loadPage(page)
        testDispatcher.scheduler.runCurrent()
        
        // Verify index is PRESERVED (paused, not stopped)
        assertEquals(0, viewModel.focusedButtonIndex.value)
    }

    @Test
    fun `loadPage with different page ID stops scanning`() = runTest {
        viewModel = createViewModel()
        val p1 = Page(id = "p1", bookId = "b1", name = "P1", rows = 1, columns = 1, buttonConfigs = emptyList())
        val p2 = Page(id = "p2", bookId = "b1", name = "P2", rows = 1, columns = 1, buttonConfigs = emptyList())
        
        // Initial load
        viewModel.loadPage(p1)
        testDispatcher.scheduler.runCurrent()
        
        // Mock scanning active
        viewModel.scannerEngine.setFocusedIndex(0)
        assertEquals(0, viewModel.focusedButtonIndex.value)
        
        // Load different page
        viewModel.loadPage(p2)
        testDispatcher.scheduler.runCurrent()
        
        // Verify index is RESET to null (stopped)
        assertEquals(null, viewModel.focusedButtonIndex.value)
    }
}

