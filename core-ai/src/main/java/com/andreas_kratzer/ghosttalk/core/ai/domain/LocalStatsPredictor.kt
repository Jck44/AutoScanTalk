package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalStatsPredictor @Inject constructor(
    private val buttonUsageRepository: ButtonUsageRepository
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun predict(
        currentPage: com.andreas_kratzer.ghosttalk.core.model.Page,
        allPages: List<com.andreas_kratzer.ghosttalk.core.model.Page>,
        bookId: String
    ): List<String> {
        return buttonUsageRepository.getPredictiveButtons(bookId, 15)
    }
}
