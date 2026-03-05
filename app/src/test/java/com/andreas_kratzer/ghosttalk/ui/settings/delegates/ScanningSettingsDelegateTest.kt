package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class ScanningSettingsDelegateTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var delegate: ScanningSettingsDelegate

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        delegate = ScanningSettingsDelegate(settingsRepository)
    }

    @Test
    fun `setScanDelayInput filters non-digits and enforces minimum`() {
        delegate.setScanDelayInput("a1b0c00d")
        verify { settingsRepository.scanDelayMillis = 1000L }

        delegate.setScanDelayInput("50")
        verify(exactly = 0) { settingsRepository.scanDelayMillis = 50L }
    }

    @Test
    fun `setHoldingTimeInput filters non-digits and allows zero`() {
        delegate.setHoldingTimeInput("5a00")
        verify { settingsRepository.holdingTimeMillis = 500L }

        delegate.setHoldingTimeInput("0")
        verify { settingsRepository.holdingTimeMillis = 0L }
    }

    @Test
    fun `setAutoStartScanning saves to repository`() {
        delegate.setAutoStartScanning(true)
        verify { settingsRepository.autoStartScanning = true }
    }
}
