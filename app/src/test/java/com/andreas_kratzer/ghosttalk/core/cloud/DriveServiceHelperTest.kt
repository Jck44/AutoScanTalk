package com.andreas_kratzer.ghosttalk.core.cloud

import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.FileList
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DriveServiceHelperTest {

    private val drive: Drive = mockk()
    private val helper = DriveServiceHelper(drive)

    @org.junit.Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
    }

    @org.junit.After
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
}
