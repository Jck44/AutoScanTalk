package com.andreas_kratzer.ghosttalk.feature.settings.domain

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject

class UpdateScanCycleLimitUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(input: String) {
        val limit = input.toIntOrNull() ?: return
        if (limit in 1..99) {
            settingsRepository.scanCycleLimit = limit
        }
    }
}
