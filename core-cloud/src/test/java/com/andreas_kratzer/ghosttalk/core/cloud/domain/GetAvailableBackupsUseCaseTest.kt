package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetAvailableBackupsUseCaseTest {

    private lateinit var useCase: GetAvailableBackupsUseCase
    private lateinit var mockContext: Context
    private lateinit var mockImportExportManager: PageImportExportManager
    private lateinit var mockDrive: Drive
    private lateinit var mockLogger: Logger
    private lateinit var mockSettingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
    private lateinit var spyStorageResolver: SyncStorageResolver

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockImportExportManager = mockk(relaxed = true)
        mockDrive = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockSettingsRepository = mockk(relaxed = true)

        mockkConstructor(com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper::class)

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any(), any()) } returns null
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().createFolder(any(), any()) } returns "folder_1"

        spyStorageResolver = spyk(SyncStorageResolver(mockContext, mockSettingsRepository, mockLogger))
        useCase = GetAvailableBackupsUseCase(mockContext, spyStorageResolver, mockImportExportManager, mockLogger)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getAvailableBackups parses metadata correctly`() = runTest {
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile1 = com.google.api.services.drive.model.File().apply {
            id = "f1"
            name = "book_1.zip"
            description = "Test Book"
            modifiedTime = com.google.api.client.util.DateTime(1000L)
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile1)

        val backups = useCase.execute(mockDrive)

        assertEquals(1, backups.size)
        assertEquals("Test Book", backups[0].bookName)
        assertEquals(1000L, backups[0].lastModified)
    }

    @Test
    fun `getAvailableBackups reads bookName from custom properties and avoids download`() = runTest {
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile1 = com.google.api.services.drive.model.File().apply {
            id = "f1"
            name = "book_1.json"
            properties = mapOf("book_name" to "Properties Book Name")
            modifiedTime = com.google.api.client.util.DateTime(1000L)
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile1)

        val backups = useCase.execute(mockDrive)

        assertEquals(1, backups.size)
        assertEquals("Properties Book Name", backups[0].bookName)
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
    }
}
