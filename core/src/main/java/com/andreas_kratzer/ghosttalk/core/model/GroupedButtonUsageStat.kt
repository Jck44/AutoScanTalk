package com.andreas_kratzer.ghosttalk.core.model

/**
 * Represents a grouped usage statistic entry, combining multiple [ButtonUsageStat]
 * items based on their label and action.
 */
data class GroupedButtonUsageStat(
    val label: String,
    val actionJson: String,
    val totalCount: Long,
    val lastUsedAt: Long,
    val children: List<ButtonUsageStat>
)
