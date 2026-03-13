package com.andreas_kratzer.ghosttalk.feature.settings.domain

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class UpdateScanDelayUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: UpdateScanDelayUseCase

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        useCase = UpdateScanDelayUseCase(settingsRepository)
    }

    @Test
    fun `invoke with valid input updates scan delay`() {
        useCase("500")
        verify { settingsRepository.scanDelayMillis = 500L }
    }

    @Test
    fun `invoke with invalid input does not update scan delay`() {
        useCase("abc")
        verify(exactly = 0) { settingsRepository.scanDelayMillis = any() }
    }

    @Test
    fun `invoke with input below 100ms does not update scan delay`() {
        useCase("99")
        verify(exactly = 0) { settingsRepository.scanDelayMillis = any() }
    }
}
