package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface ScanningSettings {
    var autoStartScanning: Boolean
    var scanDelayMillis: Long
    var resumeScanningFromStart: Boolean
    var defaultScanPattern: String
    var holdingTimeMillis: Long
    var switchActivationKey: String
    var volumeKeysActivate: Boolean
    var bluetoothDelay: Long
    
    val autoStartScanningFlow: StateFlow<Boolean>
    val scanDelayFlow: StateFlow<Long>
    val resumeScanningFromStartFlow: StateFlow<Boolean>
    val defaultScanPatternFlow: StateFlow<String>
    val holdingTimeMillisFlow: StateFlow<Long>
    val switchActivationKeyFlow: StateFlow<String>
    val volumeKeysActivateFlow: StateFlow<Boolean>
    val bluetoothDelayFlow: StateFlow<Long>
    val cuesAudioDeviceAddressFlow: StateFlow<String?>
    var cuesAudioDeviceAddress: String?
}
