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
                val hasUsage = page.buttonConfigs.any { config ->
                    val action = config?.buttonAction
                    action is NavigateToPageButtonAction && action.pageId == pageId
                }
                if (hasUsage) {
                    usages.add(UsageLocation.PageUsage(page.id, page.name))
                }
            }
        }

        // Check all templates
        val allTemplates = templateRepository.getAllTemplates().first()
        allTemplates.forEach { template ->
            val hasUsage = template.buttonConfigs.any { config ->
                val action = config?.buttonAction
                action is NavigateToPageButtonAction && action.pageId == pageId
            }
            if (hasUsage) {
                usages.add(UsageLocation.TemplateUsage(template.id, template.name))
            }
        }

        return usages
    }
}
