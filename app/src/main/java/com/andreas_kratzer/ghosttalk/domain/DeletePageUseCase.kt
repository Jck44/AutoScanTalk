package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.Page
import javax.inject.Inject

class DeletePageUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository
) {
    suspend fun execute(page: Page) {
        pageRepository.deletePage(page)
        bookRepository.updateLastModified(page.bookId)
    }
}
