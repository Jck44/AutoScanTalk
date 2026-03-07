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
        history: Flow<List<String>>,
        isUserModeActive: Flow<Boolean>
    ): Flow<List<String>?> {
        return combine(
            currentPage,
            allPages,
            activeBookId,
            history,
            isUserModeActive,
            settingsRepository.isSmartPredictionEnabledFlow
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val page = args[0] as Page?
            @Suppress("UNCHECKED_CAST")
            val pages = args[1] as List<Page>
            @Suppress("UNCHECKED_CAST")
            val bookId = args[2] as String?
            @Suppress("UNCHECKED_CAST")
            val actionHistory = args[3] as List<String>
            @Suppress("UNCHECKED_CAST")
            val isUserMode = args[4] as Boolean
            @Suppress("UNCHECKED_CAST")
            val enabled = args[5] as Boolean

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
}
