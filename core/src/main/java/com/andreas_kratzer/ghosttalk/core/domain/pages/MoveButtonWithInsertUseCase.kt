package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import javax.inject.Inject

class MoveButtonWithInsertUseCase @Inject constructor() {

    sealed class Result {
        data class Success(
            val newConfigs: List<ButtonConfig?>,
            val movedButtonLabel: String
        ) : Result()
        object Error : Result()
    }

    fun execute(
        page: Page,
        fromIndex: Int,
        toIndex: Int
    ): Result {
        if (fromIndex == toIndex || fromIndex == toIndex - 1) {
            return Result.Error
        }

        val newButtonConfigs = page.buttonConfigs.toMutableList()
        
        // Ensure 49 slots
        while (newButtonConfigs.size < GridUtils.TOTAL_SLOTS) {
            newButtonConfigs.add(null)
        }
        
        val visibleIndices = mutableListOf<Int>()
        for (r in 0 until page.rows) {
            for (c in 0 until page.columns) {
                visibleIndices.add(r * GridUtils.MAX_GRID_SIZE + c)
            }
        }
        
        val fromPos = visibleIndices.indexOf(fromIndex)
        
        // Helper to determine visible insertion position from the global target index
        val toPos = visibleIndices.indexOf(toIndex).takeIf { it != -1 }
            ?: visibleIndices.indexOf(toIndex - 1).takeIf { it != -1 }?.plus(1)
            ?: -1
            
        if (fromPos != -1 && toPos != -1 && toPos <= visibleIndices.size) {
            val movedItem = newButtonConfigs[fromIndex] ?: return Result.Error
            
            // Clear the source
            newButtonConfigs[fromIndex] = null
            
            val now = System.currentTimeMillis()
            if (fromPos < toPos) {
                // Shift items between fromPos + 1 and toPos - 1 to the left
                for (i in fromPos until toPos - 1) {
                    if (i < visibleIndices.size - 1) {
                        val currentGlobal = visibleIndices[i]
                        val nextGlobal = visibleIndices[i + 1]
                        newButtonConfigs[currentGlobal] = newButtonConfigs[nextGlobal]?.copy(updatedAt = now)
                    }
                }
                // Insert the moved item at toPos - 1
                val targetGlobal = visibleIndices[toPos - 1]
                newButtonConfigs[targetGlobal] = movedItem.copy(updatedAt = now)
            } else if (fromPos > toPos) {
                // Shift items between toPos and fromPos - 1 to the right
                for (i in fromPos downTo toPos + 1) {
                    val currentGlobal = visibleIndices[i]
                    val prevGlobal = visibleIndices[i - 1]
                    newButtonConfigs[currentGlobal] = newButtonConfigs[prevGlobal]?.copy(updatedAt = now)
                }
                // Insert the moved item at toPos
                val targetGlobal = visibleIndices[toPos]
                newButtonConfigs[targetGlobal] = movedItem.copy(updatedAt = now)
            }
            
            return Result.Success(newButtonConfigs.toList(), movedItem.label)
        }
        
        return Result.Error
    }
}
