package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.settings.UpdateHoldingTimeUseCase
import com.andreas_kratzer.ghosttalk.domain.settings.UpdateScanDelayUseCase
import com.andreas_kratzer.ghosttalk.domain.settings.UpdateBluetoothDelayUseCase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class ScanningSettingsDelegateTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var updateScanDelayUseCase: UpdateScanDelayUseCase
    private lateinit var updateHoldingTimeUseCase: UpdateHoldingTimeUseCase
    private lateinit var updateBluetoothDelayUseCase: UpdateBluetoothDelayUseCase
    private lateinit var delegate: ScanningSettingsDelegate

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        updateScanDelayUseCase = mockk(relaxed = true)
        updateHoldingTimeUseCase = mockk(relaxed = true)
        updateBluetoothDelayUseCase = mockk(relaxed = true)
        delegate = ScanningSettingsDelegate(
            settingsRepository, 
            updateScanDelayUseCase, 
            updateHoldingTimeUseCase,
            updateBluetoothDelayUseCase
        )
    }

    @Test
    fun `setScanDelayInput calls use case`() {
        delegate.setScanDelayInput("1000")
        verify { updateScanDelayUseCase("1000") }
    }

    @Test
    fun `setHoldingTimeInput calls use case`() {
        delegate.setHoldingTimeInput("500")
        verify { updateHoldingTimeUseCase("500") }
    }

    @Test
    fun `setBluetoothDelay calls use case`() {
        delegate.setBluetoothDelay("150")
        verify { updateBluetoothDelayUseCase("150") }
    }

    @Test
    fun `setAutoStartScanning saves to repository`() {
        delegate.setAutoStartScanning(true)
        verify { settingsRepository.autoStartScanning = true }
    }
}
