package com.andreas_kratzer.ghosttalk.core.actions

interface ControlDeviceSettings {
    val cuesAudioDeviceAddress: String?
    val ttsAudioDeviceAddress: String?
    val isNotificationReadingEnabled: Boolean
    val monitoredNotificationApps: Set<String>
}
