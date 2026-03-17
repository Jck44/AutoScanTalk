package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.actions.ActionCoordinator
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.actions.NavigationActionHandler
import com.andreas_kratzer.ghosttalk.core.ai.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.PredictNextActionUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.UpdateSmartPredictionsUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleHomeManager
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.core.scanning.ScannerEngine
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.domain.actions.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.ActivateButtonUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.HandleActionExecutionEventUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.ResolveDynamicButtonsUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.ResolveSmartPredictionUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.DeletePageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.ExportPageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.GetFilteredPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.GetPageUsagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.ImportPageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.MoveRowUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdateButtonConfigUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdatePageSettingsUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdateRowNameUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    
    private lateinit var application: Application
    private lateinit var pageRepository: PageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var templateRepository: TemplateRepository
    private lateinit var importExportManager: PageImportExportManager
    private lateinit var scannerEngine: ScannerEngine
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var geminiUseCase: GeminiUseCase
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var localIntentRouter: LocalIntentRouter
    private lateinit var weatherExecutor: com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor
    private lateinit var logger: Logger
    private lateinit var locationExecutor: com.andreas_kratzer.ghosttalk.domain.executors.LocationExecutor
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    private lateinit var featureGuard: FeatureGuard
    private lateinit var googleHomeManager: GoogleHomeManager
    
    private lateinit var actionLogUseCase: ActionLogUseCase
    private lateinit var getPagesUseCase: GetPagesUseCase
    private lateinit var createPageUseCase: CreatePageUseCase
    private lateinit var deletePageUseCase: DeletePageUseCase
    private lateinit var updateButtonConfigUseCase: UpdateButtonConfigUseCase
    private lateinit var updatePageSettingsUseCase: UpdatePageSettingsUseCase
    private lateinit var updateRowNameUseCase: UpdateRowNameUseCase
    private lateinit var moveRowUseCase: MoveRowUseCase
    private lateinit var moveButtonUseCase: MoveButtonUseCase
    private lateinit var moveButtonToPageUseCase: com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase
    private lateinit var importPageUseCase: ImportPageUseCase
    private lateinit var exportPageUseCase: ExportPageUseCase
    private lateinit var predictNextActionUseCase: PredictNextActionUseCase
    private lateinit var checkForPredictorUseCase: com.andreas_kratzer.ghosttalk.core.ai.domain.CheckForPredictorUseCase
    private lateinit var resolveDynamicButtonsUseCase: ResolveDynamicButtonsUseCase
    private lateinit var updateSmartPredictionsUseCase: UpdateSmartPredictionsUseCase
    private lateinit var getPageUsagesUseCase: GetPageUsagesUseCase

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
        geminiUseCase = mockk<GeminiUseCase>(relaxed = true)
        ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
        localIntentRouter = mockk<LocalIntentRouter>(relaxed = true)
        weatherExecutor = mockk<com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor>(relaxed = true)
        locationExecutor = mockk<com.andreas_kratzer.ghosttalk.domain.executors.LocationExecutor>(relaxed = true)
        logger = mockk<Logger>(relaxed = true)
        buttonUsageRepository = mockk<ButtonUsageRepository>(relaxed = true)
        featureGuard = mockk<FeatureGuard>(relaxed = true)
        googleHomeManager = mockk<GoogleHomeManager>(relaxed = true)

        actionLogUseCase = mockk<ActionLogUseCase>(relaxed = true)
        getPagesUseCase = mockk<GetPagesUseCase>(relaxed = true)
        createPageUseCase = mockk<CreatePageUseCase>(relaxed = true)
        deletePageUseCase = mockk<DeletePageUseCase>(relaxed = true)
        updateButtonConfigUseCase = mockk<UpdateButtonConfigUseCase>(relaxed = true)
        updatePageSettingsUseCase = mockk<UpdatePageSettingsUseCase>(relaxed = true)
        updateRowNameUseCase = mockk<UpdateRowNameUseCase>(relaxed = true)
        moveRowUseCase = mockk<MoveRowUseCase>(relaxed = true)
        moveButtonUseCase = mockk<MoveButtonUseCase>(relaxed = true)
        moveButtonToPageUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase>(relaxed = true)
        importPageUseCase = mockk<ImportPageUseCase>(relaxed = true)
        exportPageUseCase = mockk<ExportPageUseCase>(relaxed = true)
        predictNextActionUseCase = mockk<PredictNextActionUseCase>(relaxed = true)
        checkForPredictorUseCase = mockk<com.andreas_kratzer.ghosttalk.core.ai.domain.CheckForPredictorUseCase>(relaxed = true)
        resolveDynamicButtonsUseCase = mockk<ResolveDynamicButtonsUseCase>(relaxed = true)
        updateSmartPredictionsUseCase = mockk<UpdateSmartPredictionsUseCase>(relaxed = true)
        getPageUsagesUseCase = mockk<GetPageUsagesUseCase>(relaxed = true)

        // Mock common flows with explicit types to avoid Nothing exceptions
        every { settingsRepository.activeBookIdFlow } returns MutableStateFlow<String>("b1")
        every { settingsRepository.activeBookId } returns "b1"
        every { settingsRepository.isSmartPredictionEnabledFlow } returns MutableStateFlow<Boolean>(true)
        every { settingsRepository.isSmartPredictionEnabled } returns true
        every { settingsRepository.defaultScanPatternFlow } returns MutableStateFlow<String>("linear")
        every { settingsRepository.showTestButtonsFlow } returns MutableStateFlow<Boolean>(false)
        every { settingsRepository.scanDelayFlow } returns MutableStateFlow<Long>(3000L)
        every { settingsRepository.persistActionLogsFlow } returns MutableStateFlow<Boolean>(false)
        every { settingsRepository.keepScreenOnUserModeFlow } returns MutableStateFlow<Boolean>(false)
        every { settingsRepository.userModeScreenBehaviorFlow } returns MutableStateFlow<String>("NONE")
        every { settingsRepository.holdingTimeMillis } returns 0L
        every { settingsRepository.pageSortOrderFlow } returns MutableStateFlow("MANUAL")
        every { settingsRepository.geminiRedoPrediction } returns false
        every { settingsRepository.autoStartScanning } returns false
        every { settingsRepository.resumeScanningFromStart } returns true
        
        every { templateRepository.getAllTemplates() } returns MutableStateFlow<List<PageTemplate>>(emptyList())
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow<List<Page>>(emptyList())
        coEvery { resolveDynamicButtonsUseCase.execute(any(), any(), any(), any()) } answers { firstArg() }
        
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
        val appStateRepository = mockk<com.andreas_kratzer.ghosttalk.core.data.AppStateRepository>(relaxed = true)
        every { appStateRepository.isUserModeActive } returns MutableStateFlow(true)
        every { appStateRepository.activeBookId } returns MutableStateFlow("b1")
        every { appStateRepository.currentPageId } returns MutableStateFlow("p1")
        
        val pageManagementDelegate = PageManagementDelegate(
            pageRepository = pageRepository,
            bookRepository = bookRepository,
            templateRepository = templateRepository,
            getPagesUseCase = getPagesUseCase,
            createPageUseCase = createPageUseCase,
            deletePageUseCase = deletePageUseCase,
            updateButtonConfigUseCase = updateButtonConfigUseCase,
            updatePageSettingsUseCase = updatePageSettingsUseCase,
            updateRowNameUseCase = updateRowNameUseCase,
            moveRowUseCase = moveRowUseCase,
            moveButtonUseCase = moveButtonUseCase,
            moveButtonToPageUseCase = moveButtonToPageUseCase,
            importPageUseCase = importPageUseCase,
            exportPageUseCase = exportPageUseCase,
            getFilteredPagesUseCase = GetFilteredPagesUseCase(settingsRepository),
            getPageUsagesUseCase = getPageUsagesUseCase,
            appStateRepository = appStateRepository
        )
        val interactionDelegate = InteractionDelegate(
            application = application,
            actionLogUseCase = actionLogUseCase,
            ttsHelper = ttsHelper,
            activateButtonUseCase = ActivateButtonUseCase(ttsHelper, ResolveSmartPredictionUseCase(pageRepository)),
            handleActionExecutionEventUseCase = HandleActionExecutionEventUseCase(pageRepository, settingsRepository),
            locationExecutor = locationExecutor,
            appStateRepository = appStateRepository,
            bookRepository = bookRepository
        )
        val smartPredictionDelegate = SmartPredictionDelegate(
            updateSmartPredictionsUseCase = updateSmartPredictionsUseCase
        )
        val screenManagementDelegate = ScreenManagementDelegate(settingsRepository)
        
        val actionCoordinator = ActionCoordinator(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            logger = logger
        )
        
        val navHandler = NavigationActionHandler(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            settingsRepository = settingsRepository,
            ttsHelperLazy = object : dagger.Lazy<TextToSpeechHelper> {
                override fun get() = ttsHelper
            },
            actionEventEmitter = actionCoordinator,
            actionLogger = actionCoordinator
        )

        val actionExecutor = ActionExecutor(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            settingsRepository = settingsRepository,
            buttonUsageRepository = buttonUsageRepository,
            handlers = setOf(navHandler),
            actionCoordinator = actionCoordinator
        )

        val scanCoordinator = mockk<ScanCoordinator>(relaxed = true)

        return PageViewModel(
            application = application,
            settingsRepository = settingsRepository,
            importExportManager = importExportManager,
            googleAuthManager = googleAuthManager,
            ttsHelper = ttsHelper,
            logger = logger,
            weatherExecutor = weatherExecutor,
            featureGuard = featureGuard,
            pageManagementDelegate = pageManagementDelegate,
            interactionDelegate = interactionDelegate,
            screenManagementDelegate = screenManagementDelegate,
            smartPredictionDelegate = smartPredictionDelegate,
            resolveDynamicButtonsUseCase = resolveDynamicButtonsUseCase,
            updateSmartPredictionsUseCase = updateSmartPredictionsUseCase,
            actionExecutor = actionExecutor,
            scanCoordinator = scanCoordinator,
            geminiUseCase = geminiUseCase,
            googleHomeManager = googleHomeManager
        )
    }

    @Test
    fun `loadPage updates currentPage flow`() = runTest {
        viewModel = createViewModel()
        val page = Page(id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1, buttonConfigs = List(49) { null })
        
        viewModel.loadPage(page)
        
        assertEquals(page.id, viewModel.currentPage.value?.id)
    }

    @Test
    fun `activateButtonAtIndex navigation loads new page`() = runTest {
        viewModel = createViewModel()

        val action = NavigateToPageButtonAction(pageId = "p2")
        val config = ButtonConfig(id = "b1", label = "Nav", spokenText = "Nav", auditoryCue = null, buttonAction = action)
        val p1Configs = MutableList<ButtonConfig?>(49) { null }
        p1Configs[0] = config
        val p1 = Page(id = "p1", bookId = "b1", name = "P1", rows = 1, columns = 1, buttonConfigs = p1Configs)
        val p2 = Page(id = "p2", bookId = "b1", name = "P2", rows = 1, columns = 1, buttonConfigs = List(49) { null })
        
        coEvery { pageRepository.getPageById("p2") } returns p2

        viewModel.setActiveBookId("b1")
        viewModel.loadPage(p1)

        // Bypass resolvedPage by calling interactionDelegate directly to avoid flowOn(Dispatchers.Default) issues in test
        viewModel.interactionDelegate.activateButtonAtIndex(0, p1, "b1")
        testScheduler.advanceUntilIdle()

        assertEquals(p2.id, viewModel.currentPage.value?.id)
        assertEquals(p2.name, viewModel.currentPage.value?.name)
    }
}
