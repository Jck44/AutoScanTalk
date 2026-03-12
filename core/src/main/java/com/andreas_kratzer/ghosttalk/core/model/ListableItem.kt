package com.andreas_kratzer.ghosttalk.core.model

interface ListableItem {
    val id: String
    val name: String
    val orderIndex: Int
    val createdAt: Long
}
