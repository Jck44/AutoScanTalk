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
        fromIndices: List<Int>,
        toPageId: String,
        forceMove: Boolean = false
    ): MoveResult {
        val fromPage = pageRepository.getPageById(fromPageId) ?: return MoveResult.Error
        val toPage = pageRepository.getPageById(toPageId) ?: return MoveResult.Error
        
        if (fromPageId == toPageId) return MoveResult.Error

        val buttonsToMove = fromIndices.mapNotNull { idx ->
            fromPage.buttonConfigs.getOrNull(idx)?.let { idx to it }
        }
        if (buttonsToMove.isEmpty()) return MoveResult.Error

        var tempToPage = toPage
        val placements = mutableListOf<Pair<Int, com.andreas_kratzer.ghosttalk.core.model.ButtonConfig>>()
        var maxRequiredRows = toPage.rows
        var maxRequiredCols = toPage.columns
        var needsConfirmation = false
        var targetIndexForConfirmation = -1

        for ((_, button) in buttonsToMove) {
            val placement = GridUtils.determineTargetSlot(tempToPage, forceMove)
            when (placement) {
                is GridUtils.SlotPlacementResult.TargetFull -> 
                    return MoveResult.TargetFull
                is GridUtils.SlotPlacementResult.NeedsConfirmation -> {
                    needsConfirmation = true
                    targetIndexForConfirmation = placement.targetIndex
                    maxRequiredRows = maxOf(maxRequiredRows, placement.requiredRows)
                    maxRequiredCols = maxOf(maxRequiredCols, placement.requiredCols)
                    
                    val updatedConfigs = tempToPage.buttonConfigs.toMutableList()
                    while (updatedConfigs.size < GridUtils.TOTAL_SLOTS) {
                        updatedConfigs.add(null)
                    }
                    updatedConfigs[placement.targetIndex] = button
                    tempToPage = tempToPage.copy(
                        buttonConfigs = updatedConfigs,
                        rows = placement.requiredRows,
                        columns = placement.requiredCols
                    )
                }
                is GridUtils.SlotPlacementResult.Success -> {
                    placements.add(placement.targetIndex to button)
                    val updatedConfigs = tempToPage.buttonConfigs.toMutableList()
                    while (updatedConfigs.size < GridUtils.TOTAL_SLOTS) {
                        updatedConfigs.add(null)
                    }
                    updatedConfigs[placement.targetIndex] = button
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
            return MoveResult.NeedsConfirmation(
                targetPage = toPage,
                freeSlotIndex = targetIndexForConfirmation,
                requiredRows = maxRequiredRows,
                requiredCols = maxRequiredCols
            )
        }

        // Apply moves
        val now = System.currentTimeMillis()
        val updatedToConfigs = toPage.buttonConfigs.toMutableList()
        while (updatedToConfigs.size < GridUtils.TOTAL_SLOTS) {
            updatedToConfigs.add(null)
        }
        
        for ((targetIndex, button) in placements) {
            updatedToConfigs[targetIndex] = button.copy(updatedAt = now)
        }

        val finalToPage = toPage.copy(
            buttonConfigs = updatedToConfigs,
            rows = if (forceMove) maxRequiredRows else toPage.rows,
            columns = if (forceMove) maxRequiredCols else toPage.columns
        )

        val updatedFromConfigs = fromPage.buttonConfigs.toMutableList()
        for (idx in fromIndices) {
            if (idx in updatedFromConfigs.indices) {
                updatedFromConfigs[idx] = null
            }
        }
        val finalFromPage = fromPage.copy(buttonConfigs = updatedFromConfigs)

        // Persist
        pageRepository.movePages(finalFromPage, finalToPage)
        bookRepository.updateLastModified(fromPage.bookId)
        if (fromPage.bookId != toPage.bookId) {
            bookRepository.updateLastModified(toPage.bookId)
        }

        return MoveResult.Success(finalFromPage, finalToPage)
    }
}
