package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.InteractionDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModelStateTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(testDispatcher)

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var pageManagementDelegate: PageManagementDelegate
    private lateinit var interactionDelegate: InteractionDelegate
    private lateinit var scanCoordinator: ScanCoordinator
    
    // Dependencies needed for constructor but can be mocked relaxed
    private val application = mockk<Application>(relaxed = true)
    private val actionExecutor = mockk<ActionExecutor>(relaxed = true)
    
    private val settingsRepository = mockk<com.andreas_kratzer.ghosttalk.core.data.SettingsRepository>(relaxed = true)
    private val bookRepository = mockk<com.andreas_kratzer.ghosttalk.core.data.BookRepository>(relaxed = true)
    private val buttonTemplateRepository = mockk<com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository>(relaxed = true)
    private val buttonUsageRepository = mockk<com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository>(relaxed = true)
    private val userModeSessionRepository = mockk<com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository>(relaxed = true)
    private val updateSmartPredictionsUseCase = mockk<com.andreas_kratzer.ghosttalk.core.ai.domain.UpdateSmartPredictionsUseCase>(relaxed = true)
    private val resolveDynamicButtonsUseCase = mockk<com.andreas_kratzer.ghosttalk.domain.actions.ResolveDynamicButtonsUseCase>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        pageManagementDelegate = mockk(relaxed = true)
        every { pageManagementDelegate.activeBookId } returns MutableStateFlow<String?>("b1")
        every { pageManagementDelegate.currentPageId } returns MutableStateFlow<String?>("p1")
        every { pageManagementDelegate.searchQuery } returns MutableStateFlow("")
        every { pageManagementDelegate.filteredPages } returns MutableStateFlow<List<Page>>(emptyList())
        every { pageManagementDelegate.unfilteredPages } returns MutableStateFlow<List<Page>>(emptyList())
        every { pageManagementDelegate.currentPage } returns MutableStateFlow<Page?>(null)
        every { pageManagementDelegate.templates } returns MutableStateFlow<List<com.andreas_kratzer.ghosttalk.core.model.PageTemplate>>(emptyList())
        every { pageManagementDelegate.activeTargetPageIds } returns MutableStateFlow<Set<String>>(emptySet())
        every { pageManagementDelegate.allPagesFlow } returns MutableStateFlow<List<Page>>(emptyList())

        interactionDelegate = mockk(relaxed = true)
        every { interactionDelegate.isUserModeActive } returns MutableStateFlow(true)
        
        scanCoordinator = mockk(relaxed = true)
        every { scanCoordinator.focusedButtonIndex } returns MutableStateFlow<Int?>(null)
        every { scanCoordinator.focusedRowIndex } returns MutableStateFlow<Int?>(null)
        every { scanCoordinator.isStoppedDueToLimit } returns MutableStateFlow(false)
        every { scanCoordinator.isScanning } returns MutableStateFlow(false)
        every { scanCoordinator.currentCycleCount } returns MutableStateFlow(0)
        
        every { settingsRepository.spotifyUserDisplayNameFlow } returns MutableStateFlow<String?>(null)
        every { settingsRepository.defaultScanPatternFlow } returns MutableStateFlow("linear")
        every { settingsRepository.showTestButtonsFlow } returns MutableStateFlow(false)
        every { settingsRepository.staticRowEnabledFlow } returns MutableStateFlow(false)
        
        every { buttonTemplateRepository.getTemplates() } returns MutableStateFlow(emptyList())
        every { buttonUsageRepository.buttonHistory } returns MutableStateFlow(emptyList())
        every { userModeSessionRepository.getSessionsForBook(any()) } returns MutableStateFlow(emptyList())
        every { bookRepository.getBookByIdFlow(any()) } returns MutableStateFlow(null)
        
        every { updateSmartPredictionsUseCase.isLoading } returns MutableStateFlow(false)
        every { updateSmartPredictionsUseCase.execute(any(), any(), any(), any(), any()) } returns MutableStateFlow<List<String>?>(null)
        coEvery { resolveDynamicButtonsUseCase.execute(any(), any(), any(), any()) } answers { firstArg() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `viewModel init should restore state from SavedStateHandle`() = scope.runTest {
        // GIVEN
        val preservedId = "target_page_1"
        val preservedUserMode = true
        val mockPage = Page(id = preservedId, bookId = "b1", name = "Restored Page", rows = 1, columns = 1, buttonConfigs = emptyList())
        
        savedStateHandle = SavedStateHandle(mapOf(
            "currentPageId" to preservedId,
            "isUserModeActive" to preservedUserMode
        ))
        
        coEvery { pageManagementDelegate.getPageById(preservedId) } returns mockPage

        val systemCallManager = mockk<com.andreas_kratzer.ghosttalk.core.call.SystemCallManager>(relaxed = true).apply {
            every { callState } returns MutableStateFlow(com.andreas_kratzer.ghosttalk.core.call.CallState.NONE)
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
            ttsHelper = mockk(relaxed = true),
            actionExecutor = actionExecutor,
            scanCoordinator = scanCoordinator
        )

        val aiRestructureDelegate = com.andreas_kratzer.ghosttalk.ui.pages.delegates.AiRestructureDelegate(
            application = application,
            bookRepository = bookRepository,
            buttonUsageRepository = buttonUsageRepository,
            settingsRepository = settingsRepository,
            cloneBookUseCase = mockk(relaxed = true),
            bookRestructureProposalUseCase = mockk(relaxed = true),
            bookHierarchyProposalUseCase = mockk(relaxed = true),
            pageLayoutProposalUseCase = mockk(relaxed = true)
        )

        // WHEN
        PageViewModel(
            application = application,
            savedStateHandle = savedStateHandle,
            settingsRepository = settingsRepository,
            bookRepository = bookRepository,
            ttsHelper = mockk(relaxed = true),
            featureGuard = mockk(relaxed = true),
            pageManagementDelegate = pageManagementDelegate,
            interactionDelegate = interactionDelegate,
            screenManagementDelegate = mockk(relaxed = true),
            smartPredictionDelegate = mockk(relaxed = true),
            callManagementDelegate = callManagementDelegate,
            aiRestructureDelegate = aiRestructureDelegate,
            resolveDynamicButtonsUseCase = resolveDynamicButtonsUseCase,
            updateSmartPredictionsUseCase = updateSmartPredictionsUseCase,
            actionExecutor = actionExecutor,
            scanCoordinator = scanCoordinator,
            geminiUseCase = mockk(relaxed = true),
            buttonTemplateRepository = buttonTemplateRepository,
            systemCallManager = systemCallManager,
            philipsHueManager = mockk(relaxed = true),
            spotifyManager = mockk(relaxed = true),
            buttonUsageRepository = buttonUsageRepository,
            efficiencyAnalyzer = mockk(relaxed = true),
            pathAnalyzer = mockk(relaxed = true),
            userModeSessionRepository = userModeSessionRepository,
            splitPageUseCase = mockk(relaxed = true),
            createPageUseCase = mockk(relaxed = true),
            pageLayoutOptimizer = mockk(relaxed = true),
            cloneBookUseCase = mockk(relaxed = true)
        )
        
        // Advance to allow launch in init to execute
        runCurrent()

        // THEN
        coVerify { pageManagementDelegate.getPageById(preservedId) }
        verify { pageManagementDelegate.setCurrentPage(mockPage) }
        verify { interactionDelegate.setUserModeActive(preservedUserMode) }
    }
}
