package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import javax.inject.Inject

/**
 * Use case to identify all page IDs that are currently targets of at least one active
 * navigation button on any page or template.
 */
class IdentifyActivePageLinksUseCase @Inject constructor() {
    fun execute(pages: List<Page>, templates: List<PageTemplate>): Set<String> {
        val activeTargetPageIds = mutableSetOf<String>()

        // Check active buttons on all pages
        pages.forEach { page ->
            page.buttonConfigs.forEach { config ->
                if (config != null && config.isActive) {
                    val action = config.buttonAction
                    if (action is NavigateToPageButtonAction) {
                        activeTargetPageIds.add(action.pageId)
                    }
                }
            }
        }

        // Check active buttons on all templates
        templates.forEach { template ->
            template.buttonConfigs.forEach { config ->
                if (config != null && config.isActive) {
                    val action = config.buttonAction
                    if (action is NavigateToPageButtonAction) {
                        activeTargetPageIds.add(action.pageId)
                    }
                }
            }
        }

        return activeTargetPageIds
    }
}
