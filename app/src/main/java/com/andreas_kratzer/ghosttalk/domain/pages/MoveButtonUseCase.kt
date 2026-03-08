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
        // Ensure we have 49 slots
        while (newButtonConfigs.size < com.andreas_kratzer.ghosttalk.ui.util.GridUtils.TOTAL_SLOTS) {
            newButtonConfigs.add(null)
        }
        
        if (fromIndex !in newButtonConfigs.indices || toIndex !in newButtonConfigs.indices) return page

        // Spatial Swap instead of Shift
        val fromConfig = newButtonConfigs[fromIndex]
        val toConfig = newButtonConfigs[toIndex]
        
        newButtonConfigs[toIndex] = fromConfig
        newButtonConfigs[fromIndex] = toConfig
        
        val updatedPage = page.copy(buttonConfigs = newButtonConfigs)
        
        pageRepository.updatePage(updatedPage)
        bookRepository.updateLastModified(page.bookId)
        return updatedPage
    }
}
