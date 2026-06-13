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

        // 1. Determine target slot
        val placement = GridUtils.determineTargetSlot(toPage, forceMove)
        
        val (targetIndex, requiredRows, requiredCols) = when (placement) {
            is GridUtils.SlotPlacementResult.TargetFull -> 
                return MoveResult.TargetFull
            is GridUtils.SlotPlacementResult.NeedsConfirmation -> 
                return MoveResult.NeedsConfirmation(
                    targetPage = toPage,
                    freeSlotIndex = placement.targetIndex,
                    requiredRows = placement.requiredRows,
                    requiredCols = placement.requiredCols
                )
            is GridUtils.SlotPlacementResult.Success -> 
                Triple(placement.targetIndex, placement.requiredRows, placement.requiredCols)
        }

        // 2. Perform the move
        val now = System.currentTimeMillis()
        val updatedToConfigs = toPage.buttonConfigs.toMutableList()
        updatedToConfigs[targetIndex] = buttonToMove.copy(updatedAt = now)
        
        val finalToPage = toPage.copy(
            buttonConfigs = updatedToConfigs,
            rows = if (forceMove) requiredRows else toPage.rows,
            columns = if (forceMove) requiredCols else toPage.columns
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
