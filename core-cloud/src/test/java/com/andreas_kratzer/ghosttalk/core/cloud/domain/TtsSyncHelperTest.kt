package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class TtsSyncHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var importExportManager: PageImportExportManager
    private lateinit var syncLogProvider: SyncLogProvider
    private lateinit var logger: Logger
    private lateinit var storageProvider: SyncStorageProvider
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var helper: TtsSyncHelper

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        val cacheDir = tempFolder.newFolder("cache")
        val filesDir = tempFolder.newFolder("files")
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        
        importExportManager = mockk(relaxed = true)
        syncLogProvider = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        storageProvider = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor

        helper = TtsSyncHelper(context, importExportManager, syncLogProvider, logger)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `syncTtsCache merges and re-uploads when both sides changed`() = runTest {
        val remoteFile = RemoteSyncFile(
            id = "remote_tts_id",
            name = "tts_cache.zip",
            description = null,
            modifiedTime = 20000L,
            properties = emptyMap()
        )

        // Setup times:
        // last synced: 10000
        // local modified: 15000 (hasLocalChanged = true)
        // remote modified: 20000 (hasRemoteChanged = true)
        every { sharedPreferences.getLong("tts_cache_last_synced_local_time", 0L) } returns 10000L
        every { sharedPreferences.getLong("tts_cache_last_synced_remote_time", 0L) } returns 10000L
        every { importExportManager.getTtsCacheLastModified() } returns 15000L

        // Mock download to succeed
        coEvery { storageProvider.downloadFile("remote_tts_id", any(), any()) } coAnswers {
            // Write a dummy file to represent download
            val file = secondArg<File>()
            file.createNewFile()
            true
        }

        // Mock zip export to write dummy data so the temp file length > 0
        coEvery { importExportManager.exportTtsCacheToZip(any(), any()) } answers {
            val os = firstArg<java.io.OutputStream>()
            os.write(byteArrayOf(1, 2, 3))
        }

        // Mock upload update success
        coEvery { storageProvider.updateFile("remote_tts_id", any(), any(), any(), any()) } returns true
        coEvery { storageProvider.getFileMetadata("remote_tts_id") } returns RemoteSyncFile(
            id = "remote_tts_id",
            name = "tts_cache.zip",
            description = null,
            modifiedTime = 25000L,
            properties = emptyMap()
        )

        helper.syncTtsCache(storageProvider, listOf(remoteFile), SyncMode.TWO_WAY)

        // Verifications:
        // 1. Must download the remote file first to perform local merge
        coVerify(exactly = 1) { storageProvider.downloadFile(fileId = "remote_tts_id", destFile = any(), onProgress = any()) }
        // 2. Must import downloaded zip to merge
        coVerify(exactly = 1) { importExportManager.importTtsCacheFromZip(any<InputStream>(), any()) }
        // 3. Must upload updated/merged zip to drive
        coVerify(exactly = 1) {
            storageProvider.updateFile(
                fileId = "remote_tts_id",
                tempFile = any(),
                mimeType = "application/zip",
                description = any(),
                properties = null,
                onProgress = any()
            )
        }
    }
}
