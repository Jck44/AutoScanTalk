package com.andreas_kratzer.ghosttalk.ui.pages

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SortOrder
import com.andreas_kratzer.ghosttalk.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.ui.theme.GhosTTalkIcons
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageListScreen(
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit,
    onEditPage: (String) -> Unit
) {
    val allPages by pageViewModel.filteredPages.collectAsState()
    val templates by pageViewModel.templates.collectAsState()
    val activeBookId by pageViewModel.activeBookId.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val pageToDelete = remember { mutableStateOf<Page?>(null) }
    val usagesToDelete = remember { mutableStateOf<List<UsageLocation>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    
    val importSuccessMsg = stringResource(R.string.page_import_success)
    val exportSuccessMsg = stringResource(R.string.page_export_success)
    val exportErrorMsgTemplate = stringResource(R.string.page_export_error)

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val jsonContent = reader.readText()
                    val targetBookId = activeBookId ?: "book-default"
                    pageViewModel.importFromJson(
                        jsonString = jsonContent,
                        bookId = targetBookId,
                        onSuccess = {
                            android.widget.Toast.makeText(context, importSuccessMsg, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onError = { errorMsg ->
                            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.page_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button_content_description)
                        )
                    }
                },
                actions = {
                    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    
                    var showSortMenu by remember { mutableStateOf(false) }
                    val pageSortOrder by pageViewModel.settingsRepository.pageSortOrderFlow.collectAsState("MANUAL")
                    
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = GhosTTalkIcons.Sort,
                            contentDescription = "Sortieren"
                        )
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOrder.entries.filter { it != SortOrder.MANUAL }.forEach { order ->
                            val label = when(order) {
                                SortOrder.MANUAL -> "Manuell"
                                SortOrder.NEWEST -> "Neueste zuerst"
                                SortOrder.OLDEST -> "Älteste zuerst"
                                SortOrder.A_Z -> "A -> Z"
                                SortOrder.Z_A -> "Z -> A"
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    pageViewModel.settingsRepository.pageSortOrder = order.name
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (pageSortOrder == order.name) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }

                    if (isLandscape) {
                        Button(onClick = { importLauncher.launch("application/json") }) {
                            Text(stringResource(R.string.action_import_json_migration))
                        }
                    } else {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_import_json_migration)) }, onClick = { showMenu = false; importLauncher.launch("application/json") })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!showAddDialog) showAddDialog = true },
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.page_add_description))
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        val isLandscape = maxWidth > maxHeight
        val dynamicCardHeight = (maxHeight * if (isLandscape) 0.18f else 0.12f).coerceIn(90.dp, 140.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val searchQuery by pageViewModel.searchQuery.collectAsState()
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { pageViewModel.updateSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.action_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { pageViewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Löschen")
                        }
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium),
                singleLine = true
            )

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = dimensions.paddingLarge),
                verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                contentPadding = PaddingValues(vertical = dimensions.paddingMedium)
            ) {
                items(allPages.size, key = { index -> allPages[index].id }) { index ->
                    val page = allPages[index]
                    GhostTalkCard(
                        title = page.name,
                        subtitle = stringResource(R.string.page_grid_info, page.rows, page.columns),
                        icon = GhosTTalkIcons.Description,
                        onClick = { onEditPage(page.id) },
                        height = dynamicCardHeight,
                        modifier = Modifier,
                        trailingAction = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                var showMenu by remember { mutableStateOf(false) }
                                
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.action_more)
                                    )
                                }

                                val duplicateSuffix = stringResource(R.string.duplicate_suffix)

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_duplicate)) },
                                        onClick = {
                                            showMenu = false
                                            pageViewModel.duplicatePage(page.id, duplicateSuffix) { newId ->
                                                if (newId != null) {
                                                    onEditPage(newId)
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(GhosTTalkIcons.Copy, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_delete)) },
                                        onClick = {
                                            showMenu = false
                                            pageToDelete.value = page
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    pageToDelete.value?.let { page ->
        if (usagesToDelete.value.isEmpty()) {
            AlertDialog(
                onDismissRequest = { pageToDelete.value = null },
                title = { Text(stringResource(R.string.page_dialog_delete_title)) },
                text = { Text(stringResource(R.string.page_dialog_delete_confirm, page.name)) },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val usages = pageViewModel.getPageUsages(page.id)
                                if (usages.isNotEmpty()) {
                                    usagesToDelete.value = usages
                                } else {
                                    pageViewModel.deletePage(page)
                                    pageToDelete.value = null
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { pageToDelete.value = null },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { 
                    pageToDelete.value = null
                    usagesToDelete.value = emptyList()
                },
                title = { Text("Seite wird verwendet") },
                text = { 
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Die Seite \"${page.name}\" wird an folgenden Stellen zur Navigation verwendet:")
                        usagesToDelete.value.forEach { usage ->
                            val typePrefix = if (usage is UsageLocation.PageUsage) "Seite" else "Vorlage"
                            Text("• $typePrefix: ${usage.name}", modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                        }
                        Text("\nBeim Löschen werden auch alle Buttons entfernt, die auf diese Seite verweisen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pageViewModel.deletePage(page, deleteUsages = true)
                            pageToDelete.value = null
                            usagesToDelete.value = emptyList()
                        },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Alles Löschen")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { 
                            pageToDelete.value = null
                            usagesToDelete.value = emptyList()
                        },
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
        AddPageDialog(
            templates = templates,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, rows, cols, templateId ->
                val targetBookId = activeBookId ?: "book-default"
                pageViewModel.createNewPage(name, rows, cols, targetBookId, templateId) { newId ->
                    showAddDialog = false
                    onEditPage(newId)
                }
            }
        )
        }
    }
}
