package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ButtonTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val buttonConfig: ButtonConfig,
    val isBuiltIn: Boolean = false,
    val orderIndex: Int = 0
)
