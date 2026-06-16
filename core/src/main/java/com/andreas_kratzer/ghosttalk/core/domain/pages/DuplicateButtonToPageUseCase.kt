package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
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

        val now = System.currentTimeMillis()
        val buttonsToDuplicate = buttonsToCopy.map {
            it to it.copy(
                id = java.util.UUID.randomUUID().toString(),
                updatedAt = now
            )
        }

        val buttonsList = buttonsToDuplicate.map { it.second }
        val bulkResult = GridUtils.determineBulkTargetSlots(toPage, buttonsList, forceMove)

        val finalToPage = when (bulkResult) {
            is GridUtils.BulkPlacementResult.TargetFull -> return MoveButtonToPageUseCase.MoveResult.TargetFull
            is GridUtils.BulkPlacementResult.NeedsConfirmation -> {
                return MoveButtonToPageUseCase.MoveResult.NeedsConfirmation(
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

        // Persist (Only update the target page)
        pageRepository.updatePage(finalToPage)
        bookRepository.updateLastModified(toPage.bookId)

        return MoveButtonToPageUseCase.MoveResult.Success(fromPage, finalToPage)
    }
}
