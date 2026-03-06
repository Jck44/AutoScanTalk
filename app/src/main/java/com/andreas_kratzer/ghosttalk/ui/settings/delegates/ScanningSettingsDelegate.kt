package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.settings.UpdateBluetoothDelayUseCase
import com.andreas_kratzer.ghosttalk.domain.settings.UpdateHoldingTimeUseCase
import com.andreas_kratzer.ghosttalk.domain.settings.UpdateScanDelayUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanningSettingsDelegate @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateScanDelayUseCase: UpdateScanDelayUseCase,
    private val updateHoldingTimeUseCase: UpdateHoldingTimeUseCase,
    private val updateBluetoothDelayUseCase: UpdateBluetoothDelayUseCase
) {
    fun setAutoStartScanning(enabled: Boolean) {
        settingsRepository.autoStartScanning = enabled
    }

    fun setScanDelayInput(input: String) {
        updateScanDelayUseCase(input)
    }

    fun setResumeScanningFromStart(fromStart: Boolean) {
        settingsRepository.resumeScanningFromStart = fromStart
    }

    fun setDefaultScanPattern(pattern: String) {
        settingsRepository.defaultScanPattern = pattern
    }

    fun setHoldingTimeInput(input: String) {
        updateHoldingTimeUseCase(input)
    }

    fun setBluetoothDelay(delayMs: String) {
        updateBluetoothDelayUseCase(delayMs)
    }
}
