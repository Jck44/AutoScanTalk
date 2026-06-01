package com.andreas_kratzer.ghosttalk.core.data.impl.analytics

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EfficiencyAnalyzerTest {

    private val analyzer = EfficiencyAnalyzer()

    @Test
    fun `calculatePageMetrics with linear scanning calculates correct access times`() {
        val buttons = MutableList<ButtonConfig?>(49) { null }
        buttons[GridUtils.getGlobalIndex(0, 0)] = ButtonConfig(id = "b1", isActive = true)
        buttons[GridUtils.getGlobalIndex(0, 1)] = ButtonConfig(id = "b2", isActive = true)
        buttons[GridUtils.getGlobalIndex(1, 0)] = ButtonConfig(id = "b3", isActive = true)

        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Test Page",
            rows = 2,
            columns = 2,
            scanPattern = "linear",
            buttonConfigs = buttons
        )
        val clickCounts = mapOf(
            "b1" to 10L,
            "b2" to 5L,
            "b3" to 0L
        )

        val metrics = analyzer.calculatePageMetrics(
            page = page,
            allPages = listOf(page),
            startPageId = page.id,
            clickCounts = clickCounts,
            scanDelayMs = 1000L,
            defaultScanPattern = "linear"
        )

        // For linear scan:
        // b1 is active index 0 -> totalSteps = 1 -> accessTimeSec = 1s
        // b2 is active index 1 -> totalSteps = 2 -> accessTimeSec = 2s
        // b3 is active index 2 -> totalSteps = 3 -> accessTimeSec = 3s
        assertEquals(3, metrics.size)
        assertEquals(1, metrics["b1"]?.accessTimeSec)
        assertEquals(2, metrics["b2"]?.accessTimeSec)
        assertEquals(3, metrics["b3"]?.accessTimeSec)
    }

    @Test
    fun `calculatePageMetrics with row_by_row scanning calculates correct access times`() {
        // Grid size 2x2:
        // Row 0: index (0,0) -> "b1", index (0,1) -> "b2"
        // Row 1: index (1,0) -> "b3", index (1,1) -> "b4"
        val buttons = MutableList<ButtonConfig?>(49) { null }
        buttons[GridUtils.getGlobalIndex(0, 0)] = ButtonConfig(id = "b1", isActive = true)
        buttons[GridUtils.getGlobalIndex(0, 1)] = ButtonConfig(id = "b2", isActive = true)
        buttons[GridUtils.getGlobalIndex(1, 0)] = ButtonConfig(id = "b3", isActive = true)
        buttons[GridUtils.getGlobalIndex(1, 1)] = ButtonConfig(id = "b4", isActive = true)

        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Test Page",
            rows = 2,
            columns = 2,
            scanPattern = "row_by_row",
            buttonConfigs = buttons
        )
        val clickCounts = emptyMap<String, Long>()

        val metrics = analyzer.calculatePageMetrics(
            page = page,
            allPages = listOf(page),
            startPageId = page.id,
            clickCounts = clickCounts,
            scanDelayMs = 1000L,
            defaultScanPattern = "row_by_row"
        )

        // Row-by-Row:
        // Active rows are Row 0 and Row 1
        // b1: Row 0 (index 0 in activeRows), Col 0 -> steps = (0 + 1) + (0 + 1) = 2 steps -> 2s
        // b2: Row 0, Col 1 -> steps = (0 + 1) + (1 + 1) = 3 steps -> 3s
        // b3: Row 1 (index 1 in activeRows), Col 0 -> steps = (1 + 1) + (0 + 1) = 3 steps -> 3s
        // b4: Row 1, Col 1 -> steps = (1 + 1) + (1 + 1) = 4 steps -> 4s

        assertEquals(4, metrics.size)
        assertEquals(2, metrics["b1"]?.accessTimeSec)
        assertEquals(3, metrics["b2"]?.accessTimeSec)
        assertEquals(3, metrics["b3"]?.accessTimeSec)
        assertEquals(4, metrics["b4"]?.accessTimeSec)
    }

    @Test
    fun `calculatePageMetrics correctly normalizes frustration index and heatmap intensity`() {
        val buttons = MutableList<ButtonConfig?>(49) { null }
        buttons[GridUtils.getGlobalIndex(0, 0)] = ButtonConfig(id = "b1", isActive = true)
        buttons[GridUtils.getGlobalIndex(0, 1)] = ButtonConfig(id = "b2", isActive = true)

        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Test Page",
            rows = 2,
            columns = 2,
            scanPattern = "linear",
            buttonConfigs = buttons
        )
        // b1: accessTimeSec = 1s, clicks = 10 -> raw frustration = 10
        // b2: accessTimeSec = 2s, clicks = 20 -> raw frustration = 40
        // Max raw frustration = 40
        // Normalized: b1 = 10/40 = 0.25, b2 = 40/40 = 1.0
        // Heatmap: maxClicks = 20 -> b1 = 10/20 = 0.5, b2 = 20/20 = 1.0
        val clickCounts = mapOf(
            "b1" to 10L,
            "b2" to 20L
        )

        val metrics = analyzer.calculatePageMetrics(
            page = page,
            allPages = listOf(page),
            startPageId = page.id,
            clickCounts = clickCounts,
            scanDelayMs = 1000L,
            defaultScanPattern = "linear"
        )

        assertEquals(0.25f, metrics["b1"]?.frustrationIndex ?: 0f, 0.001f)
        assertEquals(1.0f, metrics["b2"]?.frustrationIndex ?: 0f, 0.001f)
        assertEquals(0.5f, metrics["b1"]?.heatmapIntensity ?: 0f, 0.001f)
        assertEquals(1.0f, metrics["b2"]?.heatmapIntensity ?: 0f, 0.001f)
    }

    @Test
    fun `calculatePageMetrics ignores inactive and invisible buttons`() {
        val buttons = MutableList<ButtonConfig?>(49) { null }
        buttons[GridUtils.getGlobalIndex(0, 0)] = ButtonConfig(id = "b1", isActive = true)
        buttons[GridUtils.getGlobalIndex(0, 1)] = ButtonConfig(id = "b2", isActive = false) // inactive
        buttons[GridUtils.getGlobalIndex(1, 0)] = ButtonConfig(id = "b3", isActive = true)
        buttons[GridUtils.getGlobalIndex(1, 1)] = ButtonConfig(id = "b4", isActive = true)
        buttons[GridUtils.getGlobalIndex(0, 2)] = ButtonConfig(id = "b5", isActive = true)  // invisible because columns = 2 (c = 2)

        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Test Page",
            rows = 2,
            columns = 2,
            scanPattern = "linear",
            buttonConfigs = buttons
        )

        val metrics = analyzer.calculatePageMetrics(
            page = page,
            allPages = listOf(page),
            startPageId = page.id,
            clickCounts = emptyMap(),
            scanDelayMs = 1000L,
            defaultScanPattern = "linear"
        )

        // b1 and b3, b4 should be analyzed. b2 (inactive) and b5 (invisible) should be omitted.
        assertEquals(3, metrics.size)
        assertTrue(metrics.containsKey("b1"))
        assertTrue(!metrics.containsKey("b2"))
        assertTrue(metrics.containsKey("b3"))
        assertTrue(metrics.containsKey("b4"))
        assertTrue(!metrics.containsKey("b5"))
    }
}
