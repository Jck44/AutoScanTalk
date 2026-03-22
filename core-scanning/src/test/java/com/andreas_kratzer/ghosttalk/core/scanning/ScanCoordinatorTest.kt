package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.actions.ScannerActionProvider
import com.andreas_kratzer.ghosttalk.core.ai.domain.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.settings.FeatureSettings
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ScanCoordinatorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    
    // Fresh mocks for each test
    private lateinit var scannerEngine: ScannerEngine
    private lateinit var scanningSettings: ScanningSettings
    private lateinit var featureSettings: FeatureSettings
    private lateinit var actionProvider: ScannerActionProvider
    private lateinit var checkForPredictorUseCase: CheckForPredictorUseCase
    private lateinit var ttsHelper: TextToSpeechHelper

    private val isExecuting = MutableStateFlow(false)
    private val currentPage = MutableStateFlow<Page?>(null)
    private val isUserModeActive = MutableStateFlow(true)
    private val resolvedPage = MutableStateFlow<Page?>(null)
    private val isSmartPredictionLoading = MutableStateFlow(false)
    private val smartPredictions = MutableStateFlow<List<String>?>(null)

    private val onCycleCompletedFlow = MutableSharedFlow<Unit>(replay = 1)

    private fun createCoordinator(scope: CoroutineScope) = ScanCoordinator(
        scope = scope,
        scannerEngine = scannerEngine,
        scanningSettings = scanningSettings,
        featureSettings = featureSettings,
        actionProvider = actionProvider,
        checkForPredictorUseCase = checkForPredictorUseCase,
        ttsHelper = ttsHelper
    ).apply {
        init(
            isUserModeActive = isUserModeActive,
            currentPage = currentPage,
            resolvedPage = resolvedPage,
            isSmartPredictionLoading = isSmartPredictionLoading,
            smartPredictions = smartPredictions
        )
    }

    @Before
    fun setup() {
        scannerEngine = mockk(relaxed = true)
        scanningSettings = mockk(relaxed = true)
        featureSettings = mockk(relaxed = true)
        actionProvider = mockk(relaxed = true)
        checkForPredictorUseCase = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)

        every { scanningSettings.scanDelayFlow } returns MutableStateFlow(1000L)
        every { featureSettings.isSmartPredictionEnabled } returns true
        every { scanningSettings.autoStartScanning } returns true
        every { actionProvider.isExecuting } returns isExecuting
        every { scannerEngine.focusedButtonIndex } returns MutableStateFlow(null)
        every { scannerEngine.focusedRowIndex } returns MutableStateFlow(null)
        every { scannerEngine.isScanning } returns MutableStateFlow(false)
        every { scannerEngine.onCycleCompleted } returns onCycleCompletedFlow
        
        // Reset state flows for each test
        isExecuting.value = false
        currentPage.value = null
        isUserModeActive.value = true
        resolvedPage.value = null
        isSmartPredictionLoading.value = false
        smartPredictions.value = null
    }

    @Test
    fun `should stay paused when predictions are null (loading) or empty but page not yet resolved`() = runTest(testDispatcher) {
        val rawPage = mockk<Page>(relaxed = true) {
            every { id } returns "raw1"
            every { name } returns "Raw Page"
            every { buttonConfigs } returns listOf(ButtonConfig(label = "Gemini", auditoryCue = null, buttonAction = SmartPredictionButtonAction(1), isActive = true))
        }
        
        // Setup mock answer before init triggers anything
        every { checkForPredictorUseCase(rawPage) } returns true
        
        createCoordinator(backgroundScope)
        
        currentPage.value = rawPage
        resolvedPage.value = rawPage
        smartPredictions.value = null
        isSmartPredictionLoading.value = true

        advanceUntilIdle()

        verify { scannerEngine.pauseScanning() }
        clearMocks(scannerEngine, answers = false)

        // Step 1: Predictions arrive (empty list)
        smartPredictions.value = emptyList()
        isSmartPredictionLoading.value = false
        advanceUntilIdle()
        
        // EXPECTED: It should still be paused because resolvedPage STILL has predictors (rawPage)
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }
        
        // Step 2: Now resolve the page (UI catch up)
        val resPage = mockk<Page>(relaxed = true) {
            every { id } returns "res1"
            every { buttonConfigs } returns emptyList() // No more predictors
        }
        every { checkForPredictorUseCase(resPage) } returns false
        resolvedPage.value = resPage
        advanceUntilIdle()
        
        // NOW it should resume
        verify { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), eq("res1")) }
    }

    @Test
    fun `resumeScanningIfEnabled should not start scanning when waiting for predictions`() = runTest(testDispatcher) {
        val rawPage = mockk<Page>(relaxed = true) {
            every { id } returns "raw1"
            every { name } returns "Raw Page"
            every { buttonConfigs } returns listOf(ButtonConfig(label = "Gemini", auditoryCue = null, buttonAction = SmartPredictionButtonAction(1)))
        }
        
        // Given: We are waiting for predictions (smartPredictions is null)
        every { checkForPredictorUseCase(rawPage) } returns true
        
        val scanCoordinator = createCoordinator(backgroundScope)
        
        currentPage.value = rawPage
        resolvedPage.value = rawPage
        smartPredictions.value = null
        isSmartPredictionLoading.value = true

        advanceUntilIdle()
        clearMocks(scannerEngine, answers = false)
        
        // When: Something (like PageScreen) calls resumeScanningIfEnabled
        scanCoordinator.resumeScanningIfEnabled()
        advanceUntilIdle()
        
        // Then: startScanning should NOT be called
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }
        
        // Even if model is not loading but predictions are still null
        isSmartPredictionLoading.value = false
        scanCoordinator.resumeScanningIfEnabled()
        advanceUntilIdle()
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }

        // Once predictions arrive but resolution is pending
        smartPredictions.value = emptyList()
        scanCoordinator.resumeScanningIfEnabled()
        advanceUntilIdle()
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should stop scanning when user mode becomes inactive`() = runTest(testDispatcher) {
        // Given: Scanning is active
        isUserModeActive.value = true
        val page = mockk<Page>(relaxed = true) {
            every { id } returns "p1"
            every { buttonConfigs } returns emptyList()
        }
        every { checkForPredictorUseCase(any<Page>()) } returns false
        
        createCoordinator(backgroundScope)
        
        currentPage.value = page
        resolvedPage.value = page
        
        advanceUntilIdle()
        
        // Sanity check: Start scanning was called
        verify { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), eq("p1")) }
        clearMocks(scannerEngine, answers = false)

        // When: User mode becomes inactive
        isUserModeActive.value = false
        advanceUntilIdle()

        // Then: stopScanning should be called
        verify { scannerEngine.stopScanning() }
    }

    @Test
    fun `should automatically restart scanning when entering user mode if autoStart is true`() = runTest(testDispatcher) {
        // Prepare state before init
        isUserModeActive.value = false
        every { scanningSettings.autoStartScanning } returns true
        
        val page = mockk<Page>(relaxed = true) {
            every { id } returns "p1"
            every { buttonConfigs } returns emptyList()
        }
        every { checkForPredictorUseCase(any<Page>()) } returns false
        currentPage.value = page
        resolvedPage.value = page

        createCoordinator(backgroundScope)
        advanceUntilIdle()
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }

        // When: User mode becomes active
        isUserModeActive.value = true
        advanceUntilIdle()

        // Then: Scanning should start
        verify { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), eq("p1")) }
    }

    @Test
    fun `should NOT start scanning when entering user mode if autoStart is false`() = runTest(testDispatcher) {
        // Prepare state before init
        isUserModeActive.value = false
        every { scanningSettings.autoStartScanning } returns false
        
        val page = mockk<Page>(relaxed = true) {
            every { id } returns "p1"
            every { buttonConfigs } returns emptyList()
        }
        every { checkForPredictorUseCase(any<Page>()) } returns false
        currentPage.value = page
        resolvedPage.value = page

        createCoordinator(backgroundScope)
        advanceUntilIdle()

        // When: User mode becomes active
        isUserModeActive.value = true
        advanceUntilIdle()

        // Then: Scanning should NOT start
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `ScanCoordinator regression - should restart scanning at first button when action finishes and resumeScanningFromStart is true`() = runTest(testDispatcher) {
        val focusedButtonIndexFlow = MutableStateFlow<Int?>(0)
        val isScanningFlow = MutableStateFlow(false)
        
        every { scannerEngine.focusedButtonIndex } returns focusedButtonIndexFlow
        every { scannerEngine.isScanning } returns isScanningFlow
        every { scanningSettings.autoStartScanning } returns true
        every { scanningSettings.resumeScanningFromStart } returns true
        every { scanningSettings.defaultScanPattern } returns "linear"
        every { scanningSettings.scanDelayFlow } returns MutableStateFlow(1000L)
        every { checkForPredictorUseCase(any<Page>()) } returns false

        val page = Page(
            id = "p1", 
            bookId = "book1",
            name = "T", 
            rows = 1, 
            columns = 2, 
            buttonConfigs = listOf(
                ButtonConfig(id = "b1", label = "B1", isActive = true),
                ButtonConfig(id = "b2", label = "B2", isActive = true)
            )
        )
        
        currentPage.value = page
        resolvedPage.value = page
        isUserModeActive.value = true
        
        createCoordinator(backgroundScope)
        advanceUntilIdle()
        
        // Trigger action
        isExecuting.value = true
        advanceUntilIdle()
        verify { scannerEngine.pauseScanning() }
        
        // Mock that we were at index 1 (second button)
        focusedButtonIndexFlow.value = 1
        isScanningFlow.value = false
        
        // Finish action
        isExecuting.value = false
        advanceUntilIdle()
        
        // Should restart at 0 because resumeScanningFromStart is true
        verify { scannerEngine.startScanning(
            page.buttonConfigs,
            0,
            "linear",
            page.rows,
            page.columns,
            page.rowNames,
            page.id
        ) }
    }

    @Test
    fun `should stop scanning when cycle limit is reached`() = runTest(testDispatcher) {
        val page = Page(
            id = "p1",
            bookId = "b1",
            name = "Page 1",
            buttonConfigs = listOf(ButtonConfig(label = "Button 1"))
        )
        
        every { checkForPredictorUseCase(any<Page>()) } returns false
        
        currentPage.value = page
        resolvedPage.value = page
        isUserModeActive.value = true
        
        val scanCoordinator = createCoordinator(backgroundScope)
        scanCoordinator.setScanLimitSettings(true, 2)
        
        advanceUntilIdle()
        verify { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), eq("p1")) }
        
        // First cycle
        onCycleCompletedFlow.emit(Unit)
        advanceUntilIdle()
        assertEquals(false, scanCoordinator.isStoppedDueToLimit.value)
        assertEquals(1, scanCoordinator.currentCycleCount.value)
        
        // Second cycle - should trigger limit
        onCycleCompletedFlow.emit(Unit)
        advanceUntilIdle()
        
        assertEquals(true, scanCoordinator.isStoppedDueToLimit.value)
        assertEquals(2, scanCoordinator.currentCycleCount.value)
        verify { scannerEngine.stopScanning() }
    }

    @Test
    fun `restartScanning should reset counter and start scanning`() = runTest(testDispatcher) {
        val page = Page(
            id = "p1",
            bookId = "b1",
            name = "Page 1",
            buttonConfigs = listOf(ButtonConfig(label = "Button 1"))
        )
        every { checkForPredictorUseCase(any<Page>()) } returns false
        
        currentPage.value = page
        resolvedPage.value = page
        isUserModeActive.value = true

        val scanCoordinator = createCoordinator(backgroundScope)
        advanceUntilIdle()

        // Set limit to 1
        scanCoordinator.setScanLimitSettings(true, 1)
        
        // Emit one cycle completion
        onCycleCompletedFlow.emit(Unit)
        advanceUntilIdle()
        
        assertEquals(true, scanCoordinator.isStoppedDueToLimit.value)
        
        // Restart
        scanCoordinator.restartScanning()
        advanceUntilIdle()
        
        assertEquals(false, scanCoordinator.isStoppedDueToLimit.value)
        assertEquals(0, scanCoordinator.currentCycleCount.value)
        verify { scannerEngine.startScanning(any(), 0, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `togglePause should pause and resume scanning manualy`() = runTest(testDispatcher) {
        val page = Page(
            id = "p1",
            bookId = "b1",
            name = "Page 1",
            buttonConfigs = listOf(ButtonConfig(label = "Button 1"))
        )
        every { checkForPredictorUseCase(any<Page>()) } returns false
        every { scanningSettings.autoStartScanning } returns true
        
        currentPage.value = page
        resolvedPage.value = page
        isUserModeActive.value = true

        val scanCoordinator = createCoordinator(backgroundScope)
        advanceUntilIdle()
        
        // Initially scanning starts
        verify { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), eq("p1")) }
        clearMocks(scannerEngine, answers = false)

        // When: Manually toggle pause
        scanCoordinator.togglePause()
        advanceUntilIdle()
        
        // Then: stopScanningTemporarily should be called
        verify { scannerEngine.pauseScanning() }
        assertEquals(true, scanCoordinator.isPausedManually.value)
        
        // Even if some other event (like action finishing) happens, it should NOT resume
        isExecuting.value = true
        advanceUntilIdle()
        isExecuting.value = false
        advanceUntilIdle()
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }

        // When: Toggle pause again
        scanCoordinator.togglePause()
        advanceUntilIdle()
        
        // Then: Scanning should resume
        assertEquals(false, scanCoordinator.isPausedManually.value)
        verify { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), eq("p1")) }
    }
}
