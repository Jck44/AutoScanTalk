package com.example.gostalk.model

data class AudioOutputDevice(
    val address: String,
    val name: String,
    val type: Int,
    val isBuiltIn: Boolean
)
