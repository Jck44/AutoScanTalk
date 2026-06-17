package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
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
        ),
        Page(
            id = "page3",
            bookId = "book1",
            name = "Erweiterte Suche",
            orderIndex = 3,
            buttonConfigs = listOf(
                ButtonConfig(
                    label = "AI Button",
                    spokenText = "Sag etwas Tolles",
                    isActive = true,
                    buttonAction = GeminiButtonAction(prompt = "Erzeuge ein Gedicht über Pfannkuchen")
                ),
                ButtonConfig(
                    label = "TTS Cue Button",
                    auditoryCue = AuditoryCue.TextToSpeechCue(text = "Vorlese-Hinweis für Suppe"),
                    isActive = true,
                    buttonAction = SpeakTextButtonAction()
                ),
                ButtonConfig(
                    label = "Device Control Button",
                    isActive = true,
                    buttonAction = ControlDeviceButtonAction(
                        actionType = DeviceActionType.SEND_MESSAGE,
                        messageText = "Ich bin auf dem Weg"
                    )
                ),
                ButtonConfig(
                    label = "Audio File Button",
                    audioFileName = "excluded_secret_sound.wav",
                    spokenText = "Spiele Ton",
                    isActive = true,
                    buttonAction = SpeakTextButtonAction()
                ),
                ButtonConfig(
                    label = "Inactive Match Button",
                    spokenText = "Geheimnis",
                    isActive = false,
                    buttonAction = SpeakTextButtonAction()
                )
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

    @Test
    fun `matches spokenText case insensitively`() {
        val results = useCase.execute(testPages, "tolles")
        assertEquals(1, results.size)
        assertEquals("page3", results[0].pageId)
        assertEquals(0, results[0].buttonHits[0].index)
    }

    @Test
    fun `matches text-to-speech cue case insensitively`() {
        val results = useCase.execute(testPages, "vorlese-hinweis")
        assertEquals(1, results.size)
        assertEquals("page3", results[0].pageId)
        assertEquals(1, results[0].buttonHits[0].index)
    }

    @Test
    fun `matches gemini prompt case insensitively`() {
        val results = useCase.execute(testPages, "pfannkuchen")
        assertEquals(1, results.size)
        assertEquals("page3", results[0].pageId)
        assertEquals(0, results[0].buttonHits[0].index)
    }

    @Test
    fun `matches control device message text case insensitively`() {
        val results = useCase.execute(testPages, "auf dem weg")
        assertEquals(1, results.size)
        assertEquals("page3", results[0].pageId)
        assertEquals(2, results[0].buttonHits[0].index)
    }

    @Test
    fun `ignores matches in excluded fields like audioFileName`() {
        val results = useCase.execute(testPages, "excluded_secret")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `ignores matches in inactive buttons for spokenText`() {
        val results = useCase.execute(testPages, "geheimnis")
        assertTrue(results.isEmpty())
    }
}
