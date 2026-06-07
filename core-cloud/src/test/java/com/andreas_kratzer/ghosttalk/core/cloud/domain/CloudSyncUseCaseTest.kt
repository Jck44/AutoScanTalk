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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    private val mockSettingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        
        mockkConstructor(com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper::class)
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        every { mockContext.cacheDir } returns tempDir
        every { mockContext.filesDir } returns tempDir

        val mockBook = Book(id = "test-book", name = "Test", updatedAt = System.currentTimeMillis())
        coEvery { mockBookRepository.getBookById(any()) } returns mockBook
        
        every { mockSettingsRepository.googleDriveFolderId } returns null
        every { mockSettingsRepository.syncModeBook } returns "TWO_WAY"
        every { mockSettingsRepository.syncModeTts } returns "TWO_WAY"
        every { mockSettingsRepository.syncModeStats } returns "RESTORE_ONLY"
        
        useCase = CloudSyncUseCase(mockContext, mockBookRepository, mockImportExportManager, mockSettingsRepository, mockSyncLogProvider, mockLogger)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `syncBook with BACKUP_ONLY mode uploads and overwrites remote if local is newer`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()
        
        val mockBook = Book(id = bookId, name = "Test", updatedAt = now, versionSequence = 2L)
        coEvery { mockBookRepository.getBookById(bookId) } returns mockBook
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(now - 100000L) // Remote is older
            version = 1L
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            file.writeText("{\"versionSequence\": 1, \"bookUpdatedAt\": ${now - 100000L}}")
            true
        }
        
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns "{\"versionSequence\": 2, \"bookUpdatedAt\": $now}"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) } returns true
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().getFileMetadata(any()) } returns remoteFile

        useCase.syncBook(mockDrive, bookId, SyncMode.BACKUP_ONLY)
        advanceUntilIdle()

        // Verify that uploadWithOptimisticLock was called and downloadFile was called exactly once for sequence check
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock("file_1", any(), "application/json", 1L, any()) }
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
    }

    @Test
    fun `syncBook with BACKUP_ONLY mode skips upload if remote is up-to-date`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()
        
        val mockBook = Book(id = bookId, name = "Test", updatedAt = now, versionSequence = 1L)
        coEvery { mockBookRepository.getBookById(bookId) } returns mockBook
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(now + 100000L) // Remote is newer
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            file.writeText("{\"versionSequence\": 2, \"bookUpdatedAt\": ${now + 100000L}}")
            true
        }

        useCase.syncBook(mockDrive, bookId, SyncMode.BACKUP_ONLY)
        advanceUntilIdle()

        // Verify that uploadWithOptimisticLock was NOT called, and downloadFile was called exactly once
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
    }

    @Test
    fun `syncBook with RESTORE_ONLY mode downloads and overwrites local if remote is newer`() = runTest {
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
            file.writeText("{\"versionSequence\": 2, \"bookUpdatedAt\": ${System.currentTimeMillis() + 100000L}}")
            true
        }

        useCase.syncBook(mockDrive, bookId, SyncMode.RESTORE_ONLY)
        advanceUntilIdle()

        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { mockImportExportManager.importFromJson(any(), bookId, restoreSyncSettings = false) }
    }

    @Test
    fun `syncBook with RESTORE_ONLY mode skips download if remote is in sync`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(now)
            md5Checksum = "d41d8cd98f00b204e9800998ecf8427e" // Matches local empty file MD5
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)

        useCase.syncBook(mockDrive, bookId, SyncMode.RESTORE_ONLY)
        advanceUntilIdle()

        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `syncBook with RESTORE_ONLY mode downloads and overwrites local if remote is older but not in sync`() = runTest {
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
            file.writeText("{\"versionSequence\": 2, \"bookUpdatedAt\": ${System.currentTimeMillis() - 100000L}}")
            true
        }

        useCase.syncBook(mockDrive, bookId, SyncMode.RESTORE_ONLY)
        advanceUntilIdle()

        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { mockImportExportManager.importFromJson(any(), bookId, restoreSyncSettings = false) }
    }

    @Test
    fun `syncBook with TWO_WAY mode uploads if local is newer`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(now - 100000L) // Remote is older
            version = 1L
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            file.writeText("{\"versionSequence\": 1, \"bookUpdatedAt\": ${now - 100000L}}")
            true
        }
        
        val mockBook = Book(id = bookId, name = "Test", updatedAt = now, versionSequence = 2L)
        coEvery { mockBookRepository.getBookById(bookId) } returns mockBook

        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns "{\"versionSequence\": 2, \"bookUpdatedAt\": $now}"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) } returns true

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock("file_1", any(), "application/json", 1L, any()) }
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
    }

    @Test
    fun `syncBook with TWO_WAY mode downloads if remote is newer`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"

        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(now + 100000L) // Remote is newer
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            file.writeText("{\"versionSequence\": 2, \"bookUpdatedAt\": ${now + 100000L}}")
            true
        }
        
        val mockBook = Book(id = bookId, name = "Test", updatedAt = now, versionSequence = 1L)
        coEvery { mockBookRepository.getBookById(bookId) } returns mockBook

        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns "{\"versionSequence\": 1, \"bookUpdatedAt\": $now}"
        coEvery { mockImportExportManager.importFromJson(any(), any(), any()) } returns Result.success(5)

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
        coVerify(exactly = 1) { mockImportExportManager.importFromJson(any(), eq(bookId), any()) }
    }

    @Test
    fun `syncBook with TWO_WAY mode does nothing if remote is in sync`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"

        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(now)
            md5Checksum = "d41d8cd98f00b204e9800998ecf8427e" // Matches local empty file MD5
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
        coVerify(exactly = 0) { mockImportExportManager.importFromJson(any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) }
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

        val backups = useCase.getAvailableBackups(mockDrive)
        advanceUntilIdle()

        assertEquals(1, backups.size)
        assertEquals("Properties Book Name", backups[0].bookName)
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
    }

    @Test
    fun `syncBook uses custom folder ID if provided`() = runTest {
        val bookId = "test-book"
        val customFolderId = "custom_folder_123"
        every { mockSettingsRepository.googleDriveFolderId } returns customFolderId
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.zip"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis())
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles(customFolderId) } returns listOf(remoteFile)

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        // Verify that listFiles was called with the custom folder ID
        coVerify(atLeast = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles(customFolderId) }
    }

    @Test
    fun `syncBook with TWO_WAY mode respects syncModeBook set to RESTORE_ONLY`() = runTest {
        val bookId = "test-book"
        every { mockSettingsRepository.syncModeBook } returns "RESTORE_ONLY"
        
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

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        // Verify that downloadFile was called (overwriting local) because resolvedBookMode is RESTORE_ONLY
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `syncBook with TWO_WAY mode respects syncModeBook set to OFF`() = runTest {
        val bookId = "test-book"
        every { mockSettingsRepository.syncModeBook } returns "OFF"
        every { mockSettingsRepository.syncModeTts } returns "OFF"
        every { mockSettingsRepository.syncModeStats } returns "OFF"
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis() + 100000L)
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)

        val result = useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        // Verify success is returned, but no downloading/importing/exporting or uploading took place
        assert(result)
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `syncBook with TWO_WAY mode respects syncModeStats set to BACKUP_ONLY`() = runTest {
        val bookId = "test-book"
        every { mockSettingsRepository.syncModeStats } returns "BACKUP_ONLY"
        every { mockSettingsRepository.syncModeBook } returns "OFF"
        every { mockSettingsRepository.syncModeTts } returns "OFF"
        
        // Mock local statistics as modified (so it should upload)
        coEvery { mockImportExportManager.getStatisticsLastModified(bookId) } returns System.currentTimeMillis()
        coEvery { mockImportExportManager.exportStatisticsToZip(bookId, any()) } answers {
            val os = args[1] as java.io.OutputStream
            os.write("dummy statistics data".toByteArray())
        }
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns emptyList() // No remote stats file
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any(), any(), any(), any(), any()) } returns "new_stats_id"

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        // Verify that statistics upload was invoked because statsMode resolves to BACKUP_ONLY
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), eq("application/zip"), any(), any(), any()) }
    }

    @Test
    fun `syncBook with manual RESTORE_ONLY overrides syncModeStats set to BACKUP_ONLY`() = runTest {
        val bookId = "test-book"
        // Configure repository to BACKUP_ONLY, but the parameter passed is RESTORE_ONLY (forced restore)
        every { mockSettingsRepository.syncModeStats } returns "BACKUP_ONLY"
        every { mockSettingsRepository.syncModeBook } returns "OFF"
        every { mockSettingsRepository.syncModeTts } returns "OFF"
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "stats_file_1"
            name = "statistics_$bookId.zip"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis() + 100000L) // Newer remote file
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } returns true
        coEvery { mockImportExportManager.importStatisticsFromZip(bookId, any()) } returns Unit

        useCase.syncBook(mockDrive, bookId, SyncMode.RESTORE_ONLY)
        advanceUntilIdle()

        // Verify that stats were downloaded/restored because the manual RESTORE_ONLY overrides the BACKUP_ONLY setting
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("stats_file_1", any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), any(), any(), any(), any()) }
    }

    @Test
    fun `syncBook with SAF failure performs local fallback backup`() = runTest {
        val bookId = "test-book"
        every { mockSettingsRepository.syncTargetType } returns "LOCAL_FOLDER_SAF"
        every { mockSettingsRepository.localFolderSafUri } returns null // Will cause IllegalStateException in getStorageProvider
        every { mockSettingsRepository.syncModeStats } returns "BACKUP_ONLY"
        every { mockSettingsRepository.syncModeTts } returns "BACKUP_ONLY"

        // Mock export calls to verify they are run in fallback path
        coEvery { mockImportExportManager.exportBookToJson(eq(bookId)) } returns "{}"
        coEvery { mockImportExportManager.exportStatisticsToZip(eq(bookId), any()) } returns Unit
        coEvery { mockImportExportManager.exportTtsCacheToZip(any(), any()) } returns Unit

        val result = useCase.syncBook(null, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        // Sync returns false because remote sync failed, but fallback is executed
        assertEquals(false, result)

        // Verify fallback exports were called
        coVerify(exactly = 1) { mockImportExportManager.exportBookToJson(eq(bookId)) }
        coVerify(exactly = 1) { mockImportExportManager.exportStatisticsToZip(eq(bookId), any()) }
        coVerify(exactly = 1) { mockImportExportManager.exportTtsCacheToZip(any(), any()) }
        coVerify(exactly = 1) { mockSyncLogProvider.addLogEntry(
            match { it.contains("Cloud-Sync fehlgeschlagen") && it.contains("Lokales Backup erfolgreich") },
            eq(bookId),
            any(),
            isError = true
        ) }
    }

    @Test
    fun `syncBook returns false immediately if another sync is in progress`() = runTest {
        val bookId = "test-book"
        val testBook = Book(id = bookId, name = "Test", updatedAt = System.currentTimeMillis())
        
        // Mock a slow getBookById call to make syncBook execute slowly
        coEvery { mockBookRepository.getBookById(bookId) } coAnswers {
            delay(100)
            testBook
        }

        // Launch first sync in background
        val firstResultDeferred = async {
            useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        }

        // Let the first sync start and grab the lock
        delay(20)

        // Try launching second sync concurrently
        val secondResult = useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)

        // Second sync should immediately abort and return false
        assertEquals(false, secondResult)

        // Wait for first sync to complete
        firstResultDeferred.await()
    }

    @Test
    fun `syncBook uploads for the first time if remote file does not exist in TWO_WAY mode`() = runTest {
        val bookId = "test-book"
        mockk<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>(relaxed = true)

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns emptyList() // No remote file exists
        coEvery { mockImportExportManager.exportBookToJson(eq(bookId)) } returns "{}"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), any(), any(), any(), any()) } returns "new_file_id"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().getFileMetadata(any()) } returns com.google.api.services.drive.model.File().apply {
            id = "new_file_id"
            name = "book_test-book.json"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis())
        }

        val result = useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        assertEquals(true, result)
        // Verify uploadFile was called and downloadFile was NOT called
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
    }

    @Test
    fun `syncBook uploads for the first time if remote file does not exist in BACKUP_ONLY mode`() = runTest {
        val bookId = "test-book"
        mockk<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>(relaxed = true)

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns emptyList() // No remote file exists
        coEvery { mockImportExportManager.exportBookToJson(eq(bookId)) } returns "{}"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), any(), any(), any(), any()) } returns "new_file_id"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().getFileMetadata(any()) } returns com.google.api.services.drive.model.File().apply {
            id = "new_file_id"
            name = "book_test-book.json"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis())
        }

        val result = useCase.syncBook(mockDrive, bookId, SyncMode.BACKUP_ONLY)
        advanceUntilIdle()

        assertEquals(true, result)
        // Verify uploadFile was called and downloadFile was NOT called
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
    }

    @Test
    fun `syncBook skips download and merge if remote properties structure_md5 matches local structure_md5`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"

        val mockJson = "{\"bookUpdatedAt\":123456,\"versionSequence\":2,\"pages\":[]}"
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns mockJson

        val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val data = jsonParser.decodeFromString<com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData>(mockJson)
        val cleanData = data.copy(bookUpdatedAt = 0L, versionSequence = 0L)
        val cleanJson = jsonParser.encodeToString(com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData.serializer(), cleanData)
        val messageDigest = java.security.MessageDigest.getInstance("MD5")
        val hashBytes = messageDigest.digest(cleanJson.toByteArray(Charsets.UTF_8))
        val expectedMd5 = hashBytes.joinToString("") { "%02x".format(it) }

        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(now)
            properties = mapOf("structure_md5" to expectedMd5)
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)

        val result = useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        assertEquals(true, result)
        // Verify download and upload were skipped
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) }
    }
}
