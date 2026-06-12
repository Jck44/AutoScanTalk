package com.andreas_kratzer.ghosttalk.core.tts

import android.content.Context
import android.util.Base64
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AudioCacheRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: AudioCacheRepository
    private lateinit var filesDir: File
    private lateinit var elevenLabsDir: File

    @Before
    fun setUp() {
        context = mockk()
        filesDir = tempFolder.newFolder("files")
        elevenLabsDir = File(filesDir, "elevenlabs").apply { mkdirs() }
        every { context.filesDir } returns filesDir
        repository = AudioCacheRepository(context)

        // Mock Android Base64 to use java.util.Base64 for JVM unit test compatibility
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any()) } answers {
            val input = firstArg<String>()
            val encoder = java.util.Base64.getUrlDecoder()
            encoder.decode(input)
        }
        every { Base64.encodeToString(any(), any()) } answers {
            val input = firstArg<ByteArray>()
            val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
            encoder.encodeToString(input)
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCacheFileRoundtripWithSpecialCharsInBase64() = runTest {
        // Base64 encoding of "Hallo Welt?" contains a dash/minus or other characters that URL-safe encoding produces.
        val text = "Hallo Welt?"
        val base64Text = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
        
        val newFormatFile = File(elevenLabsDir, "tts_eleven#${base64Text}~12345678#voice_abc#model_xyz#de.mp3")
        newFormatFile.createNewFile()

        val results = repository.getCachedAudios()
        assertEquals(1, results.size)
        assertEquals("$text...", results[0].text)
        assertEquals("voice_abc", results[0].voiceId)
        assertEquals("model_xyz", results[0].modelId)
    }

    @Test
    fun testCacheFileRoundtripWithLegacyDashDelimitedFiles() = runTest {
        // Legacy file format: tts_eleven#<base64>-<hash>#voice#model#lang.mp3 where base64 length is 100
        val base64Text = "A".repeat(100)
        
        val legacyFile = File(elevenLabsDir, "tts_eleven#${base64Text}-12345678#voice_abc#model_xyz#en.mp3")
        legacyFile.createNewFile()

        val results = repository.getCachedAudios()
        assertEquals(1, results.size)
        val expectedDecodedText = String(java.util.Base64.getUrlDecoder().decode(base64Text), Charsets.UTF_8)
        assertEquals("$expectedDecodedText...", results[0].text)
    }

    @Test
    fun testCacheFileWithoutTruncationTildeOrDash() = runTest {
        val text = "Short"
        val base64Text = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
        
        val plainFile = File(elevenLabsDir, "tts_eleven#${base64Text}#voice_abc#model_xyz#en.mp3")
        plainFile.createNewFile()

        val results = repository.getCachedAudios()
        assertEquals(1, results.size)
        assertEquals(text, results[0].text)
    }
}
