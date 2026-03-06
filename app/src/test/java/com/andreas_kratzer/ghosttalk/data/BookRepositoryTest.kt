package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.model.Book
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookRepositoryTest {

    private val mockBookDao = mockk<BookDao>(relaxed = true)
    private lateinit var bookRepository: BookRepository

    @Before
    fun setup() {
        bookRepository = BookRepository(mockBookDao)
    }

    @Test
    fun `getAllBooks returns flow from dao`() = runTest {
        val books = listOf(Book(id = "1", name = "Book 1"))
        every { mockBookDao.getAllBooks() } returns flowOf(books)

        bookRepository.getAllBooks().collect {
            assertEquals(books, it)
        }
    }

    @Test
    fun `getAllBooksList calls dao getAllBooksList`() = runTest {
        val books = listOf(Book(id = "1", name = "Book 1"))
        coEvery { mockBookDao.getAllBooksList() } returns books

        val result = bookRepository.getAllBooksList()
        assertEquals(books, result)
    }

    @Test
    fun `getBookById calls dao getBookById`() = runTest {
        val book = Book(id = "1", name = "Book 1")
        coEvery { mockBookDao.getBookById("1") } returns book

        val result = bookRepository.getBookById("1")
        assertEquals(book, result)
    }

    @Test
    fun `insertBook calls dao insertBook`() = runTest {
        val book = Book(id = "1", name = "Book 1")
        bookRepository.insertBook(book)
        coVerify { mockBookDao.insertBook(book) }
    }

    @Test
    fun `updateBook calls dao updateBook`() = runTest {
        val book = Book(id = "1", name = "Book 1")
        bookRepository.updateBook(book)
        coVerify { mockBookDao.updateBook(book) }
    }

    @Test
    fun `deleteBook calls dao deleteBook`() = runTest {
        val book = Book(id = "1", name = "Book 1")
        bookRepository.deleteBook(book)
        coVerify { mockBookDao.deleteBook(book) }
    }

    @Test
    fun `updateLastModified calls dao updateLastModified`() = runTest {
        bookRepository.updateLastModified("1")
        coVerify { mockBookDao.updateLastModified("1", any()) }
    }
}
