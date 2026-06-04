package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CategoryInfo(
    val name: String,
    val buttonLabels: List<String>
)

@Serializable
data class RestructureAction(
    val type: String, // "MOVE_BUTTON", "SPLIT_PAGE", "DEACTIVATE_BUTTON"
    val rationale: String,
    val buttonLabel: String? = null,
    val sourcePageName: String? = null,
    val targetPageName: String? = null,
    val newCategories: List<CategoryInfo>? = null
)

@Serializable
data class BookRestructureProposal(
    val actions: List<RestructureAction>
)
