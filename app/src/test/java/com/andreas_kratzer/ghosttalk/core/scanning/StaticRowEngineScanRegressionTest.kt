package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression am echten ScannerEngine fuer den kritischen Bug:
 * "statische Zeile aktiv + lineares Scanning" durfte die Hauptseiten-Buttons
 * NICHT mehr ueberspringen. Treibt den Produktions-Einstieg startScanning(...)
 * mit einer realen statischen Zeile (Page) und einer realen Hauptseite.
 *
 * Vor dem Fix: ENGINE FOCUSED = [0]  (nur statische Zeile)
 * Nach dem Fix: enthaelt auch die Hauptseiten-Buttons (kombinierter Index 49/50).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaticRowEngineScanRegressionTest {

    @Test
    fun `engine static row plus linear scans main page buttons`() = runTest {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        val featureGuard = mockk<FeatureGuardProxy>(relaxed = true)
        every { featureGuard.isButtonVisible(any()) } returns true

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob())

        val engine = ScannerEngine(
            scope = scope,
            featureGuard = featureGuard,
            feedbackProvider = mockk(relaxed = true),
            stateManager = ScanStateManager(),
            scanTimer = ScanTimer(),
            linearStrategy = LinearScanStrategy(),
            rowByRowStrategy = RowByRowScanStrategy()
        )

        // Statische Zeile (1x4), Button an Index 0 aktiv
        val staticButtons = MutableList<ButtonConfig?>(49) { null }
        staticButtons[0] = ButtonConfig(id = "s0", label = "Static0", isActive = true, buttonAction = SpeakTextButtonAction())
        val staticRowPage = Page(
            id = "static_row_book1", bookId = "book1", name = "Statische Zeile",
            rows = 1, columns = 4, scanPattern = "linear", buttonConfigs = staticButtons
        )

        // Hauptseite (4x4), zwei aktive Buttons an lokalem Index 0 und 1 (-> kombiniert 49/50)
        val mainButtons = MutableList<ButtonConfig?>(49) { null }
        mainButtons[0] = ButtonConfig(id = "m0", label = "Main0", isActive = true, buttonAction = SpeakTextButtonAction())
        mainButtons[1] = ButtonConfig(id = "m1", label = "Main1", isActive = true, buttonAction = SpeakTextButtonAction())

        val collected = mutableListOf<Int>()
        val collector = launch { engine.focusedButtonIndex.collect { it?.let(collected::add) } }

        engine.startScanning(
            buttonConfigs = mainButtons,
            startIndex = 0,
            pattern = "linear",
            rows = 4,
            columns = 4,
            rowNames = emptyList(),
            pageId = "page1",
            staticRowPage = staticRowPage,
            staticRowPattern = "linear"
        )

        advanceTimeBy(8000)
        engine.stopScanning()
        collector.cancel()

        assertTrue("Statische Zeile (Index 0) muss gescannt werden: ${collected.distinct()}", collected.contains(0))
        assertTrue(
            "Hauptseiten-Button (kombinierter Index 49) MUSS gescannt werden, war: ${collected.distinct()}",
            collected.contains(49)
        )
        assertTrue(
            "Hauptseiten-Button (kombinierter Index 50) MUSS gescannt werden, war: ${collected.distinct()}",
            collected.contains(50)
        )
    }
}
