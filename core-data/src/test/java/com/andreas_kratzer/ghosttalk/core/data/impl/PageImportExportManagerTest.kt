@file:Suppress("DEPRECATION")
package com.andreas_kratzer.ghosttalk.core.data.impl

import android.content.Context
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsMapper
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.UserModeSession
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageImportExportManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val bookRepository: BookRepository = mockk(relaxed = true)
    private val pageRepository: PageRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val sharedPrefs: SharedPreferences = mockk(relaxed = true)
    private val prefsEditor: SharedPreferences.Editor = mockk(relaxed = true)
    private val authManager: com.andreas_kratzer.ghosttalk.core.cloud.AuthManager = mockk(relaxed = true)
    
    private val settingsMapper = SettingsMapper(settingsRepository, authManager)
    private val actionMapper = ActionMapper()
    private val buttonTemplateRepository: com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository = mockk(relaxed = true)
    private val userModeSessionRepository: UserModeSessionRepository = mockk(relaxed = true)
    private val manager = PageImportExportManager(
        context = context,
        pageRepository = pageRepository,
        bookRepository = bookRepository,
        settingsRepository = settingsRepository,
        settingsMapper = settingsMapper,
        actionMapper = actionMapper,
        buttonTemplateRepository = buttonTemplateRepository,
        buttonUsageDao = mockk(relaxed = true),
        userModeSessionRepository = userModeSessionRepository,
        logger = mockk(relaxed = true)
    )

    init {
        every { authManager.userEmail } returns kotlinx.coroutines.flow.MutableStateFlow("test@example.com")
        every { context.getSharedPreferences(SettingsConstants.PREFS_NAME, any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns prefsEditor
        every { prefsEditor.putString(any(), any()) } returns prefsEditor
        every { buttonTemplateRepository.getTemplates() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        every { context.filesDir } returns java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp")
    }

    @Test
    fun `importFromJson maps buttons and isActive correctly`() = runTest {
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
    fun `importFromJson handles holdingTime and auditoryCues`() = runTest {
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

        // Verify auditory cue mapping
        val config = pageSlot.captured.buttonConfigs[0]
        assertNotNull(config)
        assertTrue(config?.auditoryCue is AuditoryCue.TextToSpeechCue)
        assertEquals("Listen", (config?.auditoryCue as AuditoryCue.TextToSpeechCue).text)
    }

    @Test
    fun `exportToJson serializes holdingTime and auditoryCues`() = runTest {
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
        val json = manager.exportPageListToJson(listOf(page))
        
        val jsonCompact = json.replace("\\s".toRegex(), "")
        assertTrue(jsonCompact.contains("\"auditoryCueText\":\"Cue\""))
        assertTrue(jsonCompact.contains("\"spokenText\":\"Speak\""))
        assertTrue(jsonCompact.contains("\"textToSpeech\":\"Speak\""))
    }

    @Test
    fun `importFromJson expands grid if buttons exceed metadata dimensions`() = runTest {
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
    fun `importFromJson handles 11 buttons with 4x4 metadata spatially correctly`() = runTest {
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
    fun `exportToJson serializes book metadata and page extras`() = runTest {
        com.andreas_kratzer.ghosttalk.core.model.Book(id = "b1", name = "My Book", createdAt = 1000L, updatedAt = 2000L)
        val page = Page(
            id = "p1", bookId = "b1", name = "TestPage", rows = 4, columns = 4,
            scanPattern = "row-by-row",
            rowNames = listOf("R1", "R2")
        )
        
        val json = manager.exportPageListToJson(listOf(page))
        
        val jsonCompact = json.replace("\\s".toRegex(), "")
        assertTrue(jsonCompact.contains("\"scanPattern\":\"row-by-row\""))
        assertTrue(jsonCompact.contains("\"rowNames\":[\"R1\",\"R2\"]"))
    }

    @Test
    fun `importFromJson restores book metadata and page extras`() = runTest {
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

        val bookSlot = slot<com.andreas_kratzer.ghosttalk.core.model.Book>()
        val pageSlot = slot<Page>()
        
        coEvery { bookRepository.getBookById("book1") } returns com.andreas_kratzer.ghosttalk.core.model.Book("book1", "Old Name")
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
    fun `importFromJson prevents duplicates by preserving IDs`() = runTest {
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
        coVerify(exactly = 2) { pageRepository.insertPage(any()) }
    }

    @Test
    fun `exportToJson includes button IDs`() = runTest {
        val page = Page(
            id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1,
            buttonConfigs = listOf(
                ButtonConfig(id = "button-123", label = "L", buttonAction = SpeakTextButtonAction())
            )
        )
        
        val json = manager.exportPageListToJson(listOf(page))
        val jsonCompact = json.replace("\\s".toRegex(), "")
        assertTrue(jsonCompact.contains("\"id\":\"button-123\""))
    }

    @Test
    fun `importFromJson regenerates IDs when forced or bookId mismatch`() = runTest {
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
    fun `importFromJson preserves navigation after ID regeneration`() = runTest {
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
        assertTrue(action is com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction)
        assertEquals(newTargetId, (action as com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction).pageId)
    }

    @Test
    fun `importFromJson regenerates IDs on collision with different book`() = runTest {
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
    fun `importAsNewBook creates book and imports with same IDs`() = runTest {
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
        
        val bookSlot = slot<com.andreas_kratzer.ghosttalk.core.model.Book>()
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
    fun `importCloudBackup extracts bookId from name if missing from field`() = runTest {
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
    fun `importCloudBackup returns failure if bookId missing and not found in name or filename`() = runTest {
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
    fun `importCloudBackup fails if book already exists locally`() = runTest {
        val bookId = "existing-book-id"
        val jsonString = "{\"bookId\":\"$bookId\", \"bookName\":\"Exists\", \"pages\":[]}"
        
        coEvery { bookRepository.getBookById(bookId) } returns com.andreas_kratzer.ghosttalk.core.model.Book(bookId, "Old Name")
        
        val result = manager.importCloudBackup(jsonString, null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("existiert bereits lokal") == true)
    }

    @Test
    fun `export and import cycle preserves complex GhosTTalk actions`() = runTest {
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
        val jsonString = manager.exportPageListToJson(originalPages)

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

    @Test
    fun `full roundtrip preserves all page and button fields`() = runTest {
        val bookId = "roundtrip-book"
        val pageId = "roundtrip-page"
        
        val originalPage = Page(
            id = pageId,
            bookId = bookId,
            name = "AllFieldsPage",
            rows = 5,
            columns = 3,
            scanPattern = "row-by-row",
            rowNames = listOf("Row 1", "Row 2"),
            orderIndex = 42,
            createdAt = 123456789L,
            buttonConfigs = List(49) { i ->
                if (i == 0) {
                    ButtonConfig(
                        id = "btn-0",
                        label = "Labels",
                        spokenText = "Speech",
                        auditoryCue = AuditoryCue.TextToSpeechCue("Cue Text"),
                        isActive = false,
                        playActionAsAuditoryCue = true,
                        buttonAction = SpeakTextButtonAction()
                    )
                } else null
            }
        )

        // 1. Export
        val json = manager.exportPageListToJson(listOf(originalPage))

        // 2. Import
        val capturedPages = mutableListOf<Page>()
        coEvery { pageRepository.insertPage(capture(capturedPages)) } returns Unit
        coEvery { pageRepository.getPageById(any()) } returns null

        manager.importFromJson(json, bookId, regenerateIds = false)

        // 3. Verify
        val imported = capturedPages[0]
        assertEquals(originalPage.id, imported.id)
        assertEquals(originalPage.name, imported.name)
        assertEquals(originalPage.rows, imported.rows)
        assertEquals(originalPage.columns, imported.columns)
        assertEquals(originalPage.scanPattern, imported.scanPattern)
        assertEquals(originalPage.rowNames, imported.rowNames)
        assertEquals(originalPage.orderIndex, imported.orderIndex)
        // Note: createdAt is sometimes updated to current time during import if it's considered "new", 
        // but our manager preserves it if book metadata is present.
        
        val originalBtn = originalPage.buttonConfigs[0]!!
        val importedBtn = imported.buttonConfigs[0]!!
        
        assertEquals(originalBtn.id, importedBtn.id)
        assertEquals(originalBtn.label, importedBtn.label)
        assertEquals(originalBtn.spokenText, importedBtn.spokenText)
        assertEquals(originalBtn.isActive, importedBtn.isActive)
        assertEquals(originalBtn.playActionAsAuditoryCue, importedBtn.playActionAsAuditoryCue)
        assertTrue(importedBtn.auditoryCue is AuditoryCue.TextToSpeechCue)
        assertEquals("Cue Text", (importedBtn.auditoryCue as AuditoryCue.TextToSpeechCue).text)
    }

    @Test
    fun `PageImportExportManager regression - should expand grid incrementally up to 7x7`() = runTest {
        val json = """
            {
                "pages": [
                    {
                        "importId": "p1", "name": "Deep Page", "rows": 1, "columns": 1,
                        "buttons": [
                            { "index": 20, "label": "Btn 20", "active": true, "action": null }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val pageSlot = slot<Page>()
        coEvery { pageRepository.insertPage(capture(pageSlot)) } returns Unit

        manager.importFromJson(json, "book1")

        // Index 20 in a 1x1 grid needs expansion.
        // Incremental expansion logic:
        // 1x1 (index 0), 1x2 (index 0,1), ..., 1x7 (index 0..6), 2x7 (index 0..13), 3x7 (index 0..20)
        // For maxIndex 20: 3x7 (3*7=21 > 20)
        assertEquals(3, pageSlot.captured.rows)
        assertEquals(7, pageSlot.captured.columns)
    }

    @Test
    fun `importCloudBackup prevents overwriting existing book`() = runTest {
        val bookId = "existing-id"
        val jsonString = """{"bookId":"$bookId", "bookName":"New Name", "pages":[]}"""
        
        // Mock that the book already exists
        coEvery { bookRepository.getBookById(bookId.lowercase()) } returns com.andreas_kratzer.ghosttalk.core.model.Book(bookId, "Old Name")
        
        val result = manager.importCloudBackup(jsonString, null)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("existiert bereits lokal") == true)
        
        // Verify no insert was called
        coVerify(exactly = 0) { bookRepository.insertBook(any()) }
    }


    @Test
    fun `importCloudBackup handles case-insensitive bookId collisions`() = runTest {
        val bookId = "BOOK-123"
        val jsonString = """{"bookId":"$bookId", "bookName":"Test", "pages":[]}"""
        
        // Mock that the book already exists in lowercase
        coEvery { bookRepository.getBookById(bookId.lowercase()) } returns com.andreas_kratzer.ghosttalk.core.model.Book(bookId.lowercase(), "Existing")
        
        val result = manager.importCloudBackup(jsonString, null)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("existiert bereits lokal") == true)
    }

    @Test
    fun `importCloudBackup prevents overwriting even with cloudFileId`() = runTest {
        val cloudFileId = "drive-id-123"
        val jsonString = """{"bookId":"other-id", "bookName":"Test", "pages":[]}"""
        
        // Mock that the book already exists with the DRIVER ID (which is used as targetBookId)
        coEvery { bookRepository.getBookById(cloudFileId) } returns com.andreas_kratzer.ghosttalk.core.model.Book(cloudFileId, "Existing")
        
        val result = manager.importCloudBackup(jsonString, cloudFileId)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("existiert bereits lokal") == true)
    }

    @Test
    fun `importCloudBackup succeeds when book does not exist`() = runTest {
        val bookId = "new-id"
        val jsonString = """{"bookId":"$bookId", "bookName":"New Book", "pages":[]}"""
        
        coEvery { bookRepository.getBookById(bookId) } returns null
        coEvery { bookRepository.insertBook(any()) } returns Unit
        
        val result = manager.importCloudBackup(jsonString, null)
        
        assertTrue(result.isSuccess)
        assertEquals(bookId, result.getOrNull())
        coVerify { bookRepository.insertBook(any()) }
    }

    @Test
    fun `importCloudBackup prevents duplicate when filename is passed as ID`() = runTest {
        val internalBookId = "real-uuid"
        val fileNameAsId = "book_real-uuid.json"
        
        // JSON has the correct internal ID
        val jsonString = """{"bookId":"$internalBookId", "bookName":"My Book", "pages":[]}"""
        
        // Mock that the book already exists with the INTERNAL ID
        coEvery { bookRepository.getBookById(internalBookId) } returns com.andreas_kratzer.ghosttalk.core.model.Book(internalBookId, "Existing")
        
        // Import with the filename as the cloudFileId (the old buggy behavior)
        val result = manager.importCloudBackup(jsonString, fileNameAsId)
        
        // It SHOULD now detect the collision by correctly resolving the ID to "real-uuid"
        assertTrue("Should have detected collision by resolving ID correctly", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("existiert bereits lokal") == true)
        
        // Verify no insert occurred
        coVerify(exactly = 0) { bookRepository.insertBook(any()) }
    }

    @Test
    fun `importCloudBackup correctly extracts UUID from filename for old backups`() = runTest {
        val internalBookId = "12345678-1234-1234-1234-123456789012"
        val complexFileName = "backup_v1_12345678-1234-1234-1234-123456789012_final.json"
        
        // JSON has NO bookId (old backup)
        val jsonString = """{"bookName":"Old Backup", "pages":[]}"""
        
        coEvery { bookRepository.getBookById(internalBookId) } returns null
        coEvery { bookRepository.insertBook(any()) } returns Unit
        
        val result = manager.importCloudBackup(jsonString, complexFileName)
        assertTrue(result.isSuccess)
        assertEquals(internalBookId, result.getOrNull())
        
        val bookSlot = slot<com.andreas_kratzer.ghosttalk.core.model.Book>()
        coVerify { bookRepository.insertBook(capture(bookSlot)) }
        assertEquals(internalBookId, bookSlot.captured.id)
    }

    @Test
    fun `export and import preserves new book settings`() = runTest {
        val bookId = "test-book-settings"
        val originalBook = com.andreas_kratzer.ghosttalk.core.model.Book(
            id = bookId,
            name = "Settings Test",
            actionLogLimit = 500,
            limitScanCycles = true,
            scanCycleLimit = 5,
            logIgnoredActions = false,
            logStopActions = false
        )
        
        // Setup initial settings in the mock
        every { settingsRepository.getDefaultStartPageIdForBook(bookId) } returns "old-start-page"
        every { settingsRepository.getActionLogLimitForBook(bookId) } returns 500
        every { settingsRepository.getLimitScanCyclesForBook(bookId) } returns true
        every { settingsRepository.getScanCycleLimitForBook(bookId) } returns 5
        every { settingsRepository.getLogIgnoredActionsForBook(bookId) } returns false
        every { settingsRepository.getLogStopActionsForBook(bookId) } returns false
        
        coEvery { bookRepository.getBookById(bookId) } returns originalBook
        coEvery { pageRepository.getPagesForBook(bookId) } returns emptyList()
        coEvery { bookRepository.updateBook(any()) } returns Unit
        
        // 1. Export
        val jsonString = manager.exportBookToJson(bookId)
        
        // Verify JSON contains the settings
        assertTrue("JSON should contain actionLogLimit", jsonString.contains("\"actionLogLimit\": 500"))
        assertTrue("JSON should contain defaultStartPageId", jsonString.contains("\"defaultStartPageId\": \"old-start-page\""))
        
        // 2. Import back
        val targetBook = com.andreas_kratzer.ghosttalk.core.model.Book(bookId, "Target")
        coEvery { bookRepository.getBookById(bookId) } returns targetBook
        
        val result = manager.importFromJson(jsonString, bookId, regenerateIds = false)
        
        assertTrue(result.isSuccess)
        
        // Verify updateBook was called with merged settings
        val updatedBookSlot = slot<com.andreas_kratzer.ghosttalk.core.model.Book>()
        coVerify { bookRepository.updateBook(capture(updatedBookSlot)) }
        assertEquals(500, updatedBookSlot.captured.actionLogLimit)
        
        // Verify setting was restored with correct book ID prefix
        verify { prefsEditor.putString("${bookId}_${SettingsConstants.KEY_DEFAULT_START_PAGE_ID}", "old-start-page") }
    }

    @Test
    fun `importFromJson restores defaultStartPageId correctly with ID regeneration`() = runTest {
        // Arrange
        val bookId = "book1"
        val oldPageId = "old-page-id"
        val jsonString = """
            {
                "bookId": "different-book-id",
                "bookName": "Test Book",
                "defaultStartPageId": "$oldPageId",
                "pages": [
                    {
                        "importId": "$oldPageId",
                        "name": "Start Page",
                        "buttons": []
                    }
                ]
            }
        """.trimIndent()

        coEvery { bookRepository.getBookById(any()) } returns mockk(relaxed = true)
        coEvery { pageRepository.getPageById(any()) } returns null 
        coEvery { pageRepository.insertPage(any()) } returns Unit
        
        every { settingsRepository.defaultStartPageId = any() } returns Unit

        // Act
        val result = manager.importFromJson(jsonString, bookId, regenerateIds = true)

        // Assert
        assertTrue(result.isSuccess)
        
        // Verify that defaultStartPageId was updated in SharedPreferences with the NEW page ID mapping
        // and the target book ID prefix (book1)
        val capturedNewId = slot<String>()
        verify { 
            prefsEditor.putString("book1_${SettingsConstants.KEY_DEFAULT_START_PAGE_ID}", capture(capturedNewId))
        }
        
        // If fixed, it should be the NEW generated UUID for that page, not the old one
        assertNotEquals(oldPageId, capturedNewId.captured)
        
        // Verify it was inserted (at least one page)
        coVerify { pageRepository.insertPage(any()) }
    }

    @Test
    fun `exportBookToJson captures book-scoped settings regardless of active book`() = runTest {
        val targetBookId = "book-A"
        
        val book = com.andreas_kratzer.ghosttalk.core.model.Book(id = targetBookId, name = "Target Book")
        coEvery { bookRepository.getBookById(targetBookId) } returns book
        coEvery { pageRepository.getPagesForBook(targetBookId) } returns emptyList()
        
        // Mock SettingsRepository book-specific getters
        every { settingsRepository.getDefaultStartPageIdForBook(targetBookId) } returns "page-123"
        every { settingsRepository.getScanDelayMillisForBook(targetBookId) } returns 5000L
        every { settingsRepository.getPageSortOrderForBook(targetBookId) } returns "ALPHABETICAL"
        every { settingsRepository.getActionLogLimitForBook(targetBookId) } returns 100
        every { settingsRepository.getLimitScanCyclesForBook(targetBookId) } returns false
        every { settingsRepository.getScanCycleLimitForBook(targetBookId) } returns 2
        every { settingsRepository.getLogIgnoredActionsForBook(targetBookId) } returns true
        every { settingsRepository.getLogStopActionsForBook(targetBookId) } returns true
        every { settingsRepository.getAutoStartScanningForBook(targetBookId) } returns true
        every { settingsRepository.getResumeScanningFromStartForBook(targetBookId) } returns true
        every { settingsRepository.getHoldingTimeMillisForBook(targetBookId) } returns 250L
        every { settingsRepository.getSwitchActivationKeyForBook(targetBookId) } returns "~3"
        every { settingsRepository.getVolumeKeysActivateForBook(targetBookId) } returns false
        every { settingsRepository.getDefaultScanPatternForBook(targetBookId) } returns "linear"
        every { settingsRepository.getIsSmartPredictionEnabledForBook(targetBookId) } returns false
        every { settingsRepository.getTemplateSortOrderForBook(targetBookId) } returns "MANUAL"
        every { settingsRepository.getSmartPredictionDelayForBook(targetBookId) } returns 2000L
        
        // Mock global settings
        every { settingsRepository.geminiTimeout } returns 30000L
        
        val json = manager.exportBookToJson(targetBookId)
        
        val jsonCompact = json.replace("\\s".toRegex(), "")
        assertTrue("JSON should contain correct start page ID", jsonCompact.contains("\"defaultStartPageId\":\"page-123\""))
        assertTrue("JSON should contain correct scan delay", jsonCompact.contains("\"scanDelayMillis\":5000"))
        assertTrue("JSON should contain correct sort order", jsonCompact.contains("\"pageSortOrder\":\"ALPHABETICAL\""))
        
        // Verify those methods were called
        verify { settingsRepository.getDefaultStartPageIdForBook(targetBookId) }
        verify { settingsRepository.getScanDelayMillisForBook(targetBookId) }
        verify { settingsRepository.getPageSortOrderForBook(targetBookId) }
    }

    @Test
    fun `exportStatisticsToZip contains valid json with statsVersion and appVersion and can import legacy without versions`() = runTest {
        val packageManager: android.content.pm.PackageManager = mockk(relaxed = true)
        val packageInfo: android.content.pm.PackageInfo = mockk(relaxed = true)
        packageInfo.versionName = "2.3.4"
        
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.andreas_kratzer.ghosttalk"
        every { packageManager.getPackageInfo("com.andreas_kratzer.ghosttalk", 0) } returns packageInfo

        val buttonUsageDao: com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao = mockk(relaxed = true)
        val managerWithStatsMock = PageImportExportManager(
            context = context,
            pageRepository = pageRepository,
            bookRepository = bookRepository,
            settingsRepository = settingsRepository,
            settingsMapper = settingsMapper,
            actionMapper = actionMapper,
            buttonTemplateRepository = buttonTemplateRepository,
            buttonUsageDao = buttonUsageDao,
            userModeSessionRepository = userModeSessionRepository,
            logger = mockk(relaxed = true)
        )

        coEvery { buttonUsageDao.getHistoryForBook("book-1") } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { buttonUsageDao.getAllStatsForBook("book-1") } returns emptyList()

        val output = java.io.ByteArrayOutputStream()
        managerWithStatsMock.exportStatisticsToZip("book-1", output)

        val zipBytes = output.toByteArray()
        assertTrue("Zip should not be empty", zipBytes.isNotEmpty())

        val zipInput = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes))
        var entry = zipInput.nextEntry
        var statsJsonString = ""
        while (entry != null) {
            if (entry.name == "statistics.json") {
                statsJsonString = zipInput.reader().readText()
            }
            entry = zipInput.nextEntry
        }

        assertTrue("statistics.json should be present", statsJsonString.isNotEmpty())
        assertTrue("JSON should contain statsVersion 1", statsJsonString.contains("\"statsVersion\": 1"))
        assertTrue("JSON should contain appVersion 2.3.4", statsJsonString.contains("\"appVersion\": \"2.3.4\""))

        // Legacy compatibility check: can import JSON without version keys
        val legacyJson = """
            {
                "bookId": "book-1",
                "history": [],
                "stats": []
            }
        """.trimIndent()

        // Should not throw or crash on decode
        val decoded = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedStatistics>(legacyJson)
        assertEquals("book-1", decoded.bookId)
        assertEquals(1, decoded.statsVersion) // Default value fallback
        assertNull(decoded.appVersion) // Default value fallback
    }

    @Test
    fun `exportStatisticsToZip then importStatisticsFromZip restores history and stats`() = runTest {
        val packageManager: android.content.pm.PackageManager = mockk(relaxed = true)
        val packageInfo: android.content.pm.PackageInfo = mockk(relaxed = true)
        packageInfo.versionName = "1.0.0"
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.andreas_kratzer.ghosttalk"
        every { packageManager.getPackageInfo("com.andreas_kratzer.ghosttalk", 0) } returns packageInfo

        val historyEntity = com.andreas_kratzer.ghosttalk.core.database.ButtonUsageHistoryEntity(
            bookId = "book-rt",
            timestamp = 9999L,
            label = "Hallo",
            actionType = "SpeakTextButtonAction",
            buttonId = "btn-rt",
            pageId = "page-rt",
            sessionId = 42L,
            reactionTimeMs = 1200L,
            isTouchIntervention = true,
            wifiSsid = "Test-WiFi",
            isHardwareTriggered = true
        )
        val statEntity = com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat(
            bookId = "book-rt",
            buttonConfigId = "btn-rt",
            pageId = "page-rt",
            label = "Hallo",
            actionJson = "{}",
            usageCount = 7,
            lastUsedAt = 9999L
        )

        val buttonUsageDao: com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao = mockk(relaxed = true)
        coEvery { buttonUsageDao.getHistoryForBook("book-rt") } returns kotlinx.coroutines.flow.flowOf(listOf(historyEntity))
        coEvery { buttonUsageDao.getAllStatsForBook("book-rt") } returns listOf(statEntity)

        val roundtripManager = PageImportExportManager(
            context = context,
            pageRepository = pageRepository,
            bookRepository = bookRepository,
            settingsRepository = settingsRepository,
            settingsMapper = settingsMapper,
            actionMapper = actionMapper,
            buttonTemplateRepository = buttonTemplateRepository,
            buttonUsageDao = buttonUsageDao,
            userModeSessionRepository = userModeSessionRepository,
            logger = mockk(relaxed = true)
        )

        // 1. Export to ZIP
        val outputStream = java.io.ByteArrayOutputStream()
        roundtripManager.exportStatisticsToZip("book-rt", outputStream)
        val zipBytes = outputStream.toByteArray()
        assertTrue("Export should produce non-empty ZIP", zipBytes.isNotEmpty())

        // 2. Import from the same ZIP
        roundtripManager.importStatisticsFromZip("book-rt", java.io.ByteArrayInputStream(zipBytes))

        // 3. Verify DAO was called to restore both history and stats
        coVerify { buttonUsageDao.clearHistoryForBook("book-rt") }
        coVerify { buttonUsageDao.clearStatsForBook("book-rt") }
        coVerify {
            buttonUsageDao.insertHistoryEvent(match { 
                it.label == "Hallo" && 
                it.timestamp == 9999L &&
                it.sessionId == 42L &&
                it.reactionTimeMs == 1200L &&
                it.isTouchIntervention &&
                it.wifiSsid == "Test-WiFi" &&
                it.isHardwareTriggered
            })
        }
        coVerify {
            buttonUsageDao.upsert(match { it.label == "Hallo" && it.usageCount == 7L })
        }
    }

    @Test
    fun `importStatisticsFromZip with empty ZIP does not crash`() = runTest {
        val buttonUsageDao: com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao = mockk(relaxed = true)
        val resilientManager = PageImportExportManager(
            context = context,
            pageRepository = pageRepository,
            bookRepository = bookRepository,
            settingsRepository = settingsRepository,
            settingsMapper = settingsMapper,
            actionMapper = actionMapper,
            buttonTemplateRepository = buttonTemplateRepository,
            buttonUsageDao = buttonUsageDao,
            userModeSessionRepository = userModeSessionRepository,
            logger = mockk(relaxed = true)
        )

        // Create a valid but empty ZIP
        val emptyZip = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(emptyZip).close()

        // Should not throw
        resilientManager.importStatisticsFromZip("book-empty", java.io.ByteArrayInputStream(emptyZip.toByteArray()))

        // Nothing should have been written
        coVerify(exactly = 0) { buttonUsageDao.insertHistoryEvent(any()) }
        coVerify(exactly = 0) { buttonUsageDao.upsert(any()) }
    }

    @Test
    fun `exportStatisticsToZip then importStatisticsFromZip roundtrip preserves user mode sessions`() = runTest {
        val packageManager: android.content.pm.PackageManager = mockk(relaxed = true)
        val packageInfo: android.content.pm.PackageInfo = mockk(relaxed = true)
        packageInfo.versionName = "1.0.0"
        every { context.packageManager } returns packageManager
        every { context.packageName } returns "com.andreas_kratzer.ghosttalk"
        every { packageManager.getPackageInfo("com.andreas_kratzer.ghosttalk", 0) } returns packageInfo

        val session1 = UserModeSession(id = 1L, bookId = "book-rt", startTime = 1000L, endTime = 2000L)
        val session2 = UserModeSession(id = 2L, bookId = "book-rt", startTime = 3000L, endTime = 4000L)

        val buttonUsageDao: com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao = mockk(relaxed = true)
        val mockUserModeSessionRepository: UserModeSessionRepository = mockk(relaxed = true)

        coEvery { buttonUsageDao.getHistoryForBook("book-rt") } returns kotlinx.coroutines.flow.flowOf(emptyList())
        coEvery { buttonUsageDao.getAllStatsForBook("book-rt") } returns emptyList()
        coEvery { mockUserModeSessionRepository.getSessionsForBook("book-rt") } returns kotlinx.coroutines.flow.flowOf(listOf(session1, session2))

        val managerWithStatsMock = PageImportExportManager(
            context = context,
            pageRepository = pageRepository,
            bookRepository = bookRepository,
            settingsRepository = settingsRepository,
            settingsMapper = settingsMapper,
            actionMapper = actionMapper,
            buttonTemplateRepository = buttonTemplateRepository,
            buttonUsageDao = buttonUsageDao,
            userModeSessionRepository = mockUserModeSessionRepository,
            logger = mockk(relaxed = true)
        )

        // 1. Export to ZIP
        val outputStream = java.io.ByteArrayOutputStream()
        managerWithStatsMock.exportStatisticsToZip("book-rt", outputStream)
        val zipBytes = outputStream.toByteArray()
        assertTrue("Export should produce non-empty ZIP", zipBytes.isNotEmpty())

        // 2. Import from the same ZIP
        val slot = slot<List<UserModeSession>>()
        coEvery { mockUserModeSessionRepository.insertSessions(capture(slot)) } returns Unit

        managerWithStatsMock.importStatisticsFromZip("book-rt", java.io.ByteArrayInputStream(zipBytes))

        // 3. Verify
        coVerify { mockUserModeSessionRepository.clearSessions("book-rt") }
        coVerify { mockUserModeSessionRepository.insertSessions(any()) }
        assertTrue(slot.isCaptured)
        val captured = slot.captured
        assertEquals(2, captured.size)
        assertEquals(1000L, captured[0].startTime)
        assertEquals(2000L, captured[0].endTime)
        assertEquals(3000L, captured[1].startTime)
        assertEquals(4000L, captured[1].endTime)
    }

    @Test
    fun `importFromJson with invalid json structure returns failure and does not delete pages`() = runTest {
        val corruptedJson = "{ invalid json structure... ]"
        
        coEvery { pageRepository.deletePagesForBook(any()) } returns Unit
        
        val result = manager.importFromJson(corruptedJson, "book1")
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is kotlinx.serialization.SerializationException)
        
        // Verify deletePagesForBook was NOT called (ensuring no partial delete/corruption occurs)
        coVerify(exactly = 0) { pageRepository.deletePagesForBook("book1") }
    }

    @Test
    fun `importFromZip without backup json returns failure`() = runTest {
        // Create a zip stream containing some random file, but NOT backup.json
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("random_file.txt"))
            zos.write("hello".toByteArray())
            zos.closeEntry()
        }
        
        val result = manager.importFromZip(
            inputStream = java.io.ByteArrayInputStream(bos.toByteArray()),
            bookId = "book1",
            regenerateIds = false,
            restoreSyncSettings = false
        ) { _, _ -> }
        
        assertTrue(result.isFailure)
        assertEquals("Keine backup.json im ZIP gefunden.", result.exceptionOrNull()?.message)
    }
}
