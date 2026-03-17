package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ActionLogEntry(val message: String, val timestamp: Long)
