package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Use case to get the reactive list of pages for a specific book.
 */
class GetPagesUseCase @Inject constructor(private val pageRepository: PageRepository) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun execute(activeBookIdFlow: Flow<String?>): Flow<List<Page>> {
        return activeBookIdFlow.flatMapLatest { bookId ->
            if (bookId != null) {
                pageRepository.getPagesForBookFlow(bookId)
            } else {
                flowOf(emptyList())
            }
        }
    }
}
