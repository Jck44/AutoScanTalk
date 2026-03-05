package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SortOrder
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.ui.components.rememberReorderableState
import com.andreas_kratzer.ghosttalk.ui.components.reorderableItem
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
    val experimentalSorting by pageViewModel.experimentalManualSorting.collectAsState()
    val activeBookId by pageViewModel.activeBookId.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var pageToEdit by remember { mutableStateOf<Page?>(null) }
    var pageToDelete by remember { mutableStateOf<Page?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // String resources for Toasts (need to be accessed outside Composable for the launcher)
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
                    
                    // Sort Menu
                    var showSortMenu by remember { mutableStateOf(false) }
                    val pageSortOrder by pageViewModel.settingsRepository.pageSortOrderFlow.collectAsState("MANUAL")
                    
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sortieren"
                        )
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        val orders = SortOrder.entries
                        orders.filter { it != SortOrder.MANUAL || experimentalSorting }.forEach { order ->
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
            FloatingActionButton(onClick = { if (!showAddDialog) showAddDialog = true }) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
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
                            
                            IconButton(onClick = { pageToEdit = page }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.page_rename_description),
                                    tint = MaterialTheme.colorScheme.primary
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

        pageToDelete?.let { page ->
            AlertDialog(
                onDismissRequest = { pageToDelete = null },
                title = { Text(stringResource(R.string.page_dialog_delete_title)) },
                text = { Text(stringResource(R.string.page_dialog_delete_confirm, page.name)) },
                confirmButton = {
                    Button(
                        onClick = {
                            pageViewModel.deletePage(page)
                            pageToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { pageToDelete = null },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
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

        pageToEdit?.let { page ->
            var editPageName by remember { mutableStateOf(page.name) }
            var editScanPattern by remember { mutableStateOf(page.scanPattern) }
            val gridRowLabelTemplate = stringResource(R.string.page_row_label)
            val mutableRowNames = remember { 
                androidx.compose.runtime.mutableStateListOf<String>().apply {
                    val initialNames = page.rowNames
                    for (i in 0 until page.rows) {
                        add(initialNames.getOrNull(i) ?: gridRowLabelTemplate.format(i + 1))
                    }
                }
            }
            var expandedPattern by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { pageToEdit = null },
                title = { Text(stringResource(R.string.page_dialog_settings_title)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = editPageName,
                            onValueChange = { editPageName = it },
                            label = { Text(stringResource(R.string.page_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val currentPatternLabel = when (editScanPattern) {
                            "linear" -> stringResource(R.string.settings_pattern_linear)
                            "row_by_row" -> stringResource(R.string.settings_pattern_row_by_row)
                            else -> stringResource(R.string.page_pattern_default)
                        }

                        Box {
                            OutlinedTextField(
                                value = currentPatternLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.page_scan_pattern_override)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedPattern = true }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { expandedPattern = true })
                            DropdownMenu(
                                expanded = expandedPattern,
                                onDismissRequest = { expandedPattern = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.page_pattern_default)) },
                                    onClick = { editScanPattern = null; expandedPattern = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_pattern_linear)) },
                                    onClick = { editScanPattern = "linear"; expandedPattern = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_pattern_row_by_row)) },
                                    onClick = { editScanPattern = "row_by_row"; expandedPattern = false }
                                )
                            }
                        }

                        val effectiveScanPattern = editScanPattern ?: bookDefaultScanPattern
                        if (effectiveScanPattern == "row_by_row") {
                            Text(stringResource(R.string.page_row_announcement_config), style = MaterialTheme.typography.titleSmall)
                            for (i in 0 until page.rows) {
                                OutlinedTextField(
                                    value = mutableRowNames[i],
                                    onValueChange = { mutableRowNames[i] = it },
                                    label = { Text(gridRowLabelTemplate.format(i + 1)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editPageName.isNotBlank()) {
                                pageViewModel.updatePageSettings(page.id, editPageName, editScanPattern, mutableRowNames.toList())
                                pageToEdit = null
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { pageToEdit = null },
                        colors = ButtonDefaults.textButtonColors()
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

