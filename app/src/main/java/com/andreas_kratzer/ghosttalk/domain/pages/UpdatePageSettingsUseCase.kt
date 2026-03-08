package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.Page
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

            // Re-map button configurations to maintain their logical (r, c) position
            val newButtonConfigs = MutableList<com.andreas_kratzer.ghosttalk.model.ButtonConfig?>(targetRows * targetCols) { null }
            
            for (r in 0 until minOf(oldRows, targetRows)) {
                for (c in 0 until minOf(oldCols, targetCols)) {
                    val oldIndex = r * oldCols + c
                    val newIndex = r * targetCols + c
                    if (oldIndex < page.buttonConfigs.size) {
                        newButtonConfigs[newIndex] = page.buttonConfigs[oldIndex]
                    }
                }
            }

            val updatedPage = page.copy(
                name = newName,
                scanPattern = newScanPattern,
                rowNames = newRowNames,
                rows = targetRows,
                columns = targetCols,
                buttonConfigs = newButtonConfigs
            )
            pageRepository.updatePage(updatedPage)
            bookRepository.updateLastModified(page.bookId)
            return updatedPage
        }
        return null
    }
}
