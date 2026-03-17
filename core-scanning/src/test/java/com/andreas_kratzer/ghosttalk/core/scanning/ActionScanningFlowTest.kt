package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.actions.ScannerActionProvider
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.settings.FeatureSettings
import com.andreas_kratzer.ghosttalk.core.ai.domain.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import io.mockk.mockk
import io.mockk.verify
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionScanningFlowTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(testDispatcher)
    private val scannerEngine = mockk<ScannerEngine>(relaxed = true)
    private val scanningSettings = mockk<ScanningSettings>(relaxed = true)
    private val featureSettings = mockk<FeatureSettings>(relaxed = true)
    private val actionProvider = mockk<ScannerActionProvider>(relaxed = true)
    private val checkForPredictorUseCase = mockk<CheckForPredictorUseCase>(relaxed = true)
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
        every { actionProvider.isExecuting } returns isExecuting
        every { scanningSettings.scanDelayFlow } returns MutableStateFlow(1000L)
        every { scanningSettings.autoStartScanning } returns true
        every { scanningSettings.defaultScanPattern } returns "linear"
        every { scannerEngine.focusedButtonIndex } returns MutableStateFlow(null)
        every { scannerEngine.focusedRowIndex } returns MutableStateFlow(null)
        every { scannerEngine.isScanning } returns MutableStateFlow(false)

        scanCoordinator = ScanCoordinator(
            scope = scope,
            scannerEngine = scannerEngine,
            scanningSettings = scanningSettings,
            featureSettings = featureSettings,
            actionProvider = actionProvider,
            checkForPredictorUseCase = checkForPredictorUseCase,
            ttsHelper = ttsHelper
        )
    }

    @Test
    fun `should pause scanning when action starts and resume when it finishes`() = runTest(testDispatcher) {
        val page = Page(id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1, buttonConfigs = emptyList())
        currentPage.value = page
        resolvedPage.value = page
        isUserModeActive.value = true

        scanCoordinator.init(currentPage, isUserModeActive, resolvedPage, isSmartPredictionLoading, smartPredictions)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify initial start
        verify { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), eq("p1")) }

        // Action starts
        isExecuting.value = true
        testDispatcher.scheduler.advanceUntilIdle()
        verify { scannerEngine.pauseScanning() }

        // Action finishes
        isExecuting.value = false
        testDispatcher.scheduler.advanceUntilIdle()
        verify { scannerEngine.startScanning(any(), any(), any(), any(), any(), any(), eq("p1")) }
    }
}
