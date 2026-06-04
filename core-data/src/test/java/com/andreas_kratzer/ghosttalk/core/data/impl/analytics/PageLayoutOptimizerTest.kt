package com.andreas_kratzer.ghosttalk.core.data.impl.analytics

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutOptimizerTest {

    private val optimizer = PageLayoutOptimizer()

    @Test
    fun `analyzePages suggests split when start page exceeds limit`() {
        // Arrange
        // Create 10 active buttons on a page
        val buttons = MutableList<ButtonConfig?>(49) { null }
        for (i in 0 until 10) {
            val globalIdx = GridUtils.localToGlobalIndex(i, 4)
            buttons[globalIdx] = ButtonConfig(id = "btn_$i", isActive = true)
        }

        val page = Page(
            id = "start_page",
            bookId = "book1",
            name = "Startseite",
            rows = 4,
            columns = 4,
            scanPattern = "linear",
            buttonConfigs = buttons
        )

        // Act
        // Set scanDelay to 2000ms -> limit = 12000 / 2000 = 6 max buttons on start page
        val proposals = optimizer.analyzePages(
            pages = listOf(page),
            defaultStartPageId = "start_page",
            scanDelayMs = 2000L,
            defaultScanPattern = "linear"
        )

        // Assert
        // We expect a SplitPageProposal because activeCount (10) > maxStartPageButtons (6)
        val splitProposal = proposals.filterIsInstance<PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal>()
        assertEquals(1, splitProposal.size)
        assertEquals("start_page", splitProposal[0].pageId)
        assertEquals(10, splitProposal[0].activeButtonsCount)
    }

    @Test
    fun `analyzePages suggests row-by-row scanning for large linear page`() {
        // Arrange
        // Create 12 active buttons on a page (e.g. 4x4 grid)
        val buttons = MutableList<ButtonConfig?>(49) { null }
        for (i in 0 until 12) {
            val globalIdx = GridUtils.localToGlobalIndex(i, 4)
            buttons[globalIdx] = ButtonConfig(id = "btn_$i", isActive = true)
        }

        val page = Page(
            id = "large_page",
            bookId = "book1",
            name = "Große Seite",
            rows = 4,
            columns = 4,
            scanPattern = "linear",
            buttonConfigs = buttons
        )

        // Act
        // 12 active buttons, linear scanning.
        // Linear avg steps = (12+1)/2 = 6.5.
        // Row-by-row avg steps = select row (4 rows max, so (3+1)/2 = 2.0 avg row select) + select col (avg 4 buttons per row -> (4+1)/2 = 2.5 avg col select) -> 4.5 steps.
        // Diff = 6.5 - 4.5 = 2.0 steps saved. At 1000ms, time saved is 2.0s.
        val proposals = optimizer.analyzePages(
            pages = listOf(page),
            defaultStartPageId = "start_page",
            scanDelayMs = 1000L,
            defaultScanPattern = "linear"
        )

        // Assert
        val patternProposal = proposals.filterIsInstance<PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal>()
        assertEquals(1, patternProposal.size)
        assertEquals("large_page", patternProposal[0].pageId)
        assertEquals("row_by_row", patternProposal[0].targetScanPattern)
    }

    @Test
    fun `analyzePages does not suggest optimization for small and efficient page`() {
        // Arrange
        // Create 4 active buttons on a page
        val buttons = MutableList<ButtonConfig?>(49) { null }
        for (i in 0 until 4) {
            val globalIdx = GridUtils.localToGlobalIndex(i, 4)
            buttons[globalIdx] = ButtonConfig(id = "btn_$i", isActive = true)
        }

        val page = Page(
            id = "small_page",
            bookId = "book1",
            name = "Kleine Seite",
            rows = 4,
            columns = 4,
            scanPattern = "linear",
            buttonConfigs = buttons
        )

        // Act
        val proposals = optimizer.analyzePages(
            pages = listOf(page),
            defaultStartPageId = "start_page",
            scanDelayMs = 1000L,
            defaultScanPattern = "linear"
        )

        // Assert
        // Small pages (N=4) with low scan time shouldn't yield any split or scan mode warnings.
        assertTrue(proposals.isEmpty())
    }
}
