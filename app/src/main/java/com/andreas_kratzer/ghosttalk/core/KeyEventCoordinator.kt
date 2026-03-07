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
    private var keyBuffer = ""
    private var lastEventTime = 0L
    private val bufferTimeout = 1000L // 1 second timeout

    /**
     * Returns true if the given [event] matches the configured activation triggers.
     * [isUserMode] determines if the app is currently in user mode.
     */
    fun shouldActivate(event: KeyEvent, isUserMode: Boolean): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        
        val currentTime = System.currentTimeMillis()
        val volumeActivate = settingsRepository.volumeKeysActivate
        val switchKey = settingsRepository.switchActivationKey.trim()
        val keyCode = event.keyCode

        // Log the key event for debugging
        val displayLabel = event.displayLabel
        val characters = event.characters
        android.util.Log.d("KeyEventCoordinator", "KeyEvent: keyCode=$keyCode, label='$displayLabel', chars='$characters', action=${event.action}")

        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        // Volume keys should only trigger if the setting is active AND we are in User Mode.
        // If not in User Mode, they should perform their default system action (adjust volume).
        if (isVolumeKey) {
            return volumeActivate && isUserMode
        }

        // Buffer management for sequence matching (e.g., "~3")
        if (currentTime - lastEventTime > bufferTimeout) {
            keyBuffer = ""
        }
        lastEventTime = currentTime

        // Use unicode char to handle modifiers (e.g., Shift + ` = ~)
        val unicodeChar = event.getUnicodeChar(event.metaState)
        val pressedChar = if (unicodeChar != 0) {
            unicodeChar.toChar().toString()
        } else if (event.displayLabel.code != 0) {
            event.displayLabel.toString()
        } else {
            ""
        }
        
        if (pressedChar.isNotEmpty()) {
            keyBuffer += pressedChar
            if (keyBuffer.length > 10) { // Keep buffer size reasonable
                keyBuffer = keyBuffer.takeLast(10)
            }
            android.util.Log.d("KeyEventCoordinator", "Unicode: $unicodeChar ('$pressedChar'), Current buffer: '$keyBuffer'")
        }

        val isSwitchKey = when (switchKey.lowercase()) {
            "space", "leertaste" -> keyCode == KeyEvent.KEYCODE_SPACE
            "enter", "return" -> keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            else -> {
                // Exact match or sequence match
                val exactMatch = pressedChar.isNotEmpty() && pressedChar.equals(switchKey, ignoreCase = true)
                val sequenceMatch = keyBuffer.endsWith(switchKey, ignoreCase = true)
                
                if (sequenceMatch) {
                    android.util.Log.d("KeyEventCoordinator", "Sequence match found: '$switchKey' in '$keyBuffer'")
                    keyBuffer = "" // Reset buffer after match
                }
                
                exactMatch || sequenceMatch
            }
        }

        // Switch keys should also typically only trigger in user mode to avoid interfering 
        // with text input or navigation in management views.
        return isSwitchKey && isUserMode
    }
}
