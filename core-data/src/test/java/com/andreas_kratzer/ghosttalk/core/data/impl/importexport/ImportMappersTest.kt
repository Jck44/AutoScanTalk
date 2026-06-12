package com.andreas_kratzer.ghosttalk.core.data.impl.importexport

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportMappersTest {

    @Test
    fun testMapImportIndexToGrid() {
        // GhostTalk uses index directly
        assertEquals(12, mapImportIndexToGrid(isGhostTalk = true, index = 12L, sourceColumns = 4))
        
        // Non-GhostTalk maps based on columns
        // index = 5, columns = 4 -> row = 5/4 = 1, col = 5%4 = 1 -> row * 7 + col = 1 * 7 + 1 = 8
        assertEquals(8, mapImportIndexToGrid(isGhostTalk = false, index = 5L, sourceColumns = 4))
        
        // index = 0, columns = 3 -> 0
        assertEquals(0, mapImportIndexToGrid(isGhostTalk = false, index = 0L, sourceColumns = 3))
    }

    @Test
    fun testAutoExpandGrid() {
        val buttons = listOf(
            ImportButton(index = 0L, label = "A"),
            ImportButton(index = 8L, label = "B")
        )
        // 1x1, button index 8. finalRows * finalCols <= 8.
        // 1x1 -> 1x2 -> 1x3 -> 1x4 -> 1x5 -> 1x6 -> 1x7 -> 2x7 -> finalRows = 2, finalCols = 7
        val expanded = autoExpandGrid(rows = 1, cols = 1, buttons = buttons)
        assertEquals(2, expanded.first)
        assertEquals(7, expanded.second)
    }

    @Test
    fun testExtractCloudBookId() {
        assertEquals("some-uuid", extractCloudBookId("some-uuid", "My Book", "file-123"))
        assertEquals("12345678-1234-1234-1234-1234567890ab", extractCloudBookId("", "My Book [12345678-1234-1234-1234-1234567890ab]", "file-123"))
        assertEquals("uuid-from-fileid", extractCloudBookId(null, "My Book", "book_uuid-from-fileid.json"))
        assertEquals("some-id", extractCloudBookId(null, "My Book", "some-id"))
    }

    @Test
    fun testBuildButtonConfigFromImport() {
        val warnings = mutableListOf<String>()
        val importBtn = ImportButton(
            id = "original-id",
            index = 0,
            label = "Hello",
            spokenText = "Talk",
            spokenTextMode = "TTS",
            audioFileName = "test.mp3",
            auditoryCueText = "Cue",
            active = true,
            playActionAsAuditoryCue = false
        )
        val config = buildButtonConfigFromImport(
            button = importBtn,
            forceRegeneration = false,
            pageRegenerated = false,
            finalAction = SpeakTextButtonAction(),
            warnings = warnings,
            contextDescription = "test context"
        )
        assertEquals("original-id", config.id)
        assertEquals("Hello", config.label)
        assertEquals("test.mp3", config.audioFileName)
        assertEquals("Cue", (config.auditoryCue as AuditoryCue.TextToSpeechCue).text)
        assertTrue(warnings.isEmpty())

        // Test with warning / invalid mode
        val importBtnInvalidMode = importBtn.copy(spokenTextMode = "INVALID")
        val configInvalid = buildButtonConfigFromImport(
            button = importBtnInvalidMode,
            forceRegeneration = false,
            pageRegenerated = false,
            finalAction = SpeakTextButtonAction(),
            warnings = warnings,
            contextDescription = "test context"
        )
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("Unbekannter spokenTextMode 'INVALID'"))
        
        // Test force regeneration
        val configRegen = buildButtonConfigFromImport(
            button = importBtn,
            forceRegeneration = true,
            pageRegenerated = false,
            finalAction = SpeakTextButtonAction(),
            warnings = warnings,
            contextDescription = "test context"
        )
        assertNotEquals("original-id", configRegen.id)
    }
}
