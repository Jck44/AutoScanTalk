package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanningSettingsDelegate @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    fun setAutoStartScanning(enabled: Boolean) {
        settingsRepository.autoStartScanning = enabled
    }

    fun setScanDelayInput(input: String) {
        val digitsOnly = input.filter { it.isDigit() }
        val parsed = digitsOnly.toLongOrNull()
        if (parsed != null && parsed >= 100L) {
            settingsRepository.scanDelayMillis = parsed
        }
    }

    fun setResumeScanningFromStart(fromStart: Boolean) {
        settingsRepository.resumeScanningFromStart = fromStart
    }

    fun setDefaultScanPattern(pattern: String) {
        settingsRepository.defaultScanPattern = pattern
    }

    fun setHoldingTimeInput(input: String) {
        val digitsOnly = input.filter { it.isDigit() }
        val parsed = digitsOnly.toLongOrNull()
        if (parsed != null && parsed >= 0L) {
            settingsRepository.holdingTimeMillis = parsed
        }
    }

    fun setBluetoothDelay(delayMs: String) {
        delayMs.toLongOrNull()?.let {
            settingsRepository.bluetoothDelay = it
        }
    }

    fun setSmartPredictionDelayInput(input: String) {
        val digitsOnly = input.filter { it.isDigit() }
        val parsed = digitsOnly.toLongOrNull()
        if (parsed != null && parsed >= 0L) {
            settingsRepository.smartPredictionDelayMillis = parsed
        }
    }

    fun setSmartPredictionEnabled(enabled: Boolean) {
        settingsRepository.isSmartPredictionEnabled = enabled
    }
}
