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

        val now = System.currentTimeMillis()
        val buttonsList = buttonsToMove.map { it.second.copy(updatedAt = now) }
        val bulkResult = GridUtils.determineBulkTargetSlots(toPage, buttonsList, forceMove)

        val finalToPage = when (bulkResult) {
            is GridUtils.BulkPlacementResult.TargetFull -> return MoveResult.TargetFull
            is GridUtils.BulkPlacementResult.NeedsConfirmation -> {
                return MoveResult.NeedsConfirmation(
                    targetPage = toPage,
                    freeSlotIndex = bulkResult.freeSlotIndex,
                    requiredRows = bulkResult.requiredRows,
                    requiredCols = bulkResult.requiredCols
                )
            }
            is GridUtils.BulkPlacementResult.Success -> {
                bulkResult.updatedPage
            }
        }

        val updatedFromConfigs = fromPage.buttonConfigs.toMutableList()
        for (idx in fromIndices) {
            if (idx in updatedFromConfigs.indices) {
                updatedFromConfigs[idx] = null
            }
        }
        val finalFromPage = fromPage.copy(buttonConfigs = updatedFromConfigs)

        pageRepository.movePages(finalFromPage, finalToPage)
        bookRepository.updateLastModified(fromPage.bookId)
        if (fromPage.bookId != toPage.bookId) {
            bookRepository.updateLastModified(toPage.bookId)
        }

        return MoveResult.Success(finalFromPage, finalToPage)
    }
}
