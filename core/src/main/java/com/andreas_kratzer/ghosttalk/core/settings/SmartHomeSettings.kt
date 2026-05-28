package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface SmartHomeSettings {
    var hueBridgeIp: String
    val hueBridgeIpFlow: StateFlow<String>
    
    var hueUsername: String
    val hueUsernameFlow: StateFlow<String>

    var hueBridgeFingerprint: String
    val hueBridgeFingerprintFlow: StateFlow<String>

    var hueCachedDevices: String
    val hueCachedDevicesFlow: StateFlow<String>
}

