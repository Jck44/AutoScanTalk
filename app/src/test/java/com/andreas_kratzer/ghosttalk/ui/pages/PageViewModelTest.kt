package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.andreas_kratzer.ghosttalk.core.actions.ActionCoordinator
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.actions.ActionTtsProxy
import com.andreas_kratzer.ghosttalk.core.actions.NavigationActionHandler
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.UpdateSmartPredictionsUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.domain.actions.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.DeletePageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.DuplicateButtonToPageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.ExportPageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.GetFilteredPagesUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.GetPageUsagesUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.ImportPageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.MoveRowUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdateButtonConfigUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdatePageSettingsUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdateRowNameUseCase
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.core.scanning.ScannerEngine
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.domain.actions.ActivateButtonUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.HandleActionExecutionEventUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.ResolveDynamicButtonsUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.ResolveSmartPredictionUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.InteractionDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.ScreenManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.SmartPredictionDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    private lateinit var geminiUseCase: GeminiUseCase
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var weatherExecutor: com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor
    private lateinit var logger: Logger
    private lateinit var locationExecutor: com.andreas_kratzer.ghosttalk.domain.executors.LocationExecutor
    private lateinit var buttonUsageRepository: ButtonUsageRepository
    private lateinit var featureGuard: FeatureGuard
    private lateinit var philipsHueManager: PhilipsHueManager
    private lateinit var buttonTemplateRepository: ButtonTemplateRepository
    private lateinit var userModeSessionRepository: com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
    
    private lateinit var actionLogUseCase: ActionLogUseCase
    private lateinit var getPagesUseCase: GetPagesUseCase
    private lateinit var createPageUseCase: CreatePageUseCase
    private lateinit var deletePageUseCase: DeletePageUseCase
    private lateinit var updateButtonConfigUseCase: UpdateButtonConfigUseCase
    private lateinit var updatePageSettingsUseCase: UpdatePageSettingsUseCase
    private lateinit var updateRowNameUseCase: UpdateRowNameUseCase
    private lateinit var moveRowUseCase: MoveRowUseCase
    private lateinit var moveButtonUseCase: MoveButtonUseCase
    private lateinit var moveButtonToPageUseCase: com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase
    private lateinit var duplicateButtonToPageUseCase: DuplicateButtonToPageUseCase
    private lateinit var importPageUseCase: ImportPageUseCase
    private lateinit var exportPageUseCase: ExportPageUseCase
    private lateinit var checkForPredictorUseCase: com.andreas_kratzer.ghosttalk.core.ai.domain.CheckForPredictorUseCase
    private lateinit var resolveDynamicButtonsUseCase: ResolveDynamicButtonsUseCase
    private lateinit var updateSmartPredictionsUseCase: UpdateSmartPredictionsUseCase
    private lateinit var getPageUsagesUseCase: GetPageUsagesUseCase
    private lateinit var updateMultipleButtonsUseCase: com.andreas_kratzer.ghosttalk.core.domain.pages.UpdateMultipleButtonsUseCase
    private lateinit var identifyActivePageLinksUseCase: com.andreas_kratzer.ghosttalk.core.domain.pages.IdentifyActivePageLinksUseCase

    private lateinit var viewModel: PageViewModel
    private lateinit var systemCallManager: com.andreas_kratzer.ghosttalk.core.call.SystemCallManager
    private val mockCallStateFlow = MutableStateFlow(com.andreas_kratzer.ghosttalk.core.call.CallState.NONE)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockkStatic(android.widget.Toast::class)
        val mockToast = mockk<android.widget.Toast>(relaxed = true)
        every { android.widget.Toast.makeText(any(), any<CharSequence>(), any()) } returns mockToast
        every { android.widget.Toast.makeText(any(), any<Int>(), any()) } returns mockToast

        application = mockk<Application>(relaxed = true)
        pageRepository = mockk<PageRepository>(relaxed = true)
        settingsRepository = mockk<SettingsRepository>(relaxed = true)
        bookRepository = mockk<BookRepository>(relaxed = true)
        templateRepository = mockk<TemplateRepository>(relaxed = true)
        importExportManager = mockk<PageImportExportManager>(relaxed = true)
        scannerEngine = mockk<ScannerEngine>(relaxed = true)
        geminiUseCase = mockk<GeminiUseCase>(relaxed = true)
        ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
        weatherExecutor = mockk<com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor>(relaxed = true)
        locationExecutor = mockk<com.andreas_kratzer.ghosttalk.domain.executors.LocationExecutor>(relaxed = true)
        logger = mockk<Logger>(relaxed = true)
        buttonUsageRepository = mockk<ButtonUsageRepository>(relaxed = true)
        featureGuard = mockk<FeatureGuard>(relaxed = true)
        philipsHueManager = mockk<PhilipsHueManager>(relaxed = true)
        buttonTemplateRepository = mockk<ButtonTemplateRepository>(relaxed = true)
        userModeSessionRepository = mockk<com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository>(relaxed = true)

        actionLogUseCase = mockk<ActionLogUseCase>(relaxed = true)
        getPagesUseCase = mockk<GetPagesUseCase>(relaxed = true)
        createPageUseCase = mockk<CreatePageUseCase>(relaxed = true)
        deletePageUseCase = mockk<DeletePageUseCase>(relaxed = true)
        updateButtonConfigUseCase = mockk<UpdateButtonConfigUseCase>(relaxed = true)
        updatePageSettingsUseCase = mockk<UpdatePageSettingsUseCase>(relaxed = true)
        updateRowNameUseCase = mockk<UpdateRowNameUseCase>(relaxed = true)
        moveRowUseCase = mockk<MoveRowUseCase>(relaxed = true)
        moveButtonUseCase = mockk<MoveButtonUseCase>(relaxed = true)
        moveButtonToPageUseCase = mockk<com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase>(relaxed = true)
        duplicateButtonToPageUseCase = mockk<DuplicateButtonToPageUseCase>(relaxed = true)
        importPageUseCase = mockk<ImportPageUseCase>(relaxed = true)
        exportPageUseCase = mockk<ExportPageUseCase>(relaxed = true)
        checkForPredictorUseCase = mockk<com.andreas_kratzer.ghosttalk.core.ai.domain.CheckForPredictorUseCase>(relaxed = true)
        resolveDynamicButtonsUseCase = mockk<ResolveDynamicButtonsUseCase>(relaxed = true)
        updateSmartPredictionsUseCase = mockk<UpdateSmartPredictionsUseCase>(relaxed = true)
        getPageUsagesUseCase = mockk<GetPageUsagesUseCase>(relaxed = true)
        updateMultipleButtonsUseCase = mockk<com.andreas_kratzer.ghosttalk.core.domain.pages.UpdateMultipleButtonsUseCase>(relaxed = true)
        identifyActivePageLinksUseCase = mockk<com.andreas_kratzer.ghosttalk.core.domain.pages.IdentifyActivePageLinksUseCase>(relaxed = true)

        // Mock common flows with explicit types to avoid Nothing exceptions
        every { settingsRepository.activeBookIdFlow } returns MutableStateFlow<String>("b1")
        every { settingsRepository.activeBookId } returns "b1"
        every { settingsRepository.isSmartPredictionEnabledFlow } returns MutableStateFlow<Boolean>(true)
        every { settingsRepository.isSmartPredictionEnabled } returns true
        every { settingsRepository.defaultScanPatternFlow } returns MutableStateFlow<String>("linear")
        every { settingsRepository.showTestButtonsFlow } returns MutableStateFlow<Boolean>(false)
        every { settingsRepository.scanDelayFlow } returns MutableStateFlow<Long>(3000L)
        every { settingsRepository.scanDelayMillis } returns 3000L
        every { settingsRepository.persistActionLogsFlow } returns MutableStateFlow<Boolean>(false)
        every { settingsRepository.keepScreenOnUserModeFlow } returns MutableStateFlow<Boolean>(false)
        every { settingsRepository.userModeScreenBehaviorFlow } returns MutableStateFlow<String>("NONE")
        every { settingsRepository.holdingTimeMillis } returns 0L
        every { settingsRepository.pageSortOrderFlow } returns MutableStateFlow("MANUAL")
        every { settingsRepository.staticRowEnabledFlow } returns MutableStateFlow(false)
        every { settingsRepository.geminiRedoPrediction } returns false
        every { settingsRepository.autoStartScanning } returns false
        every { settingsRepository.resumeScanningFromStart } returns true
        every { settingsRepository.hangUpPressesRequired } returns 2
        
        every { templateRepository.getAllTemplates() } returns MutableStateFlow<List<PageTemplate>>(emptyList())
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow<List<Page>>(emptyList())
        coEvery { resolveDynamicButtonsUseCase.execute(any(), any(), any(), any()) } answers { firstArg() }
        every { buttonTemplateRepository.getTemplates() } returns MutableStateFlow(emptyList())
        every { buttonUsageRepository.buttonHistory } returns MutableStateFlow(emptyList())
        every { userModeSessionRepository.getSessionsForBook(any()) } returns MutableStateFlow(emptyList())
        every { bookRepository.getBookByIdFlow(any()) } returns MutableStateFlow(null)
        every { settingsRepository.spotifyUserDisplayNameFlow } returns MutableStateFlow<String?>(null)
        every { settingsRepository.cuesAudioDeviceAddress } returns null
        every { updateSmartPredictionsUseCase.isLoading } returns MutableStateFlow(false)
        every { updateSmartPredictionsUseCase.execute(any(), any(), any(), any(), any()) } returns MutableStateFlow<List<String>?>(null)
        
        // Mock scannerEngine flows
        every { scannerEngine.focusedButtonIndex } returns MutableStateFlow<Int?>(null)
        every { scannerEngine.focusedRowIndex } returns MutableStateFlow<Int?>(null)
    }

    @After
    fun tearDown() {
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        testDispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(
        optimizer: com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer = mockk(relaxed = true)
    ): PageViewModel {
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
            duplicateButtonToPageUseCase = duplicateButtonToPageUseCase,
            importPageUseCase = importPageUseCase,
            exportPageUseCase = exportPageUseCase,
            getFilteredPagesUseCase = GetFilteredPagesUseCase(settingsRepository),
            getPageUsagesUseCase = getPageUsagesUseCase,
            updateMultipleButtonsUseCase = updateMultipleButtonsUseCase,
            identifyActivePageLinksUseCase = identifyActivePageLinksUseCase,
            appStateRepository = appStateRepository,
            settingsRepository = settingsRepository
        )
        val interactionDelegate = InteractionDelegate(
            application = application,
            actionLogUseCase = actionLogUseCase,
            ttsHelper = ttsHelper,
            activateButtonUseCase = ActivateButtonUseCase(ttsHelper, ResolveSmartPredictionUseCase(pageRepository, bookRepository), bookRepository),
            handleActionExecutionEventUseCase = HandleActionExecutionEventUseCase(pageRepository, settingsRepository),
            locationExecutor = locationExecutor,
            appStateRepository = appStateRepository,
            bookRepository = bookRepository
        )
        val smartPredictionDelegate = SmartPredictionDelegate(
            updateSmartPredictionsUseCase = updateSmartPredictionsUseCase
        )
        val screenManagementDelegate = ScreenManagementDelegate(
            settingsRepository = settingsRepository,
            callActionProxy = mockk(relaxed = true)
        )
        
        val actionCoordinator = ActionCoordinator(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            logger = logger
        )
        
        val navHandler = NavigationActionHandler(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            settingsRepository = settingsRepository,
            ttsProxyLazy = object : dagger.Lazy<ActionTtsProxy> {
                override fun get(): ActionTtsProxy = mockk(relaxed = true)
            },
            actionEventEmitter = actionCoordinator,
            actionLogger = actionCoordinator
        )

        val actionExecutor = ActionExecutor(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            settingsRepository = settingsRepository,
            buttonUsageRepository = buttonUsageRepository,
            handlers = setOf(navHandler),
            actionCoordinator = actionCoordinator,
            ttsHelper = ttsHelper,
            scanCoordinatorProvider = mockk(relaxed = true)
        )

        val scanCoordinator = mockk<ScanCoordinator>(relaxed = true)
        every { scanCoordinator.focusedButtonIndex } returns MutableStateFlow<Int?>(null)
        every { scanCoordinator.focusedRowIndex } returns MutableStateFlow<Int?>(null)
        every { scanCoordinator.currentCycleCount } returns MutableStateFlow(0)

        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        systemCallManager = mockk(relaxed = true) {
            every { callState } returns mockCallStateFlow
            every { callerName } returns MutableStateFlow(null)
            every { callerPhone } returns MutableStateFlow(null)
            every { callDurationSeconds } returns MutableStateFlow(0)
            every { isOutgoing } returns MutableStateFlow(false)
            every { isSimulatedFlow } returns MutableStateFlow(false)
        }

        val callManagementDelegate = com.andreas_kratzer.ghosttalk.ui.pages.delegates.CallManagementDelegate(
            application = application,
            systemCallManager = systemCallManager,
            settingsRepository = settingsRepository,
            ttsHelper = ttsHelper
        )

        val aiRestructureDelegate = com.andreas_kratzer.ghosttalk.ui.pages.delegates.AiRestructureDelegate(
            application = application,
            buttonUsageRepository = buttonUsageRepository,
            settingsRepository = settingsRepository,
            cloneBookUseCase = mockk(relaxed = true),
            bookHierarchyProposalUseCase = mockk(relaxed = true),
            pageLayoutProposalUseCase = mockk(relaxed = true)
        )

        return PageViewModel(
            application = application,
            savedStateHandle = SavedStateHandle(),
            settingsRepository = settingsRepository,
            bookRepository = bookRepository,
            ttsHelper = ttsHelper,
            featureGuard = featureGuard,
            pageManagementDelegate = pageManagementDelegate,
            interactionDelegate = interactionDelegate,
            screenManagementDelegate = screenManagementDelegate,
            smartPredictionDelegate = smartPredictionDelegate,
            callManagementDelegate = callManagementDelegate,
            aiRestructureDelegate = aiRestructureDelegate,
            resolveDynamicButtonsUseCase = resolveDynamicButtonsUseCase,
            updateSmartPredictionsUseCase = updateSmartPredictionsUseCase,
            actionExecutor = actionExecutor,
            scanCoordinator = scanCoordinator,
            geminiUseCase = geminiUseCase,
            buttonTemplateRepository = buttonTemplateRepository,
            systemCallManager = systemCallManager,
            philipsHueManager = philipsHueManager,
            spotifyManager = mockk(relaxed = true),
            buttonUsageRepository = buttonUsageRepository,
            efficiencyAnalyzer = mockk(relaxed = true),
            pathAnalyzer = mockk(relaxed = true),
            userModeSessionRepository = userModeSessionRepository,
            splitPageUseCase = mockk(relaxed = true),
            createPageUseCase = createPageUseCase,
            pageLayoutOptimizer = optimizer
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

    @Test
    fun `when callState becomes RINGING call scanning starts and answer is announced`() = runTest {
        every { application.getString(com.andreas_kratzer.ghosttalk.R.string.call_answer) } returns "Answer"

        viewModel = createViewModel()
        testScheduler.runCurrent()

        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.RINGING
        testScheduler.runCurrent()

        assertEquals("ANNEHMEN", viewModel.focusedCallScreenButton.value)

        io.mockk.verify {
            ttsHelper.speakRouted(
                text = "Answer",
                deviceAddress = any(),
                queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD,
                isForCues = true
            )
        }

        // Clean up call state to stop scanning loop coroutine
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        testScheduler.runCurrent()
    }

    @Test
    fun `activateFocusedButton answers call when ringing and focused on ANNEHMEN`() = runTest {
        viewModel = createViewModel()
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.RINGING
        viewModel.focusedCallScreenButton.value = "ANNEHMEN"
        testScheduler.runCurrent()

        viewModel.activateFocusedButton()

        io.mockk.verify { systemCallManager.answerCall() }

        // Clean up
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        testScheduler.runCurrent()
    }

    @Test
    fun `activateFocusedButton rejects call when ringing and focused on ABLEHNEN`() = runTest {
        viewModel = createViewModel()
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.RINGING
        viewModel.focusedCallScreenButton.value = "ABLEHNEN"
        testScheduler.runCurrent()

        viewModel.activateFocusedButton()

        io.mockk.verify { systemCallManager.hangUp() }

        // Clean up
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        testScheduler.runCurrent()
    }

    @Test
    fun `activateFocusedButton focuses hang up on first press and hangs up on second press`() = runTest {
        every { settingsRepository.hangUpPressesRequired } returns 2
        every { application.getString(com.andreas_kratzer.ghosttalk.R.string.call_hang_up) } returns "Hang Up"
        viewModel = createViewModel()
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.ACTIVE
        testScheduler.runCurrent()

        viewModel.activateFocusedButton()
        assertEquals(true, viewModel.isHangUpButtonFocused.value)
        io.mockk.verify {
            ttsHelper.speakRouted("Hang Up", any(), any(), isForCues = true)
        }

        viewModel.activateFocusedButton()
        io.mockk.verify { systemCallManager.hangUp() }

        // Clean up
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        testScheduler.runCurrent()
    }

    @Test
    fun `activateFocusedButton hangs up on first press when hangUpPressesRequired is 1`() = runTest {
        every { settingsRepository.hangUpPressesRequired } returns 1
        viewModel = createViewModel()
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.ACTIVE
        testScheduler.runCurrent()

        viewModel.activateFocusedButton()
        io.mockk.verify { systemCallManager.hangUp() }

        // Clean up
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        testScheduler.runCurrent()
    }

    @Test
    fun `activateFocusedButton hangs up on third press when hangUpPressesRequired is 3`() = runTest {
        every { settingsRepository.hangUpPressesRequired } returns 3
        every { settingsRepository.holdingTimeMillis } returns 0L // No debounce in this test
        every { application.getString(com.andreas_kratzer.ghosttalk.R.string.call_hang_up) } returns "Hang Up"
        viewModel = createViewModel()
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.ACTIVE
        testScheduler.runCurrent()

        viewModel.activateFocusedButton() // Press 1
        assertEquals(true, viewModel.isHangUpButtonFocused.value)
        assertEquals(1, viewModel.hangUpPressCount.value)

        viewModel.activateFocusedButton() // Press 2
        assertEquals(2, viewModel.hangUpPressCount.value)
        io.mockk.verify(exactly = 0) { systemCallManager.hangUp() }

        viewModel.activateFocusedButton() // Press 3
        io.mockk.verify(exactly = 1) { systemCallManager.hangUp() }

        // Clean up
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        testScheduler.runCurrent()
    }

    @Test
    fun `activateFocusedButton enforces holdingTime debounce during active calls`() = runTest {
        every { settingsRepository.hangUpPressesRequired } returns 2
        every { settingsRepository.holdingTimeMillis } returns 500L
        every { application.getString(com.andreas_kratzer.ghosttalk.R.string.call_hang_up) } returns "Hang Up"
        viewModel = createViewModel()
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.ACTIVE
        testScheduler.runCurrent()

        // First press: accepted, triggers focus overlay and starts holding time tracker
        viewModel.activateFocusedButton()
        assertEquals(1, viewModel.hangUpPressCount.value)

        // Second press: happens instantly (0ms delta), should be ignored because it is < 500ms
        viewModel.activateFocusedButton()
        assertEquals(1, viewModel.hangUpPressCount.value)
        io.mockk.verify(exactly = 0) { systemCallManager.hangUp() }

        // Simulate waiting for 600ms
        Thread.sleep(600)

        // Third press: happens after holding time, should be accepted and trigger hang up (since required presses is 2)
        viewModel.activateFocusedButton()
        assertEquals(2, viewModel.hangUpPressCount.value)
        io.mockk.verify(exactly = 1) { systemCallManager.hangUp() }

        // Clean up
        mockCallStateFlow.value = com.andreas_kratzer.ghosttalk.core.call.CallState.NONE
        testScheduler.runCurrent()
    }

    @Test
    fun `suggestRowName returns category suggestion based on row buttons`() = runTest {
        // Mock Gemini enabled
        every { settingsRepository.isGeminiEnabled } returns true
        
        // Mock Gemini response
        coEvery { geminiUseCase.generateResponse(any()) } returns "Obst"

        val pageId = "p1"
        val button1 = ButtonConfig(label = "Apfel")
        val button2 = ButtonConfig(label = "Banane")
        
        val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
        buttonConfigs[0] = button1
        buttonConfigs[1] = button2
        
        val page = Page(id = pageId, bookId = "b1", name = "P1", rows = 4, columns = 4, buttonConfigs = buttonConfigs)
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow<List<Page>>(listOf(page))

        viewModel = createViewModel()
        testScheduler.runCurrent()

        var suggestionResult = ""
        viewModel.suggestRowName(pageId, rowIndex = 0) { result ->
            suggestionResult = result
        }
        testScheduler.advanceUntilIdle()

        assertEquals("Obst", suggestionResult)
    }

    @Test
    fun `suggestButtonLabel returns suggestion when Gemini is enabled`() = runTest {
        every { settingsRepository.isGeminiEnabled } returns true
        coEvery { geminiUseCase.generateResponse(any()) } returns "Begrüßung"

        viewModel = createViewModel()
        testScheduler.runCurrent()

        var suggestionResult = ""
        val buttonConfig = ButtonConfig(
            spokenText = "Hallo Welt",
            buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()
        )
        viewModel.suggestButtonLabel(buttonConfig) { result ->
            suggestionResult = result
        }
        testScheduler.advanceUntilIdle()

        assertEquals("Begrüßung", suggestionResult)
    }

    @Test
    fun `suggestButtonLabel returns empty when Gemini is disabled`() = runTest {
        every { settingsRepository.isGeminiEnabled } returns false

        viewModel = createViewModel()
        testScheduler.runCurrent()

        var suggestionResult = "initial"
        val buttonConfig = ButtonConfig(
            spokenText = "Hallo Welt",
            buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()
        )
        viewModel.suggestButtonLabel(buttonConfig) { result ->
            suggestionResult = result
        }
        testScheduler.advanceUntilIdle()

        assertEquals("", suggestionResult)
    }

    @Test
    fun `suggestButtonLabel returns empty on API failure`() = runTest {
        every { settingsRepository.isGeminiEnabled } returns true
        coEvery { geminiUseCase.generateResponse(any()) } throws RuntimeException("Network Error")

        viewModel = createViewModel()
        testScheduler.runCurrent()

        var suggestionResult = "initial"
        val buttonConfig = ButtonConfig(
            spokenText = "Hallo Welt",
            buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction()
        )
        viewModel.suggestButtonLabel(buttonConfig) { result ->
            suggestionResult = result
        }
        testScheduler.advanceUntilIdle()

        assertEquals("", suggestionResult)
    }

    @Test
    fun `suggestRowName returns empty when Gemini is disabled`() = runTest {
        every { settingsRepository.isGeminiEnabled } returns false

        viewModel = createViewModel()
        testScheduler.runCurrent()

        var suggestionResult = "initial"
        viewModel.suggestRowName("p1", rowIndex = 0) { result ->
            suggestionResult = result
        }
        testScheduler.advanceUntilIdle()

        assertEquals("", suggestionResult)
    }

    @Test
    fun `suggestRowName returns empty and does not crash when Gemini throws`() = runTest {
        every { settingsRepository.isGeminiEnabled } returns true
        coEvery { geminiUseCase.generateResponse(any()) } throws RuntimeException("API error")

        val pageId = "p1"
        val button = ButtonConfig(label = "Hallo")
        val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
        buttonConfigs[0] = button
        val page = Page(id = pageId, bookId = "b1", name = "P1", rows = 4, columns = 4, buttonConfigs = buttonConfigs)
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow<List<Page>>(listOf(page))

        viewModel = createViewModel()
        testScheduler.runCurrent()

        var suggestionResult = "initial"
        viewModel.suggestRowName(pageId, rowIndex = 0) { result ->
            suggestionResult = result
        }
        testScheduler.advanceUntilIdle()

        assertEquals("", suggestionResult)
    }

    @Test
    fun `suggestRowName returns empty without Gemini call when row has no active buttons`() = runTest {
        every { settingsRepository.isGeminiEnabled } returns true

        val pageId = "p1"
        // Alle Buttons in Zeile 0 sind inaktiv
        val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
        buttonConfigs[0] = ButtonConfig(label = "Hidden", isActive = false)
        val page = Page(id = pageId, bookId = "b1", name = "P1", rows = 4, columns = 4, buttonConfigs = buttonConfigs)
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow<List<Page>>(listOf(page))

        viewModel = createViewModel()
        testScheduler.runCurrent()

        var suggestionResult = "initial"
        viewModel.suggestRowName(pageId, rowIndex = 0) { result ->
            suggestionResult = result
        }
        testScheduler.advanceUntilIdle()

        assertEquals("", suggestionResult)
        io.mockk.coVerify(exactly = 0) { geminiUseCase.generateResponse(any()) }
    }

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun testLayoutOptimizationFilterAndSort() = runTest {
        val page1 = Page(id = "p1", bookId = "b1", name = "Banana Page", rows = 4, columns = 4, buttonConfigs = List(49) { null })
        val page2 = Page(id = "p2", bookId = "b1", name = "Apple Page", rows = 4, columns = 4, buttonConfigs = List(49) { null })
        
        val mockOptimizer = mockk<com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer>()
        val proposals = listOf(
            com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal(
                pageId = "p1",
                pageName = "Banana Page",
                activeButtonsCount = 15,
                currentAverageScanTimeSec = 10.0,
                estimatedNewAverageScanTimeSec = 6.0
            ),
            com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal(
                pageId = "p2",
                pageName = "Apple Page",
                activeButtonsCount = 10,
                currentAverageScanTimeSec = 7.0,
                estimatedNewAverageScanTimeSec = 5.0,
                targetScanPattern = "row_by_row"
            )
        )
        
        every { settingsRepository.defaultScanPattern } returns "linear"
        every { settingsRepository.defaultStartPageId } returns null
        every { mockOptimizer.analyzePages(any(), any(), any(), any(), any()) } returns proposals
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow(listOf(page1, page2))

        viewModel = createViewModel(optimizer = mockOptimizer)
        testScheduler.runCurrent()

        // Subscribe to flow to start collection (required for WhileSubscribed stateIn flows)
        val collectionJob = launch {
            viewModel.layoutOptimizationProposals.collect {}
        }
        testScheduler.runCurrent()

        // 1. ALL and TIME_SAVED_DESC (Default)
        // Banana savings = 10 - 6 = 4. Apple savings = 7 - 5 = 2.
        // Banana should be first.
        // Wait for background thread to compute proposals due to flowOn(Dispatchers.Default)
        var list = emptyList<com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal>()
        for (i in 1..40) {
            list = viewModel.layoutOptimizationProposals.value
            if (list.isNotEmpty()) break
            Thread.sleep(25)
        }
        assertEquals("Proposals size was ${list.size}. unfilteredPages was ${viewModel.unfilteredPages.value.size}. activeBookId was ${viewModel.activeBookId.value}", 2, list.size)
        assertEquals("p1", list[0].pageId)
        assertEquals("p2", list[1].pageId)

        // 2. Filter SPLIT_ONLY
        viewModel.setProposalFilter(ProposalFilter.SPLIT_ONLY)
        testScheduler.runCurrent()
        for (i in 1..40) {
            list = viewModel.layoutOptimizationProposals.value
            if (list.size == 1) break
            Thread.sleep(25)
        }
        assertEquals(1, list.size)
        assertEquals("p1", list[0].pageId)

        // 3. Filter PATTERN_ONLY
        viewModel.setProposalFilter(ProposalFilter.PATTERN_ONLY)
        testScheduler.runCurrent()
        for (i in 1..40) {
            list = viewModel.layoutOptimizationProposals.value
            if (list.size == 1 && list[0].pageId == "p2") break
            Thread.sleep(25)
        }
        assertEquals(1, list.size)
        assertEquals("p2", list[0].pageId)

        // 4. Sort PAGE_NAME_ASC with ALL filter
        viewModel.setProposalFilter(ProposalFilter.ALL)
        viewModel.setProposalSort(ProposalSort.PAGE_NAME_ASC)
        testScheduler.runCurrent()
        for (i in 1..40) {
            list = viewModel.layoutOptimizationProposals.value
            if (list.size == 2 && list[0].pageId == "p2") break
            Thread.sleep(25)
        }
        assertEquals(2, list.size)
        assertEquals("p2", list[0].pageId) // "Apple Page" comes before "Banana Page"
        assertEquals("p1", list[1].pageId)

        collectionJob.cancel()
    }

    @Test
    fun `navigateBack when stack is not empty loads previous page`() = runTest {
        viewModel = createViewModel()
        val startPage = Page(id = "p_start", bookId = "b1", name = "Start", rows = 1, columns = 1, buttonConfigs = List(49) { null })
        val pageA = Page(id = "p_a", bookId = "b1", name = "Page A", rows = 1, columns = 1, buttonConfigs = List(49) { null })
        
        coEvery { pageRepository.getPageById("p_start") } returns startPage
        coEvery { pageRepository.getPageById("p_a") } returns pageA
        every { settingsRepository.defaultStartPageId } returns "p_start"
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow(listOf(startPage, pageA))

        viewModel.setActiveBookId("b1")
        viewModel.loadPage(startPage)
        viewModel.loadPage(pageA) // Push startPage to stack

        assertEquals("p_a", viewModel.currentPage.value?.id)

        viewModel.navigateBack()
        testScheduler.advanceUntilIdle()

        assertEquals("p_start", viewModel.currentPage.value?.id)
    }

    @Test
    fun `navigateBack when stack is empty reloads default start page`() = runTest {
        viewModel = createViewModel()
        val startPage = Page(id = "p_start", bookId = "b1", name = "Start", rows = 1, columns = 1, buttonConfigs = List(49) { null })
        
        coEvery { pageRepository.getPageById("p_start") } returns startPage
        every { settingsRepository.defaultStartPageId } returns "p_start"
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow(listOf(startPage))

        viewModel.setActiveBookId("b1")
        viewModel.loadPage(startPage) // Stack is empty since startPage was loaded first

        assertEquals("p_start", viewModel.currentPage.value?.id)

        viewModel.navigateBack()
        testScheduler.advanceUntilIdle()

        assertEquals("p_start", viewModel.currentPage.value?.id)
    }

    @Test
    fun `reorderByClickStats sorts active buttons descending by click count`() = runTest {
        val targetPageId = "p1"
        val btn1 = ButtonConfig(id = "btn1", label = "A", isActive = true)
        val btn2 = ButtonConfig(id = "btn2", label = "B", isActive = true)
        val btn3 = ButtonConfig(id = "btn3", label = "C", isActive = false)
        
        val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
        buttonConfigs[0] = btn1
        buttonConfigs[1] = btn2
        buttonConfigs[2] = btn3
        
        val page = Page(id = targetPageId, bookId = "b1", name = "P1", rows = 4, columns = 4, buttonConfigs = buttonConfigs)
        coEvery { pageRepository.getPageById(targetPageId) } returns page
        
        val mockUsageStat = mockk<com.andreas_kratzer.ghosttalk.core.model.GroupedButtonUsageStat>(relaxed = true)
        val mockChild1 = mockk<com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat>(relaxed = true) {
            every { buttonConfigId } returns "btn1"
            every { pageId } returns "p1"
            every { usageCount } returns 5L
        }
        val mockChild2 = mockk<com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat>(relaxed = true) {
            every { buttonConfigId } returns "btn2"
            every { pageId } returns "p1"
            every { usageCount } returns 10L
        }
        every { mockUsageStat.children } returns listOf(mockChild1, mockChild2)
        coEvery { buttonUsageRepository.getGroupedUsageStats("b1") } returns listOf(mockUsageStat)
        
        viewModel = createViewModel()
        viewModel.setActiveBookId("b1")
        
        val updatedPageSlot = io.mockk.slot<Page>()
        coEvery { pageRepository.updatePage(capture(updatedPageSlot)) } returns Unit
        
        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.reorderByClickStats(targetPageId) {
            latch.countDown()
        }
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        
        val updated = updatedPageSlot.captured
        // btn2 has 10 clicks, so it should be first. btn1 has 5 clicks, so second. btn3 (inactive) should be third.
        assertEquals("btn2", updated.buttonConfigs[0]?.id)
        assertEquals("btn1", updated.buttonConfigs[1]?.id)
        assertEquals("btn3", updated.buttonConfigs[2]?.id)
    }

    @Test
    fun `insertHomeNavigationEveryX inserts home buttons at correct index and removes old ones`() = runTest {
        val pageId = "p1"
        // btn1, btn2 are standard. btn3 is an existing home button. btn4 is standard.
        val btn1 = ButtonConfig(id = "btn1", label = "A", isActive = true)
        val btn2 = ButtonConfig(id = "btn2", label = "B", isActive = true)
        val btn3 = ButtonConfig(id = "btn3", label = "Startseite", isActive = true, buttonAction = NavigateToStartPageButtonAction())
        val btn4 = ButtonConfig(id = "btn4", label = "C", isActive = true)
        
        val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
        buttonConfigs[0] = btn1
        buttonConfigs[1] = btn2
        buttonConfigs[2] = btn3
        buttonConfigs[3] = btn4
        
        val page = Page(id = pageId, bookId = "b1", name = "P1", rows = 4, columns = 4, buttonConfigs = buttonConfigs)
        coEvery { pageRepository.getPageById(pageId) } returns page
        
        viewModel = createViewModel()
        viewModel.setActiveBookId("b1")
        every { settingsRepository.defaultStartPageId } returns "p_start"
        
        val updatedPageSlot = io.mockk.slot<Page>()
        coEvery { pageRepository.updatePage(capture(updatedPageSlot)) } returns Unit
        
        // Insert every 2 buttons.
        // nonHomeButtons = [btn1, btn2, btn4]
        // Counter logic:
        // - btn1: counter=0. result=[btn1]. counter=1.
        // - btn2: counter=1. result=[btn1, btn2]. counter=2.
        // - btn4: counter=2. counter % 2 == 0, so insert home first: result=[btn1, btn2, home, btn4]. counter=3.
        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.insertHomeNavigationEveryX(pageId, 2) {
            latch.countDown()
        }
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        
        val updated = updatedPageSlot.captured
        assertEquals("btn1", updated.buttonConfigs[0]?.id)
        assertEquals("btn2", updated.buttonConfigs[1]?.id)
        // At index 2 there should be a Home button
        val targetAction = updated.buttonConfigs[2]?.buttonAction
        assertEquals(true, targetAction is NavigateToStartPageButtonAction)
        assertEquals("btn4", updated.buttonConfigs[3]?.id)
    }

    @Test
    fun `shrinkGridToMinimum resizes grid to match button count`() = runTest {
        val pageId = "p1"
        // 5 buttons total. Minimal grid size should be 2 rows, 3 columns (fits up to 6 buttons).
        val buttons = (1..5).map { ButtonConfig(id = "btn$it", label = "btn$it", isActive = true) }
        val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
        buttons.forEachIndexed { i, btn -> buttonConfigs[i] = btn }
        
        val page = Page(id = pageId, bookId = "b1", name = "P1", rows = 4, columns = 4, buttonConfigs = buttonConfigs)
        coEvery { pageRepository.getPageById(pageId) } returns page
        
        viewModel = createViewModel()
        viewModel.setActiveBookId("b1")
        
        val updatedPageSlot = io.mockk.slot<Page>()
        coEvery { pageRepository.updatePage(capture(updatedPageSlot)) } returns Unit
        
        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.shrinkGridToMinimum(pageId) {
            latch.countDown()
        }
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        
        val updated = updatedPageSlot.captured
        assertEquals(2, updated.rows)
        assertEquals(3, updated.columns)
    }

    @Test
    fun `deleteDeactivatedButtons deletes buttons where isActive is false`() = runTest {
        val pageId = "p1"
        val btn1 = ButtonConfig(id = "btn1", label = "A", isActive = true)
        val btn2 = ButtonConfig(id = "btn2", label = "B", isActive = false)
        val btn3 = ButtonConfig(id = "btn3", label = "C", isActive = true)
        
        val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
        buttonConfigs[0] = btn1
        buttonConfigs[1] = btn2
        buttonConfigs[2] = btn3
        
        val page = Page(id = pageId, bookId = "b1", name = "P1", rows = 4, columns = 4, buttonConfigs = buttonConfigs)
        coEvery { pageRepository.getPageById(pageId) } returns page
        
        viewModel = createViewModel()
        viewModel.setActiveBookId("b1")
        
        val updatedPageSlot = io.mockk.slot<Page>()
        coEvery { pageRepository.updatePage(capture(updatedPageSlot)) } returns Unit
        
        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.deleteDeactivatedButtons(pageId) {
            latch.countDown()
        }
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        
        val updated = updatedPageSlot.captured
        assertEquals("btn1", updated.buttonConfigs[0]?.id)
        assertEquals("btn3", updated.buttonConfigs[1]?.id)
        assertEquals(null, updated.buttonConfigs[2])
    }

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun `magicCleanup executes all layout optimizations sequentially`() = runTest {
        val targetPageId = "p1"
        val btn1 = ButtonConfig(id = "btn1", label = "A", isActive = true)
        val btn2 = ButtonConfig(id = "btn2", label = "B", isActive = false)
        val btn3 = ButtonConfig(id = "btn3", label = "C", isActive = true)
        val btn4 = ButtonConfig(id = "btn4", label = "Startseite", isActive = true, buttonAction = NavigateToStartPageButtonAction())
        val btn5 = ButtonConfig(id = "btn5", label = "E", isActive = true)
        val btn6 = ButtonConfig(id = "btn6", label = "F", isActive = true)
        val btn7 = ButtonConfig(id = "btn7", label = "G", isActive = true)
        val btn8 = ButtonConfig(id = "btn8", label = "H", isActive = true)
        
        val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
        buttonConfigs[0] = btn1
        buttonConfigs[1] = btn2
        buttonConfigs[2] = btn3
        buttonConfigs[3] = btn4
        buttonConfigs[4] = btn5
        buttonConfigs[5] = btn6
        buttonConfigs[6] = btn7
        buttonConfigs[7] = btn8
        
        val page = Page(id = targetPageId, bookId = "b1", name = "P1", rows = 4, columns = 4, buttonConfigs = buttonConfigs, scanPattern = "linear")
        coEvery { pageRepository.getPageById(targetPageId) } returns page
        
        val mockUsageStat = mockk<com.andreas_kratzer.ghosttalk.core.model.GroupedButtonUsageStat>(relaxed = true)
        val mockChild1 = mockk<com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat>(relaxed = true) {
            every { buttonConfigId } returns "btn1"
            every { pageId } returns "p1"
            every { usageCount } returns 5L
        }
        val mockChild2 = mockk<com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat>(relaxed = true) {
            every { buttonConfigId } returns "btn3"
            every { pageId } returns "p1"
            every { usageCount } returns 10L
        }
        every { mockUsageStat.children } returns listOf(mockChild1, mockChild2)
        coEvery { buttonUsageRepository.getGroupedUsageStats("b1") } returns listOf(mockUsageStat)
        
        every { settingsRepository.isGeminiEnabled } returns true
        coEvery { geminiUseCase.generateResponse(any()) } returns "[\"MagicRow\", \"MagicRow\", \"MagicRow\"]"
        
        val mockOptimizer = mockk<com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer>()
        val proposals = listOf(
            com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal(
                pageId = targetPageId,
                pageName = "P1",
                activeButtonsCount = 6,
                currentAverageScanTimeSec = 7.0,
                estimatedNewAverageScanTimeSec = 5.0,
                targetScanPattern = "row_by_row"
            )
        )
        every { mockOptimizer.analyzePages(any(), any(), any(), any(), any()) } returns proposals
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow(listOf(page))
        every { settingsRepository.defaultScanPattern } returns "linear"
        every { settingsRepository.defaultStartPageId } returns "p_start"
        
        viewModel = createViewModel(optimizer = mockOptimizer)
        viewModel.setActiveBookId("b1")
        
        val collectionJob = launch {
            viewModel.layoutOptimizationProposals.collect {}
        }
        testScheduler.runCurrent()
        
        for (i in 1..40) {
            if (viewModel.layoutOptimizationProposals.value.isNotEmpty()) break
            Thread.sleep(25)
        }
        
        val updatedPageSlot = io.mockk.slot<Page>()
        coEvery { pageRepository.updatePage(capture(updatedPageSlot)) } returns Unit
        
        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.magicCleanup(targetPageId) {
            latch.countDown()
        }
        latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
        
        val updated = updatedPageSlot.captured
        assertEquals("btn3", updated.buttonConfigs[0]?.id)
        assertEquals("btn1", updated.buttonConfigs[1]?.id)
        assertEquals("btn5", updated.buttonConfigs[2]?.id)
        assertEquals("btn6", updated.buttonConfigs[7]?.id)
        assertEquals("btn7", updated.buttonConfigs[8]?.id)
        assertEquals(true, updated.buttonConfigs[9]?.buttonAction is NavigateToStartPageButtonAction)
        assertEquals("btn8", updated.buttonConfigs[14]?.id)
        
        assertEquals(3, updated.rows)
        assertEquals(3, updated.columns)
        assertEquals("row_by_row", updated.scanPattern)
        assertEquals(true, updated.rowNames.contains("MagicRow"))
        
        collectionJob.cancel()
    }
}
