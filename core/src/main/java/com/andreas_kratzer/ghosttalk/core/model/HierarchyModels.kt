package com.andreas_kratzer.ghosttalk.core.model

import kotlinx.serialization.Serializable

@Serializable
data class HierarchyPageNode(
    val name: String,
    val description: String,
    val subpages: List<String> = emptyList(),
    val sourcePageName: String? = null,
    val buttonIds: List<String> = emptyList()
)

@Serializable
data class BookHierarchyProposal(
    val pages: List<HierarchyPageNode>,
    val unmappedButtonIds: List<String> = emptyList()
)

@Serializable
data class PageButtonAction(
    val type: String, // "MOVE_BUTTON", "CREATE_NAV_BUTTON", "DEACTIVATE_BUTTON"
    val buttonLabel: String,
    val rationale: String,
    val buttonId: String? = null,
    val sourcePageName: String? = null,
    val targetPageName: String? = null, // for CREATE_NAV_BUTTON
    val targetPlacementDescription: String? = null
)

@Serializable
data class PageLayoutProposal(
    val pageName: String,
    val actions: List<PageButtonAction>
)
