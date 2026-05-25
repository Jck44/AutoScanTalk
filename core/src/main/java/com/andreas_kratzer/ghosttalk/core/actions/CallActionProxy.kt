package com.andreas_kratzer.ghosttalk.core.actions

import kotlinx.coroutines.flow.StateFlow

interface CallActionProxy {
    val isInCall: StateFlow<Boolean>
    fun startCall(contactName: String, contactPhone: String)
    fun hangUp()
    fun simulateIncomingCall(contactName: String, contactPhone: String)
    fun simulateOutgoingCall(contactName: String, contactPhone: String)
}
