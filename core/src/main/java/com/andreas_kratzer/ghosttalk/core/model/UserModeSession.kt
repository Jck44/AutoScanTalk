package com.andreas_kratzer.ghosttalk.core.model

data class UserModeSession(
    val id: Long,
    val bookId: String,
    val startTime: Long,
    val endTime: Long
)
