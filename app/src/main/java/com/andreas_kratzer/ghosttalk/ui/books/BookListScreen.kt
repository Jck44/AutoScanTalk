package com.andreas_kratzer.ghosttalk.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.ui.components.AppBrandHeader
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.core.ui.components.SecurityEntryDialog
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import java.text.SimpleDateFormat
import java.util.Date
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import com.andreas_kratzer.ghosttalk.feature.settings.R as SettingsR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    bookViewModel: BookViewModel,
    settingsRepository: SettingsRepository,
    securityManager: SecurityManager,
    onBookSelected: (String) -> Unit,
    onNavigateToGlobalSettings: () -> Unit
) {
    val allBooks by bookViewModel.allBooks.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<com.andreas_kratzer.ghosttalk.core.model.Book?>(null) }
    var editBookName by remember { mutableStateOf("") }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteSecurity by remember { mutableStateOf(false) }
    var deletingBook by remember { mutableStateOf<com.andreas_kratzer.ghosttalk.core.model.Book?>(null) }

    val dimensions = LocalDimensions.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("dd.MM.yyyy HH:mm", locale) }

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
                    IconButton(
                        onClick = onNavigateToGlobalSettings,
                        modifier = Modifier.testTag("book_list_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(CoreR.string.settings_title_global)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!showAddDialog) showAddDialog = true },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.testTag("book_add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.book_add_description))
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = dimensions.paddingLarge)
    ) {
        val isLandscape = maxWidth > maxHeight
        val dynamicCardHeight = (maxHeight * if (isLandscape) 0.18f else 0.12f).coerceIn(90.dp, 140.dp)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
            contentPadding = PaddingValues(vertical = dimensions.paddingMedium)
        ) {
            items(allBooks) { book ->
                val favoriteId by bookViewModel.favoriteBookId.collectAsState()
                val isFavorite = favoriteId == book.id

                GhostTalkCard(
                    title = book.name,
                    subtitle = stringResource(R.string.book_last_modified_label, dateFormat.format(Date(book.updatedAt))),
                    icon = null, // Removed left icon as requested
                    onClick = { onBookSelected(book.id) },
                    height = dynamicCardHeight,
                    testTag = "book_card_${book.id}",
                    trailingAction = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { settingsRepository.favoriteBookId = book.id },
                                modifier = Modifier.testTag("book_favorite_button_${book.id}")
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Star else GhostTalkIcons.StarBorder,
                                    contentDescription = stringResource(R.string.book_favorite_description),
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    editingBook = book
                                    editBookName = book.name
                                    showEditDialog = true
                                },
                                modifier = Modifier.testTag("book_edit_button_${book.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.action_edit),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    deletingBook = book
                                    if (securityManager.isSecurityRequiredForDeletion()) {
                                        showDeleteSecurity = true
                                    } else {
                                        showDeleteConfirm = true
                                    }
                                },
                                modifier = Modifier.testTag("book_delete_button_${book.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
            }
        }

        // Security PIN verification for deletion
        if (showDeleteSecurity) {
            SecurityEntryDialog(
                onDismiss = { showDeleteSecurity = false },
                onConfirm = { success ->
                    if (success) {
                        showDeleteSecurity = false
                        showDeleteConfirm = true
                    }
                },
                securityManager = securityManager,
                isBiometricEnabled = settingsRepository.isBiometricEnabled
            )
        }

        // Delete confirmation dialog
        if (showDeleteConfirm) {
            val bookToDelete = deletingBook
            if (bookToDelete != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text(stringResource(SettingsR.string.book_dialog_delete_title)) },
                    text = { Text(stringResource(SettingsR.string.book_dialog_delete_confirm, bookToDelete.name)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteConfirm = false
                                bookViewModel.deleteBook(bookToDelete)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(stringResource(R.string.action_delete))
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showDeleteConfirm = false },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.textButtonColors()
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }

        // Edit/Rename Dialog
        if (showEditDialog) {
            val bookToEdit = editingBook
            if (bookToEdit != null) {
                var editError by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { showEditDialog = false },
                    title = { Text(stringResource(R.string.book_dialog_rename_title)) },
                    text = {
                        Column {
                            val forceKeyboard by bookViewModel.forceSoftKeyboard.collectAsState()
                            val keyboardController = LocalSoftwareKeyboardController.current
                            OutlinedTextField(
                                value = editBookName,
                                onValueChange = {
                                    editBookName = it
                                    if (it.isNotBlank()) editError = false
                                },
                                label = { Text(stringResource(R.string.book_name_label)) },
                                singleLine = true,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged {
                                        if (it.isFocused && forceKeyboard) {
                                            keyboardController?.show()
                                        }
                                    },
                                isError = editError,
                                supportingText = {
                                    if (editError) {
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
                                    bookViewModel.updateBook(
                                        book = bookToEdit,
                                        newName = editBookName,
                                        actionLogLimit = bookToEdit.actionLogLimit,
                                        limitScanCycles = bookToEdit.limitScanCycles,
                                        scanCycleLimit = bookToEdit.scanCycleLimit,
                                        logIgnoredActions = bookToEdit.logIgnoredActions,
                                        logStopActions = bookToEdit.logStopActions
                                    )
                                    showEditDialog = false
                                } else {
                                    editError = true
                                }
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showEditDialog = false },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.textButtonColors()
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }

        if (showAddDialog) {
            var newBookName by remember { mutableStateOf("") }
            var isError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(stringResource(R.string.book_dialog_new_title)) },
                text = {
                    Column {
                        val forceKeyboard by bookViewModel.forceSoftKeyboard.collectAsState()
                        val keyboardController = LocalSoftwareKeyboardController.current

                        OutlinedTextField(
                            value = newBookName,
                            onValueChange = { 
                                newBookName = it
                                if (it.isNotBlank()) isError = false
                            },
                            label = { Text(stringResource(R.string.book_name_label)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { 
                                    if (it.isFocused && forceKeyboard) {
                                        keyboardController?.show()
                                    }
                                },
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = true,
                                capitalization = KeyboardCapitalization.Sentences,
                                keyboardType = KeyboardType.Text
                            ),
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
                                bookViewModel.createNewBook(name = newBookName)
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
                        Text(stringResource(CoreR.string.action_cancel))
                    }
                }
            )
        }
        }
    }
}
