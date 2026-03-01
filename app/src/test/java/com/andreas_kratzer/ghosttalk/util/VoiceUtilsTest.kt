package com.andreas_kratzer.ghosttalk.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceUtilsTest {

    @Test
    fun testFormatVoiceName_withPrefixAndNetwork() {
        val input = "de-de-x-deb-network"
        val expected = "Deb"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }

    @Test
    fun testFormatVoiceName_withPrefixAndLocal() {
        val input = "en-us-x-sfg-local"
        val expected = "Sfg"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }

    @Test
    fun testFormatVoiceName_withMultipleDashes() {
        val input = "en-gb-x-rjg-network"
        val expected = "Rjg"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }

    @Test
    fun testFormatVoiceName_withSpaces() {
        val input = "de-de-x-something-else"
        val expected = "Something Else"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }

    @Test
    fun testFormatVoiceName_empty() {
        val input = ""
        val expected = "Stimme"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }

    @Test
    fun testFormatVoiceName_simpleName() {
        val input = "Anna"
        val expected = "Anna"
        assertEquals(expected, VoiceUtils.formatVoiceName(input))
    }
}
