package com.andreas_kratzer.ghosttalk.core.util

import com.andreas_kratzer.ghosttalk.core.model.ListableItem
import com.andreas_kratzer.ghosttalk.core.model.SortOrder

fun <T : ListableItem> List<T>.filterAndSort(
    query: String,
    sortOrder: SortOrder,
    activeIds: Set<String> = emptySet(),
    startPageId: String? = null
): List<T> {
    val trimmedQuery = query.trim()
    val filtered = if (trimmedQuery.isBlank()) {
        this
    } else {
        this.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
    }

    val baseComparator = when (sortOrder) {
        SortOrder.MANUAL -> compareBy<T> { it.orderIndex }
        SortOrder.NEWEST -> compareByDescending<T> { it.createdAt }
        SortOrder.OLDEST -> compareBy<T> { it.createdAt }
        SortOrder.A_Z -> compareBy<T> { it.name.lowercase() }
        SortOrder.Z_A -> compareByDescending<T> { it.name.lowercase() }
        SortOrder.ACTIVE_FIRST -> compareByDescending<T> { activeIds.contains(it.id) }
            .thenBy { it.name.lowercase() }
        SortOrder.INACTIVE_FIRST -> compareBy<T> { activeIds.contains(it.id) }
            .thenBy { it.name.lowercase() }
    }

    val finalComparator = compareBy<T> { item ->
        when {
            item.id.startsWith("static_row_") -> 0
            item.id == startPageId -> 1
            else -> 2
        }
    }.then(baseComparator)

    return filtered.sortedWith(finalComparator)
}
