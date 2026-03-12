package com.andreas_kratzer.ghosttalk.ui.books

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BookViewModel @Inject constructor(
    application: Application,
    private val bookRepository: BookRepository,
    val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    val favoriteBookId = settingsRepository.favoriteBookIdFlow

    private val _allBooks = MutableStateFlow<List<Book>>(emptyList())
    val allBooks: StateFlow<List<Book>> = _allBooks.asStateFlow()

    private val _autoOpenBookEvent = MutableSharedFlow<String>(
        replay = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val autoOpenBookEvent: SharedFlow<String> = _autoOpenBookEvent.asSharedFlow()

    private var hasAutoOpened = false


    init {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { books ->
                _allBooks.value = books
                
                if (books.isEmpty()) {
                    hasAutoOpened = true // Don't auto-open if nothing exists
                    return@collect
                }

                // Ensure a favorite is set if none exists
                if (settingsRepository.favoriteBookId == null || books.none { it.id == settingsRepository.favoriteBookId }) {
                    settingsRepository.favoriteBookId = books.firstOrNull()?.id
                }

                if (!hasAutoOpened) {
                    val behavior = settingsRepository.startupBehavior
                    val favoriteId = settingsRepository.favoriteBookId

                    when (behavior) {
                        "SELECTED_BOOK", "USER_MODE" -> {
                            if (favoriteId != null && books.any { it.id == favoriteId }) {
                                hasAutoOpened = true
                                _autoOpenBookEvent.emit(favoriteId)
                            } else if (books.size == 1) {
                                // Fallback for single book if behavior is not BOOK_SELECTION
                                hasAutoOpened = true
                                _autoOpenBookEvent.emit(books[0].id)
                            }
                        }
                        "BOOK_SELECTION" -> {
                            hasAutoOpened = true
                            // Stay on book selection
                        }
                    }
                }
            }
        }
    }


    fun createNewBook(name: String) {
        val now = System.currentTimeMillis()
        val newBook = Book(id = UUID.randomUUID().toString(), name = name, createdAt = now, updatedAt = now)
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.insertBook(newBook)
        }
    }

    fun updateBookName(book: Book, newName: String) {
        val updatedBook = book.copy(name = newName, updatedAt = System.currentTimeMillis())
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.updateBook(updatedBook)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.deleteBook(book)
        }
    }
}
