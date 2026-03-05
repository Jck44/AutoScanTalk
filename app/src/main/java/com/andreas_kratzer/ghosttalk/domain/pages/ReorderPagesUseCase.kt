package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SortOrder
import javax.inject.Inject

class ReorderPagesUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(currentList: List<Page>, fromIndex: Int, toIndex: Int, activeBookId: String?) {
        val mutableList = currentList.toMutableList()
        if (fromIndex !in mutableList.indices || toIndex !in mutableList.indices) return
        
        val item = mutableList.removeAt(fromIndex)
        mutableList.add(toIndex, item)
        
        // Update indices in DB
        mutableList.forEachIndexed { index, page ->
            if (page.orderIndex != index) {
                pageRepository.updatePage(page.copy(orderIndex = index))
            }
        }
        
        activeBookId?.let { bookRepository.updateLastModified(it) }
        // Ensure we are in MANUAL mode if user reorders
        settingsRepository.pageSortOrder = SortOrder.MANUAL.name
    }
}
