package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.Page
import javax.inject.Inject

class UpdatePageSettingsUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(pageId: String, newName: String, newScanPattern: String?, newRowNames: List<String>): Page? {
        val page = pageRepository.getPageById(pageId)
        if (page != null) {
            val updatedPage = page.copy(
                name = newName,
                scanPattern = newScanPattern,
                rowNames = newRowNames
            )
            pageRepository.updatePage(updatedPage)
            bookRepository.updateLastModified(page.bookId)
            return updatedPage
        }
        return null
    }
}
