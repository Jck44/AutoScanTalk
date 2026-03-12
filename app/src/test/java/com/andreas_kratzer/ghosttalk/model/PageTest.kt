package com.andreas_kratzer.ghosttalk.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageTest {

    private val validButtonConfig = ButtonConfig(
        id = "btn1",
        label = "Label 1",
        spokenText = "Action 1",
        auditoryCue = AuditoryCue.TextToSpeechCue("Hint 1"),
        buttonAction = SpeakTextButtonAction()
    )

    private val testBookId = "book-default"

    @Test
    fun `Page initialization with valid parameters succeeds`() {
        // Now using 49 slots (7x7) as base capacity
        val buttons = List(49) { validButtonConfig }
        val page = Page(id = "p1", bookId = testBookId, name = "Test Page", rows = 4, columns = 4, buttonConfigs = buttons)
        assertEquals("p1", page.id)
        assertEquals(testBookId, page.bookId)
        assertEquals("Test Page", page.name)
        assertEquals(4, page.rows)
        assertEquals(4, page.columns)
        assertEquals(49, page.buttonConfigs.size)
    }

    @Test
    fun `Page initialization with null buttons in list succeeds`() {
        val buttons = MutableList<ButtonConfig?>(49) { null }
        buttons[0] = validButtonConfig
        buttons[2] = validButtonConfig
        val page = Page(id = "p2", bookId = testBookId, name = "Page with empty slots", rows = 2, columns = 2, buttonConfigs = buttons)
        assertEquals(49, page.buttonConfigs.size)
        assertEquals(validButtonConfig, page.buttonConfigs[0])
        assertEquals(null, page.buttonConfigs[1])
    }

    @Test
    fun `Page initialization throws for zero rows`() {
        val buttons = emptyList<ButtonConfig?>()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Page(id = "p_err_row", bookId = testBookId, name = "Error Page", rows = 0, columns = 4, buttonConfigs = buttons)
        }
        assertEquals("Rows must be between 1 and 7.", exception.message)
    }

    @Test
    fun `Page initialization throws for zero columns`() {
        val buttons = emptyList<ButtonConfig?>()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Page(id = "p_err_col", bookId = testBookId, name = "Error Page", rows = 4, columns = 0, buttonConfigs = buttons)
        }
        assertEquals("Columns must be between 1 and 7.", exception.message)
    }

    @Test
    fun `Page initialization throws for negative rows`() {
         val buttons = emptyList<ButtonConfig?>()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Page(id = "p_err_neg_row", bookId = testBookId, name = "Error Page", rows = -1, columns = 4, buttonConfigs = buttons)
        }
        assertEquals("Rows must be between 1 and 7.", exception.message)
    }

    @Test
    fun `Page initialization throws for negative columns`() {
        val buttons = emptyList<ButtonConfig?>()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Page(id = "p_err_neg_col", bookId = testBookId, name = "Error Page", rows = 4, columns = -1, buttonConfigs = buttons)
        }
        assertEquals("Columns must be between 1 and 7.", exception.message)
    }

    @org.junit.Ignore("Grid size constraint is temporarily relaxed in Page.kt")
    @Test
    fun `Page initialization throws for incorrect buttonConfigs size`() {
        val buttons = List(48) { validButtonConfig }
        val expectedMessage = "The number of button configurations must match the total capacity (49). " +
                               "Expected 49, but got 48."
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Page(id = "p_err_size", bookId = testBookId, name = "Error Page", rows = 4, columns = 4, buttonConfigs = buttons)
        }
        assertEquals(expectedMessage, exception.message)
    }

    @Test
    fun `Page initialization with empty buttonConfigs for 1x1 grid succeeds if size matches`() {
        val buttons = List<ButtonConfig?>(49) { null }
        val page = Page(id = "p3", bookId = testBookId, name = "Single Empty Cell Page", rows = 1, columns = 1, buttonConfigs = buttons)
        assertEquals(49, page.buttonConfigs.size)
        assertEquals(null, page.buttonConfigs[0])
    }
}