package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import javax.inject.Inject

class MoveButtonToPageUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    sealed class MoveResult {
        data class Success(val fromPage: Page, val toPage: Page) : MoveResult()
        data class NeedsConfirmation(
            val targetPage: Page,
            val freeSlotIndex: Int,
            val requiredRows: Int,
            val requiredCols: Int
        ) : MoveResult()
        object TargetFull : MoveResult()
        object Error : MoveResult()
    }

    suspend fun execute(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean = false
    ): MoveResult {
        val fromPage = pageRepository.getPageById(fromPageId) ?: return MoveResult.Error
        val toPage = pageRepository.getPageById(toPageId) ?: return MoveResult.Error
        
        if (fromPageId == toPageId) return MoveResult.Error // Should use MoveButtonUseCase

        val buttonToMove = fromPage.buttonConfigs.getOrNull(fromIndex) ?: return MoveResult.Error

        // 1. Find first free slot in toPage
        val targetIndex = toPage.buttonConfigs.indexOfFirst { it == null }
        if (targetIndex == -1) return MoveResult.TargetFull

        val reqRow = (targetIndex / GridUtils.MAX_GRID_SIZE) + 1
        val reqCol = (targetIndex % GridUtils.MAX_GRID_SIZE) + 1

        val needsRow = reqRow > toPage.rows
        val needsCol = reqCol > toPage.columns

        if ((needsRow || needsCol) && !forceMove) {
            return MoveResult.NeedsConfirmation(
                targetPage = toPage,
                freeSlotIndex = targetIndex,
                requiredRows = reqRow.coerceAtLeast(toPage.rows),
                requiredCols = reqCol.coerceAtLeast(toPage.columns)
            )
        }

        // 2. Perform the move
        val updatedToConfigs = toPage.buttonConfigs.toMutableList()
        updatedToConfigs[targetIndex] = buttonToMove
        
        val finalToPage = toPage.copy(
            buttonConfigs = updatedToConfigs,
            rows = if (forceMove) reqRow.coerceAtLeast(toPage.rows) else toPage.rows,
            columns = if (forceMove) reqCol.coerceAtLeast(toPage.columns) else toPage.columns
        )

        val updatedFromConfigs = fromPage.buttonConfigs.toMutableList()
        updatedFromConfigs[fromIndex] = null
        val finalFromPage = fromPage.copy(buttonConfigs = updatedFromConfigs)

        // 3. Persist
        pageRepository.movePages(finalFromPage, finalToPage)
        bookRepository.updateLastModified(fromPage.bookId)
        if (fromPage.bookId != toPage.bookId) {
            bookRepository.updateLastModified(toPage.bookId)
        }

        return MoveResult.Success(finalFromPage, finalToPage)
    }
}
