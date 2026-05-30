package com.andreas_kratzer.ghosttalk.core.cloud.domain


import android.content.Context
import android.util.Log
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
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncUseCaseTest {

    private lateinit var useCase: CloudSyncUseCase
    private val mockContext: Context = mockk(relaxed = true)

    private val mockBookRepository: BookRepository = mockk(relaxed = true)
    private val mockImportExportManager: PageImportExportManager = mockk(relaxed = true)
    private val mockDrive: Drive = mockk(relaxed = true)
    private val mockLogger: Logger = mockk(relaxed = true)
    private val mockSyncLogProvider: SyncLogProvider = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        
        mockkConstructor(com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper::class)
        every { mockContext.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")

        val mockBook = Book(id = "test-book", name = "Test", updatedAt = System.currentTimeMillis())
        coEvery { mockBookRepository.getBookById(any()) } returns mockBook
        useCase = CloudSyncUseCase(mockContext, mockBookRepository, mockImportExportManager, mockSyncLogProvider, mockLogger)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `syncBook with BACKUP_ONLY mode uploads and overwrites remote if local is newer`() = runTest {
        // Prepare mocks for a scenario where local is newer, so BACKUP_ONLY should upload
        val bookId = "test-book"
        mockk<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>(relaxed = true)
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.zip"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis() - 100000L) // Remote is older
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        
        coEvery { mockImportExportManager.exportBookToZip(bookId, any(), any()) } returns Unit
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any()) } returns true
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().getFileMetadata(any()) } returns remoteFile

        useCase.syncBook(mockDrive, bookId, SyncMode.BACKUP_ONLY)
        advanceUntilIdle()

        // Verify that updateFile was called (overwriting remote) and downloadFile was NOT called
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile("file_1", any(), any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
    }

    @Test
    fun `syncBook with BACKUP_ONLY mode skips upload if remote is up-to-date`() = runTest {
        // Prepare mocks for a scenario where remote is newer, so BACKUP_ONLY should skip upload
        val bookId = "test-book"
        mockk<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>(relaxed = true)
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.zip"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis() + 100000L) // Remote is newer
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)

        useCase.syncBook(mockDrive, bookId, SyncMode.BACKUP_ONLY)
        advanceUntilIdle()

        // Verify that updateFile and downloadFile were NOT called
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
    }

    @Test
    fun `syncBook with RESTORE_ONLY mode downloads and overwrites local if remote is newer`() = runTest {
        // Prepare mocks for a scenario where remote is newer, so RESTORE_ONLY should download
        val bookId = "test-book"
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis() + 100000L) // Remote is newer
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            file.writeText("{\"restored\": true}")
            true
        }

        useCase.syncBook(mockDrive, bookId, SyncMode.RESTORE_ONLY)
        advanceUntilIdle()

        // Verify that downloadFile was called (overwriting local) and updateFile was NOT called
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any()) }
        // Verify import was called with restoreSyncSettings = false
        coVerify(exactly = 1) { mockImportExportManager.importFromJson(any(), bookId, restoreSyncSettings = false) }
    }

    @Test
    fun `syncBook with RESTORE_ONLY mode skips download if remote is in sync`() = runTest {
        // Prepare mocks for a scenario where remote is in sync, so RESTORE_ONLY should skip download
        val bookId = "test-book"
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis()) // Remote is exactly in sync
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)

        useCase.syncBook(mockDrive, bookId, SyncMode.RESTORE_ONLY)
        advanceUntilIdle()

        // Verify that downloadFile and updateFile were NOT called
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `syncBook with RESTORE_ONLY mode downloads and overwrites local if remote is older but not in sync`() = runTest {
        // Prepare mocks for a scenario where remote is older, so RESTORE_ONLY should still download to revert local modifications
        val bookId = "test-book"
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis() - 100000L) // Remote is older
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            file.writeText("{\"restored\": true}")
            true
        }

        useCase.syncBook(mockDrive, bookId, SyncMode.RESTORE_ONLY)
        advanceUntilIdle()

        // Verify that downloadFile was called to revert local changes, and updateFile was NOT called
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any()) }
        // Verify import was called with restoreSyncSettings = false
        coVerify(exactly = 1) { mockImportExportManager.importFromJson(any(), bookId, restoreSyncSettings = false) }
    }

    @Test
    fun `syncBook with TWO_WAY mode uploads if local is newer`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.zip"
            modifiedTime = com.google.api.client.util.DateTime(now - 10000L) // Remote is older
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        
        // Mock importExportManager to return data
        coEvery { mockImportExportManager.exportBookToZip(bookId, any(), any()) } returns Unit
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any()) } returns true

        // Ensure tempFile has a newer timestamp by mocking its lastModified if needed, 
        // but it will have 'now' roughly. Remote is 10s older.
        
        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile("file_1", any(), any(), any(), any()) }
    }

    @Test
    fun `syncBook with TWO_WAY mode downloads if remote is newer`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"

        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.zip"
            modifiedTime = com.google.api.client.util.DateTime(now + 10000L) // Remote is newer
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            file.writeText("{\"downloaded\": true}")
            true
        }
        coEvery { mockImportExportManager.importFromZip(any(), any(), any(), any(), any()) } returns Result.success(1)

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
        coVerify(exactly = 1) { mockImportExportManager.importFromZip(any(), bookId, any(), any(), any()) }
    }

    @Test
    fun `syncBook with TWO_WAY mode does nothing if synchronized within grace period`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"

        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.zip"
            modifiedTime = com.google.api.client.util.DateTime(now + 500L) // Remote is only 0.5s newer
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `syncBook returns false in RESTORE_ONLY mode if remote file is missing`() = runTest {
        val bookId = "test-book"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns emptyList()

        val result = useCase.syncBook(mockDrive, bookId, SyncMode.RESTORE_ONLY)
        advanceUntilIdle()

        assert(!result)
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

        val backups = useCase.getAvailableBackups(mockDrive)
        advanceUntilIdle()

        assertEquals(1, backups.size)
        assertEquals("Test Book", backups[0].bookName)
        assertEquals(1000L, backups[0].lastModified)
    }
}
