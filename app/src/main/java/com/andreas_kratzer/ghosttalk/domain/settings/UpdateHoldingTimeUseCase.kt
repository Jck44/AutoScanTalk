package com.andreas_kratzer.ghosttalk.domain.settings

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject

class UpdateHoldingTimeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(input: String) {
        val digitsOnly = input.filter { it.isDigit() }
        val parsed = digitsOnly.toLongOrNull()
        if (parsed != null && parsed >= 0L) {
            settingsRepository.holdingTimeMillis = parsed
        }
    }
}
