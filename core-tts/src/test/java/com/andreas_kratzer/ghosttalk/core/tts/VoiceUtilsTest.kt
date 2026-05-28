package com.andreas_kratzer.ghosttalk.core.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceUtilsTest {

    @Test
    fun `formatVoiceName handles standard network voice`() {
        val input = "de-de-x-deb-network"
        val expected = "Deb"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }

    @Test
    fun `formatVoiceName handles local voice`() {
        val input = "en-us-x-sfg-local"
        val expected = "Sfg"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }

    @Test
    fun `formatVoiceName handles multiple voice segments`() {
        val input = "en-gb-x-fis-multi-segment"
        val expected = "Fis Multi Segment"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }

    @Test
    fun `formatVoiceName handles standalone x segment`() {
        val input = "en-us-x-sfg-x-variant"
        val expected = "Sfg Variant"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }

    @Test
    fun `formatVoiceName handles mixed casing and dashes`() {
        val input = "DE-DE-X-GS-NETWORK"
        val expected = "Gs"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }

    @Test
    fun `formatVoiceName returns Stimme for empty or blank input`() {
        assertEquals("Stimme", VoiceUtils.formatVoiceName(""))
        assertEquals("Stimme", VoiceUtils.formatVoiceName("   "))
        assertEquals("Stimme", VoiceUtils.formatVoiceName("de-de-"))
    }

    @Test
    fun `formatVoiceName handles names without x-prefix`() {
        val input = "com.google.android.tts:de-de"
        // Regex "de-de-" will remove the prefix if it's at the start. 
        // Here it's not at the start.
        // PrefixRegex is "^[a-z]{2}-[a-z]{2}-"
        // "com.google.android.tts:de-de" -> no change by prefixRegex
        // -> "com.google.android.tts:de de"
        val result = VoiceUtils.formatVoiceName(input)
        assert(result.isNotEmpty())
    }
}
