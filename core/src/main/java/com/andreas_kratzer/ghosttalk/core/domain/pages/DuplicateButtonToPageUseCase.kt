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
        fromIndices: List<Int>,
        toPageId: String,
        forceMove: Boolean = false
    ): MoveButtonToPageUseCase.MoveResult {
        val fromPage = pageRepository.getPageById(fromPageId) ?: return MoveButtonToPageUseCase.MoveResult.Error
        val toPage = pageRepository.getPageById(toPageId) ?: return MoveButtonToPageUseCase.MoveResult.Error

        val buttonsToCopy = fromIndices.mapNotNull { idx ->
            fromPage.buttonConfigs.getOrNull(idx)
        }
        if (buttonsToCopy.isEmpty()) return MoveButtonToPageUseCase.MoveResult.Error

        var tempToPage = toPage
        val placements = mutableListOf<Pair<Int, com.andreas_kratzer.ghosttalk.core.model.ButtonConfig>>()
        var maxRequiredRows = toPage.rows
        var maxRequiredCols = toPage.columns
        var needsConfirmation = false
        var targetIndexForConfirmation = -1

        for (button in buttonsToCopy) {
            val placement = GridUtils.determineTargetSlot(tempToPage, forceMove)
            val duplicatedButton = button.copy(
                id = UUID.randomUUID().toString(),
                updatedAt = System.currentTimeMillis()
            )
            when (placement) {
                is GridUtils.SlotPlacementResult.TargetFull -> 
                    return MoveButtonToPageUseCase.MoveResult.TargetFull
                is GridUtils.SlotPlacementResult.NeedsConfirmation -> {
                    needsConfirmation = true
                    targetIndexForConfirmation = placement.targetIndex
                    maxRequiredRows = maxOf(maxRequiredRows, placement.requiredRows)
                    maxRequiredCols = maxOf(maxRequiredCols, placement.requiredCols)
                    
                    val updatedConfigs = tempToPage.buttonConfigs.toMutableList()
                    while (updatedConfigs.size < GridUtils.TOTAL_SLOTS) {
                        updatedConfigs.add(null)
                    }
                    updatedConfigs[placement.targetIndex] = duplicatedButton
                    tempToPage = tempToPage.copy(
                        buttonConfigs = updatedConfigs,
                        rows = placement.requiredRows,
                        columns = placement.requiredCols
                    )
                }
                is GridUtils.SlotPlacementResult.Success -> {
                    placements.add(placement.targetIndex to duplicatedButton)
                    
                    val updatedConfigs = tempToPage.buttonConfigs.toMutableList()
                    while (updatedConfigs.size < GridUtils.TOTAL_SLOTS) {
                        updatedConfigs.add(null)
                    }
                    updatedConfigs[placement.targetIndex] = duplicatedButton
                    tempToPage = tempToPage.copy(
                        buttonConfigs = updatedConfigs,
                        rows = placement.requiredRows,
                        columns = placement.requiredCols
                    )
                    maxRequiredRows = maxOf(maxRequiredRows, placement.requiredRows)
                    maxRequiredCols = maxOf(maxRequiredCols, placement.requiredCols)
                }
            }
        }

        if (needsConfirmation && !forceMove) {
            return MoveButtonToPageUseCase.MoveResult.NeedsConfirmation(
                targetPage = toPage,
                freeSlotIndex = targetIndexForConfirmation,
                requiredRows = maxRequiredRows,
                requiredCols = maxRequiredCols
            )
        }

        // Apply copies
        val updatedToConfigs = toPage.buttonConfigs.toMutableList()
        while (updatedToConfigs.size < GridUtils.TOTAL_SLOTS) {
            updatedToConfigs.add(null)
        }
        for ((targetIndex, button) in placements) {
            updatedToConfigs[targetIndex] = button
        }

        val finalToPage = toPage.copy(
            buttonConfigs = updatedToConfigs,
            rows = if (forceMove) maxRequiredRows else toPage.rows,
            columns = if (forceMove) maxRequiredCols else toPage.columns
        )

        // Persist (Only update the target page)
        pageRepository.updatePage(finalToPage)
        bookRepository.updateLastModified(toPage.bookId)

        return MoveButtonToPageUseCase.MoveResult.Success(fromPage, finalToPage)
    }
}
