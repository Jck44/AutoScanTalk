package com.andreas_kratzer.ghosttalk.domain.settings


import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class UpdateHoldingTimeUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: UpdateHoldingTimeUseCase

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        useCase = UpdateHoldingTimeUseCase(settingsRepository)
    }

    @Test
    fun `invoke with valid input updates holding time`() {
        useCase("200")
        verify { settingsRepository.holdingTimeMillis = 200L }
    }

    @Test
    fun `invoke with non-numeric input does not update settings`() {
        useCase("abc")
        verify(exactly = 0) { settingsRepository.holdingTimeMillis = any() }
    }

    @Test
    fun `invoke with negative numbers after cleaning updates settings`() {
        // Since the code uses .filter { it.isDigit() }, "-100" becomes "100"
        useCase("-100")
        verify { settingsRepository.holdingTimeMillis = 100L }
    }
}
