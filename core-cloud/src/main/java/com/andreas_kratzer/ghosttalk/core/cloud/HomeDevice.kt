package com.andreas_kratzer.ghosttalk.core.cloud

/**
 * Data class representing a Smart Home device.
 */
data class HomeDevice(
    val id: String,
    val name: String,
    val type: String = "LIGHT",
    val traits: List<String> = emptyList()
)
