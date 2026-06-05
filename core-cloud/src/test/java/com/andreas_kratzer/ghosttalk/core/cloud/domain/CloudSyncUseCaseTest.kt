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
        every { mockContext.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")

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
    @Test
    fun `syncBook with TWO_WAY mode uploads if local is newer`() = runTest {
        val bookId = "test-book"
        val now = System.currentTimeMillis()
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        
        val remoteFile = com.google.api.services.drive.model.File().apply {
            id = "file_1"
            name = "book_$bookId.zip"
            modifiedTime = com.google.api.client.util.DateTime(now - 100000L) // Remote is older
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            java.util.zip.ZipOutputStream(file.outputStream()).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("backup.json"))
                zos.write("{\"bookUpdatedAt\": 0}".toByteArray())
                zos.closeEntry()
            }
            true
        }
        
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns "{\"bookUpdatedAt\": $now}"
        coEvery { mockImportExportManager.exportBookToZip(bookId, any(), any()) } returns Unit
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any()) } returns true

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
            modifiedTime = com.google.api.client.util.DateTime(now + 100000L) // Remote is newer
        }
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns listOf(remoteFile)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            java.util.zip.ZipOutputStream(file.outputStream()).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("backup.json"))
                zos.write("{\"bookUpdatedAt\": ${now + 100000L}}".toByteArray())
                zos.closeEntry()
            }
            true
        }
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns "{\"bookUpdatedAt\": 0}"
        coEvery { mockImportExportManager.importFromJson(any(), any(), any()) } returns Result.success(Unit)

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
        coVerify(exactly = 1) { mockImportExportManager.importFromJson(any(), bookId, any()) }
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
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) } answers {
            val file = args[1] as File
            java.util.zip.ZipOutputStream(file.outputStream()).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("backup.json"))
                zos.write("{\"bookUpdatedAt\": ${now + 500L}}".toByteArray())
                zos.closeEntry()
            }
            true
        }
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns "{\"bookUpdatedAt\": $now}"

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile("file_1", any(), any()) }
        coVerify(exactly = 0) { mockImportExportManager.importFromJson(any(), any(), any()) }
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
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any()) }
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
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().updateFile(any(), any(), any(), any(), any()) }
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
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any(), any(), any()) } returns "new_stats_id"

        useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        // Verify that statistics upload was invoked because statsMode resolves to BACKUP_ONLY
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), eq("application/zip"), eq("Test"), any()) }
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
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), any(), any(), any()) }
    }

    @Test
    fun `syncBook with SAF failure performs local fallback backup`() = runTest {
        val bookId = "test-book"
        every { mockSettingsRepository.syncTargetType } returns "LOCAL_FOLDER_SAF"
        every { mockSettingsRepository.localFolderSafUri } returns null // Will cause IllegalStateException in getStorageProvider
        every { mockSettingsRepository.syncModeStats } returns "BACKUP_ONLY"
        every { mockSettingsRepository.syncModeTts } returns "BACKUP_ONLY"

        // Mock export calls to verify they are run in fallback path
        coEvery { mockImportExportManager.exportBookToZip(eq(bookId), any(), any(), any()) } returns Unit
        coEvery { mockImportExportManager.exportStatisticsToZip(eq(bookId), any()) } returns Unit
        coEvery { mockImportExportManager.exportTtsCacheToZip(any(), any()) } returns Unit

        val result = useCase.syncBook(null, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        // Sync returns false because remote sync failed, but fallback is executed
        assertEquals(false, result)

        // Verify fallback exports were called
        coVerify(exactly = 1) { mockImportExportManager.exportBookToZip(eq(bookId), any(), any(), any()) }
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
        coEvery { mockImportExportManager.exportBookToZip(eq(bookId), any(), any(), any()) } returns Unit
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), any(), any(), any()) } returns "new_file_id"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().getFileMetadata(any()) } returns com.google.api.services.drive.model.File().apply {
            id = "new_file_id"
            name = "book_test-book.zip"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis())
        }

        val result = useCase.syncBook(mockDrive, bookId, SyncMode.TWO_WAY)
        advanceUntilIdle()

        assertEquals(true, result)
        // Verify uploadFile was called and downloadFile was NOT called
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
    }

    @Test
    fun `syncBook uploads for the first time if remote file does not exist in BACKUP_ONLY mode`() = runTest {
        val bookId = "test-book"
        mockk<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>(relaxed = true)

        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any()) } returns "folder_1"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().listFiles("folder_1") } returns emptyList() // No remote file exists
        coEvery { mockImportExportManager.exportBookToZip(eq(bookId), any(), any(), any()) } returns Unit
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), any(), any(), any()) } returns "new_file_id"
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().getFileMetadata(any()) } returns com.google.api.services.drive.model.File().apply {
            id = "new_file_id"
            name = "book_test-book.zip"
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis())
        }

        val result = useCase.syncBook(mockDrive, bookId, SyncMode.BACKUP_ONLY)
        advanceUntilIdle()

        assertEquals(true, result)
        // Verify uploadFile was called and downloadFile was NOT called
        coVerify(exactly = 1) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().uploadFile(any(), any<File>(), any(), any(), any()) }
        coVerify(exactly = 0) { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().downloadFile(any(), any(), any()) }
    }
}
