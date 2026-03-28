package com.andreas_kratzer.ghosttalk.ui.util

import com.andreas_kratzer.ghosttalk.core.model.ListableItem
import com.andreas_kratzer.ghosttalk.core.model.SortOrder

fun <T : ListableItem> List<T>.filterAndSort(
    query: String,
    sortOrder: SortOrder,
    activeIds: Set<String> = emptySet()
): List<T> {
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
        SortOrder.ACTIVE_FIRST -> filtered.sortedWith(
            compareByDescending<T> { activeIds.contains(it.id) }
                .thenBy { it.name.lowercase() }
        )
        SortOrder.INACTIVE_FIRST -> filtered.sortedWith(
            compareBy<T> { activeIds.contains(it.id) }
                .thenBy { it.name.lowercase() }
        )
    }
}
