package com.andreas_kratzer.ghosttalk.domain.settings

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class UpdateBluetoothDelayUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val useCase = UpdateBluetoothDelayUseCase(settingsRepository)

    @Test
    fun `invoke updates repository with valid long`() {
        useCase("150")
        verify { settingsRepository.bluetoothDelay = 150L }
    }

    @Test
    fun `invoke does nothing with invalid input`() {
        useCase("invalid")
        verify(exactly = 0) { settingsRepository.bluetoothDelay = any() }
    }
}
