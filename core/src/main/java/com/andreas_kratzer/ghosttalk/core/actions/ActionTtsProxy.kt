package com.andreas_kratzer.ghosttalk.core.actions

interface ActionTtsProxy {
    val isReady: Boolean
    var isReadingNotification: Boolean
    fun speakRouted(
        text: String,
        deviceAddress: String?,
        queueMode: Int = 0, 
        isForCues: Boolean = false,
        onDone: (() -> Unit)? = null
    )
}
