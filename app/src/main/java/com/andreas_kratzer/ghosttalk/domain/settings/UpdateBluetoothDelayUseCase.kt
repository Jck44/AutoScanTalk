package com.andreas_kratzer.ghosttalk.domain.settings

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import javax.inject.Inject

class UpdateBluetoothDelayUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(delayMs: String) {
        delayMs.toLongOrNull()?.let {
            settingsRepository.bluetoothDelay = it
        }
    }
}
