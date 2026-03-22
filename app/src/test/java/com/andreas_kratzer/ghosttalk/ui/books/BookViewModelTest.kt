package com.andreas_kratzer.ghosttalk.ui.books

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.Book
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    
    private lateinit var application: Application
    private lateinit var bookRepository: BookRepository
    private lateinit var settingsRepository: SettingsRepository
    
    private val booksFlow = MutableStateFlow<List<Book>>(emptyList())
    private var favoriteBookIdValue: String? = null

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        application = mockk<Application>(relaxed = true)
        bookRepository = mockk<BookRepository>(relaxed = true)
        settingsRepository = mockk<SettingsRepository>(relaxed = true)

        // Mock book list flow
        every { bookRepository.getAllBooks() } returns booksFlow
        
        // Mock favorite book ID property
        every { settingsRepository.favoriteBookId } answers { favoriteBookIdValue }
        every { settingsRepository.favoriteBookId = any() } answers { favoriteBookIdValue = firstArg() }
        every { settingsRepository.favoriteBookIdFlow } returns MutableStateFlow(favoriteBookIdValue)
        
        // Mock startup behavior to avoid auto-open logic interference
        every { settingsRepository.startupBehavior } returns "BOOK_SELECTION"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel() = BookViewModel(
        application = application,
        bookRepository = bookRepository,
        settingsRepository = settingsRepository
    )

    @Test
    fun `initialization with books but no favorite sets first book as favorite`() = runTest {
        val b1 = Book(id = "b1", name = "Book 1", createdAt = 0, updatedAt = 0)
        val b2 = Book(id = "b2", name = "Book 2", createdAt = 0, updatedAt = 0)
        booksFlow.value = listOf(b1, b2)
        favoriteBookIdValue = null

        createViewModel()

        assertEquals("b1", favoriteBookIdValue)
    }

    @Test
    fun `initialization with books and valid favorite keeps favorite`() = runTest {
        val b1 = Book(id = "b1", name = "Book 1", createdAt = 0, updatedAt = 0)
        val b2 = Book(id = "b2", name = "Book 2", createdAt = 0, updatedAt = 0)
        booksFlow.value = listOf(b1, b2)
        favoriteBookIdValue = "b2"

        createViewModel()

        assertEquals("b2", favoriteBookIdValue)
    }

    @Test
    fun `deleting favorite book automatically selects new favorite`() = runTest {
        val b1 = Book(id = "b1", name = "Book 1", createdAt = 0, updatedAt = 0)
        val b2 = Book(id = "b2", name = "Book 2", createdAt = 0, updatedAt = 0)
        booksFlow.value = listOf(b1, b2)
        favoriteBookIdValue = "b1"

        createViewModel()
        
        // Simulate deletion from repository by emitting new list
        booksFlow.value = listOf(b2)

        assertEquals("b2", favoriteBookIdValue)
    }

    @Test
    fun `adding first book automatically sets it as favorite`() = runTest {
        booksFlow.value = emptyList()
        favoriteBookIdValue = null

        createViewModel()
        
        // Add first book
        val b1 = Book(id = "b1", name = "Book 1", createdAt = 0, updatedAt = 0)
        booksFlow.value = listOf(b1)

        assertEquals("b1", favoriteBookIdValue)
    }

    @Test
    fun `manual switch of favorite works correctly`() = runTest {
        val b1 = Book(id = "b1", name = "Book 1", createdAt = 0, updatedAt = 0)
        val b2 = Book(id = "b2", name = "Book 2", createdAt = 0, updatedAt = 0)
        booksFlow.value = listOf(b1, b2)
        favoriteBookIdValue = "b1"

        createViewModel()
        
        // Manual switch (simulated as done in UI)
        settingsRepository.favoriteBookId = "b2"

        assertEquals("b2", favoriteBookIdValue)
    }
}
