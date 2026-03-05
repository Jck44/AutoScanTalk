package com.andreas_kratzer.ghosttalk.domain.actions

import android.util.Log
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.settings.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class UpdateSmartPredictionsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val predictNextActionUseCase: PredictNextActionUseCase,
    private val checkForPredictorUseCase: CheckForPredictorUseCase
) {
    fun execute(
        currentPage: Flow<Page?>,
        allPages: Flow<List<Page>>,
        activeBookId: Flow<String?>
    ): Flow<List<String>> {
        return combine(
            currentPage,
            allPages,
            activeBookId,
            settingsRepository.isSmartPredictionEnabledFlow
        ) { page, pages, bookId, enabled ->
            if (page != null && enabled && bookId != null) {
                if (checkForPredictorUseCase(page)) {
                    try {
                        return@combine predictNextActionUseCase.predict(page, pages, bookId)
                    } catch (e: Exception) {
                        Log.e("UpdateSmartPredictionsUseCase", "Smart Prediction failed", e)
                    }
                }
            }
            emptyList()
        }
    }
}
