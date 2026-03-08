package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.Page
import javax.inject.Inject

class MoveRowUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(pageId: String, fromRow: Int, toRow: Int): Page? {
        val page = pageRepository.getPageById(pageId) ?: return null
        if (fromRow == toRow) return page

        val rows = page.rows
        val cols = page.columns
        
        val newButtonConfigs = page.buttonConfigs.toMutableList()
        val fromStart = fromRow * cols
        val rowToMove = newButtonConfigs.subList(fromStart, fromStart + cols).toList()
        
        // Remove the row
        repeat(cols) { newButtonConfigs.removeAt(fromStart) }
        
        // Insert it at new position
        val toStart = toRow * cols
        newButtonConfigs.addAll(toStart, rowToMove)
        
        // Reorder row names if exist
        val newRowNames = page.rowNames.toMutableList()
        if (newRowNames.isNotEmpty()) {
            val name = if (fromRow < newRowNames.size) newRowNames.removeAt(fromRow) else "Zeile ${fromRow + 1}"
            if (toRow <= newRowNames.size) {
                newRowNames.add(toRow, name)
            } else {
                newRowNames.add(name)
            }
        }

        val updatedPage = page.copy(
            buttonConfigs = newButtonConfigs,
            rowNames = newRowNames
        )
        
        pageRepository.updatePage(updatedPage)
        bookRepository.updateLastModified(page.bookId)
        return updatedPage
    }
}
