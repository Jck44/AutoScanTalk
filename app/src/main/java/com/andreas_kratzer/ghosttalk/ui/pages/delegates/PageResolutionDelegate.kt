package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.domain.actions.ResolveDynamicButtonsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageResolutionDelegate @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val pageRepository: PageRepository,
    private val resolveDynamicButtonsUseCase: ResolveDynamicButtonsUseCase
) {
    private val _isEditPreviewActive = MutableStateFlow(false)
    val isEditPreviewActive: StateFlow<Boolean> = _isEditPreviewActive.asStateFlow()

    fun toggleEditPreviewActive() {
        _isEditPreviewActive.value = !_isEditPreviewActive.value
    }

    fun getIsPreviewOrUserMode(
        scope: CoroutineScope,
        isUserModeActive: StateFlow<Boolean>
    ): StateFlow<Boolean> {
        return combine(isUserModeActive, isEditPreviewActive) { userMode, editPreview ->
            userMode || editPreview
        }.stateIn(scope, SharingStarted.Eagerly, isUserModeActive.value || isEditPreviewActive.value)
    }

    fun getStaticRowPage(
        scope: CoroutineScope,
        activeBookId: StateFlow<String?>,
        allPages: StateFlow<List<Page>>,
        smartPredictions: StateFlow<List<String>?>
    ): StateFlow<Page?> {
        return combine(
            activeBookId,
            allPages,
            settingsRepository.staticRowEnabledFlow,
            smartPredictions
        ) { bookId, listPages, enabled, predictions ->
            if (bookId != null && enabled) {
                val expectedId = "static_row_$bookId"
                val existing = listPages.find { it.id == expectedId }
                if (existing == null) {
                    scope.launch(Dispatchers.IO) {
                        if (pageRepository.getPageById(expectedId) == null) {
                            val newPage = Page(
                                id = expectedId,
                                bookId = bookId,
                                name = "Statische Zeile",
                                templateId = null,
                                rows = 1,
                                columns = 4,
                                scanPattern = "linear",
                                rowNames = emptyList(),
                                buttonConfigs = emptyList(),
                                orderIndex = -1
                            )
                            pageRepository.insertPage(newPage)
                        }
                    }
                    null
                } else {
                    resolveDynamicButtonsUseCase.execute(existing, bookId, predictions, listPages)
                }
            } else {
                null
            }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)
    }

    fun getResolvedPage(
        scope: CoroutineScope,
        currentPage: StateFlow<Page?>,
        isPreviewOrUserMode: StateFlow<Boolean>,
        smartPredictions: StateFlow<List<String>?>,
        activeBookId: StateFlow<String?>,
        unfilteredPages: StateFlow<List<Page>>
    ): StateFlow<Page?> {
        return combine(
            currentPage,
            isPreviewOrUserMode,
            smartPredictions,
            activeBookId,
            unfilteredPages
        ) { page, isPreviewMode, predictions, bookId, listPages ->
            Log.d("PageResolutionDelegate", "Combined: page=${page?.id}, isPreviewMode=$isPreviewMode, bookId=$bookId, listPages=${listPages.size}")
            if (page != null && isPreviewMode && bookId != null) {
                resolveDynamicButtonsUseCase.execute(page, bookId, predictions, listPages)
            } else {
                page
            }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, currentPage.value)
    }
}
