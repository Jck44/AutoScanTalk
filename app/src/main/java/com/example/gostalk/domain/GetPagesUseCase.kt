package com.example.gostalk.domain

import com.example.gostalk.data.PageRepository
import com.example.gostalk.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Use case to get the reactive list of pages for a specific book.
 */
class GetPagesUseCase(private val pageRepository: PageRepository) {
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
