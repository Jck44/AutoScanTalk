package com.andreas_kratzer.ghosttalk.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.data.BookDao
import com.andreas_kratzer.ghosttalk.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BookViewModel(
    application: Application,
    private val bookDao: BookDao
) : AndroidViewModel(application) {

    private val _allBooks = MutableStateFlow<List<Book>>(emptyList())
    val allBooks: StateFlow<List<Book>> = _allBooks.asStateFlow()

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    init {
        viewModelScope.launch {
            bookDao.getAllBooks().collect { books ->
                _allBooks.value = books
            }
        }
    }

    fun selectBook(id: String) {
        _activeBookId.value = id
    }

    fun createNewBook(name: String) {
        val newBook = Book(id = UUID.randomUUID().toString(), name = name)
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.insertBook(newBook)
        }
    }

    fun updateBookName(book: Book, newName: String) {
        val updatedBook = book.copy(name = newName)
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateBook(updatedBook)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.deleteBook(book)
        }
    }
}

class BookViewModelFactory(
    private val application: Application,
    private val bookDao: BookDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BookViewModel(application, bookDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
