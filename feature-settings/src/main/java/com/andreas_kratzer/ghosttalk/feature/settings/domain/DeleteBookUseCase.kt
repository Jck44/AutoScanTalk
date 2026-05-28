package com.andreas_kratzer.ghosttalk.feature.settings.domain

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject

class DeleteBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(): Result<Unit> {
        val activeBookId = settingsRepository.activeBookId
        return if (activeBookId.isNotBlank()) {
            val book = bookRepository.getBookById(activeBookId)
            if (book != null) {
                bookRepository.deleteBook(book)
                settingsRepository.activeBookId = "book-default"
                Result.success(Unit)
            } else {
                Result.failure(Exception("Book not found"))
            }
        } else {
            Result.failure(IllegalStateException("No active book selected"))
        }
    }
}
