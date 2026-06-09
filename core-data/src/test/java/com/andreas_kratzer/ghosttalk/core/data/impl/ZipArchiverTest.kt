package com.andreas_kratzer.ghosttalk.core.data.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class ZipArchiverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var zipArchiver: ZipArchiver

    @Before
    fun setup() {
        zipArchiver = ZipArchiver()
    }

    @Test
    fun testZipAndUnzipCycle() {
        // Prepare string content
        val stringEntries = mapOf(
            "backup.json" to "{\"key\": \"value\"}",
            "vocal_profiles.json" to "[]"
        )

        // Prepare temporary files
        val tempDir = tempFolder.newFolder("source_files")
        val file1 = File(tempDir, "file1.mp3").apply { writeText("mp3 data") }
        val file2 = File(tempDir, "file2.ogg").apply { writeText("ogg data") }

        val fileEntries = listOf(
            file1 to "tts_cache/file1.mp3",
            file2 to "audio_recordings/file2.ogg"
        )

        // Zip to output stream
        val outputStream = ByteArrayOutputStream()
        zipArchiver.zip(outputStream, stringEntries, fileEntries)

        // Target directories for unzip
        val destTtsDir = tempFolder.newFolder("dest_tts")
        val destAudioDir = tempFolder.newFolder("dest_audio")

        val targetDirs = mapOf(
            "tts_cache/" to destTtsDir,
            "audio_recordings/" to destAudioDir
        )

        val unzippedStrings = mutableMapOf<String, String>()
        val unzippedFiles = mutableMapOf<String, String>()

        val handler = object : ZipArchiver.UnzipHandler {
            override fun handleStringEntry(name: String, content: String) {
                unzippedStrings[name] = content
            }

            override fun handleFileEntry(name: String, time: Long, inputStream: java.io.InputStream, targetFile: File) {
                targetFile.parentFile?.mkdirs()
                targetFile.outputStream().use { out ->
                    inputStream.copyTo(out)
                }
                unzippedFiles[name] = targetFile.readText()
            }
        }

        // Unzip
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        zipArchiver.unzip(inputStream, targetDirs, handler)

        // Verify
        assertEquals("{\"key\": \"value\"}", unzippedStrings["backup.json"])
        assertEquals("[]", unzippedStrings["vocal_profiles.json"])
        assertEquals("mp3 data", unzippedFiles["tts_cache/file1.mp3"])
        assertEquals("ogg data", unzippedFiles["audio_recordings/file2.ogg"])

        // Check if files actually written on disk
        assertTrue(File(destTtsDir, "file1.mp3").exists())
        assertEquals("mp3 data", File(destTtsDir, "file1.mp3").readText())
        assertTrue(File(destAudioDir, "file2.ogg").exists())
        assertEquals("ogg data", File(destAudioDir, "file2.ogg").readText())
    }

    @Test
    fun testIsSafeFile() {
        val parent = File("/tmp/allowed")
        val safeFile = File("/tmp/allowed/sub/file.txt")
        val unsafeFile = File("/tmp/allowed/../forbidden/file.txt")

        assertTrue(zipArchiver.isSafeFile(parent, safeFile))
        assertFalse(zipArchiver.isSafeFile(parent, unsafeFile))
    }
}
