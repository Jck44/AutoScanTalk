package com.andreas_kratzer.ghosttalk.ui.pages.structure

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
}
