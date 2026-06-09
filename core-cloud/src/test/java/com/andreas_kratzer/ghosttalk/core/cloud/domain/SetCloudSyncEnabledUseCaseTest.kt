package com.andreas_kratzer.ghosttalk.core.cloud.domain


import androidx.work.WorkManager
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class SetCloudSyncEnabledUseCaseTest {

    private lateinit var settingsRepository: CloudSettings
    private lateinit var workManager: WorkManager
    private lateinit var useCase: SetCloudSyncEnabledUseCase

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0

        settingsRepository = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        useCase = SetCloudSyncEnabledUseCase(settingsRepository, workManager)
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `invoke with true enables setting and enqueues work`() {
        useCase(true)

        verify { settingsRepository.isDataCloudSyncEnabled = true }
        verify { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
    }

    @Test
    fun `invoke with false disables setting and cancels work`() {
        useCase(false)

        verify { settingsRepository.isDataCloudSyncEnabled = false }
        verify { workManager.cancelUniqueWork("CloudSyncWorker") }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `reschedule enqueues work when sync is enabled`() {
        every { settingsRepository.isDataCloudSyncEnabled } returns true
        every { settingsRepository.syncIntervalMinutes } returns 15
        every { settingsRepository.syncTargetType } returns "GOOGLE_DRIVE"

        useCase.reschedule()

        verify { workManager.enqueueUniquePeriodicWork("CloudSyncWorker", any(), any()) }
    }

    @Test
    fun `reschedule does not enqueue work when sync is disabled`() {
        every { settingsRepository.isDataCloudSyncEnabled } returns false

        useCase.reschedule()

        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `work request has connected network constraint when target is not SAF`() {
        every { settingsRepository.syncTargetType } returns "GOOGLE_DRIVE"
        every { settingsRepository.syncIntervalMinutes } returns 15
        val slot = io.mockk.slot<androidx.work.PeriodicWorkRequest>()
        every { workManager.enqueueUniquePeriodicWork(any(), any(), capture(slot)) } returns mockk()

        useCase(true)

        val request = slot.captured
        org.junit.Assert.assertEquals(androidx.work.NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `work request has no network constraint when target is SAF`() {
        every { settingsRepository.syncTargetType } returns "LOCAL_FOLDER_SAF"
        every { settingsRepository.syncIntervalMinutes } returns 15
        val slot = io.mockk.slot<androidx.work.PeriodicWorkRequest>()
        every { workManager.enqueueUniquePeriodicWork(any(), any(), capture(slot)) } returns mockk()

        useCase(true)

        val request = slot.captured
        org.junit.Assert.assertEquals(androidx.work.NetworkType.NOT_REQUIRED, request.workSpec.constraints.requiredNetworkType)
    }
}
