package com.andreas_kratzer.ghosttalk.core.data.impl.clone

import com.andreas_kratzer.ghosttalk.core.database.ButtonEntity
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloneHelpersTest {

    @Test
    fun testOptimalGridUpTo5() {
        assertEquals(2 to 2, CloneHelpers.optimalGridUpTo5(0))
        assertEquals(2 to 2, CloneHelpers.optimalGridUpTo5(3))
        assertEquals(2 to 2, CloneHelpers.optimalGridUpTo5(4))
        assertEquals(3 to 3, CloneHelpers.optimalGridUpTo5(5))
        assertEquals(3 to 3, CloneHelpers.optimalGridUpTo5(9))
        assertEquals(4 to 4, CloneHelpers.optimalGridUpTo5(10))
        assertEquals(4 to 4, CloneHelpers.optimalGridUpTo5(16))
        assertEquals(5 to 5, CloneHelpers.optimalGridUpTo5(17))
        assertEquals(5 to 5, CloneHelpers.optimalGridUpTo5(50))
    }

    @Test
    fun testOptimalGridUpTo7() {
        assertEquals(2 to 2, CloneHelpers.optimalGridUpTo7(0))
        assertEquals(2 to 2, CloneHelpers.optimalGridUpTo7(4))
        assertEquals(3 to 3, CloneHelpers.optimalGridUpTo7(5))
        assertEquals(3 to 3, CloneHelpers.optimalGridUpTo7(9))
        assertEquals(4 to 4, CloneHelpers.optimalGridUpTo7(10))
        assertEquals(4 to 4, CloneHelpers.optimalGridUpTo7(16))
        assertEquals(5 to 5, CloneHelpers.optimalGridUpTo7(17))
        assertEquals(5 to 5, CloneHelpers.optimalGridUpTo7(25))
        assertEquals(6 to 6, CloneHelpers.optimalGridUpTo7(26))
        assertEquals(6 to 6, CloneHelpers.optimalGridUpTo7(36))
        assertEquals(7 to 7, CloneHelpers.optimalGridUpTo7(37))
        assertEquals(7 to 7, CloneHelpers.optimalGridUpTo7(100))
    }

    @Test
    fun testExpandGridByOne() {
        assertEquals(4 to 5, CloneHelpers.expandGridByOne(4, 4))
        assertEquals(5 to 7, CloneHelpers.expandGridByOne(5, 6))
        assertEquals(6 to 7, CloneHelpers.expandGridByOne(5, 7))
        assertEquals(7 to 7, CloneHelpers.expandGridByOne(6, 7))
        assertEquals(7 to 7, CloneHelpers.expandGridByOne(7, 7))
    }

    @Test
    fun testExpandGridToFit() {
        assertEquals(2 to 2, CloneHelpers.expandGridToFit(2, 2, 4))
        assertEquals(3 to 2, CloneHelpers.expandGridToFit(2, 2, 5))
        assertEquals(4 to 4, CloneHelpers.expandGridToFit(4, 4, 16))
        assertEquals(5 to 4, CloneHelpers.expandGridToFit(4, 4, 17))
        assertEquals(7 to 7, CloneHelpers.expandGridToFit(4, 4, 49))
        assertEquals(7 to 7, CloneHelpers.expandGridToFit(7, 7, 50))
    }

    @Test
    fun testFindFreeSlot() {
        assertEquals(0, CloneHelpers.findFreeSlot(emptySet()))
        assertEquals(1, CloneHelpers.findFreeSlot(setOf(0)))
        assertEquals(2, CloneHelpers.findFreeSlot(setOf(0, 1)))
        assertEquals(1, CloneHelpers.findFreeSlot(setOf(0, 2), excluded = 2))
        assertEquals(3, CloneHelpers.findFreeSlot(setOf(0, 1, 2), excluded = 2))
    }

    @Test
    fun testCreateNavButton() {
        val btn = CloneHelpers.createNavButton(
            id = "btn-1",
            pageId = "page-1",
            targetPageId = "page-2",
            label = "Target",
            slot = 5,
            isActive = true
        )
        assertEquals("btn-1", btn.id)
        assertEquals("page-1", btn.pageId)
        assertEquals(5, btn.globalIndex)
        assertEquals("Target", btn.label)
        assertEquals("Öffne Target", btn.spokenText)
        assertEquals(AuditoryCue.TextToSpeechCue("Öffne Target"), btn.auditoryCue)
        assertTrue(btn.isActive)
    }

    @Test
    fun testChunkButtonsEmpty() {
        val chunks = CloneHelpers.chunkButtons(
            basePageName = "Test",
            buttons = emptyList(),
            targetBookId = "book-1",
            basePageId = "base-1",
            templateId = null,
            isActive = true,
            gridStrategy = { CloneHelpers.optimalGridUpTo5(it) },
            navButtonCreator = { _, _, _ -> mockkBtn() }
        )
        assertEquals(1, chunks.size)
        assertEquals("Test", chunks[0].page.name)
        assertEquals("base-1", chunks[0].page.id)
        assertTrue(chunks[0].buttons.isEmpty())
    }

    @Test
    fun testChunkButtonsSinglePage() {
        val buttons = List(10) { mockkBtn(it) }
        val chunks = CloneHelpers.chunkButtons(
            basePageName = "Test",
            buttons = buttons,
            targetBookId = "book-1",
            basePageId = "base-1",
            templateId = "temp-1",
            isActive = true,
            gridStrategy = { CloneHelpers.optimalGridUpTo5(it) },
            navButtonCreator = { _, _, _ -> mockkBtn() }
        )
        assertEquals(1, chunks.size)
        assertEquals("Test", chunks[0].page.name)
        assertEquals("base-1", chunks[0].page.id)
        assertEquals("temp-1", chunks[0].page.templateId)
        assertEquals(10, chunks[0].buttons.size)
        assertEquals(0, chunks[0].buttons[0].globalIndex)
        assertEquals(9, chunks[0].buttons[9].globalIndex)
    }

    @Test
    fun testChunkButtonsMultiPage() {
        val buttons = List(97) { mockkBtn(it) }
        val chunks = CloneHelpers.chunkButtons(
            basePageName = "Test",
            buttons = buttons,
            targetBookId = "book-1",
            basePageId = "base-1",
            templateId = null,
            isActive = true,
            gridStrategy = { 4 to 4 },
            navButtonCreator = { curPageId, nextPageId, pageIndex ->
                CloneHelpers.createNavButton(
                    id = "nav-$pageIndex",
                    pageId = curPageId,
                    targetPageId = nextPageId,
                    label = "Weiter",
                    slot = 48,
                    isActive = true
                )
            }
        )
        // 97 buttons: Page 1: 48 items + 1 nav button (49 total). Page 2: 48 items + 1 nav button (49 total). Page 3: 1 item (1 total).
        assertEquals(3, chunks.size)
        assertEquals("Test", chunks[0].page.name)
        assertEquals("base-1", chunks[0].page.id)
        assertEquals(49, chunks[0].buttons.size)
        assertEquals("Weiter", chunks[0].buttons[48].label)

        assertEquals("Test 2", chunks[1].page.name)
        assertEquals(49, chunks[1].buttons.size)
        assertEquals("Weiter", chunks[1].buttons[48].label)

        assertEquals("Test 3", chunks[2].page.name)
        assertEquals(1, chunks[2].buttons.size)
        assertFalse(chunks[2].buttons.any { it.label == "Weiter" })
    }

    private fun mockkBtn(index: Int = 0): ButtonEntity {
        return ButtonEntity(
            id = UUID.randomUUID().toString(),
            pageId = "",
            globalIndex = index,
            label = "Btn $index",
            spokenText = "Speak $index",
            auditoryCue = null,
            buttonAction = SpeakTextButtonAction(),
            isActive = true
        )
    }
}
