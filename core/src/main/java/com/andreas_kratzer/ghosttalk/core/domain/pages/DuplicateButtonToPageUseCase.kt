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
        val duplicatedButton = buttonToCopy.copy(id = UUID.randomUUID().toString())

        // 1. Find first free slot in toPage
        val targetIndex = toPage.buttonConfigs.indexOfFirst { it == null }
        if (targetIndex == -1) return MoveButtonToPageUseCase.MoveResult.TargetFull

        val reqRow = (targetIndex / GridUtils.MAX_GRID_SIZE) + 1
        val reqCol = (targetIndex % GridUtils.MAX_GRID_SIZE) + 1

        val needsRow = reqRow > toPage.rows
        val needsCol = reqCol > toPage.columns

        if ((needsRow || needsCol) && !forceMove) {
            return MoveButtonToPageUseCase.MoveResult.NeedsConfirmation(
                targetPage = toPage,
                freeSlotIndex = targetIndex,
                requiredRows = reqRow.coerceAtLeast(toPage.rows),
                requiredCols = reqCol.coerceAtLeast(toPage.columns)
            )
        }

        // 2. Perform the copy
        val updatedToConfigs = toPage.buttonConfigs.toMutableList()
        updatedToConfigs[targetIndex] = duplicatedButton
        
        val finalToPage = toPage.copy(
            buttonConfigs = updatedToConfigs,
            rows = if (forceMove) reqRow.coerceAtLeast(toPage.rows) else toPage.rows,
            columns = if (forceMove) reqCol.coerceAtLeast(toPage.columns) else toPage.columns
        )

        // 3. Persist (Only update the target page)
        pageRepository.updatePage(finalToPage)
        bookRepository.updateLastModified(toPage.bookId)

        return MoveButtonToPageUseCase.MoveResult.Success(fromPage, finalToPage)
    }
}
