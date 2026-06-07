package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SettingsProfile(
    val id: String,
    val name: String,
    val config: ProfileConfig,
    val profileVersionSequence: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
