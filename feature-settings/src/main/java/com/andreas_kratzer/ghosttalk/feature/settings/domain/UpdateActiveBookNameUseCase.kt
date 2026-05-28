package com.andreas_kratzer.ghosttalk.feature.settings.domain

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject

class UpdateActiveBookNameUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(newName: String): Result<Unit> {
        val activeBookId = settingsRepository.activeBookId
        return if (activeBookId.isNotBlank()) {
            val book = bookRepository.getBookById(activeBookId)
            if (book != null) {
                bookRepository.updateBook(book.copy(name = newName, updatedAt = System.currentTimeMillis()))
                Result.success(Unit)
            } else {
                Result.failure(Exception("Book not found"))
            }
        } else {
            Result.failure(IllegalStateException("No active book selected"))
        }
    }
}
