package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface ScanningSettings {
    val autoStartScanningFlow: StateFlow<Boolean>
    val scanDelayFlow: StateFlow<Long>
    val resumeScanningFromStartFlow: StateFlow<Boolean>
    val defaultScanPatternFlow: StateFlow<String>
    
    var autoStartScanning: Boolean
    var scanDelayMillis: Long
    var resumeScanningFromStart: Boolean
    var defaultScanPattern: String
    
    val cuesAudioDeviceAddressFlow: StateFlow<String?>
    var cuesAudioDeviceAddress: String?
}
