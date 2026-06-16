package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPagesUseCaseTest {

    private val useCase = SearchPagesUseCase()

    private val testPages = listOf(
        Page(
            id = "page1",
            bookId = "book1",
            name = "Frühstück",
            orderIndex = 1,
            buttonConfigs = listOf(
                ButtonConfig(label = "Kaffee bitte", isActive = true, buttonAction = SpeakTextButtonAction()),
                ButtonConfig(label = "Tee", isActive = false, buttonAction = SpeakTextButtonAction()),
                ButtonConfig(label = "Milch", isActive = true, buttonAction = SpeakTextButtonAction())
            )
        ),
        Page(
            id = "page2",
            bookId = "book1",
            name = "Mittagessen",
            orderIndex = 2,
            buttonConfigs = listOf(
                ButtonConfig(label = "Suppe", isActive = true, buttonAction = SpeakTextButtonAction()),
                ButtonConfig(label = "Kaffee am Nachmittag", isActive = true, buttonAction = SpeakTextButtonAction())
            )
        )
    )

    @Test
    fun `empty query returns empty results`() {
        val results = useCase.execute(testPages, "   ")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `matches page name case insensitively`() {
        val results = useCase.execute(testPages, "früh")
        assertEquals(1, results.size)
        val hit = results[0]
        assertEquals("page1", hit.pageId)
        assertTrue(hit.matchedOnName)
        assertTrue(hit.buttonHits.isEmpty())
    }

    @Test
    fun `matches button label case insensitively`() {
        val results = useCase.execute(testPages, "milch")
        assertEquals(1, results.size)
        val hit = results[0]
        assertEquals("page1", hit.pageId)
        assertFalse(hit.matchedOnName)
        assertEquals(1, hit.buttonHits.size)
        assertEquals(2, hit.buttonHits[0].index)
        assertEquals("Milch", hit.buttonHits[0].label)
    }

    @Test
    fun `ignores inactive buttons`() {
        val results = useCase.execute(testPages, "tee")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `matches both page name and buttons across multiple pages`() {
        // "kaffee" matches page1 button 0 ("Kaffee bitte") and page2 button 1 ("Kaffee am Nachmittag")
        val results = useCase.execute(testPages, "kaffee")
        assertEquals(2, results.size)
        
        val hit1 = results[0]
        assertEquals("page1", hit1.pageId)
        assertFalse(hit1.matchedOnName)
        assertEquals(1, hit1.buttonHits.size)
        assertEquals(0, hit1.buttonHits[0].index)

        val hit2 = results[1]
        assertEquals("page2", hit2.pageId)
        assertFalse(hit2.matchedOnName)
        assertEquals(1, hit2.buttonHits.size)
        assertEquals(1, hit2.buttonHits[0].index)
    }
}
