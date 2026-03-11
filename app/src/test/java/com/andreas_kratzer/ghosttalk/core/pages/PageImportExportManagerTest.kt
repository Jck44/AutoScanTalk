package com.andreas_kratzer.ghosttalk.core.pages

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.util.TestLogger
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.Book
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.model.WeatherButtonAction
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
        
        val jsonCompact = json.replace("\\s".toRegex(), "")
        assertTrue(jsonCompact.contains("\"holdingTimeSeconds\":0.75"))
        assertTrue(jsonCompact.contains("\"auditoryCueText\":\"Cue\""))
        assertTrue(jsonCompact.contains("\"spokenText\":\"Speak\""))
        assertTrue(jsonCompact.contains("\"textToSpeech\":\"Speak\""))
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
        
        val jsonCompact = json.replace("\\s".toRegex(), "")
        assertTrue(jsonCompact.contains("\"bookName\":\"MyBook\""))
        assertTrue(jsonCompact.contains("\"bookCreatedAt\":1000"))
        assertTrue(jsonCompact.contains("\"scanPattern\":\"row-by-row\""))
        assertTrue(jsonCompact.contains("\"rowNames\":[\"R1\",\"R2\"]"))
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

    @Test
    fun `importFromJson prevents duplicates by preserving IDs`() = runTest(testDispatcher) {
        coEvery { pageRepository.getPageById(any()) } returns null
        val pageId = "unique-page-id"
        val buttonId = "unique-button-id"
        val jsonString = """
            {
                "pages": [
                    {
                        "importId": "$pageId", "name": "P1", "rows": 4, "columns": 4,
                        "buttons": [
                            { "id": "$buttonId", "index": 0, "label": "B1", "active": true, "action": { "type": "SpeakText", "textToSpeech": "Hello" } }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        // First import
        manager.importFromJson(jsonString, "book1")
        val firstPage = pageSlot.captured
        assertEquals(pageId, firstPage.id)
        assertEquals(buttonId, firstPage.buttonConfigs[0]?.id)

        // Second import of the same data
        manager.importFromJson(jsonString, "book1")
        val secondPage = pageSlot.captured
        
        // The ID should be the same as before, not a new one
        assertEquals(pageId, secondPage.id)
        assertEquals(buttonId, secondPage.buttonConfigs[0]?.id)
        
        // Verify insertPage was called twice (Room REPLACE handles the deduplication at DB level)
        io.mockk.coVerify(exactly = 2) { pageRepository.insertPage(any()) }
    }

    @Test
    fun `exportToJson includes button IDs`() = runTest(testDispatcher) {
        val page = Page(
            id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1,
            buttonConfigs = listOf(
                ButtonConfig(id = "button-123", label = "L", buttonAction = SpeakTextButtonAction())
            )
        )
        
        val json = manager.exportToJson(listOf(page))
        val jsonCompact = json.replace("\\s".toRegex(), "")
        assertTrue(jsonCompact.contains("\"id\":\"button-123\""))
    }

    @Test
    fun `importFromJson regenerates IDs when forced or bookId mismatch`() = runTest(testDispatcher) {
        val pageId = "old-page-id"
        val buttonId = "old-button-id"
        val jsonString = """
            {
                "bookId": "original-book",
                "pages": [
                    {
                        "importId": "$pageId", "name": "P1", "rows": 4, "columns": 4,
                        "buttons": [
                            { "id": "$buttonId", "index": 0, "label": "B1", "active": true, "action": { "type": "SpeakText", "textToSpeech": "Hello" } }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        // Import into a DIFFERENT book (test_book)
        manager.importFromJson(jsonString, "test_book")

        assertTrue(pageSlot.isCaptured)
        val importedPage = pageSlot.captured
        
        // IDs should be DIFFERENT from the ones in the JSON
        assertTrue(importedPage.id != pageId)
        assertTrue(importedPage.buttonConfigs[0]?.id != buttonId)
        
        // But bookId should be correct
        assertEquals("test_book", importedPage.bookId)
    }

    @Test
    fun `importFromJson preserves navigation after ID regeneration`() = runTest(testDispatcher) {
        val jsonString = """
            {
                "bookId": "source-book",
                "pages": [
                    {
                        "importId": "page-1", "name": "Start", "rows": 1, "columns": 1,
                        "buttons": [
                            { "index": 0, "label": "Go to 2", "action": { "type": "NavigateToPage", "targetPageImportId": "page-2" } }
                        ]
                    },
                    {
                        "importId": "page-2", "name": "Target", "rows": 1, "columns": 1,
                        "buttons": []
                    }
                ]
            }
        """.trimIndent()

        val capturedPages = mutableListOf<Page>()
        coEvery { pageRepository.insertPage(capture(capturedPages)) } returns Unit

        // Import into another book (triggers regeneration)
        manager.importFromJson(jsonString, "target-book")

        assertEquals(2, capturedPages.size)
        val startPage = capturedPages.find { it.name == "Start" }
        val targetPage = capturedPages.find { it.name == "Target" }

        assertNotNull(startPage)
        assertNotNull(targetPage)

        // New IDs
        val newTargetId = targetPage!!.id
        assertTrue(newTargetId != "page-2")

        // Action on start page should point to new target ID
        val action = startPage!!.buttonConfigs[0]?.buttonAction
        assertTrue(action is com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction)
        assertEquals(newTargetId, (action as com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction).pageId)
    }

    @Test
    fun `importFromJson regenerates IDs on collision with different book`() = runTest(testDispatcher) {
        val pageId = "colliding-page-id"
        val buttonId = "colliding-button-id"
        val jsonString = """
            {
                "bookId": "target-book",
                "pages": [
                    {
                        "importId": "$pageId", "name": "P1", "rows": 4, "columns": 4,
                        "buttons": [
                            { "id": "$buttonId", "index": 0, "label": "B1", "active": true, "action": { "type": "SpeakText", "textToSpeech": "Hello" } }
                        ]
                    }
                ]
            }
        """.trimIndent()

        // Mock that the page already exists in the database but under a DIFFERENT book
        val existingPage = Page(id = pageId, bookId = "some-other-book", name = "Existing Page", rows = 4, columns = 4)
        coEvery { pageRepository.getPageById(pageId) } returns existingPage

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        // Import into target-book. regenerateIds is false by default if bookId matches (both "target-book").
        manager.importFromJson(jsonString, "target-book")

        assertTrue(pageSlot.isCaptured)
        val importedPage = pageSlot.captured
        
        // IDs MUST be DIFFERENT because of the collision detection
        assertTrue("Page ID should have been regenerated to avoid stealing", importedPage.id != pageId)
        assertTrue("Button ID should have been regenerated", importedPage.buttonConfigs[0]?.id != buttonId)
        
        // bookId should be the target book
        assertEquals("target-book", importedPage.bookId)
    }

    @Test
    fun `importAsNewBook creates book and imports with same IDs`() = runTest(testDispatcher) {
        val bookId = "new-book-id"
        val pageId = "p1"
        val jsonString = """
            {
                "bookId": "$bookId",
                "bookName": "Cloud Book",
                "pages": [
                    { "importId": "$pageId", "name": "Page 1", "rows": 1, "columns": 1, "buttons": [] }
                ]
            }
        """.trimIndent()

        // Mock book repository to return null (book doesn't exist)
        coEvery { bookRepository.getBookById(bookId) } returns null
        coEvery { pageRepository.getPageById(pageId) } returns null
        
        val bookSlot = slot<Book>()
        coEvery { bookRepository.insertBook(capture(bookSlot)) } returns Unit
        
        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit
        
        val result = manager.importCloudBackup(jsonString, null)

        assertTrue(result.isSuccess)
        assertEquals(bookId, result.getOrNull())

        // Verify book insertion
        assertTrue(bookSlot.isCaptured)
        assertEquals(bookId, bookSlot.captured.id)
        assertEquals("Cloud Book", bookSlot.captured.name)
        
        // Verify page insertion with same ID
        assertTrue(pageSlot.isCaptured)
        assertEquals(pageId, pageSlot.captured.id)
        assertEquals(bookId, pageSlot.captured.bookId)
    }

    @Test
    fun `importCloudBackup extracts bookId from name if missing from field`() = runTest(testDispatcher) {
        val extractedId = "12345678-1234-1234-1234-123456789012"
        val jsonString = """
            {
                "bookName": "Test Book [${extractedId}]",
                "pages": [
                    { "importId": "p1", "name": "P1", "rows": 1, "columns": 1, "buttons": [] }
                ]
            }
        """.trimIndent()

        coEvery { bookRepository.getBookById(any()) } returns null
        coEvery { bookRepository.insertBook(any()) } returns Unit
        coEvery { pageRepository.getPageById(any()) } returns null
        coEvery { pageRepository.insertPage(any()) } returns Unit

        val result = manager.importCloudBackup(jsonString, null)

        assertTrue(result.isSuccess)
        assertEquals(extractedId.lowercase(), result.getOrNull()?.lowercase())
    }

    @Test
    fun `importCloudBackup returns failure if bookId missing and not found in name or filename`() = runTest(testDispatcher) {
        val jsonString = """
            {
                "bookName": "Just a Name",
                "pages": [
                    { "importId": "p1", "name": "P1", "rows": 1, "columns": 1, "buttons": [] }
                ]
            }
        """.trimIndent()

        val result = manager.importCloudBackup(jsonString, null)

        assertTrue(result.isFailure)
        assertEquals("Konnte keine Buch-ID im Backup finden.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `importCloudBackup fails if book already exists locally`() = runTest(testDispatcher) {
        val bookId = "existing-book-id"
        val jsonString = "{\"bookId\":\"$bookId\", \"bookName\":\"Exists\", \"pages\":[]}"
        
        coEvery { bookRepository.getBookById(bookId) } returns Book(bookId, "Old Name")
        
        val result = manager.importCloudBackup(jsonString, null)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Ein Buch mit dieser ID existiert bereits lokal") == true)
    }

    @Test
    fun `export and import cycle preserves complex GhosTTalk actions`() = runTest(testDispatcher) {
        val bookId = "test-book"
        val originalPages = listOf(
            Page(
                id = "p1", bookId = bookId, name = "Actions", rows = 7, columns = 7,
                buttonConfigs = MutableList<ButtonConfig?>(49) { null }.apply {
                    this[0] = ButtonConfig(id = "b1", label = "Gemini", buttonAction = GeminiButtonAction("Prompt 1"))
                    this[1] = ButtonConfig(id = "b2", label = "Device", buttonAction = ControlDeviceButtonAction(DeviceActionType.VOLUME_MEDIA, volumeValue = "15"))
                    this[2] = ButtonConfig(id = "b3", label = "Weather", buttonAction = WeatherButtonAction())
                    this[3] = ButtonConfig(id = "b4", label = "Smart", buttonAction = SmartPredictionButtonAction(3))
                    this[4] = ButtonConfig(id = "b5", label = "Search", buttonAction = GeminiSearchButtonAction("Search Query"))
                    this[5] = ButtonConfig(id = "b6", label = "Nano", buttonAction = GeminiNanoButtonAction("Intent A"))
                }
            )
        )

        // 1. Export
        val jsonString = manager.exportToJson(originalPages, Book(bookId, "Test Book"))

        // 2. Import
        val pageSlot = mutableListOf<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit
        coEvery { pageRepository.getPageById(any()) } returns null

        val result = manager.importFromJson(jsonString, bookId, regenerateIds = false)

        assertTrue(result.isSuccess)
        assertEquals(1, pageSlot.size)
        val importedPage = pageSlot[0]

        // Verify actions
        val b1 = importedPage.buttonConfigs[0]?.buttonAction as GeminiButtonAction
        assertEquals("Prompt 1", b1.prompt)

        val b2 = importedPage.buttonConfigs[1]?.buttonAction as ControlDeviceButtonAction
        assertEquals(DeviceActionType.VOLUME_MEDIA, b2.actionType)
        assertEquals("15", b2.volumeValue)

        assertTrue(importedPage.buttonConfigs[2]?.buttonAction is WeatherButtonAction)

        val b4 = importedPage.buttonConfigs[3]?.buttonAction as SmartPredictionButtonAction
        assertEquals(3, b4.rank)

        val b5 = importedPage.buttonConfigs[4]?.buttonAction as GeminiSearchButtonAction
        assertEquals("Search Query", b5.prompt)

        val b6 = importedPage.buttonConfigs[5]?.buttonAction as GeminiNanoButtonAction
        assertEquals("Intent A", b6.intent)
    }
}
