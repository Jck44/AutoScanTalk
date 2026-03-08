package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.Page
import javax.inject.Inject

class MoveButtonUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(pageId: String, fromIndex: Int, toIndex: Int): Page? {
        val page = pageRepository.getPageById(pageId) ?: return null
        if (fromIndex == toIndex) return page
        
        val newButtonConfigs = page.buttonConfigs.toMutableList()
        if (fromIndex !in newButtonConfigs.indices || toIndex !in newButtonConfigs.indices) return page

        // Simple shift/swap logic. Here we use "insert before target" style like a list.
        val config = newButtonConfigs.removeAt(fromIndex)
        newButtonConfigs.add(toIndex, config)

        val updatedPage = page.copy(buttonConfigs = newButtonConfigs)
        
        pageRepository.updatePage(updatedPage)
        bookRepository.updateLastModified(page.bookId)
        return updatedPage
    }
}
