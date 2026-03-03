package com.andreas_kratzer.ghosttalk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.andreas_kratzer.ghosttalk.model.Book

import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.components.GhostTalkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    bookViewModel: BookViewModel,
    onBookSelected: (String) -> Unit
) {
    val allBooks by bookViewModel.allBooks.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var bookToEdit by remember { mutableStateOf<Book?>(null) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.book_list_title)) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.book_add_description))
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(allBooks) { book ->
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                GhostTalkCard(
                    title = book.name,
                    subtitle = stringResource(R.string.book_created_label, dateFormat.format(Date(book.createdAt))),
                    icon = Icons.Default.Edit, // Books usually don't have a specific icon, but we can use Edit or a custom one
                    onClick = { onBookSelected(book.id) },
                    trailingAction = {
                        Row {
                            IconButton(onClick = { bookToEdit = book }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.book_rename_description),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { bookToDelete = book }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.book_delete_description),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
            }
        }

        if (showAddDialog) {
            var newBookName by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(stringResource(R.string.book_dialog_new_title)) },
                text = {
                    OutlinedTextField(
                        value = newBookName,
                        onValueChange = { newBookName = it },
                        label = { Text(stringResource(R.string.book_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newBookName.isNotBlank()) {
                                bookViewModel.createNewBook(newBookName)
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_create))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showAddDialog = false },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        bookToEdit?.let { book ->
            var editBookName by remember { mutableStateOf(book.name) }

            AlertDialog(
                onDismissRequest = { bookToEdit = null },
                title = { Text(stringResource(R.string.book_dialog_rename_title)) },
                text = {
                    OutlinedTextField(
                        value = editBookName,
                        onValueChange = { editBookName = it },
                        label = { Text(stringResource(R.string.book_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editBookName.isNotBlank()) {
                                bookViewModel.updateBookName(book, editBookName)
                                bookToEdit = null
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { bookToEdit = null },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        bookToDelete?.let { book ->
            AlertDialog(
                onDismissRequest = { bookToDelete = null },
                title = { Text(stringResource(R.string.book_dialog_delete_title)) },
                text = { Text(stringResource(R.string.book_dialog_delete_confirm, book.name)) },
                confirmButton = {
                    Button(
                        onClick = {
                            bookViewModel.deleteBook(book)
                            bookToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { bookToDelete = null },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}
