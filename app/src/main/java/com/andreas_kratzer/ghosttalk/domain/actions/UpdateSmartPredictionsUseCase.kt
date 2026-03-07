package com.andreas_kratzer.ghosttalk.domain.actions

import android.util.Log
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.settings.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class UpdateSmartPredictionsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val predictNextActionUseCase: PredictNextActionUseCase,
    private val checkForPredictorUseCase: CheckForPredictorUseCase
) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun execute(
        currentPage: Flow<Page?>,
        allPages: Flow<List<Page>>,
        activeBookId: Flow<String?>,
        history: Flow<List<String>>
    ): Flow<List<String>> {
        return combine(
            currentPage,
            allPages,
            activeBookId,
            history,
            settingsRepository.isSmartPredictionEnabledFlow
        ) { page, pages, bookId, actionHistory, enabled ->
            if (page != null && enabled && bookId != null) {
                if (checkForPredictorUseCase(page)) {
                    try {
                        _isLoading.value = true
                        return@combine predictNextActionUseCase.predict(page, pages, bookId)
                    } catch (e: Exception) {
                        Log.e("UpdateSmartPredictionsUseCase", "Smart Prediction failed", e)
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
            _isLoading.value = false
            emptyList()
        }
    }
}
