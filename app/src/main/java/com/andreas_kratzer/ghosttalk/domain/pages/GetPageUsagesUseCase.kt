package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetPageUsagesUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val templateRepository: TemplateRepository
) {
    suspend fun execute(pageId: String): List<UsageLocation> {
        val usages = mutableListOf<UsageLocation>()

        // Check all pages
        val allPages = pageRepository.getAllPagesFlow().first()
        allPages.forEach { page ->
            if (page.id != pageId) {
                page.buttonConfigs.forEach { config ->
                    val action = config?.buttonAction
                    if (action is NavigateToPageButtonAction && action.pageId == pageId) {
                        usages.add(UsageLocation.PageUsage(page.id, page.name, config.label))
                    }
                }
            }
        }

        // Check all templates
        val allTemplates = templateRepository.getAllTemplates().first()
        allTemplates.forEach { template ->
            template.buttonConfigs.forEach { config ->
                val action = config?.buttonAction
                if (action is NavigateToPageButtonAction && action.pageId == pageId) {
                    usages.add(UsageLocation.TemplateUsage(template.id, template.name, config.label ?: ""))
                }
            }
        }

        return usages
    }
}
