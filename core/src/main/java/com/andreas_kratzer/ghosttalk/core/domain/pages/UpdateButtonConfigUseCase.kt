package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import javax.inject.Inject

class UpdateButtonConfigUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(pageId: String, index: Int, newConfig: ButtonConfig?): Page? {
        val page = pageRepository.getPageById(pageId)
        if (page != null && index in page.buttonConfigs.indices) {
            val updatedConfigs = page.buttonConfigs.toMutableList()
            val oldConfig = updatedConfigs[index]
            val now = System.currentTimeMillis()
            val updatedPage = if (newConfig == null && oldConfig != null) {
                // Button deleted: Set page updatedAt to max(now, oldConfig.updatedAt + 1)
                val newPageTime = maxOf(now, oldConfig.updatedAt + 1L)
                updatedConfigs[index] = null
                page.copy(
                    updatedAt = newPageTime,
                    buttonConfigs = updatedConfigs
                )
            } else {
                updatedConfigs[index] = newConfig?.copy(updatedAt = now)
                page.copy(buttonConfigs = updatedConfigs)
            }
            pageRepository.updatePage(updatedPage)
            bookRepository.updateLastModified(page.bookId)
            return updatedPage
        }
        return null
    }
}
