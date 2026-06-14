package com.andreas_kratzer.ghosttalk.ui.pages.structure

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page

fun navigableButtons(
    page: Page,
    outgoingButtonIndices: Set<Int>,
    effectiveStartPageId: String?
): List<Pair<Int, ButtonConfig>> {
    return page.buttonConfigs.mapIndexedNotNull { index, btn ->
        if (btn != null && btn.isActive && btn.label.isNotBlank()) {
            val action = btn.buttonAction
            val isSelfLoop = when (action) {
                is NavigateToPageButtonAction -> {
                    val target = action.pageId.ifEmpty { effectiveStartPageId }
                    target == page.id
                }
                is NavigateToStartPageButtonAction -> {
                    effectiveStartPageId == page.id
                }
                else -> false
            }
            if (index !in outgoingButtonIndices && !isSelfLoop) {
                index to btn
            } else null
        } else null
    }
}
