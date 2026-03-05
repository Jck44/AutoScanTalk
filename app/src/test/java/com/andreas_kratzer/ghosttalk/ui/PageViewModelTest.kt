package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.actions.FrequentActionResolver
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.pages.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.scanning.ScannerEngine
import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.*
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.InteractionDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.SmartPredictionDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var geminiUseCaseFactory: com.andreas_kratzer.ghosttalk.domain.GeminiUseCaseFactory
    private lateinit var ttsHelper: com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
    private lateinit var bookRepository: BookRepository
    private val predictNextActionUseCase = mockk<PredictNextActionUseCase>(relaxed = true)
    private val scannerEngine = mockk<ScannerEngine>(relaxed = true)
    private val importPageUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.ImportPageUseCase>(relaxed = true)
    private val exportPageUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.ExportPageUseCase>(relaxed = true)
    private val deletePageUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.DeletePageUseCase>(relaxed = true)
    private val reorderPagesUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.ReorderPagesUseCase>(relaxed = true)
    private val updateButtonConfigUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.UpdateButtonConfigUseCase>(relaxed = true)
    private val updatePageSettingsUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.UpdatePageSettingsUseCase>(relaxed = true)
    private val updateRowNameUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.UpdateRowNameUseCase>(relaxed = true)
    private val logger = com.andreas_kratzer.ghosttalk.core.util.TestLogger()
    private lateinit var featureGuard: com.andreas_kratzer.ghosttalk.domain.FeatureGuard
    
    private lateinit var activateButtonUseCase: ActivateButtonUseCase
    private lateinit var checkForPredictorUseCase: CheckForPredictorUseCase
    private lateinit var viewModel: PageViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        application = mockk(relaxed = true)
        pageRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        importExportManager = mockk<PageImportExportManager>(relaxed = true)
        getPagesUseCase = mockk<GetPagesUseCase>(relaxed = true)
        googleAuthManager = mockk(relaxed = true)
        geminiUseCaseFactory = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true) {
            every { isReady } returns true
            every { speakRouted(any(), any(), any(), any(), any(), any()) } answers {
                val callback = arg<(() -> Unit)?>(5)
                callback?.invoke()
            }
            every { speak(any(), any(), any()) } answers {
                val callback = arg<(() -> Unit)?>(2)
                callback?.invoke()
            }
        }
        bookRepository = mockk(relaxed = true)
        templateRepository = mockk(relaxed = true) {
            every { getAllTemplates() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        }
        featureGuard = mockk(relaxed = true) {
            every { isButtonVisible(any()) } returns true
            every { isActionEnabled(any()) } returns true
        }
        
        activateButtonUseCase = mockk(relaxed = true)
        checkForPredictorUseCase = mockk(relaxed = true)
        
        every { settingsRepository.ttsLanguageFlow } returns MutableStateFlow("default")
        every { settingsRepository.ttsVoiceNameFlow } returns MutableStateFlow(null)
        every { settingsRepository.scanDelayFlow } returns MutableStateFlow(1000L)
        every { settingsRepository.scanDelayMillis } returns 1000L
        every { settingsRepository.defaultScanPattern } returns "linear"
        every { settingsRepository.defaultScanPatternFlow } returns MutableStateFlow("linear")
        every { settingsRepository.autoStartScanning } returns false
        every { settingsRepository.persistActionLogs } returns false
        every { settingsRepository.persistActionLogsFlow } returns MutableStateFlow(false)
        every { settingsRepository.actionLogsStorage } returns null
        every { settingsRepository.actionLogsStorageFlow } returns MutableStateFlow(null)
        every { settingsRepository.ttsVolumeMultiplierFlow } returns MutableStateFlow(1.0f)
        every { settingsRepository.cuesVolumeMultiplierFlow } returns MutableStateFlow(1.0f)
        
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow(emptyList())
        every { actionLogUseCase.loadSavedLogs() } returns emptyList()
        val engineFocusFlow = MutableStateFlow<Int?>(null)
        val engineRowFlow = MutableStateFlow<Int?>(null)
        every { scannerEngine.focusedButtonIndex } returns engineFocusFlow
        every { scannerEngine.focusedRowIndex } returns engineRowFlow
        every { scannerEngine.setFocusedIndex(any()) } answers { engineFocusFlow.value = it.invocation.args[0] as Int? }
        every { scannerEngine.stopScanning() } answers { 
            engineFocusFlow.value = null
            engineRowFlow.value = null
        }
        every { scannerEngine.startScanning(any(), any(), any(), any(), any(), any()) } answers {
            engineFocusFlow.value = it.invocation.args[1] as Int?
        }
        every { scannerEngine.scanDelayMillis = any() } returns Unit
        every { scannerEngine.scanDelayMillis } returns 1000L
        coEvery { frequentActionResolver.resolve(any<Page>(), any<String>()) } answers { it.invocation.args[0] as Page }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.clearAllMocks()
    }

    private fun createViewModel(): PageViewModel {
        val pageManagementDelegate = PageManagementDelegate(
            pageRepository = pageRepository,
            bookRepository = bookRepository,
            settingsRepository = settingsRepository,
            templateRepository = templateRepository,
            getPagesUseCase = getPagesUseCase,
            createPageUseCase = createPageUseCase,
            deletePageUseCase = deletePageUseCase,
            reorderPagesUseCase = reorderPagesUseCase,
            updateButtonConfigUseCase = updateButtonConfigUseCase,
            updatePageSettingsUseCase = updatePageSettingsUseCase,
            updateRowNameUseCase = updateRowNameUseCase,
            importPageUseCase = importPageUseCase,
            exportPageUseCase = exportPageUseCase
        )
        val interactionDelegate = InteractionDelegate(
            application = application,
            pageRepository = pageRepository,
            settingsRepository = settingsRepository,
            actionLogUseCase = actionLogUseCase,
            ttsHelper = ttsHelper,
            activateButtonUseCase = activateButtonUseCase
        )
        val smartPredictionDelegate = SmartPredictionDelegate(
            settingsRepository = settingsRepository,
            predictNextActionUseCase = predictNextActionUseCase,
            checkForPredictorUseCase = checkForPredictorUseCase
        )

        return PageViewModel(
            application = application,
            settingsRepository = settingsRepository,
            importExportManager = importExportManager,
            scannerEngine = scannerEngine,
            googleAuthManager = googleAuthManager,
            geminiUseCaseFactory = geminiUseCaseFactory,
            ttsHelper = ttsHelper,
            localIntentRouter = mockk(relaxed = true),
            logger = logger,
            buttonUsageRepository = buttonUsageRepository,
            featureGuard = featureGuard,
            pageManagementDelegate = pageManagementDelegate,
            interactionDelegate = interactionDelegate,
            smartPredictionDelegate = smartPredictionDelegate
        )
    }

    @Test
    fun `activateButtonAtIndex calls activateButtonUseCase`() = runTest {
        viewModel = createViewModel()
        val page = Page(id = "p1", bookId = "b1", name = "T", buttonConfigs = listOf(null))
        viewModel.loadPage(page)
        advanceUntilIdle()

        viewModel.activateButtonAtIndex(0)
        advanceUntilIdle()

        coVerify { 
            activateButtonUseCase.execute(0, any(), any(), any(), any(), any(), any()) 
        }
    }

    @Test
    fun `createNewPage delegates to CreatePageUseCase`() = runTest {
        viewModel = createViewModel()
        
        viewModel.createNewPage("Test Page", rows = 2, columns = 2, bookId = "test-book-id", templateId = null, onCreated = {})
        advanceUntilIdle()

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
        
        viewModel.setUserModeActive(true)
        viewModel.interactionDelegate.logAction("Hey")
        advanceUntilIdle()
        
        verify { actionLogUseCase.formatAndAddEntry("Hey", any()) }
    }

    @Test
    fun `clearActionLog calls clearLogs on ActionLogUseCase`() = runTest {
        viewModel = createViewModel()
        
        viewModel.clearActionLogs()
        verify { actionLogUseCase.clearLogs() }
    }

    @Test
    fun `importFromJson delegates to importPageUseCase`() = runTest {
        val jsonString = "{}"
        coEvery { importPageUseCase.execute(any<String>(), any<String>()) } returns Result.success(1)

        var successCalled = false
        viewModel = createViewModel()
        viewModel.importFromJson(jsonString, "test_book", onSuccess = { successCalled = true }, onError = {})
        
        advanceUntilIdle()

        assertTrue("Success callback should be called", successCalled)
        coVerify { importPageUseCase.execute(jsonString, "test_book") }
    }

    @Test
    fun `startScanning delegates to ScannerEngine`() = runTest {
        every { featureGuard.isButtonVisible(any()) } returns true
        val engineFocusFlow = MutableStateFlow<Int?>(null)
        every { scannerEngine.focusedButtonIndex } returns engineFocusFlow
        every { scannerEngine.startScanning(any(), any(), any(), any(), any(), any()) } answers {
            engineFocusFlow.value = args[1] as Int
        }
        
        viewModel = createViewModel()
        
        val button = ButtonConfig(label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction("Hey"), isActive = true)
        val page = Page(id = "p1", bookId = "b1", name = "T", rows = 1, columns = 1, buttonConfigs = listOf(button))
        viewModel.loadPage(page)
        advanceUntilIdle()
        
        viewModel.startScanning(0)
        advanceTimeBy(110)
        
        val focused = viewModel.focusedButtonIndex.value
        viewModel.stopScanning()
        
        assertEquals(0, focused)
    }

    @Test
    fun `activateButtonAtIndex navigation loads new page`() = runTest {
        // Since InteractionDelegate.activateButtonAtIndex is now using ActivateButtonUseCase, 
        // we either mock that use case to call onPageLoadRequested or test the real use case separately.
        // For this test, I will provide a real ActivateButtonUseCase instance to test the full flow.
        
        val resolveSmartPredictionUseCase = ResolveSmartPredictionUseCase(pageRepository)
        val realActivateButtonUseCase = ActivateButtonUseCase(ttsHelper, resolveSmartPredictionUseCase)
        
        val interactionDelegate = InteractionDelegate(
            application, pageRepository, settingsRepository, actionLogUseCase, ttsHelper, realActivateButtonUseCase
        )
        
        val pageManagementDelegate = PageManagementDelegate(
            pageRepository, bookRepository, settingsRepository, templateRepository, getPagesUseCase, 
            createPageUseCase, deletePageUseCase, reorderPagesUseCase, updateButtonConfigUseCase, 
            updatePageSettingsUseCase, updateRowNameUseCase, importPageUseCase, exportPageUseCase
        )
        
        val smartPredictionDelegate = SmartPredictionDelegate(
            settingsRepository, predictNextActionUseCase, checkForPredictorUseCase
        )

        viewModel = PageViewModel(
            application, settingsRepository, importExportManager, scannerEngine, googleAuthManager,
            geminiUseCaseFactory, ttsHelper, mockk(relaxed = true), logger, buttonUsageRepository, 
            featureGuard, pageManagementDelegate, interactionDelegate, smartPredictionDelegate
        )

        val action = NavigateToPageButtonAction(pageId = "p2")
        val config = ButtonConfig(label = "Nav", auditoryCue = null, buttonAction = action)
        val p1 = Page(id = "p1", bookId = "b1", name = "P1", rows = 1, columns = 1, buttonConfigs = listOf(config))
        val p2 = Page(id = "p2", bookId = "b1", name = "P2", rows = 1, columns = 1, buttonConfigs = emptyList())
        
        coEvery { pageRepository.getPageById("p2") } returns p2

        viewModel.loadPage(p1)
        advanceUntilIdle()
        assertEquals("p1", viewModel.currentPage.value?.id)
        
        viewModel.activateButtonAtIndex(0)
        advanceUntilIdle()
        
        assertEquals("p2", viewModel.currentPage.value?.id)
    }

    @Test
    fun `loadPage with same page ID pauses scanning`() = runTest {
        viewModel = createViewModel()
        val page = Page(id = "p1", bookId = "b1", name = "P1", rows = 1, columns = 1, buttonConfigs = emptyList())
        
        viewModel.loadPage(page)
        advanceUntilIdle()
        
        viewModel.scannerEngine.setFocusedIndex(0)
        assertEquals(0, viewModel.focusedButtonIndex.value)
        
        viewModel.loadPage(page)
        advanceUntilIdle()
        
        assertEquals(0, viewModel.focusedButtonIndex.value)
    }

    @Test
    fun `loadPage with different page ID stops scanning`() = runTest {
        viewModel = createViewModel()
        val p1 = Page(id = "p1", bookId = "b1", name = "P1", rows = 1, columns = 1, buttonConfigs = emptyList())
        val p2 = Page(id = "p2", bookId = "b1", name = "P2", rows = 1, columns = 1, buttonConfigs = emptyList())
        
        viewModel.loadPage(p1)
        advanceUntilIdle()
        
        viewModel.scannerEngine.setFocusedIndex(0)
        assertEquals(0, viewModel.focusedButtonIndex.value)
        
        viewModel.loadPage(p2)
        testDispatcher.scheduler.runCurrent()
        
        assertEquals(null, viewModel.focusedButtonIndex.value)
    }

    @Test
    fun `resumeScanningIfEnabled starts scan if autoStartScanning is true on re-entry`() = runTest {
        every { settingsRepository.autoStartScanning } returns true
        every { featureGuard.isButtonVisible(any()) } returns true
        viewModel = createViewModel()
        val button = ButtonConfig(label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction("Hey"), isActive = true)
        val page = Page(id = "p1", bookId = "b1", name = "P1", rows = 1, columns = 1, buttonConfigs = listOf(button))
        
        viewModel.loadPage(page)
        advanceUntilIdle()
        
        // Ensure scanning is fully stopped
        viewModel.stopScanning()
        assertEquals(null, viewModel.focusedButtonIndex.value)
        
        // Simulate UI re-entry calling resumeScanningIfEnabled
        viewModel.resumeScanningIfEnabled()
        advanceTimeBy(1100)
        
        assertEquals(0, viewModel.focusedButtonIndex.value)
    }

    @Test
    fun `isUserModeActive prevents scanning from resuming automatically`() = runTest {
        every { settingsRepository.autoStartScanning } returns true
        every { featureGuard.isButtonVisible(any()) } returns true
        viewModel = createViewModel()
        val button = ButtonConfig(label = "Test", auditoryCue = null, buttonAction = SpeakTextButtonAction("Hey"), isActive = true)
        val page = Page(id = "p1", bookId = "b1", name = "P1", rows = 1, columns = 1, buttonConfigs = listOf(button))
        
        // 1. Load page but DO NOT set user mode active
        viewModel.loadPage(page)
        advanceUntilIdle()
        
        // Ensure scanning hasn't started
        viewModel.stopScanning()
        assertEquals(null, viewModel.focusedButtonIndex.value)
        
        // 2. Simulate ActionExecutor finishing its execution
        // Because isUserModeActive is false (default), it should NOT resume scanning
        viewModel.actionExecutor.setExecutingStateForTest(true)
        advanceUntilIdle()
        viewModel.actionExecutor.setExecutingStateForTest(false)
        advanceTimeBy(1100)
        
        // Verification: Scan should still not have started
        assertEquals(null, viewModel.focusedButtonIndex.value)
        
        // 3. Now set user mode active and simulate execution block again
        viewModel.setUserModeActive(true)
        advanceUntilIdle()
        
        viewModel.actionExecutor.setExecutingStateForTest(true)
        viewModel.stopScanning() // Stop any scan started by setUserModeActive/loadPage combo
        advanceUntilIdle()
        
        viewModel.actionExecutor.setExecutingStateForTest(false)
        advanceTimeBy(1100)
        
        // Verification: Now the scan should have resumed
        assertEquals(0, viewModel.focusedButtonIndex.value)
        
        // Clean up to prevent UncompletedCoroutinesError
        viewModel.stopScanning()
    }
}
