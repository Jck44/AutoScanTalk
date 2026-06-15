package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.ui.geometry.Offset
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlin.math.sqrt

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

fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
    if (l2 == 0f) return sqrt((p.x - a.x) * (p.x - a.x) + (p.y - a.y) * (p.y - a.y))
    var t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2
    t = t.coerceIn(0f, 1f)
    val projectionX = a.x + t * (b.x - a.x)
    val projectionY = a.y + t * (b.y - a.y)
    return sqrt((p.x - projectionX) * (p.x - projectionX) + (p.y - projectionY) * (p.y - projectionY))
}
