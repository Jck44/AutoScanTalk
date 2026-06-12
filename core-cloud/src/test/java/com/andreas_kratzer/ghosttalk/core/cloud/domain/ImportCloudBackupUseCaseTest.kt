package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
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
    private lateinit var mockSyncAnchorStore: SyncAnchorStore

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockBookRepository = mockk(relaxed = true)
        mockImportExportManager = mockk(relaxed = true)
        mockSyncLogProvider = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockSettingsRepository = mockk(relaxed = true)
        mockDrive = mockk(relaxed = true)
        mockSyncAnchorStore = mockk(relaxed = true)

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
            logger = mockLogger,
            syncAnchorStore = mockSyncAnchorStore
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

    @Test
    fun `downloadAndImport sets anchor to struct md5 of downloaded remote json`() = runTest {
        val storageProvider = mockk<SyncStorageProvider>(relaxed = true)
        val book = Book(id = "book-1", name = "Test Book", updatedAt = 1000L)
        
        coEvery { storageProvider.downloadFile(any(), any(), any()) } answers {
            val file = secondArg<File>()
            file.writeText("{\"id\":\"book-1\", \"name\":\"Test Book\", \"versionSequence\": 5}")
            true
        }
        
        coEvery { mockImportExportManager.importFromJson(any(), any(), any()) } returns Result.success(com.andreas_kratzer.ghosttalk.core.data.export.ImportResult(1))
        
        val success = useCase.downloadAndImport(
            storageProvider = storageProvider,
            remoteFileId = "remote-1",
            fileName = "book_book-1.json",
            book = book,
            remoteLastModified = 2000L,
            onProgress = { _, _ -> }
        )
        
        assertTrue(success)
        verify(exactly = 1) { mockSyncAnchorStore.setAnchor("book-1", any()) }
    }

    @Test
    fun `downloadAndImport does not set anchor when import fails`() = runTest {
        val storageProvider = mockk<SyncStorageProvider>(relaxed = true)
        val book = Book(id = "book-1", name = "Test Book", updatedAt = 1000L)
        
        coEvery { storageProvider.downloadFile(any(), any(), any()) } answers {
            val file = secondArg<File>()
            file.writeText("{\"id\":\"book-1\", \"name\":\"Test Book\", \"versionSequence\": 5}")
            true
        }
        
        coEvery { mockImportExportManager.importFromJson(any(), any(), any()) } returns Result.failure(Exception("Import failed"))
        
        val success = useCase.downloadAndImport(
            storageProvider = storageProvider,
            remoteFileId = "remote-1",
            fileName = "book_book-1.json",
            book = book,
            remoteLastModified = 2000L,
            onProgress = { _, _ -> }
        )
        
        assertFalse(success)
        verify(exactly = 0) { mockSyncAnchorStore.setAnchor(any(), any()) }
    }
}
