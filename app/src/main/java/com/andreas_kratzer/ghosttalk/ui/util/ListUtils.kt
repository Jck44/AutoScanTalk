package com.andreas_kratzer.ghosttalk.ui.util

import com.andreas_kratzer.ghosttalk.model.SortOrder

interface ListableItem {
    val id: String
    val name: String
    val orderIndex: Int
    val createdAt: Long
}

fun <T : ListableItem> List<T>.filterAndSort(query: String, sortOrder: SortOrder): List<T> {
    val filtered = if (query.isBlank()) {
        this
    } else {
        this.filter { it.name.contains(query, ignoreCase = true) }
    }

    return when (sortOrder) {
        SortOrder.MANUAL -> filtered.sortedBy { it.orderIndex }
        SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
        SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
        SortOrder.A_Z -> filtered.sortedBy { it.name.lowercase() }
        SortOrder.Z_A -> filtered.sortedByDescending { it.name.lowercase() }
    }
}
