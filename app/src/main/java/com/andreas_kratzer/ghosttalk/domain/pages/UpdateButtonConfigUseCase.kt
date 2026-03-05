package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import javax.inject.Inject

class UpdateButtonConfigUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(pageId: String, index: Int, newConfig: ButtonConfig?): com.andreas_kratzer.ghosttalk.model.Page? {
        val page = pageRepository.getPageById(pageId)
        if (page != null && index in page.buttonConfigs.indices) {
            val updatedConfigs = page.buttonConfigs.toMutableList()
            updatedConfigs[index] = newConfig
            val updatedPage = page.copy(buttonConfigs = updatedConfigs)
            pageRepository.updatePage(updatedPage)
            bookRepository.updateLastModified(page.bookId)
            return updatedPage
        }
        return null
    }
}
