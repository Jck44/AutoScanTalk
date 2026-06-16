package com.andreas_kratzer.ghosttalk.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.core.ui.components.SecurityEntryDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.adaptiveCardHeight
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
    val isLoading by bookViewModel.isLoading.collectAsState()
    
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

    GhostTalkScaffold(
        title = stringResource(CoreR.string.app_name),
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!showAddDialog) showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.testTag("book_add_fab"),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_new_book)) }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = dimensions.screenPaddingHorizontal)
    ) {
        val isLandscape = maxWidth > maxHeight
        val dynamicCardHeight = adaptiveCardHeight()

        if (isLoading) {
            // Render blank surface during loading to avoid visual flash/glitch
            Box(modifier = Modifier.fillMaxSize())
        } else if (allBooks.isEmpty()) {
            com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkEmptyState(
                icon = com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.Book,
                title = stringResource(R.string.book_list_empty_title),
                description = stringResource(R.string.book_list_empty_desc),
                actionLabel = stringResource(R.string.book_list_empty_action),
                onAction = { if (!showAddDialog) showAddDialog = true }
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 360.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                contentPadding = PaddingValues(vertical = dimensions.paddingMedium)
            ) {
                items(allBooks) { book ->
                    val favoriteId by bookViewModel.favoriteBookId.collectAsState()
                    val isFavorite = favoriteId == book.id
                    var showMenu by remember { mutableStateOf(false) }

                    GhostTalkCard(
                        title = book.name,
                        subtitle = stringResource(R.string.book_last_modified_label, dateFormat.format(Date(book.updatedAt))),
                        icon = null, // Removed left icon as requested
                        onClick = { onBookSelected(book.id) },
                        height = dynamicCardHeight,
                        testTag = "book_card_${book.id}",
                    trailingAction = {
                        val duplicateSuffix = stringResource(R.string.duplicate_suffix)
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
                            
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.testTag("book_menu_button_${book.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Optionen",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_edit)) },
                                        onClick = {
                                            showMenu = false
                                            editingBook = book
                                            editBookName = book.name
                                            showEditDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null
                                            )
                                        },
                                        modifier = Modifier.testTag("book_edit_button_${book.id}")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_duplicate)) },
                                        onClick = {
                                            showMenu = false
                                            bookViewModel.duplicateBook(book.id, duplicateSuffix)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = GhostTalkIcons.Copy,
                                                contentDescription = null
                                            )
                                        },
                                        modifier = Modifier.testTag("book_duplicate_button_${book.id}")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_delete)) },
                                        onClick = {
                                            showMenu = false
                                            deletingBook = book
                                            if (securityManager.isSecurityRequiredForDeletion()) {
                                                showDeleteSecurity = true
                                            } else {
                                                showDeleteConfirm = true
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        modifier = Modifier.testTag("book_delete_button_${book.id}")
                                    )
                                }
                            }
                        }
                    }
                )
            }
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
                GhostTalkDialog(
                    title = stringResource(SettingsR.string.book_dialog_delete_title),
                    onDismiss = { showDeleteConfirm = false },
                    confirmText = stringResource(R.string.action_delete),
                    onConfirm = {
                        showDeleteConfirm = false
                        bookViewModel.deleteBook(bookToDelete)
                    },
                    dismissText = stringResource(R.string.action_cancel),
                    isDestructive = true
                ) {
                    Text(stringResource(SettingsR.string.book_dialog_delete_confirm, bookToDelete.name))
                }
            }
        }

        // Edit/Rename Dialog
        if (showEditDialog) {
            val bookToEdit = editingBook
            if (bookToEdit != null) {
                var editError by remember { mutableStateOf(false) }
                GhostTalkDialog(
                    title = stringResource(R.string.book_dialog_rename_title),
                    onDismiss = { showEditDialog = false },
                    confirmText = stringResource(R.string.action_save),
                    onConfirm = {
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
                    dismissText = stringResource(R.string.action_cancel),
                    isDestructive = false
                ) {
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
            }
        }

        if (showAddDialog) {
            var newBookName by remember { mutableStateOf("") }
            var isError by remember { mutableStateOf(false) }

            GhostTalkDialog(
                title = stringResource(R.string.book_dialog_new_title),
                onDismiss = { showAddDialog = false },
                confirmText = stringResource(R.string.action_create),
                onConfirm = {
                    if (newBookName.isNotBlank()) {
                        bookViewModel.createNewBook(name = newBookName)
                        showAddDialog = false
                    } else {
                        isError = true
                    }
                },
                dismissText = stringResource(CoreR.string.action_cancel),
                isDestructive = false
            ) {
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
        }
        }
    }
}
