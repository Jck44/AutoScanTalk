package com.andreas_kratzer.ghosttalk.core.audio

interface AudioSettings {
    var bluetoothDelay: Long
    val bluetoothDelayFlow: kotlinx.coroutines.flow.StateFlow<Long>
    var recordingAudioSource: Int
    val recordingAudioSourceFlow: kotlinx.coroutines.flow.StateFlow<Int>
    var blockVolumeKeys: Boolean
    val blockVolumeKeysFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    var speakerVolume: Int
    val speakerVolumeFlow: kotlinx.coroutines.flow.StateFlow<Int>
    var headphoneVolume: Int
    val headphoneVolumeFlow: kotlinx.coroutines.flow.StateFlow<Int>

    var preferredMainSpeakerName: String?
    val preferredMainSpeakerNameFlow: kotlinx.coroutines.flow.StateFlow<String?>
    var preferredCueSpeakerName: String?
    val preferredCueSpeakerNameFlow: kotlinx.coroutines.flow.StateFlow<String?>
}
