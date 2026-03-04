package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.data.ButtonActionAdapter
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import javax.inject.Inject

/**
 * Resolves [FrequentActionButtonAction]s dynamically at runtime into their concrete actions
 * based on usage statistics.
 */
class FrequentActionResolver @Inject constructor(
    private val buttonUsageRepository: ButtonUsageRepository
) {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ButtonAction::class.java, ButtonActionAdapter())
        .create()

    /**
     * Resolves all FrequentActionButtonAction buttons on a page.
     * Returns a new Page object with concrete ButtonConfigs.
     */
    suspend fun resolve(page: Page, bookId: String): Page {
        val frequentButtons = page.buttonConfigs.filterNotNull().filter {
            it.buttonAction is FrequentActionButtonAction
        }
        
        if (frequentButtons.isEmpty()) return page

        val maxRank = frequentButtons.maxOf {
            (it.buttonAction as FrequentActionButtonAction).rank
        }
        
        val topActions = buttonUsageRepository.getTopActions(bookId, maxRank)

        val resolved = page.buttonConfigs.map { config ->
            if (config?.buttonAction is FrequentActionButtonAction) {
                val rank = config.buttonAction.rank
                val stat = topActions.getOrNull(rank - 1)
                
                if (stat != null) {
                    try {
                        val concreteAction = gson.fromJson(stat.actionJson, ButtonAction::class.java)
                        config.copy(
                            label = stat.label,
                            buttonAction = concreteAction
                        )
                    } catch (e: Exception) {
                        null // Fallback mechanism for JSON parsing failures -> empty slot
                    }
                } else {
                    null // No entry for this rank in statistics -> empty slot
                }
            } else {
                config
            }
        }
        return page.copy(buttonConfigs = resolved)
    }
}
