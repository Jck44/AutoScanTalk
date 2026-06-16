package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.Page
import javax.inject.Inject

data class ButtonHit(
    val index: Int,
    val label: String
)

data class PageSearchResult(
    val pageId: String,
    val matchedOnName: Boolean,
    val buttonHits: List<ButtonHit>
)

class SearchPagesUseCase @Inject constructor() {
    fun execute(pages: List<Page>, query: String): List<PageSearchResult> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return emptyList()

        return pages.mapNotNull { page ->
            val matchedOnName = page.name.contains(trimmedQuery, ignoreCase = true)
            val buttonHits = page.buttonConfigs.mapIndexedNotNull { index, btn ->
                if (btn != null && btn.isActive && btn.label.contains(trimmedQuery, ignoreCase = true)) {
                    ButtonHit(index, btn.label)
                } else null
            }

            if (matchedOnName || buttonHits.isNotEmpty()) {
                PageSearchResult(
                    pageId = page.id,
                    matchedOnName = matchedOnName,
                    buttonHits = buttonHits
                )
            } else null
        }
    }
}
