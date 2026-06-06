package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.database.BookDao
import com.andreas_kratzer.ghosttalk.core.model.Book
import kotlinx.coroutines.flow.Flow

class BookRepositoryImpl(private val bookDao: BookDao) : BookRepository {
    override fun getAllBooks(): Flow<List<Book>> = bookDao.getAllBooks()
    override suspend fun getAllBooksList(): List<Book> = bookDao.getAllBooksList()
    override suspend fun getBookById(id: String): Book? = bookDao.getBookById(id)
    override fun getBookByIdFlow(id: String): Flow<Book?> = bookDao.getBookByIdFlow(id)
    override suspend fun insertBook(book: Book) = bookDao.insertBook(book)
    override suspend fun updateBook(book: Book) = bookDao.updateBook(book)
    override suspend fun deleteBook(book: Book) = bookDao.deleteBook(book)
    override suspend fun updateLastModified(bookId: String, timestamp: Long, incrementSequence: Boolean) {
        if (incrementSequence) {
            bookDao.incrementVersionSequence(bookId, timestamp)
        } else {
            bookDao.updateLastModified(bookId, timestamp)
        }
    }
}
