package com.andreas_kratzer.ghosttalk.model

data class AudioOutputDevice(
    val address: String,
    val name: String,
    val type: Int,
    val isBuiltIn: Boolean
)
