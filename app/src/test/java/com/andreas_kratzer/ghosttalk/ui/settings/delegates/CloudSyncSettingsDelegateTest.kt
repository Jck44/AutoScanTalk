package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.app.Application
import androidx.work.WorkManager
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.CloudSyncUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncSettingsDelegateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var application: Application
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var cloudSyncUseCase: CloudSyncUseCase
    private lateinit var workManager: WorkManager
    private lateinit var delegate: CloudSyncSettingsDelegate

    @Before
    fun setup() {
        application = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        googleAuthManager = mockk(relaxed = true)
        cloudSyncUseCase = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        
        every { settingsRepository.syncIntervalMinutes } returns 15L
        
        delegate = CloudSyncSettingsDelegate(
            application,
            settingsRepository,
            googleAuthManager,
            cloudSyncUseCase,
            workManager
        )
    }

    @Test
    fun `setCloudSyncEnabled schedules or cancels work`() {
        delegate.setCloudSyncEnabled(true)
        verify { settingsRepository.isCloudSyncEnabled = true }
        // Verify scheduling
        verify { workManager.enqueueUniquePeriodicWork("CloudSyncWorker", any(), any()) }

        delegate.setCloudSyncEnabled(false)
        verify { settingsRepository.isCloudSyncEnabled = false }
        // Verify cancellation
        verify { workManager.cancelUniqueWork("CloudSyncWorker") }
    }

    @Test
    fun `performManualSync returns early if no credentials`() = runTest {
        every { googleAuthManager.getGoogleCredential() } returns null
        delegate.performManualSync(com.andreas_kratzer.ghosttalk.domain.SyncMode.TWO_WAY, testScope)
        io.mockk.coVerify(exactly = 0) { cloudSyncUseCase.syncBook(any(), any(), any()) }
    }

    @Test
    fun `signIn handles failure correctly`() = runTest {
        val activity = mockk<android.app.Activity>(relaxed = true)
        io.mockk.coEvery { googleAuthManager.signIn(activity) } returns false
        
        delegate.signIn(activity, testScope)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assert(delegate.signInErrorMessage.value?.contains("fehlgeschlagen") == true)
    }
}
