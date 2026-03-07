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
     * [isUserMode] determines if the app is currently in user mode.
     */
    fun shouldActivate(event: KeyEvent, isUserMode: Boolean): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        
        val volumeActivate = settingsRepository.volumeKeysActivate
        val switchKey = settingsRepository.switchActivationKey.trim()
        val keyCode = event.keyCode

        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        // Volume keys should only trigger if the setting is active AND we are in User Mode.
        // If not in User Mode, they should perform their default system action (adjust volume).
        if (isVolumeKey) {
            return volumeActivate && isUserMode
        }

        val isSwitchKey = when (switchKey.lowercase()) {
            "space", "leertaste" -> keyCode == KeyEvent.KEYCODE_SPACE
            "enter", "return" -> keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            else -> {
                val pressedChar = event.displayLabel.toString()
                switchKey.isNotEmpty() && pressedChar.equals(switchKey, ignoreCase = true)
            }
        }

        // Switch keys should also typically only trigger in user mode to avoid interfering 
        // with text input or navigation in management views.
        return isSwitchKey && isUserMode
    }
}
