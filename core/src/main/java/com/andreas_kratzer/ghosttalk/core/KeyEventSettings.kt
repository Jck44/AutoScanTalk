package com.andreas_kratzer.ghosttalk.core

interface KeyEventSettings {
    var volumeKeysActivate: Boolean
    var switchActivationKey: String
    
    val volumeKeysActivateFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    val switchActivationKeyFlow: kotlinx.coroutines.flow.StateFlow<String>
}
