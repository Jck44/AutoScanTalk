package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import javax.inject.Inject

class UpdatePageSettingsUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(
        pageId: String, 
        newName: String, 
        newScanPattern: String?, 
        newRowNames: List<String>,
        newRows: Int? = null,
        newColumns: Int? = null
    ): Page? {
        val page = pageRepository.getPageById(pageId)
        if (page != null) {
            val oldRows = page.rows
            val oldCols = page.columns
            val targetRows = newRows ?: oldRows
            val targetCols = newColumns ?: oldCols

            val updatedPage = page.copy(
                name = newName,
                scanPattern = newScanPattern,
                rowNames = newRowNames,
                rows = targetRows,
                columns = targetCols
            )
            pageRepository.updatePageSettingsOnly(updatedPage)
            bookRepository.updateLastModified(page.bookId)
            return updatedPage
        }
        return null
    }
}
