package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.model.SettingsProfile
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ProfileSyncOrchestratorTest {

    private lateinit var orchestrator: ProfileSyncOrchestrator
    private lateinit var mockContext: Context
    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var mockImportExportManager: PageImportExportManager
    private lateinit var mockSyncLogProvider: SyncLogProvider
    private lateinit var mockLogger: Logger
    private lateinit var mockStorageResolver: SyncStorageResolver
    private lateinit var mockStorageProvider: SyncStorageProvider
    private lateinit var mockDrive: Drive

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockSettingsRepository = mockk(relaxed = true)
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

        coEvery { mockStorageResolver.resolveProfilesStorageProvider(any()) } returns mockStorageProvider
        coEvery { mockStorageProvider.listFiles() } returns emptyList()

        orchestrator = ProfileSyncOrchestrator(
            context = mockContext,
            settingsRepository = mockSettingsRepository,
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
    fun `syncProfiles uploads local profiles`() = runTest {
        val mockProfile = SettingsProfile(
            id = "p1",
            name = "Profile 1",
            config = mockk(relaxed = true),
            profileVersionSequence = 1L,
            updatedAt = System.currentTimeMillis()
        )
        coEvery { mockSettingsRepository.getAllProfiles() } returns listOf(mockProfile)

        val result = orchestrator.syncProfiles(mockDrive)
        assertTrue(result)

        coVerify(exactly = 1) { mockSettingsRepository.getAllProfiles() }
    }

    @Test
    fun `syncProfiles propagates CancellationException`() = runTest {
        coEvery { mockSettingsRepository.getAllProfiles() } throws kotlinx.coroutines.CancellationException("Job cancelled")

        try {
            orchestrator.syncProfiles(mockDrive)
            org.junit.Assert.fail("Should have thrown CancellationException")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected
        }
    }
}
