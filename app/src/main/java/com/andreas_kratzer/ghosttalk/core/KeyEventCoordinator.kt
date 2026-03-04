package com.andreas_kratzer.ghosttalk.core

import android.view.KeyEvent
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates hardware key events (Volume keys, external switches) 
 * and determines if they should trigger an action based on settings.
 */
@Singleton
class KeyEventCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    /**
     * Returns true if the given [event] matches the configured activation triggers.
     */
    fun shouldActivate(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        
        val volumeActivate = settingsRepository.volumeKeysActivate
        val switchKey = settingsRepository.switchActivationKey.trim()
        val keyCode = event.keyCode

        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        val isSwitchKey = when (switchKey.lowercase()) {
            "space", "leertaste" -> keyCode == KeyEvent.KEYCODE_SPACE
            "enter", "return" -> keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            else -> {
                val pressedChar = event.displayLabel.toString()
                switchKey.isNotEmpty() && pressedChar.equals(switchKey, ignoreCase = true)
            }
        }

        return (volumeActivate && isVolumeKey) || isSwitchKey
    }
}
