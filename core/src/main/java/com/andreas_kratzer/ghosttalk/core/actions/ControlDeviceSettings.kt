package com.andreas_kratzer.ghosttalk.core.actions

interface ControlDeviceSettings {
    var cuesAudioDeviceAddress: String?
    val cuesAudioDeviceAddressFlow: kotlinx.coroutines.flow.StateFlow<String?>
    var ttsAudioDeviceAddress: String?
    val ttsAudioDeviceAddressFlow: kotlinx.coroutines.flow.StateFlow<String?>
    var isNotificationReadingEnabled: Boolean
    val isNotificationReadingEnabledFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    var monitoredNotificationApps: Set<String>
    val monitoredNotificationAppsFlow: kotlinx.coroutines.flow.StateFlow<Set<String>>
    var autoReadMode: com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode
    val autoReadModeFlow: kotlinx.coroutines.flow.StateFlow<com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode>
    var autoReadOnlyInUserMode: Boolean
    val autoReadOnlyInUserModeFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    var autoReadInStandby: Boolean
    val autoReadInStandbyFlow: kotlinx.coroutines.flow.StateFlow<Boolean>

    var simulateCallsEnabled: Boolean
    val simulateCallsEnabledFlow: kotlinx.coroutines.flow.StateFlow<Boolean>

    var speakerVolume: Int
    val speakerVolumeFlow: kotlinx.coroutines.flow.StateFlow<Int>
    var headphoneVolume: Int
    val headphoneVolumeFlow: kotlinx.coroutines.flow.StateFlow<Int>

    fun getDeviceName(persistentId: String): String?
    fun saveDeviceName(persistentId: String, name: String)
    fun cleanupDeviceCache(keepPersistentIds: Set<String>)
    fun getCachedDevices(): Map<String, String>
}
