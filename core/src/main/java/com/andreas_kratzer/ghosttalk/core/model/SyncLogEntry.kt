package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SyncLogEntry(
    val message: String,
    val timestamp: Long,
    val bookId: String? = null,
    val bookName: String? = null,
    val isError: Boolean = false
)
