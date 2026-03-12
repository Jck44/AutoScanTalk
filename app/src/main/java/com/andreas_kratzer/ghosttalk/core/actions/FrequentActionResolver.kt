package com.andreas_kratzer.ghosttalk.core.actions

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Resolves [FrequentActionButtonAction]s dynamically at runtime into their concrete actions
 * based on usage statistics.
 */
class FrequentActionResolver @Inject constructor(
    private val buttonUsageRepository: ButtonUsageRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Resolves all FrequentActionButtonAction buttons on a page.
     * Returns a new Page object with concrete ButtonConfigs.
     */
    suspend fun resolve(page: Page, bookId: String): Page = withContext(Dispatchers.Default) {
        val frequentButtons = page.buttonConfigs.filterNotNull().filter {
            it.buttonAction is FrequentActionButtonAction
        }
        
        if (frequentButtons.isEmpty()) return@withContext page

        val maxRank = frequentButtons.maxOf {
            (it.buttonAction as FrequentActionButtonAction).rank
        }
        
        // Database call moved to IO
        val topActions = withContext(Dispatchers.IO) {
            buttonUsageRepository.getTopActions(bookId, maxRank)
        }

        // Create a lookup for current page buttons to avoid O(n^2)
        val currentPageButtons = page.buttonConfigs.filterNotNull().associateBy { it.id }

        var changed = false
        val resolved = page.buttonConfigs.map { config ->
            val action = config?.buttonAction
            if (action is FrequentActionButtonAction && config.isActive) {
                val rank = action.rank
                val stat = topActions.getOrNull(rank - 1)
                
                val resolvedConfig = if (stat != null) {
                    val matchingConfig = currentPageButtons[stat.buttonConfigId]
                    if (matchingConfig != null) {
                        // Recursion guard
                        val isDynamic = matchingConfig.buttonAction is FrequentActionButtonAction || 
                                      matchingConfig.buttonAction is SmartPredictionButtonAction
                        
                        if (isDynamic) null else matchingConfig
                    } else {
                        // Fallback: Reconstruct from stat
                        try {
                            val concreteAction = json.decodeFromString<ButtonAction>(stat.actionJson)
                            config.copy(
                                id = stat.buttonConfigId,
                                label = stat.label,
                                buttonAction = concreteAction
                            )
                        } catch (e: Exception) {
                            Log.e("FrequentActionResolver", "Failed to parse action JSON: ${stat.actionJson}", e)
                            null
                        }
                    }
                } else {
                    null
                }
                
                if (resolvedConfig !== config) changed = true
                resolvedConfig
            } else {
                config
            }
        }
        if (changed) page.copy(buttonConfigs = resolved) else page
    }
}
