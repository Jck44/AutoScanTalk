package com.andreas_kratzer.ghosttalk.domain

import android.content.Intent
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.Drive
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class PerformManualSyncUseCaseTest {

    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var cloudSyncUseCase: CloudSyncUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: PerformManualSyncUseCase

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        
        googleAuthManager = mockk(relaxed = true)
        cloudSyncUseCase = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        useCase = PerformManualSyncUseCase(googleAuthManager, cloudSyncUseCase, settingsRepository)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `execute returns Error if no credentials found`() = runTest {
        every { googleAuthManager.getGoogleCredential() } returns null

        val result = useCase.execute(SyncMode.TWO_WAY)

        assertTrue(result is PerformManualSyncUseCase.Result.Error)
        assertEquals("Keine Google-Anmeldedaten gefunden.", (result as PerformManualSyncUseCase.Result.Error).message)
    }

    @Test
    fun `execute returns Success on successful sync`() = runTest {
        val drive = mockk<Drive>(relaxed = true)
        every { googleAuthManager.getGoogleCredential() } returns mockk()
        every { settingsRepository.activeBookId } returns "book1"

        val result = useCase.execute(SyncMode.TWO_WAY, driveOverride = drive)

        assertTrue(result is PerformManualSyncUseCase.Result.Success)
        coVerify { cloudSyncUseCase.syncBook(drive, "book1", SyncMode.TWO_WAY) }
        verify { settingsRepository.lastSuccessfulSyncTime = any() }
    }

    @Test
    fun `execute returns RecoverableAuth on UserRecoverableAuthIOException`() = runTest {
        val drive = mockk<Drive>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        val recoverableException = UserRecoverableAuthException("Auth required", intent)
        every { googleAuthManager.getGoogleCredential() } returns mockk()
        val ioException = mockk<UserRecoverableAuthIOException>(relaxed = true)
        every { ioException.intent } returns intent
        coEvery { cloudSyncUseCase.syncBook(any(), any(), any()) } throws ioException

        val result = useCase.execute(SyncMode.TWO_WAY, driveOverride = drive)

        assertTrue("Expected RecoverableAuth but got $result", result is PerformManualSyncUseCase.Result.RecoverableAuth)
        if (result is PerformManualSyncUseCase.Result.RecoverableAuth) {
            assertTrue("Intent mismatch", intent === result.intent)
        }
    }
}
