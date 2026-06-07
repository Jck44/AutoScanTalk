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
    private lateinit var mockContext: Context
    private lateinit var mockBookRepository: BookRepository
    private lateinit var mockImportExportManager: PageImportExportManager
    private lateinit var mockDrive: Drive
    private lateinit var mockLogger: Logger
    private lateinit var mockSyncLogProvider: SyncLogProvider
    private lateinit var mockSettingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockBookRepository = mockk(relaxed = true)
        mockImportExportManager = mockk(relaxed = true)
        mockDrive = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockSyncLogProvider = mockk(relaxed = true)
        mockSettingsRepository = mockk(relaxed = true)

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
        every { mockSettingsRepository.isCloudSyncEnabled } returns true
        every { mockSettingsRepository.activeProfileId } returns "default-profile"
        coEvery { mockSettingsRepository.getProfileById(any()) } returns null

        coEvery { mockImportExportManager.getStatisticsLastModified(any<String>()) } returns 0L
        every { mockImportExportManager.getTtsCacheLastModified() } returns 0L
        every { mockImportExportManager.getAudioRecordingsLastModified() } returns 0L
        
        // Mock new findFolder / createFolder signatures
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any(), any()) } returns null
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().createFolder(any(), any()) } returns "folder_1"

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

    private fun calculateStructuralMd5FromJson(jsonStr: String): String {
        return try {
            val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
            val data = jsonParser.decodeFromString<com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData>(jsonStr)
            val cleanData = data.copy(
                bookUpdatedAt = 0L,
                versionSequence = 0L,
                sourceDevice = null,
                isCloudSyncEnabled = null,
                syncIntervalMinutes = null,
                syncModeBook = null,
                syncModeTts = null,
                syncModeStats = null,
                syncMode = null
            )
            val cleanJson = jsonParser.encodeToString(com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData.serializer(), cleanData)
            val messageDigest = java.security.MessageDigest.getInstance("MD5")
            val hashBytes = messageDigest.digest(cleanJson.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    @Test
    fun `syncBook performs trivial merge and skips upload if merged struct matches remote struct`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"

        val localJson = "{\"bookUpdatedAt\":1000,\"versionSequence\":1,\"pages\":[]}"
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns localJson

        val expectedMd5 = calculateStructuralMd5FromJson(localJson)

        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(now)
            properties = mapOf("version_sequence" to "3", "structure_md5" to expectedMd5)
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)

        // Mock download during conflict evaluation
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any()) } answers {
            val file = secondArg<File>()
            file.writeText("{\"bookUpdatedAt\":2000,\"versionSequence\":3,\"pages\":[]}")
            true
        }

        // Import mock
        coEvery { mockImportExportManager.importFromJson(any(), any(), any()) } returns Result.success(1)

        val result = useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        assertEquals(true, result)
        // Verify we imported the fast-forwarded book
        coVerify(exactly = 1) { mockImportExportManager.importFromJson(any(), eq(bookId), any()) }
        // Verify we did NOT upload anything because it's a trivial merge
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadWithOptimisticLock(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `syncBook syncs audio recordings zip file separately after successful sync`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"

        val localJson = "{\"bookUpdatedAt\":1001,\"versionSequence\":1,\"pages\":[]}"
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns localJson

        val expectedMd5 = calculateStructuralMd5FromJson(localJson)

        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(now)
            properties = mapOf("version_sequence" to "1", "structure_md5" to expectedMd5)
        }
        val remoteAudioFile = com.google.api.services.drive.model.File().apply {
            id = "audio_file_1"
            name = "audio_$bookId.zip"
            modifiedTime = com.google.api.client.util.DateTime(now - 100000)
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile, remoteAudioFile)

        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.getLong("audio_last_synced_remote_time_$bookId", 0L) } returns (now - 100000)
        every { mockPrefs.getLong("audio_last_synced_local_time_$bookId", 0L) } returns 0L

        val mockEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor

        // Set local audio changed
        coEvery { mockImportExportManager.getAudioRecordingsLastModified() } returns now
        coEvery { mockImportExportManager.exportAudioRecordingsToZip(any(), any()) } coAnswers {
            // Write some mock zip data
            val os = firstArg<java.io.OutputStream>()
            os.write(byteArrayOf(1, 2, 3))
        }

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any(), any()) } returns true
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().getFileMetadata("audio_file_1") } returns com.google.api.services.drive.model.File().apply {
            id = "audio_file_1"
            modifiedTime = com.google.api.client.util.DateTime(now)
        }

        val result = useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        assertEquals(true, result)
        // Verify audio export and update were called
        coVerify(exactly = 1) { mockImportExportManager.exportAudioRecordingsToZip(any(), any()) }
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(eq("audio_file_1"), any(), eq("application/zip"), any(), any(), any()) }
    }

    @Test
    fun `syncBook merges audio recordings on conflict in TWO_WAY mode`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"

        val localJson = "{\"bookUpdatedAt\":1000,\"versionSequence\":1,\"pages\":[]}"
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns localJson

        val expectedMd5 = calculateStructuralMd5FromJson(localJson)

        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.json"
            modifiedTime = com.google.api.client.util.DateTime(now)
            properties = mapOf("version_sequence" to "1", "structure_md5" to expectedMd5)
        }
        val remoteAudioFile = com.google.api.services.drive.model.File().apply {
            id = "audio_file_1"
            name = "audio_$bookId.zip"
            modifiedTime = com.google.api.client.util.DateTime(now) // Remote also changed
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile, remoteAudioFile)

        // Mock SharedPreferences
        val mockPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.getLong("audio_last_synced_remote_time_$bookId", 0L) } returns (now - 100000)
        every { mockPrefs.getLong("audio_last_synced_local_time_$bookId", 0L) } returns (now - 100000)

        val mockEditor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor

        // Set local audio changed
        coEvery { mockImportExportManager.getAudioRecordingsLastModified() } returns now
        coEvery { mockImportExportManager.exportAudioRecordingsToZip(any(), any()) } coAnswers {
            val os = firstArg<java.io.OutputStream>()
            os.write(byteArrayOf(1, 2, 3))
        }
        coEvery { mockImportExportManager.importAudioRecordingsFromZip(any(), any()) } returns Unit

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("audio_file_1", any(), any()) } answers {
            val file = args[1] as File
            file.writeBytes(byteArrayOf(1, 2, 3))
            true
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any(), any()) } returns true
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().getFileMetadata("audio_file_1") } returns com.google.api.services.drive.model.File().apply {
            id = "audio_file_1"
            modifiedTime = com.google.api.client.util.DateTime(now)
        }

        val result = useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        assertEquals(true, result)
        // Verify audio download/merge and export/update were BOTH called
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("audio_file_1", any(), any()) }
        coVerify(exactly = 1) { mockImportExportManager.importAudioRecordingsFromZip(any(), any()) }
        coVerify(exactly = 1) { mockImportExportManager.exportAudioRecordingsToZip(any(), any()) }
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(eq("audio_file_1"), any(), eq("application/zip"), any(), any(), any()) }
    }

    @Test
    fun `syncBook auto-imports remote profile when local profile does not exist`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder("GhosTTalk_Sync") } returns "parent_folder_1"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder("Profiles", "parent_folder_1") } returns "profiles_folder_1"

        val remoteProfileFile = com.google.api.services.drive.model.File().apply {
            id = "profile_file_1"
            name = "profile_profile1.json"
            description = "My Custom Profile"
            version = 2L
            modifiedTime = com.google.api.client.util.DateTime(now)
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("profiles_folder_1") } returns listOf(remoteProfileFile)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("parent_folder_1") } returns emptyList()

        val jsonSerializer = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
        val testProfile = com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
            id = "profile1",
            name = "My Custom Profile",
            config = com.andreas_kratzer.ghosttalk.core.model.ProfileConfig(
                favoriteBookId = "book_1",
                incomingCallDelayUserModeInactive = 42
            ),
            profileVersionSequence = 2L,
            updatedAt = now
        )
        val modernJson = jsonSerializer.encodeToString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), testProfile)

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("profile_file_1", any(), any()) } answers {
            val file = args[1] as File
            file.writeText(modernJson)
            true
        }

        coEvery { mockSettingsRepository.getAllProfiles() } returns emptyList()
        coEvery { mockSettingsRepository.getProfileById("profile1") } returns null

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockSettingsRepository.insertProfile(withArg {
            assertEquals("profile1", it.id)
            assertEquals("My Custom Profile", it.name)
            assertEquals("book_1", it.config.favoriteBookId)
            assertEquals(42, it.config.incomingCallDelayUserModeInactive)
            assertEquals(2L, it.profileVersionSequence)
        }) }
    }

    @Test
    fun `syncBook auto-imports remote profile in legacy format when local profile does not exist`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder("GhosTTalk_Sync") } returns "parent_folder_1"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder("Profiles", "parent_folder_1") } returns "profiles_folder_1"

        val remoteProfileFile = com.google.api.services.drive.model.File().apply {
            id = "profile_file_1"
            name = "profile_profile1.json"
            description = "Legacy Profile Description"
            version = 1L
            modifiedTime = com.google.api.client.util.DateTime(now)
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("profiles_folder_1") } returns listOf(remoteProfileFile)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("parent_folder_1") } returns emptyList()

        val jsonSerializer = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
        val legacyConfig = com.andreas_kratzer.ghosttalk.core.model.ProfileConfig(
            favoriteBookId = "book_legacy",
            incomingCallDelayUserModeInactive = 99
        )
        val legacyJson = jsonSerializer.encodeToString(com.andreas_kratzer.ghosttalk.core.model.ProfileConfig.serializer(), legacyConfig)

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("profile_file_1", any(), any()) } answers {
            val file = args[1] as File
            file.writeText(legacyJson)
            true
        }

        coEvery { mockSettingsRepository.getAllProfiles() } returns emptyList()
        coEvery { mockSettingsRepository.getProfileById("profile1") } returns null

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockSettingsRepository.insertProfile(withArg {
            assertEquals("profile1", it.id)
            assertEquals("Legacy Profile Description", it.name)
            assertEquals("book_legacy", it.config.favoriteBookId)
            assertEquals(99, it.config.incomingCallDelayUserModeInactive)
            assertEquals(1L, it.profileVersionSequence)
        }) }
    }

    @Test
    fun `syncBook merges settings profile when local and remote differ`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder("GhosTTalk_Sync") } returns "parent_folder_1"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder("Profiles", "parent_folder_1") } returns "profiles_folder_1"

        val remoteProfileFile = com.google.api.services.drive.model.File().apply {
            id = "profile_file_1"
            name = "profile_profile1.json"
            description = "Remote Profile Name"
            version = 1L
            modifiedTime = com.google.api.client.util.DateTime(now)
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("profiles_folder_1") } returns listOf(remoteProfileFile)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("parent_folder_1") } returns emptyList()

        val jsonSerializer = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
        
        val baseConfig = com.andreas_kratzer.ghosttalk.core.model.ProfileConfig(
            favoriteBookId = "book_base",
            incomingCallDelayUserModeInactive = 10
        )
        val baseProfile = com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
            id = "profile1",
            name = "Base Name",
            config = baseConfig,
            profileVersionSequence = 1L,
            updatedAt = now - 2000
        )
        
        val remoteConfig = com.andreas_kratzer.ghosttalk.core.model.ProfileConfig(
            favoriteBookId = "book_base",
            incomingCallDelayUserModeInactive = 20
        )
        val remoteProfile = com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
            id = "profile1",
            name = "Remote Name",
            config = remoteConfig,
            profileVersionSequence = 2L,
            updatedAt = now - 1000
        )
        val remoteJson = jsonSerializer.encodeToString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), remoteProfile)

        val localConfig = com.andreas_kratzer.ghosttalk.core.model.ProfileConfig(
            favoriteBookId = "book_local",
            incomingCallDelayUserModeInactive = 10
        )
        val localProfile = com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
            id = "profile1",
            name = "Local Name",
            config = localConfig,
            profileVersionSequence = 2L,
            updatedAt = now
        )

        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val baseBackupFile = File(tempDir, "local_backups/profile_profile1.json")
        baseBackupFile.parentFile?.mkdirs()
        val baseJson = jsonSerializer.encodeToString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), baseProfile)
        baseBackupFile.writeText(baseJson)

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("profile_file_1", any(), any()) } answers {
            val file = args[1] as File
            file.writeText(remoteJson)
            true
        }

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile("profile_file_1", any(), any(), any(), any(), any()) } returns true

        coEvery { mockSettingsRepository.getAllProfiles() } returns listOf(localProfile)
        coEvery { mockSettingsRepository.getProfileById("profile1") } returns localProfile

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockSettingsRepository.updateProfile(withArg {
            assertEquals("profile1", it.id)
            assertEquals("Local Name", it.name)
            assertEquals("book_local", it.config.favoriteBookId)
            assertEquals(20, it.config.incomingCallDelayUserModeInactive)
            assertEquals(3L, it.profileVersionSequence)
        }) }

        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile("profile_file_1", any(), "application/json", "Local Name", any(), any()) }

        baseBackupFile.delete()
    }
}
