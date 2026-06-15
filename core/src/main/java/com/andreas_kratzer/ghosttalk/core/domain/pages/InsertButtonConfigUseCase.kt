package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import javax.inject.Inject

class InsertButtonConfigUseCase @Inject constructor() {

    sealed class Result {
        data class Success(val newConfigs: List<ButtonConfig?>) : Result()
        object GridFull : Result()
        object InvalidIndex : Result()
    }

    fun execute(
        page: Page,
        index: Int,
        newConfig: ButtonConfig,
        forceShift: Boolean = false
    ): Result {
        val newButtonConfigs = page.buttonConfigs.toMutableList()
        
        // Ensure TOTAL_SLOTS (49)
        while (newButtonConfigs.size < GridUtils.TOTAL_SLOTS) {
            newButtonConfigs.add(null)
        }
        
        if (index !in newButtonConfigs.indices) {
            return Result.InvalidIndex
        }
        
        // Check if the entire slots are completely full
        if ((newButtonConfigs[index] != null || forceShift) && newButtonConfigs.none { it == null }) {
            return Result.GridFull
        }
        
        if (newButtonConfigs[index] == null && !forceShift) {
            // Target is empty, just replace
            newButtonConfigs[index] = newConfig
        } else {
            // Target is not empty or we force shift, shift items down following the visible layout flow
            val visibleIndices = mutableListOf<Int>()
            for (r in 0 until page.rows) {
                for (c in 0 until page.columns) {
                    visibleIndices.add(r * GridUtils.MAX_GRID_SIZE + c)
                }
            }
            
            val dropVisiblePos = visibleIndices.indexOf(index)
            if (dropVisiblePos != -1) {
                val lastVisibleGlobal = visibleIndices.last()
                val lastItem = newButtonConfigs[lastVisibleGlobal]
                
                // Shift visible items down by 1
                val now = System.currentTimeMillis()
                for (i in visibleIndices.size - 1 downTo dropVisiblePos + 1) {
                    val currentGlobal = visibleIndices[i]
                    val prevGlobal = visibleIndices[i - 1]
                    newButtonConfigs[currentGlobal] = newButtonConfigs[prevGlobal]?.copy(updatedAt = now)
                }
                
                // Rescue the last item by placing it in the first available invisible slot
                if (lastItem != null) {
                    for (i in 0 until GridUtils.TOTAL_SLOTS) {
                        if (i !in visibleIndices && newButtonConfigs[i] == null) {
                            newButtonConfigs[i] = lastItem.copy(updatedAt = now)
                            break
                        }
                    }
                }
            }
            
            // Insert new config
            newButtonConfigs[index] = newConfig
        }
        
        return Result.Success(newButtonConfigs.toList())
    }
}
