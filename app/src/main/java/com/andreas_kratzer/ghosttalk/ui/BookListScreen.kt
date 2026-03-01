package com.andreas_kratzer.ghosttalk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.andreas_kratzer.ghosttalk.model.Book

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
                title = { Text("GhosTTalk - Bücher") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Neues Buch hinzufügen")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allBooks) { book ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookSelected(book.id) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = book.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                            Text(
                                text = "Erstellt: ${dateFormat.format(Date(book.createdAt))}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(onClick = { bookToEdit = book }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Buch umbenennen",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { bookToDelete = book }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Buch löschen",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            var newBookName by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Neues Buch erstellen") },
                text = {
                    OutlinedTextField(
                        value = newBookName,
                        onValueChange = { newBookName = it },
                        label = { Text("Name des Buches") },
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
                        Text("Erstellen")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showAddDialog = false },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text("Abbrechen")
                    }
                }
            )
        }

        bookToEdit?.let { book ->
            var editBookName by remember { mutableStateOf(book.name) }

            AlertDialog(
                onDismissRequest = { bookToEdit = null },
                title = { Text("Buch umbenennen") },
                text = {
                    OutlinedTextField(
                        value = editBookName,
                        onValueChange = { editBookName = it },
                        label = { Text("Name des Buches") },
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
                        Text("Speichern")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { bookToEdit = null },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text("Abbrechen")
                    }
                }
            )
        }

        bookToDelete?.let { book ->
            AlertDialog(
                onDismissRequest = { bookToDelete = null },
                title = { Text("Buch löschen?") },
                text = { Text("Möchtest du das Buch \"${book.name}\" wirklich unwiderruflich löschen? Alle darin enthaltenen Seiten gehen verloren.") },
                confirmButton = {
                    Button(
                        onClick = {
                            bookViewModel.deleteBook(book)
                            bookToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Löschen")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { bookToDelete = null },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text("Abbrechen")
                    }
                }
            )
        }
    }
}
