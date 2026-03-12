package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `ButtonConfig roundtrip with SpeakTextAction`() {
        val configs = listOf(
            ButtonConfig("b1", "Hello", spokenText = "Hi", auditoryCue = null, buttonAction = SpeakTextButtonAction(), isActive = true)
        )

        val json = converters.fromButtonConfigList(configs)
        assertNotNull(json)

        val restored = converters.toButtonConfigList(json!!)
        assertNotNull(restored)
        assertEquals(1, restored!!.size)
        assertEquals("Hello", restored[0]!!.label)
        assertTrue(restored[0]!!.buttonAction is SpeakTextButtonAction)
        assertEquals("Hi", restored[0]!!.spokenText)
    }

    @Test
    fun `ButtonConfig roundtrip with NavigateAction`() {
        val configs = listOf(
            ButtonConfig("b1", "Go", auditoryCue = null, buttonAction = NavigateToPageButtonAction("page2"), isActive = true)
        )

        val json = converters.fromButtonConfigList(configs)
        val restored = converters.toButtonConfigList(json!!)

        assertTrue(restored!![0]!!.buttonAction is NavigateToPageButtonAction)
        assertEquals("page2", (restored[0]!!.buttonAction as NavigateToPageButtonAction).pageId)
    }

    @Test
    fun `ButtonConfig roundtrip with GeminiAction`() {
        val configs = listOf(
            ButtonConfig("b1", "Ask", auditoryCue = null, buttonAction = GeminiButtonAction("What?"), isActive = true)
        )

        val json = converters.fromButtonConfigList(configs)
        val restored = converters.toButtonConfigList(json!!)

        assertTrue(restored!![0]!!.buttonAction is GeminiButtonAction)
        assertEquals("What?", (restored[0]!!.buttonAction as GeminiButtonAction).prompt)
    }

    @Test
    fun `ButtonConfig roundtrip with FrequentAction`() {
        val configs = listOf(
            ButtonConfig("b1", "Rank 1", auditoryCue = null, buttonAction = com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction(1), isActive = true)
        )

        val json = converters.fromButtonConfigList(configs)
        val restored = converters.toButtonConfigList(json!!)

        assertTrue(restored!![0]!!.buttonAction is com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction)
        assertEquals(1, (restored[0]!!.buttonAction as com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction).rank)
    }

    @Test
    fun `ButtonConfig roundtrip with AuditoryCue`() {
        val configs = listOf(
            ButtonConfig("b1", "Label", spokenText = "Speak", auditoryCue = AuditoryCue.TextToSpeechCue("Cue text"), buttonAction = SpeakTextButtonAction(), isActive = true)
        )

        val json = converters.fromButtonConfigList(configs)
        val restored = converters.toButtonConfigList(json!!)

        val cue = restored!![0]!!.auditoryCue
        assertNotNull(cue)
        assertTrue(cue is AuditoryCue.TextToSpeechCue)
        assertEquals("Cue text", (cue as AuditoryCue.TextToSpeechCue).text)
    }

    @Test
    fun `ButtonConfig list with nulls roundtrips correctly`() {
        val configs: List<ButtonConfig?> = listOf(
            ButtonConfig("b1", "A", spokenText = "1", auditoryCue = null, buttonAction = SpeakTextButtonAction()),
            null,
            ButtonConfig("b3", "C", spokenText = "3", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        )

        val json = converters.fromButtonConfigList(configs)
        val restored = converters.toButtonConfigList(json!!)

        assertEquals(3, restored!!.size)
        assertNotNull(restored[0])
        assertNull(restored[1])
        assertNotNull(restored[2])
    }

    @Test
    fun `null list returns null`() {
        assertNull(converters.fromButtonConfigList(null))
        assertNull(converters.toButtonConfigList(null))
    }

    @Test
    fun `StringList roundtrip`() {
        val strings = listOf("Row 1", "Row 2", "Row 3")

        val json = converters.fromStringList(strings)
        val restored = converters.toStringList(json!!)

        assertEquals(3, restored!!.size)
        assertEquals("Row 1", restored[0])
    }

    @Test
    fun `isActive property roundtrips correctly`() {
        val configs = listOf(
            ButtonConfig("b1", "Active", spokenText = "1", auditoryCue = null, buttonAction = SpeakTextButtonAction(), isActive = true),
            ButtonConfig("b2", "Inactive", spokenText = "2", auditoryCue = null, buttonAction = SpeakTextButtonAction(), isActive = false)
        )

        val json = converters.fromButtonConfigList(configs)
        val restored = converters.toButtonConfigList(json!!)

        assertTrue(restored!![0]!!.isActive)
        assertEquals(false, restored[1]!!.isActive)
    }
}
