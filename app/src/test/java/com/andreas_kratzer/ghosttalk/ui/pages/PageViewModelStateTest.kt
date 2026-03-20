package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.lifecycle.SavedStateHandle
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.InteractionDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import android.app.Application
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import kotlinx.coroutines.flow.MutableStateFlow

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
        interactionDelegate = mockk(relaxed = true)
        scanCoordinator = mockk(relaxed = true)
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

        // WHEN
        val viewModel = PageViewModel(
            application = application,
            savedStateHandle = savedStateHandle,
            settingsRepository = mockk(relaxed = true),
            bookRepository = mockk(relaxed = true),
            importExportManager = mockk(relaxed = true),
            googleAuthManager = mockk(relaxed = true),
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
            googleHomeManager = mockk(relaxed = true)
        )
        
        // Advance to allow launch in init to execute
        runCurrent()

        // THEN
        coVerify { pageManagementDelegate.getPageById(preservedId) }
        verify { pageManagementDelegate.setCurrentPage(mockPage) }
        verify { interactionDelegate.setUserModeActive(preservedUserMode) }
    }
}
