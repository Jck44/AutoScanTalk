package com.andreas_kratzer.ghosttalk.core.util

/**
 * Singleton to track the source of UI interaction events.
 * Since key dispatching runs synchronously with Compose click triggering,
 * we can determine if a click was triggered by a key event.
 */
object InputSourceTracker {
    @Volatile
    var isHardwareTriggered: Boolean = false
}
