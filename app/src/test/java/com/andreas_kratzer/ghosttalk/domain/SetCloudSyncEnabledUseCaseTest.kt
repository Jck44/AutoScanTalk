package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.domain.auth.SetCloudSyncEnabledUseCase

import androidx.work.WorkManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class SetCloudSyncEnabledUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
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

        verify { settingsRepository.isCloudSyncEnabled = true }
        verify { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
    }

    @Test
    fun `invoke with false disables setting and cancels work`() {
        useCase(false)

        verify { settingsRepository.isCloudSyncEnabled = false }
        verify { workManager.cancelUniqueWork("CloudSyncWorker") }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }
}
