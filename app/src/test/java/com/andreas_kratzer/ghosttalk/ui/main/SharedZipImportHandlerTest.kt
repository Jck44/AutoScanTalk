package com.andreas_kratzer.ghosttalk.ui.main

import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SharedZipImportHandlerTest {

    @Test
    fun testDetectZipType_withBackupJson_returnsBook() {
        val importExportManager = mockk<PageImportExportProvider>()
        val handler = SharedZipImportHandler(importExportManager)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("backup.json"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
        }

        val bais = ByteArrayInputStream(baos.toByteArray())
        val result = handler.detectZipType(bais)
        assertEquals(ZipType.BOOK, result)
    }

    @Test
    fun testDetectZipType_withTtsCache_returnsTtsCache() {
        val importExportManager = mockk<PageImportExportProvider>()
        val handler = SharedZipImportHandler(importExportManager)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("tts_cache/voice_hello.mp3"))
            zos.write("audio_data".toByteArray())
            zos.closeEntry()
        }

        val bais = ByteArrayInputStream(baos.toByteArray())
        val result = handler.detectZipType(bais)
        assertEquals(ZipType.TTS_CACHE, result)
    }

    @Test
    fun testDetectZipType_withInvalidZip_returnsInvalid() {
        val importExportManager = mockk<PageImportExportProvider>()
        val handler = SharedZipImportHandler(importExportManager)

        val bais = ByteArrayInputStream("not_a_zip".toByteArray())
        val result = handler.detectZipType(bais)
        assertEquals(ZipType.INVALID, result)
    }
}
