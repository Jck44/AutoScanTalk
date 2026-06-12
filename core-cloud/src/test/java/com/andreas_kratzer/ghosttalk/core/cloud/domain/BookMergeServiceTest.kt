package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.export.ImportResult
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class BookMergeServiceTest {

    private lateinit var service: BookMergeService
    private lateinit var mockContext: Context
    private lateinit var mockBookRepository: BookRepository
    private lateinit var mockImportExportManager: PageImportExportManager
    private lateinit var mockSyncLogProvider: SyncLogProvider
    private lateinit var mockLogger: Logger
    private lateinit var mockStorageResolver: SyncStorageResolver
    private lateinit var mockStorageProvider: SyncStorageProvider
    private lateinit var mockDrive: Drive
    private lateinit var mockSyncAnchorStore: SyncAnchorStore

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockBookRepository = mockk(relaxed = true)
        mockImportExportManager = mockk(relaxed = true)
        mockSyncLogProvider = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockStorageResolver = mockk(relaxed = true)
        mockStorageProvider = mockk(relaxed = true)
        mockDrive = mockk(relaxed = true)
        mockSyncAnchorStore = mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        every { mockContext.cacheDir } returns tempDir
        every { mockContext.filesDir } returns tempDir

        service = BookMergeService(
            context = mockContext,
            bookRepository = mockBookRepository,
            importExportManager = mockImportExportManager,
            syncLogProvider = mockSyncLogProvider,
            logger = mockLogger,
            storageResolver = mockStorageResolver,
            syncAnchorStore = mockSyncAnchorStore
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `performMergeConflict perform trivial merge successfully`() = runTest {
        val bookId = "test-book"
        val book = Book(id = bookId, name = "Test Book", updatedAt = System.currentTimeMillis())

        val localJson = "{\"bookUpdatedAt\":1000,\"versionSequence\":1,\"pages\":[]}"
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns localJson
        coEvery { mockImportExportManager.importFromJson(any(), any(), any()) } returns Result.success(ImportResult(1))

        val engine = BookMergeEngine(mockLogger)
        val expectedMd5 = engine.calculateStructuralMd5FromJson(localJson)

        val remoteMasterFile = RemoteSyncFile(
            id = "master_1",
            name = "book_$bookId.json",
            description = "Master JSON",
            modifiedTime = System.currentTimeMillis(),
            mimeType = "application/json",
            properties = mapOf("version_sequence" to "3", "structure_md5" to expectedMd5)
        )

        coEvery { mockStorageProvider.downloadFile("master_1", any()) } answers {
            val file = secondArg<File>()
            file.writeText("{\"bookUpdatedAt\":2000,\"versionSequence\":3,\"pages\":[]}")
            true
        }

        val result = service.performMergeConflict(
            drive = mockDrive,
            storageProvider = mockStorageProvider,
            bookId = bookId,
            book = book,
            remoteMasterFile = remoteMasterFile,
            effectiveMasterFile = remoteMasterFile,
            remoteConflictFiles = emptyList(),
            legacyZipFile = null,
            localSeq = 1L,
            remoteFiles = listOf(remoteMasterFile),
            audioSyncMode = SyncMode.TWO_WAY,
            masterFileName = "book_$bookId.json"
        )

        assertTrue(result.success)
        coVerify(exactly = 1) { mockImportExportManager.importFromJson(any(), eq(bookId), any()) }
        coVerify(exactly = 0) { mockStorageProvider.uploadFile(any(), any(), any(), any(), any()) }
        // Verify setAnchor is called on trivial merge success
        verify(exactly = 1) { mockSyncAnchorStore.setAnchor(bookId, expectedMd5) }
    }

    @Test
    fun `performMergeConflict sets anchor to merged struct md5 on full success`() = runTest {
        val bookId = "test-book"
        val book = Book(id = bookId, name = "Test Book", updatedAt = System.currentTimeMillis())

        val localJson = "{\"bookUpdatedAt\":1000,\"versionSequence\":2,\"pages\":[]}"
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns localJson
        coEvery { mockImportExportManager.importFromJson(any(), any(), any()) } returns Result.success(ImportResult(1))

        val remoteMasterFile = RemoteSyncFile(
            id = "master_1",
            name = "book_$bookId.json",
            description = "Master JSON",
            modifiedTime = System.currentTimeMillis(),
            mimeType = "application/json",
            properties = mapOf("version_sequence" to "3", "structure_md5" to "definitely-different-md5")
        )

        coEvery { mockStorageProvider.downloadFile(any(), any()) } answers {
            val file = secondArg<File>()
            file.writeText("{\"bookUpdatedAt\":2000,\"versionSequence\":3,\"pages\":[]}")
            true
        }
        coEvery { mockStorageProvider.updateFile(any(), any(), any(), any(), any(), any()) } returns true

        val result = service.performMergeConflict(
            drive = null,
            storageProvider = mockStorageProvider,
            bookId = bookId,
            book = book,
            remoteMasterFile = remoteMasterFile,
            effectiveMasterFile = remoteMasterFile,
            remoteConflictFiles = emptyList(),
            legacyZipFile = null,
            localSeq = 2L,
            remoteFiles = listOf(remoteMasterFile),
            audioSyncMode = SyncMode.TWO_WAY,
            masterFileName = "book_$bookId.json"
        )

        assertEquals(MergeStatus.SUCCESS, result.status)
        verify(exactly = 1) { mockSyncAnchorStore.setAnchor(bookId, any()) }
    }

    @Test
    fun `performMergeConflict reports UPLOAD_PENDING when cloud upload is rejected`() = runTest {
        val bookId = "test-book"
        val book = Book(id = bookId, name = "Test Book", updatedAt = System.currentTimeMillis())

        val localJson = "{\"bookUpdatedAt\":1000,\"versionSequence\":2,\"pages\":[]}"
        coEvery { mockImportExportManager.exportBookToJson(bookId) } returns localJson
        coEvery { mockImportExportManager.importFromJson(any(), any(), any()) } returns Result.success(ImportResult(1))

        val remoteMasterFile = RemoteSyncFile(
            id = "master_1",
            name = "book_$bookId.json",
            description = "Master JSON",
            modifiedTime = System.currentTimeMillis(),
            mimeType = "application/json",
            properties = mapOf("version_sequence" to "3", "structure_md5" to "definitely-different-md5")
        )
        val conflictFile = remoteMasterFile.copy(id = "conflict_1", name = "book_${bookId}_conflict.json")

        coEvery { mockStorageProvider.downloadFile(any(), any()) } answers {
            val file = secondArg<File>()
            file.writeText("{\"bookUpdatedAt\":2000,\"versionSequence\":3,\"pages\":[]}")
            true
        }
        coEvery { mockStorageProvider.updateFile(any(), any(), any(), any(), any(), any()) } returns false

        val result = service.performMergeConflict(
            drive = null,
            storageProvider = mockStorageProvider,
            bookId = bookId,
            book = book,
            remoteMasterFile = remoteMasterFile,
            effectiveMasterFile = remoteMasterFile,
            remoteConflictFiles = listOf(conflictFile),
            legacyZipFile = null,
            localSeq = 2L,
            remoteFiles = listOf(remoteMasterFile, conflictFile),
            audioSyncMode = SyncMode.TWO_WAY,
            masterFileName = "book_$bookId.json"
        )

        assertEquals(MergeStatus.MERGED_LOCALLY_UPLOAD_PENDING, result.status)
        assertTrue(result.success)
        coVerify(exactly = 0) { mockStorageProvider.deleteFile(any()) }
        // Verify setAnchor is NOT called when merge upload is pending
        verify(exactly = 0) { mockSyncAnchorStore.setAnchor(any(), any()) }
    }

    @Test
    fun `extractAudioRecordingsFromZip extracts atomically and blocks zip slip`() {
        val workDir = java.nio.file.Files.createTempDirectory("merge_audio_test").toFile()
        every { mockContext.filesDir } returns workDir

        val zipFile = File(workDir, "sync.zip")
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("audio_recordings/good.mp3"))
            zos.write("AUDIO".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("audio_recordings/../evil.mp3"))
            zos.write("EVIL".toByteArray())
            zos.closeEntry()
        }

        service.extractAudioRecordingsFromZip(zipFile)

        val audioDir = File(workDir, "audio_recordings")
        val good = File(audioDir, "good.mp3")
        assertTrue("Gueltige Audio-Datei muss extrahiert sein", good.exists())
        assertEquals("AUDIO", good.readText())
        assertTrue("Zip-Slip-Datei darf nicht geschrieben werden", !File(workDir, "evil.mp3").exists())
        val leftovers = audioDir.listFiles()?.filter { it.name.endsWith(".part") } ?: emptyList()
        assertTrue("Keine .part-Tempdateien erwartet", leftovers.isEmpty())

        workDir.deleteRecursively()
    }
}
