package com.andreas_kratzer.ghosttalk.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.PageImportExportManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.google.api.services.drive.Drive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import android.util.Log
import io.mockk.mockkStatic

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncUseCaseTest {

    private lateinit var useCase: CloudSyncUseCase
    private val mockContext: Context = mockk(relaxed = true)
    private val mockSettingsRepository: SettingsRepository = mockk(relaxed = true)
    private val mockImportExportManager: PageImportExportManager = mockk(relaxed = true)
    private val mockDrive: Drive = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        
        mockkConstructor(com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper::class)
        every { mockContext.cacheDir } returns File(System.getProperty("java.io.tmpdir"))
        every { mockSettingsRepository.isCloudSyncEnabled } returns true
        useCase = CloudSyncUseCase(mockContext, mockSettingsRepository, mockImportExportManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `syncBook with BACKUP_ONLY mode always uploads and overwrites remote`() = runTest {
        // Prepare mocks for a scenario where remote is newer, but BACKUP_ONLY should still overwrite it
        val bookId = "test-book"
        val mockDriveHelper = mockk<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>(relaxed = true)
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis() + 100000L) // Remote is newer
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns "{}"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any()) } returns true

        useCase.syncBook(mockDrive, bookId, SyncMode.BACKUP_ONLY)
        advanceUntilIdle()

        // Verify that updateFile was called (overwriting remote) and downloadFile was NOT called
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile("file_1", any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any()) }
    }

    @Test
    fun `syncBook with RESTORE_ONLY mode always downloads and overwrites local`() = runTest {
        // Prepare mocks for a scenario where local is newer, but RESTORE_ONLY should still download
        val bookId = "test-book"
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis() - 100000L) // Remote is older
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any()) } answers {
            val file = args[1] as File
            file.writeText("{\"restored\": true}")
            true
        }

        useCase.syncBook(mockDrive, bookId, SyncMode.RESTORE_ONLY)
        advanceUntilIdle()

        // Verify that downloadFile was called (overwriting local) and updateFile was NOT called
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any()) }
        // Verify import was called
        coVerify(exactly = 1) { mockImportExportManager.importBookFromJson(any(), bookId) }
    }
}
