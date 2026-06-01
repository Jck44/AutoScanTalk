package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
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
        }

        // WHEN
        val viewModel = PageViewModel(
            application = application,
            savedStateHandle = savedStateHandle,
            settingsRepository = mockk(relaxed = true),
            bookRepository = mockk(relaxed = true),
            importExportManager = mockk(relaxed = true),
            ttsHelper = mockk(relaxed = true),
            logger = mockk(relaxed = true),
            weatherExecutor = mockk(relaxed = true),
            featureGuard = mockk(relaxed = true),
            pageManagementDelegate = pageManagementDelegate,
            interactionDelegate = interactionDelegate,
            screenManagementDelegate = mockk(relaxed = true),
            smartPredictionDelegate = mockk(relaxed = true),
            resolveDynamicButtonsUseCase = mockk(relaxed = true),
            updateSmartPredictionsUseCase = mockk(relaxed = true),
            actionExecutor = actionExecutor,
            scanCoordinator = scanCoordinator,
            geminiUseCase = mockk(relaxed = true),
            buttonTemplateRepository = mockk(relaxed = true),
            systemCallManager = systemCallManager,
            philipsHueManager = mockk(relaxed = true),
            spotifyManager = mockk(relaxed = true),
            buttonUsageRepository = mockk(relaxed = true),
            efficiencyAnalyzer = mockk(relaxed = true),
            pathAnalyzer = mockk(relaxed = true)
        )
        
        // Advance to allow launch in init to execute
        runCurrent()

        // THEN
        coVerify { pageManagementDelegate.getPageById(preservedId) }
        verify { pageManagementDelegate.setCurrentPage(mockPage) }
        verify { interactionDelegate.setUserModeActive(preservedUserMode) }
    }
}
