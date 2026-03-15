package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface SmartHomeSettings {
    var googleHomeProjectId: String
    val googleHomeProjectIdFlow: StateFlow<String>
    
    var hueBridgeIp: String
    val hueBridgeIpFlow: StateFlow<String>
    
    var hueUsername: String
    val hueUsernameFlow: StateFlow<String>

    var hueAccessToken: String
    val hueAccessTokenFlow: StateFlow<String>

    var hueRefreshToken: String
    val hueRefreshTokenFlow: StateFlow<String>
}
