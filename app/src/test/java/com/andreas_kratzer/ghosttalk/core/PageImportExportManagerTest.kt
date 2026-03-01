package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.util.TestLogger
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageImportExportManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val pageRepository: PageRepository = mockk(relaxed = true)
    private val logger: Logger = TestLogger
    private val manager = PageImportExportManager(pageRepository, logger, testDispatcher)

    @Test
    fun `importFromJson maps buttons and isActive correctly`() = runTest(testDispatcher) {
        val jsonString = """
            {
                "pages": [
                    {
                        "importId": "p1", "name": "TestPage", "rows": 1, "columns": 3,
                        "buttons": [
                            { "index": 0, "label": "Active Btn", "active": true, "action": { "type": "SpeakText", "textToSpeech": "Hello" } },
                            { "index": 1, "label": "", "active": true, "action": null },
                            { "index": 2, "label": "Inactive Btn", "active": false, "action": { "type": "SpeakText", "textToSpeech": "Hidden" } }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        val result = manager.importFromJson(jsonString, "test_book")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        
        assertTrue(pageSlot.isCaptured)
        val page = pageSlot.captured
        assertEquals("test_book", page.bookId)
        assertEquals(3, page.buttonConfigs.size)

        // b1: active
        assertNotNull(page.buttonConfigs[0])
        assertTrue(page.buttonConfigs[0]!!.isActive)
        assertEquals("Active Btn", page.buttonConfigs[0]!!.label)

        // b2: empty/invalid action -> null
        assertNull(page.buttonConfigs[1])

        // b3: inactive
        assertNotNull(page.buttonConfigs[2])
        assertEquals(false, page.buttonConfigs[2]!!.isActive)
    }

    @Test
    fun `exportToJson serializes isActive state`() = runTest(testDispatcher) {
        val page = Page(
            id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 2,
            buttonConfigs = listOf(
                ButtonConfig("b1", "Active", auditoryCue = null, buttonAction = SpeakTextButtonAction("1"), isActive = true),
                ButtonConfig("b2", "Inactive", auditoryCue = null, buttonAction = SpeakTextButtonAction("2"), isActive = false)
            )
        )

        val json = manager.exportToJson(listOf(page))
        
        assertTrue(json.contains("\"active\":true"))
        assertTrue(json.contains("\"active\":false"))
        assertTrue(json.contains("\"label\":\"Active\""))
        assertTrue(json.contains("\"label\":\"Inactive\""))
    }
}
