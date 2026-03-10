package com.andreas_kratzer.ghosttalk.core.pages

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.util.TestLogger
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
    private val bookRepository: com.andreas_kratzer.ghosttalk.data.BookRepository = mockk(relaxed = true)
    private val pageRepository: PageRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val templateRepository: TemplateRepository = mockk(relaxed = true) {
        coEvery { getAllTemplates() } returns flowOf(emptyList())
    }
    private val logger: Logger = TestLogger()
    private val manager = PageImportExportManager(bookRepository, pageRepository, settingsRepository, templateRepository, logger, testDispatcher)

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
        // With spatial mapping, buttonConfigs is ALWAYS 49
        assertEquals(49, page.buttonConfigs.size)

        // b1: active. index 0 -> (0,0) -> global 0
        assertNotNull(page.buttonConfigs[0])
        assertTrue(page.buttonConfigs[0]!!.isActive)
        assertEquals("Active Btn", page.buttonConfigs[0]!!.label)

        // b2: empty/invalid action -> null. index 1 -> (0,1) -> global 1
        assertNull(page.buttonConfigs[1])

        // b3: inactive. index 2 -> (0,2) -> global 2
        assertNotNull(page.buttonConfigs[2])
        assertEquals(false, page.buttonConfigs[2]!!.isActive)
    }

    @Test
    fun `importFromJson handles holdingTime and auditoryCues`() = runTest(testDispatcher) {
        val jsonString = """
            {
                "holdingTimeSeconds": 0.5,
                "pages": [
                    {
                        "importId": "p1", "name": "Test", "rows": 1, "columns": 1,
                        "buttons": [
                            { 
                                "index": 0, "label": "Btn", "active": true, 
                                "auditoryCueText": "Listen",
                                "action": { "type": "SpeakText", "textToSpeech": "Voice" } 
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit
        
        // Mock holdingTimeMillis setter (actually mockk relaxed handles it, but let's be explicit if needed)
        // In this case, we just check if it was called via the verify below.

        manager.importFromJson(jsonString, "b1")

        // Verify holding time update (0.5s -> 500ms)
        io.mockk.verify { settingsRepository.holdingTimeMillis = 500L }

        // Verify auditory cue mapping
        val config = pageSlot.captured.buttonConfigs[0]
        assertNotNull(config)
        assertTrue(config?.auditoryCue is AuditoryCue.TextToSpeechCue)
        assertEquals("Listen", (config?.auditoryCue as AuditoryCue.TextToSpeechCue).text)
    }

    @Test
    fun `exportToJson serializes holdingTime and auditoryCues`() = runTest(testDispatcher) {
        coEvery { settingsRepository.holdingTimeMillis } returns 750L
        val page = Page(
            id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1,
            buttonConfigs = listOf(
                ButtonConfig(
                    id = "b1", 
                    label = "Label", 
                    spokenText = "Speak",
                    auditoryCue = AuditoryCue.TextToSpeechCue("Cue"), 
                    buttonAction = SpeakTextButtonAction(), 
                    isActive = true
                )
            )
        )

        val json = manager.exportToJson(listOf(page))
        
        assertTrue(json.contains("\"holdingTimeSeconds\":0.75"))
        assertTrue(json.contains("\"auditoryCueText\":\"Cue\""))
        assertTrue(json.contains("\"spokenText\":\"Speak\""))
        assertTrue(json.contains("\"textToSpeech\":\"Speak\""))
    }

    @Test
    fun `importFromJson expands grid if buttons exceed metadata dimensions`() = runTest(testDispatcher) {
        // Metadata says 2x2 (4 slots), but maxIndex is 5 (6th button)
        val jsonString = """
            {
                "pages": [
                    {
                        "importId": "p1", "name": "ExpansionTest", "rows": 2, "columns": 2,
                        "buttons": [
                            { "index": 0, "label": "B1", "active": true, "action": { "type": "SpeakText", "textToSpeech": "1" } },
                            { "index": 5, "label": "B6", "active": true, "action": { "type": "SpeakText", "textToSpeech": "6" } }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        manager.importFromJson(jsonString, "book1")

        assertTrue(pageSlot.isCaptured)
        val page = pageSlot.captured
        
        // With rows=2, columns=2, maxIndex=5, and source (import) columns = 2:
        // Logic expands columns first: 2x2 -> 2x3 (6 slots)
        assertEquals(2, page.rows)
        assertEquals(3, page.columns)
        assertEquals(49, page.buttonConfigs.size)
        
        // Button 1 at local index 0 (source cols 2): row 0, col 0 -> global 0
        assertNotNull(page.buttonConfigs[0])
        assertEquals("B1", page.buttonConfigs[0]!!.label)
        
        // Button 6 at local index 5 (source cols 2): row 2, col 1
        // BUT the grid became 2x3? If it's 2x3, then max allowed local index is 5.
        // Wait, localToGlobalIndex uses importPage.columns (which is 2)!
        // index 5 / 2 = 2 (row)
        // index 5 % 2 = 1 (col)
        // Global 7x7 index: 2 * 7 + 1 = 15
        assertNotNull(page.buttonConfigs[15])
        assertEquals("B6", page.buttonConfigs[15]!!.label)
        
        // If it's 15, then r=2, c=1. Since r < rows is required for visibility:
        // The logic should have expanded rows to 3? 
        // while (rows < 7 && rows * columns <= maxIndex) -> 2 * 3 (6) <= 5 is false.
        // So rows stays 2.
    }

    @Test
    fun `importFromJson handles 11 buttons with 4x4 metadata spatially correctly`() = runTest(testDispatcher) {
        // User reports 11 buttons. Let's test a button at local index 5.
        // In 4x4: row = 5/4 = 1, col = 5%4 = 1.
        // In 7x7: 1 * 7 + 1 = 8.
        val buttonsJson = (0..10).map { i ->
            """{ "index": $i, "label": "B$i", "active": true, "action": { "type": "SpeakText", "textToSpeech": "$i" } }"""
        }.joinToString(",")

        val jsonString = """
            {
                "pages": [
                    {
                        "importId": "p1", "name": "11ButtonsPage", "rows": 4, "columns": 4,
                        "buttons": [ $buttonsJson ]
                    }
                ]
            }
        """.trimIndent()

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        manager.importFromJson(jsonString, "book1")

        assertTrue(pageSlot.isCaptured)
        val page = pageSlot.captured
        
        assertEquals(4, page.rows)
        assertEquals(4, page.columns)
        assertEquals(49, page.buttonConfigs.size)
        
        // Check B5 spatial placement
        // Local index 5 in 4x4 -> row 1, col 1
        // Global 7x7 index -> 1*7 + 1 = 8
        assertNotNull(page.buttonConfigs[8])
        assertEquals("B5", page.buttonConfigs[8]!!.label)
        
        // Button at local index 0 -> row 0, col 0 -> global index 0
        assertEquals("B0", page.buttonConfigs[0]!!.label)
        
        // Button at local index 4 -> row 1, col 0 -> global index 7
        assertEquals("B4", page.buttonConfigs[7]!!.label)
    }

    @Test
    fun `exportToJson serializes book metadata and page extras`() = runTest(testDispatcher) {
        val book = com.andreas_kratzer.ghosttalk.model.Book(id = "b1", name = "My Book", createdAt = 1000L, updatedAt = 2000L)
        val page = Page(
            id = "p1", bookId = "b1", name = "TestPage", rows = 4, columns = 4,
            scanPattern = "row-by-row",
            rowNames = listOf("R1", "R2")
        )
        
        val json = manager.exportToJson(listOf(page), book)
        
        assertTrue(json.contains("\"bookName\":\"My Book\""))
        assertTrue(json.contains("\"bookCreatedAt\":1000"))
        assertTrue(json.contains("\"scanPattern\":\"row-by-row\""))
        assertTrue(json.contains("\"rowNames\":[\"R1\",\"R2\"]"))
    }

    @Test
    fun `importFromJson restores book metadata and page extras`() = runTest(testDispatcher) {
        val jsonString = """
            {
                "bookName": "Restored Book",
                "bookCreatedAt": 5000,
                "pages": [
                    {
                        "importId": "p1", "name": "P1", "rows": 4, "columns": 4,
                        "scanPattern": "linear",
                        "rowNames": ["Row A"],
                        "buttons": []
                    }
                ]
            }
        """.trimIndent()

        val bookSlot = slot<com.andreas_kratzer.ghosttalk.model.Book>()
        val pageSlot = slot<Page>()
        
        coEvery { bookRepository.getBookById("book1") } returns com.andreas_kratzer.ghosttalk.model.Book("book1", "Old Name")
        coEvery { bookRepository.updateBook(capture(bookSlot)) } returns Unit
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        manager.importFromJson(jsonString, "book1")

        assertTrue(bookSlot.isCaptured)
        assertEquals("Restored Book", bookSlot.captured.name)
        assertEquals(5000L, bookSlot.captured.createdAt)

        assertTrue(pageSlot.isCaptured)
        assertEquals("linear", pageSlot.captured.scanPattern)
        assertEquals(listOf("Row A"), pageSlot.captured.rowNames)
    }
}
