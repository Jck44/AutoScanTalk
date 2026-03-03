package com.andreas_kratzer.ghosttalk.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BookViewModel @Inject constructor(
    application: Application,
    private val bookRepository: BookRepository
) : AndroidViewModel(application) {

    private val _allBooks = MutableStateFlow<List<Book>>(emptyList())
    val allBooks: StateFlow<List<Book>> = _allBooks.asStateFlow()


    init {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { books ->
                _allBooks.value = books
            }
        }
    }


    fun createNewBook(name: String) {
        val newBook = Book(id = UUID.randomUUID().toString(), name = name)
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.insertBook(newBook)
        }
    }

    fun updateBookName(book: Book, newName: String) {
        val updatedBook = book.copy(name = newName)
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
