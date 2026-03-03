package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
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
            ButtonConfig("b1", "Hello", auditoryCue = null, buttonAction = SpeakTextButtonAction("Hi"), isActive = true)
        )

        val json = converters.fromButtonConfigList(configs)
        assertNotNull(json)

        val restored = converters.toButtonConfigList(json!!)
        assertNotNull(restored)
        assertEquals(1, restored!!.size)
        assertEquals("Hello", restored[0]!!.label)
        assertTrue(restored[0]!!.buttonAction is SpeakTextButtonAction)
        assertEquals("Hi", (restored[0]!!.buttonAction as SpeakTextButtonAction).textToSpeech)
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
    fun `ButtonConfig roundtrip with AuditoryCue`() {
        val configs = listOf(
            ButtonConfig("b1", "Label", auditoryCue = AuditoryCue.TextToSpeechCue("Cue text"), buttonAction = SpeakTextButtonAction("Speak"), isActive = true)
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
            ButtonConfig("b1", "A", auditoryCue = null, buttonAction = SpeakTextButtonAction("1")),
            null,
            ButtonConfig("b3", "C", auditoryCue = null, buttonAction = SpeakTextButtonAction("3"))
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
            ButtonConfig("b1", "Active", auditoryCue = null, buttonAction = SpeakTextButtonAction("1"), isActive = true),
            ButtonConfig("b2", "Inactive", auditoryCue = null, buttonAction = SpeakTextButtonAction("2"), isActive = false)
        )

        val json = converters.fromButtonConfigList(configs)
        val restored = converters.toButtonConfigList(json!!)

        assertTrue(restored!![0]!!.isActive)
        assertEquals(false, restored[1]!!.isActive)
    }
}
