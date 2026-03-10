package com.andreas_kratzer.ghosttalk.ui.books

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import java.text.SimpleDateFormat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import com.andreas_kratzer.ghosttalk.ui.theme.GhosTTalkIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.Book
import com.andreas_kratzer.ghosttalk.ui.components.AppBrandHeader
import com.andreas_kratzer.ghosttalk.ui.components.SecurityEntryDialog
import com.andreas_kratzer.ghosttalk.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.ui.components.PinEntryDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.ui.settings.dialogs.BackupSelectionDialog
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    bookViewModel: BookViewModel,
    securityManager: SecurityManager,
    settingsRepository: SettingsRepository,
    onBookSelected: (String) -> Unit,
    onNavigateToGlobalSettings: () -> Unit
) {
    val context = LocalContext.current
    val allBooks by bookViewModel.allBooks.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }
    var bookToEdit by remember { mutableStateOf<Book?>(null) }
    
    var showSecurityDialogForDelete by remember { mutableStateOf(false) }
    var showSecurityDialogForEdit by remember { mutableStateOf(false) }
    val isUnlocked by securityManager.isUnlocked.collectAsState()
    
    val dimensions = LocalDimensions.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppBrandHeader(
                        isLandscape = true, // Smaller version for TopAppBar
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToGlobalSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title_global)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!showAddDialog) showAddDialog = true },
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.book_add_description))
            }
        }
    ) { paddingValues ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val dynamicCardHeight = (configuration.screenHeightDp * if (isLandscape) 0.18f else 0.12f).dp.coerceIn(90.dp, 140.dp)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = dimensions.paddingLarge),
            verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
            contentPadding = PaddingValues(vertical = dimensions.paddingMedium)
        ) {
            items(allBooks) { book ->
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                val favoriteId by bookViewModel.favoriteBookId.collectAsState()
                val isFavorite = favoriteId == book.id
                
                var showMenu by remember { mutableStateOf(false) }

                GhostTalkCard(
                    title = book.name,
                    subtitle = stringResource(R.string.book_last_modified_label, dateFormat.format(Date(book.updatedAt))),
                    icon = null, // Removed left icon as requested
                    onClick = { onBookSelected(book.id) },
                    height = dynamicCardHeight,
                    trailingAction = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            IconButton(
                                onClick = { 
                                    if (!isFavorite || allBooks.size > 1) {
                                        bookViewModel.settingsRepository.favoriteBookId = if (isFavorite) null else book.id
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Star else GhosTTalkIcons.StarBorder,
                                    contentDescription = stringResource(R.string.book_favorite_description),
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            androidx.compose.foundation.layout.Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.action_more)
                                    )
                                }
                                
                                androidx.compose.material3.DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.book_rename_description)) },
                                        onClick = {
                                            showMenu = false
                                            if (!isUnlocked && securityManager.isSecurityRequiredForEdit()) {
                                                bookToEdit = book
                                                showSecurityDialogForEdit = true
                                            } else {
                                                bookToEdit = book
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Edit, contentDescription = null)
                                        }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.book_delete_description)) },
                                        onClick = {
                                            showMenu = false
                                            if (!isUnlocked && securityManager.isSecurityRequiredForDeletion()) {
                                                bookToDelete = book
                                                showSecurityDialogForDelete = true
                                            } else {
                                                bookToDelete = book 
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete, 
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        if (showSecurityDialogForDelete) {
            SecurityEntryDialog(
                onDismiss = { 
                    showSecurityDialogForDelete = false
                    bookToDelete = null
                },
                onConfirm = { success ->
                    if (success) {
                        showSecurityDialogForDelete = false
                        // The confirmation dialog for deletion will now show because bookToDelete is set
                    } else {
                        bookToDelete = null
                        showSecurityDialogForDelete = false
                    }
                },
                securityManager = securityManager,
                isBiometricEnabled = settingsRepository.isBiometricEnabled
            )
        }

        if (showSecurityDialogForEdit) {
            SecurityEntryDialog(
                onDismiss = { 
                    showSecurityDialogForEdit = false
                    bookToEdit = null
                },
                onConfirm = { success ->
                    if (success) {
                        showSecurityDialogForEdit = false
                        // bookToEdit is already set from the IconButton click
                    } else {
                        bookToEdit = null
                        showSecurityDialogForEdit = false
                    }
                },
                securityManager = securityManager,
                isBiometricEnabled = settingsRepository.isBiometricEnabled
            )
        }

        if (showAddDialog) {
            var newBookName by remember { mutableStateOf("") }
            var isError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(stringResource(R.string.book_dialog_new_title)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            value = newBookName,
                            onValueChange = { 
                                newBookName = it
                                if (it.isNotBlank()) isError = false
                            },
                            label = { Text(stringResource(R.string.book_name_label)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                            isError = isError,
                            supportingText = {
                                if (isError) {
                                    Text(stringResource(R.string.error_book_name_required))
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newBookName.isNotBlank()) {
                                bookViewModel.createNewBook(newBookName)
                                showAddDialog = false
                            } else {
                                isError = true
                            }
                        },

                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.action_create))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showAddDialog = false },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        bookToEdit?.let { book ->
            var editBookName by remember { mutableStateOf(book.name) }
            var isError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { bookToEdit = null },
                title = { Text(stringResource(R.string.book_dialog_rename_title)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            value = editBookName,
                            onValueChange = { 
                                editBookName = it 
                                if (it.isNotBlank()) isError = false
                            },
                            label = { Text(stringResource(R.string.book_name_label)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                            isError = isError,
                            supportingText = {
                                if (isError) {
                                    Text(stringResource(R.string.error_book_name_required))
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editBookName.isNotBlank()) {
                                bookViewModel.updateBookName(book, editBookName)
                                bookToEdit = null
                            } else {
                                isError = true
                            }
                        },

                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { bookToEdit = null },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        bookToDelete?.let { book ->
            // Only show delete confirmation if not currently showing PIN dialog
            if (!showSecurityDialogForDelete) {
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
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.action_delete))
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { bookToDelete = null },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.textButtonColors()
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
    }
}
