package com.andreas_kratzer.ghosttalk.domain.actions

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.settings.CheckForPredictorUseCase
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
        history: Flow<List<String>>,
        isUserModeActive: Flow<Boolean>
    ): Flow<List<String>?> {
        val inputFlow = combine(
            currentPage,
            allPages,
            activeBookId,
            history,
            isUserModeActive
        ) { page, pages, bookId, hist, isUserMode ->
            UpdateParams(page, pages, bookId, hist, isUserMode)
        }

        return combine(
            inputFlow,
            settingsRepository.isSmartPredictionEnabledFlow
        ) { params, enabled ->
            val page = params.currentPage
            val pages = params.allPages
            val bookId = params.activeBookId
            val isUserMode = params.isUserModeActive

            if (page != null && enabled && bookId != null && isUserMode) {
                if (checkForPredictorUseCase(page)) {
                    Log.d("UpdateSmartPredictionsUseCase", "Triggering prediction for page ${page.id}")
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
            if (!isUserMode) null else emptyList()
        }
    }

    private data class UpdateParams(
        val currentPage: Page?,
        val allPages: List<Page>,
        val activeBookId: String?,
        val history: List<String>,
        val isUserModeActive: Boolean
    )
}
