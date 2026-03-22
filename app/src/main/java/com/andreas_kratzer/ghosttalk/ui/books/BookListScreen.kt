package com.andreas_kratzer.ghosttalk.ui.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    bookViewModel: BookViewModel,
    securityManager: SecurityManager,
    settingsRepository: SettingsRepository,
    onBookSelected: (String) -> Unit,
    onNavigateToGlobalSettings: () -> Unit
) {
    val allBooks by bookViewModel.allBooks.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    
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
                    testTag = "book_card_${book.id}",
                    trailingAction = {
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
                    }
                )
            }
        }

        /* security dialogs for delete and edit removed as they moved to settings screen */

        if (showAddDialog) {
            var newBookName by remember { mutableStateOf("") }
            var isError by remember { mutableStateOf(false) }
            var logLimit by remember { mutableStateOf(100f) }
            var limitScanCycles by remember { mutableStateOf(false) }
            var scanCycleLimit by remember { mutableStateOf(2f) }
            var logIgnoredActions by remember { mutableStateOf(true) }

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
                                autoCorrect = true,
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
