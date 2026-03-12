package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.database.BookRepository
import com.andreas_kratzer.ghosttalk.core.database.PageRepository
import com.andreas_kratzer.ghosttalk.core.database.TemplateRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class DeletePageUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val templateRepository: TemplateRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(page: Page, deleteUsages: Boolean = false) {
        if (deleteUsages) {
            val pageId = page.id
            
            // Clear usages in all pages
            val allPages = pageRepository.getAllPages()
            allPages.forEach { otherPage ->
                val updatedConfigs = otherPage.buttonConfigs.map { config ->
                    val action = config?.buttonAction
                    if (action is NavigateToPageButtonAction && action.pageId == pageId) {
                        null
                    } else config
                }
                if (updatedConfigs != otherPage.buttonConfigs) {
                    pageRepository.updatePage(otherPage.copy(buttonConfigs = updatedConfigs))
                }
            }

            // Clear usages in all templates
            val allTemplates = templateRepository.getAllTemplates().first()
            allTemplates.forEach { template ->
                val updatedConfigs = template.buttonConfigs.map { config ->
                    val action = config?.buttonAction
                    if (action is NavigateToPageButtonAction && action.pageId == pageId) {
                        null
                    } else config
                }
                if (updatedConfigs != template.buttonConfigs) {
                    templateRepository.insert(template.copy(buttonConfigs = updatedConfigs))
                }
            }
        }

        pageRepository.deletePage(page)
        bookRepository.updateLastModified(page.bookId)
    }
}
