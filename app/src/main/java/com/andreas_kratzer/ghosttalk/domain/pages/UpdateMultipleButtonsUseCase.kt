package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import javax.inject.Inject

/**
 * Use case to update the [isActive] state of multiple buttons across different pages and templates.
 */
class UpdateMultipleButtonsUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val templateRepository: TemplateRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(usages: List<UsageLocation>, isActive: Boolean) {
        // Group by page/template ID to avoid multiple updates for the same entity
        val pageUsages = usages.filterIsInstance<UsageLocation.PageUsage>()
            .groupBy { it.id }
        
        val templateUsages = usages.filterIsInstance<UsageLocation.TemplateUsage>()
            .groupBy { it.id }

        // Update pages
        pageUsages.forEach { (pageId, usages) ->
            val page = pageRepository.getPageById(pageId)
            if (page != null) {
                val updatedConfigs = page.buttonConfigs.toMutableList()
                var changed = false
                usages.forEach { usage ->
                    if (usage.index in updatedConfigs.indices) {
                        val config = updatedConfigs[usage.index]
                        if (config != null) {
                            updatedConfigs[usage.index] = config.copy(isActive = isActive)
                            changed = true
                        }
                    }
                }
                if (changed) {
                    pageRepository.updatePage(page.copy(buttonConfigs = updatedConfigs))
                    bookRepository.updateLastModified(page.bookId)
                }
            }
        }

        // Update templates
        templateUsages.forEach { (templateId, usages) ->
            val template = templateRepository.getById(templateId)
            if (template != null) {
                val updatedConfigs = template.buttonConfigs.toMutableList()
                var changed = false
                usages.forEach { usage ->
                    if (usage.index in updatedConfigs.indices) {
                        val config = updatedConfigs[usage.index]
                        if (config != null) {
                            updatedConfigs[usage.index] = config.copy(isActive = isActive)
                            changed = true
                        }
                    }
                }
                if (changed) {
                    templateRepository.update(template.copy(buttonConfigs = updatedConfigs))
                }
            }
        }
    }
}
