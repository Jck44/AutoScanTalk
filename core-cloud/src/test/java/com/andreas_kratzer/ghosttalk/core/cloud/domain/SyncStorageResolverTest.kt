package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncStorageResolverTest {

    private lateinit var resolver: SyncStorageResolver
    private lateinit var mockContext: Context
    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var mockLogger: Logger
    private lateinit var mockDrive: Drive

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockSettingsRepository = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockDrive = mockk(relaxed = true)

        io.mockk.mockkStatic(android.net.Uri::class)
        val mockUri = mockk<android.net.Uri>(relaxed = true)
        every { android.net.Uri.parse(any()) } returns mockUri

        io.mockk.mockkStatic(androidx.documentfile.provider.DocumentFile::class)
        every { androidx.documentfile.provider.DocumentFile.fromTreeUri(any(), any()) } returns mockk(relaxed = true)

        resolver = SyncStorageResolver(mockContext, mockSettingsRepository, mockLogger)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getStorageProvider returns DocumentFolderSyncStorageProvider if folderId starts with content uri`() = runTest {
        val folderId = "content://com.android.externalstorage.documents/tree/primary%3AGhostTalk"
        val provider = resolver.getStorageProvider(drive = null, folderId = folderId)
        assertTrue(provider is DocumentFolderSyncStorageProvider)
    }

    @Test
    fun `getStorageProvider returns DriveApiSyncStorageProvider if drive client is present`() = runTest {
        mockkConstructor(com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper::class)
        coEvery { anyConstructed<com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper>().findFolder(any(), any()) } returns "folder_1"
        every { mockSettingsRepository.googleDriveFolderId } returns "folder_1"
        every { mockSettingsRepository.lastFolderValidationTime } returns System.currentTimeMillis()

        val provider = resolver.getStorageProvider(drive = mockDrive, folderId = "folder_1")
        assertTrue(provider is DriveApiSyncStorageProvider)
    }
}
