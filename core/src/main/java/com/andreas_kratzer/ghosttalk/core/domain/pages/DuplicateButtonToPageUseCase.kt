package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import java.util.UUID
import javax.inject.Inject

class DuplicateButtonToPageUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean = false
    ): MoveButtonToPageUseCase.MoveResult {
        val fromPage = pageRepository.getPageById(fromPageId) ?: return MoveButtonToPageUseCase.MoveResult.Error
        val toPage = pageRepository.getPageById(toPageId) ?: return MoveButtonToPageUseCase.MoveResult.Error
        
        val buttonToCopy = fromPage.buttonConfigs.getOrNull(fromIndex) ?: return MoveButtonToPageUseCase.MoveResult.Error

        // Create a new button with a unique ID
        val duplicatedButton = buttonToCopy.copy(
            id = UUID.randomUUID().toString(),
            updatedAt = System.currentTimeMillis()
        )

        // 1. Determine target slot
        val placement = GridUtils.determineTargetSlot(toPage, forceMove)
        
        val (targetIndex, requiredRows, requiredCols) = when (placement) {
            is GridUtils.SlotPlacementResult.TargetFull -> 
                return MoveButtonToPageUseCase.MoveResult.TargetFull
            is GridUtils.SlotPlacementResult.NeedsConfirmation -> 
                return MoveButtonToPageUseCase.MoveResult.NeedsConfirmation(
                    targetPage = toPage,
                    freeSlotIndex = placement.targetIndex,
                    requiredRows = placement.requiredRows,
                    requiredCols = placement.requiredCols
                )
            is GridUtils.SlotPlacementResult.Success -> 
                Triple(placement.targetIndex, placement.requiredRows, placement.requiredCols)
        }

        // 2. Perform the copy
        val updatedToConfigs = toPage.buttonConfigs.toMutableList()
        updatedToConfigs[targetIndex] = duplicatedButton
        
        val finalToPage = toPage.copy(
            buttonConfigs = updatedToConfigs,
            rows = if (forceMove) requiredRows else toPage.rows,
            columns = if (forceMove) requiredCols else toPage.columns
        )

        // 3. Persist (Only update the target page)
        pageRepository.updatePage(finalToPage)
        bookRepository.updateLastModified(toPage.bookId)

        return MoveButtonToPageUseCase.MoveResult.Success(fromPage, finalToPage)
    }
}
