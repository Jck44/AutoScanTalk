package com.andreas_kratzer.ghosttalk.core.audio

interface AudioSettings {
    var bluetoothDelay: Long
    val bluetoothDelayFlow: kotlinx.coroutines.flow.StateFlow<Long>
}
