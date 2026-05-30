package com.andreas_kratzer.ghosttalk.core.cloud

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.FileList
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream

class DriveServiceHelperTest {

    private val drive: Drive = mockk()
    private val helper = DriveServiceHelper(drive)

    @Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    @Test
    fun `searchFiles uses correct contains query`() = runTest {
        val queryText = "mybook"
        val expectedQuery = "name contains 'mybook' and trashed = false"
        
        val filesMock: Drive.Files = mockk()
        val listMock: Drive.Files.List = mockk(relaxed = true)
        val fileList = FileList().apply { files = emptyList() }

        every { drive.files() } returns filesMock
        every { filesMock.list() } returns listMock
        every { listMock.setQ(any()) } returns listMock
        every { listMock.setFields(any()) } returns listMock
        every { listMock.execute() } returns fileList

        helper.searchFiles(queryText)

        verify { listMock.setQ(expectedQuery) }
    }

    @Test
    fun `downloadFile succeeds on first attempt`() = runTest {
        val fileId = "testFileId"
        val targetFile = File.createTempFile("test_download", ".tmp")
        targetFile.deleteOnExit()

        val filesMock: Drive.Files = mockk()
        val getMock: Drive.Files.Get = mockk(relaxed = true)
        val downloaderMock: com.google.api.client.googleapis.media.MediaHttpDownloader = mockk(relaxed = true)

        every { drive.files() } returns filesMock
        every { filesMock.get(fileId) } returns getMock
        every { getMock.mediaHttpDownloader } returns downloaderMock
        every { getMock.executeMediaAndDownloadTo(any()) } answers {
            val os = firstArg<OutputStream>()
            os.write("success data".toByteArray())
        }

        var progressCalled = false
        val result = helper.downloadFile(fileId, targetFile) { progress ->
            progressCalled = true
        }

        assertTrue(result)
        assertTrue(targetFile.exists())
        assertEquals("success data", targetFile.readText())
        
        targetFile.delete()
    }

    @Test
    fun `downloadFile retries on IOException and succeeds on second attempt`() = runTest {
        val fileId = "testFileId"
        val targetFile = File.createTempFile("test_download", ".tmp")
        targetFile.deleteOnExit()

        val filesMock: Drive.Files = mockk()
        val getMock: Drive.Files.Get = mockk(relaxed = true)
        val downloaderMock: com.google.api.client.googleapis.media.MediaHttpDownloader = mockk(relaxed = true)

        every { drive.files() } returns filesMock
        every { filesMock.get(fileId) } returns getMock
        every { getMock.mediaHttpDownloader } returns downloaderMock

        var attempt = 0
        every { getMock.executeMediaAndDownloadTo(any()) } answers {
            attempt++
            if (attempt == 1) {
                throw IOException("Transient network error")
            } else {
                val os = firstArg<OutputStream>()
                os.write("success data on retry".toByteArray())
            }
        }

        val result = helper.downloadFile(fileId, targetFile)

        assertTrue(result)
        assertTrue(targetFile.exists())
        assertEquals("success data on retry", targetFile.readText())
        assertEquals(2, attempt)

        targetFile.delete()
    }

    @Test
    fun `downloadFile fails and cleans up targetFile after max retries`() = runTest {
        val fileId = "testFileId"
        val targetFile = File.createTempFile("test_download", ".tmp")
        targetFile.deleteOnExit()

        // Pre-create file to ensure it gets cleaned up
        targetFile.writeText("partial/old data")

        val filesMock: Drive.Files = mockk()
        val getMock: Drive.Files.Get = mockk(relaxed = true)
        val downloaderMock: com.google.api.client.googleapis.media.MediaHttpDownloader = mockk(relaxed = true)

        every { drive.files() } returns filesMock
        every { filesMock.get(fileId) } returns getMock
        every { getMock.mediaHttpDownloader } returns downloaderMock
        every { getMock.executeMediaAndDownloadTo(any()) } throws IOException("Persistent connection failure")

        val result = helper.downloadFile(fileId, targetFile)

        assertFalse(result)
        assertFalse(targetFile.exists())
    }

    @Test
    fun `downloadFile rethrows UserRecoverableAuthIOException immediately`() = runTest {
        val fileId = "testFileId"
        val targetFile = File.createTempFile("test_download", ".tmp")
        targetFile.deleteOnExit()

        val filesMock: Drive.Files = mockk()
        val getMock: Drive.Files.Get = mockk(relaxed = true)
        val downloaderMock: com.google.api.client.googleapis.media.MediaHttpDownloader = mockk(relaxed = true)

        every { drive.files() } returns filesMock
        every { filesMock.get(fileId) } returns getMock
        every { getMock.mediaHttpDownloader } returns downloaderMock
        
        val authException = mockk<UserRecoverableAuthIOException>(relaxed = true)
        every { getMock.executeMediaAndDownloadTo(any()) } throws authException

        try {
            helper.downloadFile(fileId, targetFile)
            fail("Expected UserRecoverableAuthIOException to be thrown")
        } catch (e: UserRecoverableAuthIOException) {
            // expected
        }

        verify(exactly = 1) { getMock.executeMediaAndDownloadTo(any()) }
        
        targetFile.delete()
    }

    @Test
    fun `downloadFile aborts immediately on client error`() = runTest {
        val fileId = "testFileId"
        val targetFile = File.createTempFile("test_download", ".tmp")
        targetFile.deleteOnExit()

        val filesMock: Drive.Files = mockk()
        val getMock: Drive.Files.Get = mockk(relaxed = true)
        val downloaderMock: com.google.api.client.googleapis.media.MediaHttpDownloader = mockk(relaxed = true)

        every { drive.files() } returns filesMock
        every { filesMock.get(fileId) } returns getMock
        every { getMock.mediaHttpDownloader } returns downloaderMock

        val mockException = mockk<GoogleJsonResponseException>()
        every { mockException.statusCode } returns 404
        every { mockException.message } returns "Not Found"
        every { mockException.details } returns null

        every { getMock.executeMediaAndDownloadTo(any()) } throws mockException

        val result = helper.downloadFile(fileId, targetFile)

        assertFalse(result)
        verify(exactly = 1) { getMock.executeMediaAndDownloadTo(any()) }
        
        targetFile.delete()
    }

    @Test
    fun `downloadFile retries on 500 server error and succeeds`() = runTest {
        val fileId = "testFileId"
        val targetFile = File.createTempFile("test_download", ".tmp")
        targetFile.deleteOnExit()

        val filesMock: Drive.Files = mockk()
        val getMock: Drive.Files.Get = mockk(relaxed = true)
        val downloaderMock: com.google.api.client.googleapis.media.MediaHttpDownloader = mockk(relaxed = true)

        every { drive.files() } returns filesMock
        every { filesMock.get(fileId) } returns getMock
        every { getMock.mediaHttpDownloader } returns downloaderMock

        val mockException = mockk<GoogleJsonResponseException>()
        every { mockException.statusCode } returns 500
        every { mockException.message } returns "Internal Server Error"
        every { mockException.details } returns null

        var attempt = 0
        every { getMock.executeMediaAndDownloadTo(any()) } answers {
            attempt++
            if (attempt == 1) {
                throw mockException
            } else {
                val os = firstArg<OutputStream>()
                os.write("server success".toByteArray())
            }
        }

        val result = helper.downloadFile(fileId, targetFile)

        assertTrue(result)
        assertEquals("server success", targetFile.readText())
        assertEquals(2, attempt)

        targetFile.delete()
    }
}
