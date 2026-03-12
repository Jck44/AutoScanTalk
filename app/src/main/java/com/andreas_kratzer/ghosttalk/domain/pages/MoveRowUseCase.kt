package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.database.BookRepository
import com.andreas_kratzer.ghosttalk.core.database.PageRepository
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
        
        val maxCols = com.andreas_kratzer.ghosttalk.ui.util.GridUtils.MAX_GRID_SIZE
        val newButtonConfigs = page.buttonConfigs.toMutableList()
        
        // Ensure we have 49 slots
        while (newButtonConfigs.size < com.andreas_kratzer.ghosttalk.ui.util.GridUtils.TOTAL_SLOTS) {
            newButtonConfigs.add(null)
        }

        // Extract the 7-slot block for the source row
        val fromStart = fromRow * maxCols
        val rowToMove = newButtonConfigs.subList(fromStart, fromStart + maxCols).toList()
        
        // Remove the 7-slot block
        repeat(maxCols) { newButtonConfigs.removeAt(fromStart) }
        
        // Insert it at new position in 7-slot chunks
        val toStart = toRow * maxCols
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
