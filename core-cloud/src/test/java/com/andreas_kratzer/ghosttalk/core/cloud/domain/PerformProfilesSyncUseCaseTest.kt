package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Intent
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleWebAuthManager
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.Drive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerformProfilesSyncUseCaseTest {

    private lateinit var useCase: PerformProfilesSyncUseCase
    private lateinit var mockGoogleAuthManager: GoogleAuthManager
    private lateinit var mockGoogleWebAuthManager: GoogleWebAuthManager
    private lateinit var mockCloudSyncUseCase: CloudSyncUseCase
    private lateinit var mockSettingsRepository: CloudSettings
    private lateinit var mockDrive: Drive

    @Before
    fun setup() {
        mockGoogleAuthManager = mockk(relaxed = true)
        mockGoogleWebAuthManager = mockk(relaxed = true)
        mockCloudSyncUseCase = mockk(relaxed = true)
        mockSettingsRepository = mockk(relaxed = true)
        mockDrive = mockk(relaxed = true)

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        // Mock companion object of DriveServiceHelper
        mockkObject(com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper.Companion)

        useCase = PerformProfilesSyncUseCase(
            googleAuthManager = mockGoogleAuthManager,
            googleWebAuthManager = mockGoogleWebAuthManager,
            cloudSyncUseCase = mockCloudSyncUseCase,
            settingsRepository = mockSettingsRepository
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `execute returns Error if drive client building fails`() = runTest {
        every { mockSettingsRepository.googleAuthType } returns com.andreas_kratzer.ghosttalk.core.model.CloudAuthType.SYSTEM
        coEvery { com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper.buildDriveClient(any(), any(), any()) } returns null

        val result = useCase.execute { _, _ -> }

        assertTrue(result is PerformProfilesSyncUseCase.Result.Error)
        assertEquals("Keine Google-Anmeldedaten oder Verbindung fehlgeschlagen.", (result as PerformProfilesSyncUseCase.Result.Error).message)
        coVerify(exactly = 0) { mockCloudSyncUseCase.syncProfilesOnly(any(), any()) }
    }

    @Test
    fun `execute returns Success when syncProfilesOnly completes successfully`() = runTest {
        every { mockSettingsRepository.googleAuthType } returns com.andreas_kratzer.ghosttalk.core.model.CloudAuthType.SYSTEM
        coEvery { com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper.buildDriveClient(any(), any(), any()) } returns mockDrive
        coEvery { mockCloudSyncUseCase.syncProfilesOnly(mockDrive, any()) } returns true

        val result = useCase.execute { _, _ -> }

        assertTrue(result is PerformProfilesSyncUseCase.Result.Success)
        coVerify(exactly = 1) { mockCloudSyncUseCase.syncProfilesOnly(mockDrive, any()) }
    }

    @Test
    fun `execute returns RecoverableAuth on UserRecoverableAuthIOException`() = runTest {
        every { mockSettingsRepository.googleAuthType } returns com.andreas_kratzer.ghosttalk.core.model.CloudAuthType.SYSTEM
        coEvery { com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper.buildDriveClient(any(), any(), any()) } returns mockDrive
        val mockIntent = mockk<Intent>()
        val exception = mockk<UserRecoverableAuthIOException>()
        every { exception.intent } returns mockIntent
        coEvery { mockCloudSyncUseCase.syncProfilesOnly(mockDrive, any()) } throws exception

        val result = useCase.execute { _, _ -> }

        assertTrue(result is PerformProfilesSyncUseCase.Result.RecoverableAuth)
        assertEquals(mockIntent, (result as PerformProfilesSyncUseCase.Result.RecoverableAuth).intent)
    }
}
