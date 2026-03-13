package com.andreas_kratzer.ghosttalk.core.actions

interface SpeechSettings {
    var cuesAudioDeviceAddress: String?
    val cuesAudioDeviceAddressFlow: kotlinx.coroutines.flow.StateFlow<String?>
    var ttsAudioDeviceAddress: String?
    val ttsAudioDeviceAddressFlow: kotlinx.coroutines.flow.StateFlow<String?>
}
