import com.andreas_kratzer.ghosttalk.core.pages.PageImportExportManager
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
    private val pageRepository: PageRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val templateRepository: TemplateRepository = mockk(relaxed = true) {
        coEvery { getAllTemplates() } returns flowOf(emptyList())
    }
    private val logger: Logger = TestLogger()
    private val manager = PageImportExportManager(pageRepository, settingsRepository, templateRepository, logger, testDispatcher)

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
}
