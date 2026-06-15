package com.andreas_kratzer.ghosttalk.ui.books

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.CloneBookUseCase
import com.andreas_kratzer.ghosttalk.core.model.Book
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
    private val cloneBookUseCase: CloneBookUseCase,
    val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    val favoriteBookId = settingsRepository.favoriteBookIdFlow
    val forceSoftKeyboard = settingsRepository.forceSoftKeyboardFlow

    private val _allBooks = MutableStateFlow<List<Book>>(emptyList())
    val allBooks: StateFlow<List<Book>> = _allBooks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _autoOpenBookEvent = MutableSharedFlow<String>(
        replay = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val autoOpenBookEvent: SharedFlow<String> = _autoOpenBookEvent.asSharedFlow()

    private var hasAutoOpened = false

    // True while we still expect to auto-navigate away from the book list on
    // startup. The book list renders blank during this window so the user
    // doesn't briefly see the list before landing on the opened book.
    private val _isResolvingStartDestination = MutableStateFlow(
        settingsRepository.isSetupCompleted &&
            settingsRepository.startupBehavior in setOf("SELECTED_BOOK", "USER_MODE")
    )
    val isResolvingStartDestination: StateFlow<Boolean> = _isResolvingStartDestination.asStateFlow()

    /** Called once the auto-open navigation has happened (or won't happen). */
    fun markStartDestinationResolved() {
        _isResolvingStartDestination.value = false
    }


    init {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { books ->
                _allBooks.value = books
                _isLoading.value = false

                if (books.isEmpty()) {
                    hasAutoOpened = true // Don't auto-open if nothing exists
                    _isResolvingStartDestination.value = false
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
                            } else {
                                // No favorite to auto-open: stay on the book list
                                _isResolvingStartDestination.value = false
                            }
                        }
                        "BOOK_SELECTION" -> {
                            hasAutoOpened = true
                            _isResolvingStartDestination.value = false
                            // Stay on book selection
                        }
                    }
                }
            }
        }
    }


    fun createNewBook(
        name: String, 
        actionLogLimit: Int = 100,
        limitScanCycles: Boolean = false,
        scanCycleLimit: Int = 2,
        logIgnoredActions: Boolean = true,
        logStopActions: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val newBook = Book(
            id = UUID.randomUUID().toString(), 
            name = name, 
            createdAt = now, 
            updatedAt = now,
            actionLogLimit = actionLogLimit,
            limitScanCycles = limitScanCycles,
            scanCycleLimit = scanCycleLimit,
            logIgnoredActions = logIgnoredActions,
            logStopActions = logStopActions
        )
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.insertBook(newBook)
        }
    }

    fun updateBook(
        book: Book, 
        newName: String, 
        actionLogLimit: Int,
        limitScanCycles: Boolean,
        scanCycleLimit: Int,
        logIgnoredActions: Boolean,
        logStopActions: Boolean
    ) {
        val updatedBook = book.copy(
            name = newName, 
            actionLogLimit = actionLogLimit,
            limitScanCycles = limitScanCycles,
            scanCycleLimit = scanCycleLimit,
            logIgnoredActions = logIgnoredActions,
            logStopActions = logStopActions,
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.updateBook(updatedBook)
        }
    }

    fun duplicateBook(bookId: String, suffix: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = bookRepository.getBookById(bookId) ?: return@launch
            cloneBookUseCase.execute(
                sourceBookId = bookId,
                customName = "${book.name}$suffix"
            )
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.deleteBook(book)
        }
    }
}
