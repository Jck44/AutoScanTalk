package com.andreas_kratzer.ghosttalk.ui

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.pages.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.scanning.ScannerEngine
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.actions.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.ActivateButtonUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.HandleActionExecutionEventUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.PredictNextActionUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.ResolveSmartPredictionUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.UpdateSmartPredictionsUseCase
import com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCaseFactory
import com.andreas_kratzer.ghosttalk.domain.pages.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.DeletePageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.ExportPageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.GetFilteredPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.ImportPageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.ReorderPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdateButtonConfigUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdatePageSettingsUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdateRowNameUseCase
import com.andreas_kratzer.ghosttalk.domain.settings.FeatureGuard
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.InteractionDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.ScreenManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.SmartPredictionDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var application: Application
    private lateinit var pageRepository: PageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var templateRepository: TemplateRepository
    private lateinit var importExportManager: PageImportExportManager
    private lateinit var scannerEngine: ScannerEngine
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var geminiUseCaseFactory: GeminiUseCaseFactory
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var localIntentRouter: LocalIntentRouter
    private lateinit var logger: Logger
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    private lateinit var featureGuard: FeatureGuard
    
    private lateinit var actionLogUseCase: ActionLogUseCase
    private lateinit var getPagesUseCase: GetPagesUseCase
    private lateinit var createPageUseCase: CreatePageUseCase
    private lateinit var deletePageUseCase: DeletePageUseCase
    private lateinit var reorderPagesUseCase: ReorderPagesUseCase
    private lateinit var updateButtonConfigUseCase: UpdateButtonConfigUseCase
    private lateinit var updatePageSettingsUseCase: UpdatePageSettingsUseCase
    private lateinit var updateRowNameUseCase: UpdateRowNameUseCase
    private lateinit var importPageUseCase: ImportPageUseCase
    private lateinit var exportPageUseCase: ExportPageUseCase
    private lateinit var predictNextActionUseCase: PredictNextActionUseCase
    private lateinit var checkForPredictorUseCase: com.andreas_kratzer.ghosttalk.domain.settings.CheckForPredictorUseCase

    private lateinit var viewModel: PageViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        application = mockk<Application>(relaxed = true)
        pageRepository = mockk<PageRepository>(relaxed = true)
        settingsRepository = mockk<SettingsRepository>(relaxed = true)
        bookRepository = mockk<BookRepository>(relaxed = true)
        templateRepository = mockk<TemplateRepository>(relaxed = true)
        importExportManager = mockk<PageImportExportManager>(relaxed = true)
        scannerEngine = mockk<ScannerEngine>(relaxed = true)
        googleAuthManager = mockk<GoogleAuthManager>(relaxed = true)
        geminiUseCaseFactory = mockk<GeminiUseCaseFactory>(relaxed = true)
        ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
        localIntentRouter = mockk<LocalIntentRouter>(relaxed = true)
        logger = mockk<Logger>(relaxed = true)
        buttonUsageRepository = mockk<ButtonUsageRepository>(relaxed = true)
        featureGuard = mockk<FeatureGuard>(relaxed = true)

        actionLogUseCase = mockk<ActionLogUseCase>(relaxed = true)
        getPagesUseCase = mockk<GetPagesUseCase>(relaxed = true)
        createPageUseCase = mockk<CreatePageUseCase>(relaxed = true)
        deletePageUseCase = mockk<DeletePageUseCase>(relaxed = true)
        reorderPagesUseCase = mockk<ReorderPagesUseCase>(relaxed = true)
        updateButtonConfigUseCase = mockk<UpdateButtonConfigUseCase>(relaxed = true)
        updatePageSettingsUseCase = mockk<UpdatePageSettingsUseCase>(relaxed = true)
        updateRowNameUseCase = mockk<UpdateRowNameUseCase>(relaxed = true)
        importPageUseCase = mockk<ImportPageUseCase>(relaxed = true)
        exportPageUseCase = mockk<ExportPageUseCase>(relaxed = true)
        predictNextActionUseCase = mockk<PredictNextActionUseCase>(relaxed = true)
        checkForPredictorUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.settings.CheckForPredictorUseCase>(relaxed = true)

        // Mock common flows with explicit types to avoid Nothing exceptions
        every { settingsRepository.activeBookIdFlow } returns MutableStateFlow<String>("b1")
        every { settingsRepository.activeBookId } returns "b1"
        every { settingsRepository.isSmartPredictionEnabledFlow } returns MutableStateFlow<Boolean>(true)
        every { settingsRepository.defaultScanPatternFlow } returns MutableStateFlow<String>("linear")
        every { settingsRepository.showTestButtonsFlow } returns MutableStateFlow<Boolean>(false)
        every { settingsRepository.experimentalManualSortingFlow } returns MutableStateFlow<Boolean>(false)
        every { settingsRepository.scanDelayFlow } returns MutableStateFlow<Long>(3000L)
        every { settingsRepository.persistActionLogsFlow } returns MutableStateFlow<Boolean>(false)
        every { settingsRepository.keepScreenOnUserModeFlow } returns MutableStateFlow<Boolean>(false)
        every { settingsRepository.userModeScreenBehaviorFlow } returns MutableStateFlow<String>("NONE")
        every { settingsRepository.holdingTimeMillis } returns 0L
        
        every { templateRepository.getAllTemplates() } returns MutableStateFlow<List<PageTemplate>>(emptyList())
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow<List<Page>>(emptyList())
        
        // Mock scannerEngine flows
        every { scannerEngine.focusedButtonIndex } returns MutableStateFlow<Int?>(null)
        every { scannerEngine.focusedRowIndex } returns MutableStateFlow<Int?>(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): PageViewModel {
        val pageManagementDelegate = PageManagementDelegate(
            pageRepository = pageRepository,
            bookRepository = bookRepository,
            templateRepository = templateRepository,
            getPagesUseCase = getPagesUseCase,
            createPageUseCase = createPageUseCase,
            deletePageUseCase = deletePageUseCase,
            reorderPagesUseCase = reorderPagesUseCase,
            updateButtonConfigUseCase = updateButtonConfigUseCase,
            updatePageSettingsUseCase = updatePageSettingsUseCase,
            updateRowNameUseCase = updateRowNameUseCase,
            importPageUseCase = importPageUseCase,
            exportPageUseCase = exportPageUseCase,
            getFilteredPagesUseCase = GetFilteredPagesUseCase(settingsRepository)
        )
        val interactionDelegate = InteractionDelegate(
            application = application,
            actionLogUseCase = actionLogUseCase,
            ttsHelper = ttsHelper,
            activateButtonUseCase = ActivateButtonUseCase(ttsHelper, ResolveSmartPredictionUseCase(pageRepository)),
            handleActionExecutionEventUseCase = HandleActionExecutionEventUseCase(pageRepository, settingsRepository)
        )
        val smartPredictionDelegate = SmartPredictionDelegate(
            updateSmartPredictionsUseCase = UpdateSmartPredictionsUseCase(settingsRepository, predictNextActionUseCase, checkForPredictorUseCase)
        )
        val screenManagementDelegate = ScreenManagementDelegate(settingsRepository)

        return PageViewModel(
            application, settingsRepository, importExportManager, scannerEngine, googleAuthManager,
            geminiUseCaseFactory, ttsHelper, localIntentRouter, logger, buttonUsageRepository, 
            featureGuard, pageManagementDelegate, interactionDelegate, screenManagementDelegate, smartPredictionDelegate
        )
    }

    @Test
    fun `loadPage updates currentPage flow`() = runTest {
        viewModel = createViewModel()
        val page = Page(id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1, buttonConfigs = emptyList())
        
        viewModel.loadPage(page)
        advanceUntilIdle()
        
        assertEquals(page, viewModel.currentPage.value)
    }

    @Test
    fun `activateButtonAtIndex navigation loads new page`() = runTest {
        viewModel = createViewModel()

        val action = NavigateToPageButtonAction(pageId = "p2")
        val config = ButtonConfig(label = "Nav", auditoryCue = null, buttonAction = action)
        val p1 = Page(id = "p1", bookId = "b1", name = "P1", rows = 1, columns = 1, buttonConfigs = listOf(config))
        val p2 = Page(id = "p2", bookId = "b1", name = "P2", rows = 1, columns = 1, buttonConfigs = emptyList())
        
        coEvery { pageRepository.getPageById("p2") } returns p2

        viewModel.loadPage(p1)
        advanceUntilIdle()

        viewModel.activateButtonAtIndex(0)
        advanceUntilIdle()

        assertEquals(p2, viewModel.currentPage.value)
    }
}
