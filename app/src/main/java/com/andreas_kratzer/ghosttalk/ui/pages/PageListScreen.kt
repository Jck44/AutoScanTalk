package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SortOrder
import com.andreas_kratzer.ghosttalk.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.ui.components.rememberReorderableState
import com.andreas_kratzer.ghosttalk.ui.components.reorderableItem
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.domain.pages.UsageLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageListScreen(
    pageViewModel: PageViewModel,
    onNavigateBack: () -> Unit,
    onEditPage: (String) -> Unit
) {
    val allPages by pageViewModel.filteredPages.collectAsState()
    val templates by pageViewModel.templates.collectAsState()
    val experimentalSorting by pageViewModel.experimentalManualSorting.collectAsState()
    val activeBookId by pageViewModel.activeBookId.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var pageToDelete by remember { mutableStateOf<Page?>(null) }
    var usagesToDelete by remember { mutableStateOf<List<UsageLocation>>(emptyList()) }
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

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val jsonContent = pageViewModel.exportToJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            val writer = OutputStreamWriter(outputStream)
                            writer.write(jsonContent)
                            writer.close()
                        }
                    }
                    android.widget.Toast.makeText(context, exportSuccessMsg, android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, exportErrorMsgTemplate.format(e.message), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val reorderState = rememberReorderableState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

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
                    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    
                    var showSortMenu by remember { mutableStateOf(false) }
                    val pageSortOrder by pageViewModel.settingsRepository.pageSortOrderFlow.collectAsState("MANUAL")
                    
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sortieren"
                        )
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOrder.entries.filter { it != SortOrder.MANUAL || experimentalSorting }.forEach { order ->
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
                        Button(
                            onClick = { exportLauncher.launch("GhosTTalk_Export.json") },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.action_export_json))
                        }
                        Button(onClick = { importLauncher.launch("application/json") }) {
                            Text(stringResource(R.string.action_import_json))
                        }
                    } else {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_export_json)) }, onClick = { showMenu = false; exportLauncher.launch("GhosTTalk_Export.json") })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_import_json)) }, onClick = { showMenu = false; importLauncher.launch("application/json") })
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
                        icon = Icons.Default.Description,
                        onClick = { onEditPage(page.id) },
                        modifier = if (experimentalSorting) {
                            Modifier.reorderableItem(
                                state = reorderState,
                                index = index,
                                onDrag = {
                                    reorderState.findTargetIndexForGrid(gridState)?.let { targetIndex ->
                                        pageViewModel.reorderPages(index, targetIndex)
                                    }
                                }
                            )
                        } else {
                            Modifier
                        },
                        trailingAction = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (experimentalSorting) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Verschieben",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = { pageToDelete = page }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.page_delete_description),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    pageToDelete?.let { page ->
        if (usagesToDelete.isEmpty()) {
            AlertDialog(
                onDismissRequest = { pageToDelete = null },
                title = { Text(stringResource(R.string.page_dialog_delete_title)) },
                text = { Text(stringResource(R.string.page_dialog_delete_confirm, page.name)) },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val usages = pageViewModel.getPageUsages(page.id)
                                if (usages.isNotEmpty()) {
                                    usagesToDelete = usages
                                } else {
                                    pageViewModel.deletePage(page)
                                    pageToDelete = null
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
                        onClick = { pageToDelete = null },
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
                    pageToDelete = null
                    usagesToDelete = emptyList()
                },
                title = { Text("Seite wird verwendet") },
                text = { 
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Die Seite \"${page.name}\" wird an folgenden Stellen zur Navigation verwendet:")
                        usagesToDelete.forEach { usage ->
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
                            pageToDelete = null
                            usagesToDelete = emptyList()
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
                            pageToDelete = null
                            usagesToDelete = emptyList()
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
