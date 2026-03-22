package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SortOrder
import com.andreas_kratzer.ghosttalk.ui.util.filterAndSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetFilteredPagesUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    fun execute(
        allPages: Flow<List<Page>>,
        searchQuery: Flow<String>
    ): Flow<List<Page>> {
        return combine(
            allPages,
            settingsRepository.pageSortOrderFlow,
            searchQuery
        ) { pages, sortOrderStr, query ->
            val sortOrder = try {
                SortOrder.valueOf(sortOrderStr)
            } catch (_: Exception) {
                SortOrder.MANUAL
            }
            pages.filterAndSort(query, sortOrder)
        }
    }
}
