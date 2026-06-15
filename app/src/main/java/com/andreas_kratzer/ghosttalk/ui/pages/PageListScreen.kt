@file:Suppress("UNUSED_VALUE")
package com.andreas_kratzer.ghosttalk.ui.pages

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SortOrder
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.core.ui.components.adaptiveCardHeight
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.components.UsageLocationRow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import com.andreas_kratzer.ghosttalk.feature.settings.R as SettingsR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageListScreen(
    pageViewModel: PageViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onEditPage: (String) -> Unit,
    onEditTemplate: (String) -> Unit,
    onOpenStructureEditor: () -> Unit,
    onNavigateToTemplates: () -> Unit = {},
    onNavigateToStaticRow: () -> Unit = {},
    showTopBar: Boolean = true
) {
    val allPages by pageViewModel.filteredPages.collectAsState()
    val templates by pageViewModel.templates.collectAsState()
    val activeBookId by pageViewModel.activeBookId.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val pageToDelete = remember { mutableStateOf<Page?>(null) }
    val usagesToDelete = remember { mutableStateOf<List<UsageLocation>>(emptyList()) }
    val activeTargetPageIds by pageViewModel.activeTargetPageIds.collectAsState()
    
    val pageToActivate = remember { mutableStateOf<Page?>(null) }
    val activationUsages = remember { mutableStateOf<List<UsageLocation>>(emptyList()) }
    
    val pageToDeactivate = remember { mutableStateOf<Page?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    
    var showSortMenu by remember { mutableStateOf(false) }
    val pageSortOrder by pageViewModel.pageSortOrderFlow.collectAsState("MANUAL")
    
    val importSuccessMsg = stringResource(CoreR.string.page_import_success)
    stringResource(CoreR.string.page_export_success)
    stringResource(R.string.page_export_error)

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

    if (onNavigateBack != null) {
        BackHandler {
            onNavigateBack()
        }
    }

    if (pageToActivate.value != null) {
        ActivatePageDialog(
            pageName = pageToActivate.value?.name ?: "",
            usages = activationUsages.value,
            onDismiss = {
                pageToActivate.value = null
                activationUsages.value = emptyList()
            },
            onConfirm = { selectedUsages ->
                pageViewModel.activateButtons(selectedUsages, true)
                pageToActivate.value = null
                activationUsages.value = emptyList()
            }
        )
    }

    if (pageToDeactivate.value != null) {
        AlertDialog(
            onDismissRequest = { pageToDeactivate.value = null },
            title = { Text(stringResource(R.string.page_deactivate_dialog_title)) },
            text = { Text(stringResource(R.string.page_deactivate_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val page = pageToDeactivate.value ?: return@TextButton
                        coroutineScope.launch {
                            val usages = pageViewModel.getPageUsages(page.id)
                            pageViewModel.activateButtons(usages, false)
                            pageToDeactivate.value = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_page_deactivate))
                }
            },
            dismissButton = {
                TextButton(onClick = { pageToDeactivate.value = null }) {
                    Text(stringResource(CoreR.string.action_cancel))
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

    val page = pageToDelete.value
    if (page != null) {
        val usages = usagesToDelete.value
        com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog(
            title = if (usages.isEmpty()) stringResource(R.string.page_dialog_delete_title) else stringResource(R.string.page_dialog_in_use_title),
            onDismiss = { 
                pageToDelete.value = null
                usagesToDelete.value = emptyList()
            },
            confirmText = if (usages.isEmpty()) stringResource(CoreR.string.action_delete) else stringResource(R.string.page_dialog_delete_all),
            onConfirm = {
                pageViewModel.deletePage(page, deleteUsages = usages.isNotEmpty())
                pageToDelete.value = null
                usagesToDelete.value = emptyList()
            },
            dismissText = stringResource(CoreR.string.action_cancel),
            isDestructive = true
        ) { 
            if (usages.isEmpty()) {
                Text(stringResource(R.string.page_dialog_delete_confirm, page.name))
            } else {
                Text(stringResource(R.string.page_dialog_in_use_message, page.name))
                
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .heightIn(max = 280.dp)
                        .verticalScroll(scrollState)
                ) {
                    Column {
                        for (usage in usages) {
                            UsageLocationRow(
                                usage = usage,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            pageToDelete.value = null
                                            usagesToDelete.value = emptyList()
                                            if (usage is UsageLocation.PageUsage) {
                                                onEditPage(usage.id)
                                            } else {
                                                onEditTemplate(usage.id)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = GhostTalkIcons.ArrowForward,
                                            contentDescription = stringResource(R.string.action_navigate)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                
                Text(stringResource(R.string.page_dialog_delete_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    GhostTalkScaffold(
        title = stringResource(CoreR.string.page_list_title),
        onNavigateBack = onNavigateBack,
        showTopBar = showTopBar,
        actions = {
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            
            IconButton(onClick = onOpenStructureEditor) {
                Icon(
                    imageVector = GhostTalkIcons.Link,
                    contentDescription = stringResource(R.string.structure_editor_title)
                )
            }
            
            IconButton(onClick = { showSortMenu = true }) {
                Icon(
                    imageVector = GhostTalkIcons.Sort,
                    contentDescription = stringResource(R.string.action_sort)
                )
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                SortOrder.entries.filter { it != SortOrder.MANUAL }.forEach { order ->
                    val label = when(order) {
                        SortOrder.MANUAL -> stringResource(R.string.sort_manual)
                        SortOrder.NEWEST -> stringResource(R.string.sort_newest)
                        SortOrder.OLDEST -> stringResource(R.string.sort_oldest)
                        SortOrder.A_Z -> stringResource(R.string.sort_a_z)
                        SortOrder.Z_A -> stringResource(R.string.sort_z_a)
                        SortOrder.ACTIVE_FIRST -> stringResource(R.string.sort_active_first)
                        SortOrder.INACTIVE_FIRST -> stringResource(R.string.sort_inactive_first)
                    }
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            pageViewModel.pageSortOrder = order.name
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!showAddDialog) showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.testTag("page_add_fab"),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_new_page)) }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        val dynamicCardHeight = adaptiveCardHeight()

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val searchQuery by pageViewModel.searchQuery.collectAsState()
            if (showTopBar) {
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
                        .padding(horizontal = dimensions.screenPaddingHorizontal, vertical = dimensions.paddingMedium),
                    singleLine = true
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensions.screenPaddingHorizontal, vertical = dimensions.paddingMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
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
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = GhostTalkIcons.Sort,
                                contentDescription = stringResource(R.string.action_sort)
                            )
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOrder.entries.filter { it != SortOrder.MANUAL }.forEach { order ->
                                val label = when(order) {
                                    SortOrder.MANUAL -> stringResource(R.string.sort_manual)
                                    SortOrder.NEWEST -> stringResource(R.string.sort_newest)
                                    SortOrder.OLDEST -> stringResource(R.string.sort_oldest)
                                    SortOrder.A_Z -> stringResource(R.string.sort_a_z)
                                    SortOrder.Z_A -> stringResource(R.string.sort_z_a)
                                    SortOrder.ACTIVE_FIRST -> stringResource(R.string.sort_active_first)
                                    SortOrder.INACTIVE_FIRST -> stringResource(R.string.sort_inactive_first)
                                }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        pageViewModel.pageSortOrder = order.name
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
                    }
                }
            }

            if (onNavigateBack == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensions.screenPaddingHorizontal, vertical = dimensions.paddingSmall),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
                ) {
                    val buttonModifier = Modifier.weight(1f)
                    androidx.compose.material3.OutlinedButton(
                        onClick = onOpenStructureEditor,
                        modifier = buttonModifier
                    ) {
                        Icon(GhostTalkIcons.Link, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = stringResource(R.string.structure_editor_title),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onNavigateToTemplates,
                        modifier = buttonModifier
                    ) {
                        Icon(GhostTalkIcons.GridView, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = stringResource(CoreR.string.template_manage_title),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onNavigateToStaticRow,
                        modifier = buttonModifier
                    ) {
                        Icon(GhostTalkIcons.GridView, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(
                            text = stringResource(SettingsR.string.content_manage_configure_static_row),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (allPages.isEmpty()) {
                com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkEmptyState(
                    icon = com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.Description,
                    title = stringResource(R.string.page_list_empty_title),
                    description = stringResource(R.string.page_list_empty_desc),
                    actionLabel = stringResource(R.string.page_list_empty_action),
                    onAction = { if (!showAddDialog) showAddDialog = true }
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = dimensions.screenPaddingHorizontal),
                    verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                    contentPadding = PaddingValues(vertical = dimensions.paddingMedium)
                ) {
                    items(allPages.size, key = { index -> allPages[index].id }) { index ->
                        val page = allPages[index]
                        val isReferenced = activeTargetPageIds.contains(page.id) || page.id.startsWith("static_row_")
                        GhostTalkCard(
                        title = page.name,
                        subtitle = stringResource(R.string.page_grid_info, page.rows, page.columns),
                        icon = GhostTalkIcons.Description,
                        onClick = { onEditPage(page.id) },
                        height = dynamicCardHeight,
                        testTag = "page_card_${page.id}",
                        modifier = Modifier.then(
                            if (!isReferenced) Modifier.alpha(0.6f) else Modifier
                        ),
                        trailingAction = if (page.id.startsWith("static_row_")) null else {
                            {
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
                                                Icon(GhostTalkIcons.Copy, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_page_deactivate)) },
                                            onClick = {
                                                showMenu = false
                                                pageToDeactivate.value = page
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Clear, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_page_activate)) },
                                            onClick = {
                                                showMenu = false
                                                coroutineScope.launch {
                                                    val usages = pageViewModel.getPageUsages(page.id)
                                                    activationUsages.value = usages
                                                    pageToActivate.value = page
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(CoreR.string.action_delete)) },
                                            onClick = {
                                                showMenu = false
                                                coroutineScope.launch {
                                                    val usages = pageViewModel.getPageUsages(page.id)
                                                    usagesToDelete.value = usages
                                                    pageToDelete.value = page
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
            }
        }
    }
}
}
