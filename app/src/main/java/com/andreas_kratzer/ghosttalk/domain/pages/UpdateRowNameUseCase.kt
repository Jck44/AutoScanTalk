package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.database.BookRepository
import com.andreas_kratzer.ghosttalk.core.database.PageRepository
import javax.inject.Inject

class UpdateRowNameUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(pageId: String, rowIndex: Int, newName: String): Page? {
        val page = pageRepository.getPageById(pageId)
        if (page != null) {
            if (rowIndex < 0 || rowIndex >= page.rows) return null
            
            val updatedNames = page.rowNames.toMutableList()
            while (updatedNames.size <= rowIndex) {
                updatedNames.add("Zeile ${updatedNames.size + 1}")
            }
            updatedNames[rowIndex] = newName
            
            val updatedPage = page.copy(rowNames = updatedNames)
            pageRepository.updatePageSettingsOnly(updatedPage)
            bookRepository.updateLastModified(page.bookId)
            return updatedPage
        }
        return null
    }
}
