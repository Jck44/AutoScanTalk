package com.andreas_kratzer.ghosttalk.ui.pages

import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.scanning.ScannerEngine
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.settings.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ScanCoordinatorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(testDispatcher)
    private val scannerEngine = mockk<ScannerEngine>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val actionExecutor = mockk<ActionExecutor>(relaxed = true)
    private val checkForPredictorUseCase = mockk<CheckForPredictorUseCase>()
    private val ttsHelper = mockk<TextToSpeechHelper>(relaxed = true)

    private val isExecuting = MutableStateFlow(false)
    private val currentPage = MutableStateFlow<Page?>(null)
    private val isUserModeActive = MutableStateFlow(true)
    private val resolvedPage = MutableStateFlow<Page?>(null)
    private val isSmartPredictionLoading = MutableStateFlow(false)
    private val smartPredictions = MutableStateFlow<List<String>?>(null)

    private lateinit var scanCoordinator: ScanCoordinator

    @Before
    fun setup() {
        every { settingsRepository.scanDelayFlow } returns MutableStateFlow(1000L)
        every { settingsRepository.isSmartPredictionEnabled } returns true
        every { settingsRepository.autoStartScanning } returns true
        every { actionExecutor.isExecuting } returns isExecuting
        every { scannerEngine.focusedButtonIndex } returns MutableStateFlow(null)
        
        scanCoordinator = ScanCoordinator(
            scope = scope,
            scannerEngine = scannerEngine,
            settingsRepository = settingsRepository,
            actionExecutor = actionExecutor,
            currentPage = currentPage,
            isUserModeActive = isUserModeActive,
            resolvedPage = resolvedPage,
            isSmartPredictionLoading = isSmartPredictionLoading,
            checkForPredictorUseCase = checkForPredictorUseCase,
            ttsHelper = ttsHelper,
            smartPredictions = smartPredictions
        )
        // scanCoordinator.init() // MOVE TO TEST
    }

    @Test
    fun `should stay paused when predictions are null (loading) or empty but page not yet resolved`() = runTest(testDispatcher) {
        isUserModeActive.value = true
        isExecuting.value = false
        
        val rawPage = mockk<Page>(relaxed = true) {
            every { id } returns "raw1"
            every { name } returns "Raw Page"
            every { buttonConfigs } returns listOf(ButtonConfig(label = "Gemini", auditoryCue = null, buttonAction = SmartPredictionButtonAction(1), isActive = true))
        }
        
        every { checkForPredictorUseCase(rawPage) } returns true
        currentPage.value = rawPage
        resolvedPage.value = rawPage
        smartPredictions.value = null
        isSmartPredictionLoading.value = true

        scanCoordinator.init()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { scannerEngine.pauseScanning() }
        clearMocks(scannerEngine, answers = false)

        // Step 1: Predictions arrive (empty list)
        smartPredictions.value = emptyList()
        isSmartPredictionLoading.value = false
        testDispatcher.scheduler.advanceUntilIdle()
        
        // EXPECTED: It should still be paused because resolvedPage STILL has predictors (rawPage)
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }
        
        // Step 2: Now resolve the page (UI catch up)
        val resPage = mockk<Page>(relaxed = true) {
            every { id } returns "res1"
            every { buttonConfigs } returns emptyList() // No more predictors
        }
        every { checkForPredictorUseCase(resPage) } returns false
        resolvedPage.value = resPage
        
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
        currentPage.value = rawPage
        resolvedPage.value = rawPage
        smartPredictions.value = null
        isSmartPredictionLoading.value = true
        
        // When: Something (like PageScreen) calls resumeScanningIfEnabled
        scanCoordinator.resumeScanningIfEnabled()
        
        // Then: startScanning should NOT be called
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }
        
        // Even if model is not loading but predictions are still null
        isSmartPredictionLoading.value = false
        scanCoordinator.resumeScanningIfEnabled()
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }

        // Once predictions arrive but resolution is pending
        smartPredictions.value = emptyList()
        scanCoordinator.resumeScanningIfEnabled()
        verify(exactly = 0) { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), any()) }
    }
}
