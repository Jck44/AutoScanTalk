package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun getAllBooksList(): List<Book>
    suspend fun getBookById(id: String): Book?
    fun getBookByIdFlow(id: String): Flow<Book?>
    suspend fun insertBook(book: Book)
    suspend fun updateBook(book: Book)
    suspend fun deleteBook(book: Book)
    suspend fun updateLastModified(bookId: String, timestamp: Long = System.currentTimeMillis())
}
