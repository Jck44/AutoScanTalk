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
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
            storageResolver = mockStorageResolver
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
        coEvery { mockImportExportManager.importFromJson(any(), any(), any()) } returns Result.success(1)

        val remoteMasterFile = RemoteSyncFile(
            id = "master_1",
            name = "book_$bookId.json",
            description = "Master JSON",
            modifiedTime = System.currentTimeMillis(),
            mimeType = "application/json",
            properties = mapOf("version_sequence" to "3", "structure_md5" to "d41d8cd98f00b204e9800998ecf8427e")
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
    }
}
