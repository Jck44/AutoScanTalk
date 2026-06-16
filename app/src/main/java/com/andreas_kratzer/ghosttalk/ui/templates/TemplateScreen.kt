package com.andreas_kratzer.ghosttalk.ui.templates

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SortOrder
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.core.ui.components.adaptiveCardHeight
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    templateViewModel: TemplateViewModel,
    onNavigateBack: () -> Unit,
    onTemplateClick: (String) -> Unit
) {
    val templates by templateViewModel.templates.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<PageTemplate?>(null) }
    val dimensions = LocalDimensions.current

    BackHandler {
        onNavigateBack()
    }

    GhostTalkScaffold(
        title = stringResource(CoreR.string.template_manage_title),
        onNavigateBack = onNavigateBack,
        actions = {
            var showSortMenu by remember { mutableStateOf(false) }
            val templateSortOrder by templateViewModel.settingsRepository.templateSortOrderFlow.collectAsState("MANUAL")
            
            IconButton(onClick = { showSortMenu = true }) {
                Icon(GhostTalkIcons.Sort, contentDescription = "Sortieren")
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                SortOrder.entries.filter { it != SortOrder.MANUAL }.forEach { order ->
                    val label = when(order) {
                        SortOrder.MANUAL -> "Manuell"
                        SortOrder.NEWEST -> "Neueste zuerst"
                        SortOrder.OLDEST -> "Älteste zuerst"
                        SortOrder.A_Z -> "A -> Z"
                        SortOrder.Z_A -> "Z -> A"
                        SortOrder.ACTIVE_FIRST -> stringResource(R.string.sort_active_first)
                        SortOrder.INACTIVE_FIRST -> stringResource(R.string.sort_inactive_first)
                    }
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            templateViewModel.settingsRepository.templateSortOrder = order.name
                            showSortMenu = false
                        },
                        trailingIcon = {
                            if (templateSortOrder == order.name) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!showAddDialog) showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_new_template)) }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val dynamicCardHeight = adaptiveCardHeight()

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val searchQuery by templateViewModel.searchQuery.collectAsState()
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { templateViewModel.updateSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { templateViewModel.updateSearchQuery("") }) {
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

                if (templates.isEmpty()) {
                    com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkEmptyState(
                        icon = GhostTalkIcons.GridView,
                        title = stringResource(R.string.template_list_empty_title),
                        description = stringResource(R.string.template_list_empty_desc),
                        actionLabel = stringResource(R.string.template_list_empty_action),
                        onAction = { if (!showAddDialog) showAddDialog = true }
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 300.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = dimensions.screenPaddingHorizontal),
                        verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                        contentPadding = PaddingValues(vertical = dimensions.paddingMedium)
                    ) {
                        items(templates.size, key = { index -> templates[index].id }) { index ->
                            val template = templates[index]
                        GhostTalkCard(
                            title = template.name,
                            subtitle = "Raster: ${template.rows}x${template.columns} " + if (template.isBuiltIn) "(${stringResource(R.string.template_built_in_label)})" else "(${stringResource(R.string.template_custom_label)})",
                            icon = GhostTalkIcons.GridView,
                            onClick = { onTemplateClick(template.id) },
                            height = dynamicCardHeight,
                            modifier = Modifier.then(
                                if (!templateViewModel.pageRepository.getUsedTemplateIdsFlow().collectAsState(emptySet()).value.contains(template.id)) {
                                    Modifier.alpha(0.6f)
                                } else {
                                    Modifier
                                }
                            ),
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
                                                templateViewModel.duplicateTemplate(template.id, duplicateSuffix) { newId ->
                                                    if (newId != null) {
                                                        onTemplateClick(newId)
                                                    }
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(GhostTalkIcons.Copy, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(CoreR.string.action_delete)) },
                                            onClick = {
                                                showMenu = false
                                                templateToDelete = template
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

                var usagesToDelete by remember { mutableStateOf<List<UsageLocation>>(emptyList()) }
                val coroutineScope = rememberCoroutineScope()

                templateToDelete?.let { template ->
                    if (usagesToDelete.isEmpty()) {
                        GhostTalkDialog(
                            title = stringResource(R.string.template_delete_title),
                            onDismiss = { templateToDelete = null },
                            confirmText = "Löschen",
                            onConfirm = {
                                coroutineScope.launch {
                                    val usages = templateViewModel.getTemplateUsages(template.id)
                                    if (usages.isNotEmpty()) {
                                        usagesToDelete = usages
                                    } else {
                                        templateViewModel.deleteTemplate(template)
                                        templateToDelete = null
                                    }
                                }
                            },
                            dismissText = stringResource(CoreR.string.action_cancel),
                            isDestructive = true
                        ) {
                            Text(stringResource(R.string.template_delete_confirm, template.name))
                        }
                    } else {
                        GhostTalkDialog(
                            title = stringResource(R.string.template_dialog_in_use_title),
                            onDismiss = { 
                                templateToDelete = null
                                usagesToDelete = emptyList()
                            },
                            confirmText = stringResource(R.string.template_dialog_delete_anyway),
                            onConfirm = {
                                templateViewModel.deleteTemplate(template, clearUsages = true)
                                templateToDelete = null
                                usagesToDelete = emptyList()
                            },
                            dismissText = stringResource(CoreR.string.action_cancel),
                            isDestructive = true
                        ) { 
                            Column {
                                Text(stringResource(R.string.template_dialog_in_use_message, template.name))
                                for (usage in usagesToDelete) {
                                    Text(stringResource(R.string.template_dialog_usage_page_item, usage.name), modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                                }
                                Text(stringResource(R.string.template_dialog_delete_warning), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (showAddDialog) {
                    AddTemplateDialog(
                        onDismiss = { showAddDialog = false },
                        onConfirm = { name: String, rows: Int, cols: Int ->
                            templateViewModel.createTemplate(name, rows, cols)
                            showAddDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AddTemplateDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, rows: Int, columns: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val dimensions = LocalDimensions.current

    GhostTalkDialog(
        title = stringResource(R.string.template_create_new),
        onDismiss = onDismiss,
        confirmText = stringResource(CoreR.string.action_create),
        onConfirm = {
            if (name.isNotBlank()) {
                onConfirm(name, 4, 4)
            }
        },
        dismissText = stringResource(CoreR.string.action_cancel),
        confirmEnabled = name.isNotBlank()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.template_name_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
