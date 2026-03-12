package com.andreas_kratzer.ghosttalk.core.model

data class AudioOutputDevice(
    val address: String,
    val name: String,
    val type: Int,
    val isBuiltIn: Boolean
)
