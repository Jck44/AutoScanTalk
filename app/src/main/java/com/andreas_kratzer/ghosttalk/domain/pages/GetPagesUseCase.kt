package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Use case to get the reactive list of pages for a specific book.
 */
class GetPagesUseCase @javax.inject.Inject constructor(private val pageRepository: PageRepository) {
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
