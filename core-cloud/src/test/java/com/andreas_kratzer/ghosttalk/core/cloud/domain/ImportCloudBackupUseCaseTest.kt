package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ImportCloudBackupUseCaseTest {

    private lateinit var useCase: ImportCloudBackupUseCase
    private lateinit var mockContext: Context
    private lateinit var mockBookRepository: BookRepository
    private lateinit var mockImportExportManager: PageImportExportManager
    private lateinit var mockSyncLogProvider: SyncLogProvider
    private lateinit var mockLogger: Logger
    private lateinit var mockSettingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
    private lateinit var spyStorageResolver: SyncStorageResolver
    private lateinit var mockDrive: Drive

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockBookRepository = mockk(relaxed = true)
        mockImportExportManager = mockk(relaxed = true)
        mockSyncLogProvider = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockSettingsRepository = mockk(relaxed = true)
        mockDrive = mockk(relaxed = true)

        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        every { mockContext.cacheDir } returns tempDir
        every { mockContext.filesDir } returns tempDir

        mockkConstructor(com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper::class)

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any(), any()) } returns "folder_1"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            file.writeText("{\"id\":\"book-1\", \"name\":\"Test Book\"}")
            true
        }

        spyStorageResolver = spyk(SyncStorageResolver(mockContext, mockSettingsRepository, mockLogger))
        useCase = ImportCloudBackupUseCase(
            context = mockContext,
            bookRepository = mockBookRepository,
            importExportManager = mockImportExportManager,
            syncLogProvider = mockSyncLogProvider,
            storageResolver = spyStorageResolver,
            logger = mockLogger
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `execute imports backup JSON successfully`() = runTest {
        coEvery { mockImportExportManager.importCloudBackup(any(), any()) } returns Result.success("book-1")
        
        val result = useCase.execute(mockDrive, "file-123", "book_1.json")
        
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockImportExportManager.importCloudBackup(any(), "file-123") }
    }
}
