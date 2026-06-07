package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateBluetoothDelayUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateHoldingTimeUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateScanCycleLimitUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateScanDelayUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanningSettingsDelegate @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateScanDelayUseCase: UpdateScanDelayUseCase,
    private val updateHoldingTimeUseCase: UpdateHoldingTimeUseCase,
    private val updateBluetoothDelayUseCase: UpdateBluetoothDelayUseCase,
    private val updateScanCycleLimitUseCase: UpdateScanCycleLimitUseCase
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

    fun setLimitScanCycles(enabled: Boolean) {
        settingsRepository.limitScanCycles = enabled
    }

    fun setScanCycleLimitInput(input: String) {
        updateScanCycleLimitUseCase(input)
    }

    fun setStaticRowEnabled(enabled: Boolean) {
        settingsRepository.staticRowEnabled = enabled
    }



    fun setLateClickThresholdInput(input: String) {
        input.toLongOrNull()?.let { settingsRepository.lateClickThresholdMillis = it }
    }

    fun setVocalSwitchEnabled(enabled: Boolean) {
        settingsRepository.isVocalSwitchEnabled = enabled
    }
}
