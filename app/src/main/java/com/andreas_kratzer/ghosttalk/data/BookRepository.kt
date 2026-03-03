package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.model.Book
import kotlinx.coroutines.flow.Flow

class BookRepository(private val bookDao: BookDao) {
    fun getAllBooks(): Flow<List<Book>> = bookDao.getAllBooks()
    suspend fun getBookById(id: String): Book? = bookDao.getBookById(id)
    suspend fun insertBook(book: Book) = bookDao.insertBook(book)
    suspend fun updateBook(book: Book) = bookDao.updateBook(book)
    suspend fun deleteBook(book: Book) = bookDao.deleteBook(book)
}
