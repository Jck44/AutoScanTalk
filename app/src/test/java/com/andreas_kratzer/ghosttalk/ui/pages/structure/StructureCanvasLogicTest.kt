package com.andreas_kratzer.ghosttalk.ui.pages.structure

import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureCanvasLogicTest {

    private fun createPageHelper(buttonConfigs: List<ButtonConfig?>): Page {
        return Page(
            id = "page_1",
            bookId = "book_1",
            name = "Test Page",
            templateId = null,
            rows = 2,
            columns = 2,
            scanPattern = null,
            rowNames = emptyList(),
            orderIndex = 0,
            createdAt = 0L,
            updatedAt = 0L,
            buttonConfigs = buttonConfigs
        )
    }

    @Test
    fun testNavigableButtonsAllowsValidButtons() {
        val page = createPageHelper(listOf(
            ButtonConfig(label = "Valid 1", isActive = true, buttonAction = NavigateToPageButtonAction("page_2")),
            ButtonConfig(label = "Valid 2", isActive = true, buttonAction = NavigateToStartPageButtonAction())
        ))
        val buttons = navigableButtons(page, emptySet(), effectiveStartPageId = "start_page")
        assertEquals(2, buttons.size)
        assertEquals(0, buttons[0].first)
        assertEquals("Valid 1", buttons[0].second.label)
        assertEquals(1, buttons[1].first)
        assertEquals("Valid 2", buttons[1].second.label)
    }

    @Test
    fun testNavigableButtonsFiltersInactive() {
        val page = createPageHelper(listOf(
            ButtonConfig(label = "Inactive Button", isActive = false, buttonAction = NavigateToPageButtonAction("page_2"))
        ))
        val buttons = navigableButtons(page, emptySet(), effectiveStartPageId = "start_page")
        assertTrue(buttons.isEmpty())
    }

    @Test
    fun testNavigableButtonsFiltersBlankLabels() {
        val page = createPageHelper(listOf(
            ButtonConfig(label = "", isActive = true, buttonAction = NavigateToPageButtonAction("page_2")),
            ButtonConfig(label = "   ", isActive = true, buttonAction = NavigateToPageButtonAction("page_2"))
        ))
        val buttons = navigableButtons(page, emptySet(), effectiveStartPageId = "start_page")
        assertTrue(buttons.isEmpty())
    }

    @Test
    fun testNavigableButtonsFiltersSelfLoops() {
        // Direct self-loop
        val page = createPageHelper(listOf(
            ButtonConfig(label = "Self Loop", isActive = true, buttonAction = NavigateToPageButtonAction("page_1"))
        ))
        val buttonsDirect = navigableButtons(page, emptySet(), effectiveStartPageId = "start_page")
        assertTrue(buttonsDirect.isEmpty())

        // Start page self-loop
        val pageStart = createPageHelper(listOf(
            ButtonConfig(label = "Start Page Loop", isActive = true, buttonAction = NavigateToStartPageButtonAction())
        ))
        val buttonsStart = navigableButtons(pageStart, emptySet(), effectiveStartPageId = "page_1")
        assertTrue(buttonsStart.isEmpty())
    }

    @Test
    fun testNavigableButtonsFiltersOutgoingButtons() {
        val page = createPageHelper(listOf(
            ButtonConfig(label = "Outgoing Connection 1", isActive = true, buttonAction = NavigateToPageButtonAction("page_2")),
            ButtonConfig(label = "Outgoing Connection 2", isActive = true, buttonAction = NavigateToPageButtonAction("page_3"))
        ))
        
        // Mark button index 0 as already having an outgoing edge
        val buttons = navigableButtons(page, setOf(0), effectiveStartPageId = "start_page")
        assertEquals(1, buttons.size)
        assertEquals(1, buttons[0].first)
        assertEquals("Outgoing Connection 2", buttons[0].second.label)
    }

    @Test
    fun testDistanceToSegment() {
        val a = androidx.compose.ui.geometry.Offset(0f, 0f)
        val b = androidx.compose.ui.geometry.Offset(10f, 0f)

        // Point on the segment
        assertEquals(0f, distanceToSegment(androidx.compose.ui.geometry.Offset(5f, 0f), a, b), 0.001f)

        // Point above the segment midpoint
        assertEquals(5f, distanceToSegment(androidx.compose.ui.geometry.Offset(5f, 5f), a, b), 0.001f)

        // Point past end B (should project to B)
        assertEquals(5f, distanceToSegment(androidx.compose.ui.geometry.Offset(15f, 0f), a, b), 0.001f)

        // Point past start A (should project to A)
        assertEquals(5f, distanceToSegment(androidx.compose.ui.geometry.Offset(-5f, 0f), a, b), 0.001f)
    }

    @Test
    fun testCalculateOverviewLayoutWithOrphans() {
        val startPage = Page(
            id = "start_page",
            bookId = "book_1",
            name = "Start Page",
            templateId = null,
            rows = 2,
            columns = 2,
            scanPattern = null,
            rowNames = emptyList(),
            orderIndex = 0,
            createdAt = 0L,
            updatedAt = 0L,
            buttonConfigs = emptyList()
        )
        val orphans = (1..10).map { i ->
            Page(
                id = "orphan_$i",
                bookId = "book_1",
                name = "Orphan $i",
                templateId = null,
                rows = 2,
                columns = 2,
                scanPattern = null,
                rowNames = emptyList(),
                orderIndex = i,
                createdAt = 0L,
                updatedAt = 0L,
                buttonConfigs = emptyList()
            )
        }
        val pages = listOf(startPage) + orphans
        val graph = BookNavigationGraph.from(pages, "start_page")
        
        val layout = calculateOverviewLayout(
            graph = graph,
            pages = pages,
            nodeWidthPx = 160f,
            nodeHeightPx = 54f,
            horizontalGapPx = 100f,
            verticalGapPx = 32f
        )
        
        // Assert container and all orphans are in layout
        assertTrue(layout.containsKey("__orphans_container__"))
        for (i in 1..10) {
            assertTrue(layout.containsKey("orphan_$i"))
        }
        
        // Let's print out the Y values
        val containerY = layout["__orphans_container__"]!!.y
        val orphan1Y = layout["orphan_1"]!!.y
        val orphan10Y = layout["orphan_10"]!!.y
        
        println("Container Y: $containerY")
        println("Orphan 1 Y: $orphan1Y")
        println("Orphan 10 Y: $orphan10Y")
        
        // Verify gap between subsequent orphans
        for (i in 1..9) {
            val yCurr = layout["orphan_$i"]!!.y
            val yNext = layout["orphan_${i + 1}"]!!.y
            assertEquals(86f, yNext - yCurr, 0.001f) // nodeHeightPx (54) + verticalGapPx (32)
        }
        
        // Calculate border height using formula
        val maxOrphanY = (1..10).map { layout["orphan_$it"]!!.y }.maxOrNull()!!
        val groupHeight = (maxOrphanY + 54f) - containerY
        println("Calculated Group Height: $groupHeight")
        // containerY is startPage.y (0f) + nodeHeightPx (54f) + 100f = 154f
        // orphan_1 is containerY + 54f + 40f = 248f
        // orphan_10 is orphan_1 + 9 * 86f = 248f + 774f = 1022f
        // groupHeight = (1022f + 54f) - 154f = 1076f - 154f = 922f
        assertEquals(922f, groupHeight, 0.001f)
    }
}
