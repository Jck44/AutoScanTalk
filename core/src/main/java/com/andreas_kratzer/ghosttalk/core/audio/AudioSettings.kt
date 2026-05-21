package com.andreas_kratzer.ghosttalk.core.audio

interface AudioSettings {
    var bluetoothDelay: Long
    val bluetoothDelayFlow: kotlinx.coroutines.flow.StateFlow<Long>
    var recordingAudioSource: Int
    val recordingAudioSourceFlow: kotlinx.coroutines.flow.StateFlow<Int>
}
