package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, manifest = Config.NONE, sdk = [35])
class ScanningIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var scanCoordinator: ScanCoordinator

    @Inject
    lateinit var interactionDelegate: InteractionDelegate

    @Inject
    lateinit var actionExecutor: ActionExecutor

    @Inject
    lateinit var scanningSettings: com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings

    @Before
    fun init() {
        hiltRule.inject()
        scanningSettings.autoStartScanning = true
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun verifyScanningFlowAndActionExecutionResumesScanning() = runTest {
        // 1. Arrange page with button configs
        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Test Page",
            rows = 1,
            columns = 2,
            buttonConfigs = listOf(
                ButtonConfig(id = "b1", label = "Sprechen", isActive = true),
                ButtonConfig(id = "b2", label = "Navigieren", isActive = true)
            )
        )

        // Set user mode active explicitly
        interactionDelegate.init(
            scope = backgroundScope,
            actionExecutor = actionExecutor,
            onPageLoadRequested = {},
            onGoBackRequested = {},
            smartPredictions = kotlinx.coroutines.flow.MutableStateFlow(null),
            currentBookIdFlow = kotlinx.coroutines.flow.MutableStateFlow(null),
            onVocalSwitchTriggered = { _, _, _, _, _ -> }
        )
        interactionDelegate.scanCoordinator = scanCoordinator
        
        interactionDelegate.setUserModeActive(true)
        val isUserModeActive = interactionDelegate.isUserModeActive
        assertTrue("User Mode should be active", isUserModeActive.value)

        // Initialize scan coordinator with the page flows
        scanCoordinator.init(
            currentPage = kotlinx.coroutines.flow.MutableStateFlow(page),
            isUserModeActive = isUserModeActive,
            resolvedPage = kotlinx.coroutines.flow.MutableStateFlow(page),
            isSmartPredictionLoading = kotlinx.coroutines.flow.MutableStateFlow(false),
            smartPredictions = kotlinx.coroutines.flow.MutableStateFlow(null)
        )

        // Force observeJob to run in our test coroutine scope to ensure combine flow emissions are caught instantly by runTest/advanceUntilIdle
        val scopeField = ScanCoordinator::class.java.getDeclaredField("scope")
        scopeField.isAccessible = true
        scopeField.set(scanCoordinator, this)
        
        // Re-call init to launch observeJob in our test scope
        scanCoordinator.init(
            currentPage = kotlinx.coroutines.flow.MutableStateFlow(page),
            isUserModeActive = isUserModeActive,
            resolvedPage = kotlinx.coroutines.flow.MutableStateFlow(page),
            isSmartPredictionLoading = kotlinx.coroutines.flow.MutableStateFlow(false),
            smartPredictions = kotlinx.coroutines.flow.MutableStateFlow(null)
        )

        // 2. Start scanning via coordinator
        scanCoordinator.startScanning()
        advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("Scanning should start", scanCoordinator.isScanning.value)

        // 3. Trigger action execution simulation
        // When ActionExecutor states it is executing, scanCoordinator should pause scanning
        actionExecutor.setExecutingStateForTest(true)
        advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        
        // Let flow combinations propagate
        // Wait or check status
        assertFalse("Scanning should be paused while ActionExecutor is running", scanCoordinator.isScanning.value)

        // 4. Complete action execution simulation
        actionExecutor.setExecutingStateForTest(false)
        advanceUntilIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // Scanning should resume automatically
        assertTrue("Scanning should resume automatically when action is finished", scanCoordinator.isScanning.value)

        // Clean up coroutines to prevent UncompletedCoroutinesError
        scanCoordinator.clear()
    }
}
