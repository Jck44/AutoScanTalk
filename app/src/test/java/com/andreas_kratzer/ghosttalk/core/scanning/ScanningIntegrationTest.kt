package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.actions.ActionCoordinator
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanningIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var scannerEngine: ScannerEngine
    private lateinit var scanCoordinator: ScanCoordinator
    private lateinit var actionExecutor: ActionExecutor
    
    // Child scope for components to allow controlled cancellation
    private lateinit var componentJob: Job
    private lateinit var componentScope: CoroutineScope

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val buttonUsageRepository = mockk<ButtonUsageRepository>(relaxed = true)
    private val scanningSettings = mockk<ScanningSettings>(relaxed = true)
    private val ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)
    private val featureGuard = mockk<FeatureGuardProxy>(relaxed = true)
    private val callActionProxy = mockk<CallActionProxy>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        every { featureGuard.isButtonVisible(any()) } returns true
        every { featureGuard.isActionEnabled(any()) } returns true
        
        // Mock flows before initialization to avoid collect errors
        every { scanningSettings.scanDelayFlow } returns MutableStateFlow(1000L)
        every { scanningSettings.autoStartScanning } returns true
        every { scanningSettings.resumeScanningFromStart } returns true
        every { scanningSettings.defaultScanPattern } returns "linear"
        every { callActionProxy.isInCall } returns MutableStateFlow(false)
        
        componentJob = SupervisorJob()
        componentScope = CoroutineScope(testDispatcher + componentJob)

        scannerEngine = ScannerEngine(
            scope = componentScope,
            featureGuard = featureGuard,
            feedbackProvider = mockk(relaxed = true),
            stateManager = ScanStateManager(),
            scanTimer = ScanTimer()
        )

        val actionCoordinator = ActionCoordinator(componentScope, mockk(relaxed = true))
        
        actionExecutor = ActionExecutor(
            scope = componentScope,
            settingsRepository = settingsRepository,
            buttonUsageRepository = buttonUsageRepository,
            handlers = emptySet(),
            actionCoordinator = actionCoordinator,
            ttsHelper = ttsHelper,
            scanCoordinatorProvider = mockk(relaxed = true),
            firebaseAnalyticsManager = mockk(relaxed = true)
        )

        scanCoordinator = ScanCoordinator(
            scope = componentScope,
            scannerEngine = scannerEngine,
            scanningSettings = scanningSettings,
            actionProvider = actionExecutor,
            callActionProxy = callActionProxy
        )
    }

    @Test
    fun `test row-to-button scanning sequence`() = testScope.runTest {
        try {
            val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
            buttonConfigs[0] = ButtonConfig(id = "b1", label = "B1", isActive = true, buttonAction = SpeakTextButtonAction())
            buttonConfigs[1] = ButtonConfig(id = "b2", label = "B2", isActive = true, buttonAction = SpeakTextButtonAction())
            buttonConfigs[7] = ButtonConfig(id = "b3", label = "B3", isActive = true, buttonAction = SpeakTextButtonAction())
            buttonConfigs[8] = ButtonConfig(id = "b4", label = "B4", isActive = true, buttonAction = SpeakTextButtonAction())

            val page = Page(
                id = "p1", bookId = "b1", name = "Test", rows = 2, columns = 2,
                buttonConfigs = buttonConfigs,
                scanPattern = "row_by_row"
            )

            scanCoordinator.init(
                currentPage = MutableStateFlow(page),
                isUserModeActive = MutableStateFlow(true),
                resolvedPage = MutableStateFlow(page),
                isSmartPredictionLoading = MutableStateFlow(false),
                smartPredictions = MutableStateFlow(emptyList()) 
            )

            advanceTimeBy(100)
            assertTrue("Scanner should be active", scannerEngine.isScanning.value)
            
            advanceTimeBy(150) // settle delay
            assertEquals(0, scannerEngine.focusedRowIndex.value)
            
            advanceTimeBy(1005)
            assertEquals(1, scannerEngine.focusedRowIndex.value)

            scanCoordinator.selectCurrentRow()
            advanceTimeBy(150) // button scan settle delay
            
            assertEquals(1, scannerEngine.focusedRowIndex.value)
            assertEquals(7, scannerEngine.focusedButtonIndex.value)

            advanceTimeBy(1005)
            assertEquals(8, scannerEngine.focusedButtonIndex.value)

            // Execute action at button 8
            actionExecutor.executeButtonAction(page.buttonConfigs[8]!!)
            
            // Should reset to row 0
            advanceTimeBy(600) // resume delay + settle delay
            assertEquals(0, scannerEngine.focusedRowIndex.value)
            assertNull(scannerEngine.focusedButtonIndex.value)
        } finally {
            componentJob.cancel()
        }
    }

    @Test
    fun `test holding time does not block scan jumping`() = testScope.runTest {
        try {
            val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
            for (i in 0 until 4) {
                buttonConfigs[i] = ButtonConfig(id = "b$i", label = "B$i", isActive = true, buttonAction = SpeakTextButtonAction())
            }
            val page = Page(
                id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 4,
                buttonConfigs = buttonConfigs,
                scanPattern = "linear"
            )

            every { settingsRepository.holdingTimeMillis } returns 2000L
            
            scanCoordinator.init(
                currentPage = MutableStateFlow(page),
                isUserModeActive = MutableStateFlow(true),
                resolvedPage = MutableStateFlow(page),
                isSmartPredictionLoading = MutableStateFlow(false),
                smartPredictions = MutableStateFlow(emptyList())
            )

            advanceTimeBy(200)
            assertTrue("Scanner should be active (linear)", scannerEngine.isScanning.value)
            
            // Initial focus at 0
            assertEquals(0, scannerEngine.focusedButtonIndex.value)

            // 1. FIRST CLICK at T=200ms (NOT ignored)
            actionExecutor.setTimeProviderForTest { 200L }
            actionExecutor.executeButtonAction(page.buttonConfigs[0]!!)
            
            // The first click will cause a scan reset in ScanCoordinator due to isExecuting jumping to true.
            // Wait for it to resume and re-focus index 0.
            advanceTimeBy(500) 
            assertEquals(0, scannerEngine.focusedButtonIndex.value)

            // 2. WAIT for it to advance to Index 1
            advanceTimeBy(1005)
            assertEquals(1, scannerEngine.focusedButtonIndex.value)

            // 3. SECOND CLICK at T=1300ms (IGNORED because 1300-200 < 2000 holding time)
            actionExecutor.setTimeProviderForTest { 1300L }
            actionExecutor.executeButtonAction(page.buttonConfigs[1]!!)
            
            // Verify scanner is STILL scanning (it shouldn't have paused because the click was ignored)
            assertTrue("Scanner should still be active after ignored click", scannerEngine.isScanning.value)
            assertEquals(1, scannerEngine.focusedButtonIndex.value)
            
            // 4. WAIT for focus to advance to Index 2
            advanceTimeBy(1005)
            assertEquals(2, scannerEngine.focusedButtonIndex.value)
        } finally {
            componentJob.cancel()
        }
    }

    @Test
    fun `test scanning pauses when smart prediction is loading and resumes when loaded`() = testScope.runTest {
        try {
            val rawButtonConfigs = MutableList<ButtonConfig?>(49) { null }
            rawButtonConfigs[0] = ButtonConfig(id = "b1", label = "B1", isActive = true, buttonAction = com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction(1))
            val rawPage = Page(
                id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1,
                buttonConfigs = rawButtonConfigs,
                scanPattern = "linear"
            )

            val resolvedButtonConfigs = MutableList<ButtonConfig?>(49) { null }
            resolvedButtonConfigs[0] = ButtonConfig(id = "b1", label = "Prediction 1", isActive = true, buttonAction = SpeakTextButtonAction())
            val resolvedPage = Page(
                id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1,
                buttonConfigs = resolvedButtonConfigs,
                scanPattern = "linear"
            )

            val isUserModeActive = MutableStateFlow(true)
            val currentPage = MutableStateFlow(rawPage)
            val resolvedPageFlow = MutableStateFlow(resolvedPage)
            
            // Start in a LOADING state
            val isSmartPredictionLoading = MutableStateFlow(true)
            val smartPredictions = MutableStateFlow<List<String>?>(null)

            scanCoordinator.init(
                currentPage = currentPage,
                isUserModeActive = isUserModeActive,
                resolvedPage = resolvedPageFlow,
                isSmartPredictionLoading = isSmartPredictionLoading,
                smartPredictions = smartPredictions
            )

            // Let the scanner initialize in loading state (it should be paused)
            advanceTimeBy(100)
            assertTrue("Scanner should not be active initially when loading", !scannerEngine.isScanning.value)

            // 1. Simulate Loading finished / Predictions arrive
            isSmartPredictionLoading.value = false
            smartPredictions.value = emptyList()
            advanceTimeBy(200)
            
            // Now scanner should become active!
            assertTrue("Scanner should be active after predictions load", scannerEngine.isScanning.value)

            // 2. Simulate Smart Prediction Loading again (e.g. page refresh or new query)
            isSmartPredictionLoading.value = true
            advanceTimeBy(100)
            // Scanner should pause/stop temporarily
            assertTrue("Scanner should pause when loading starts", !scannerEngine.isScanning.value)

            // 3. Simulate Loading finished again
            isSmartPredictionLoading.value = false
            advanceTimeBy(600) // Settle delays
            assertTrue("Scanner should resume after loading finished", scannerEngine.isScanning.value)
        } finally {
            componentJob.cancel()
        }
    }

    @Test
    fun `test scan stops after cycle limit is reached`() = testScope.runTest {
        try {
            val buttonConfigs = MutableList<ButtonConfig?>(49) { null }
            buttonConfigs[0] = ButtonConfig(id = "b1", label = "B1", isActive = true, buttonAction = SpeakTextButtonAction())
            val page = Page(
                id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1,
                buttonConfigs = buttonConfigs,
                scanPattern = "linear"
            )

            scanCoordinator.init(
                currentPage = MutableStateFlow(page),
                isUserModeActive = MutableStateFlow(true),
                resolvedPage = MutableStateFlow(page),
                isSmartPredictionLoading = MutableStateFlow(false),
                smartPredictions = MutableStateFlow(emptyList())
            )
            
            scanCoordinator.setScanLimitSettings(enabled = true, limit = 1)
            
            advanceTimeBy(150)
            assertTrue("Scanner should be active", scannerEngine.isScanning.value)
            
            // Advance time to complete cycle (it triggers onCycleCompleted)
            advanceTimeBy(1000)
            
            // Scanner should stop after 1 cycle
            assertTrue("Scanner should stop due to limit", !scannerEngine.isScanning.value)
            assertTrue("isStoppedDueToLimit should be true", scanCoordinator.isStoppedDueToLimit.value)
        } finally {
            componentJob.cancel()
        }
    }
}
