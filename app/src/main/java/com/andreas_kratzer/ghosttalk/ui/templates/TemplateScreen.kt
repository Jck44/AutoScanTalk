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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SortOrder
import com.andreas_kratzer.ghosttalk.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import kotlinx.coroutines.launch

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.template_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreR.string.back_button_content_description))
                    }
                },
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
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!showAddDialog) showAddDialog = true },
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.template_create_new))
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isLandscape = maxWidth > maxHeight
            val dynamicCardHeight = (maxHeight * if (isLandscape) 0.18f else 0.12f).coerceIn(90.dp, 140.dp)

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
                        .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium),
                    singleLine = true
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = dimensions.paddingLarge),
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

                var usagesToDelete by remember { mutableStateOf<List<UsageLocation>>(emptyList()) }
                val coroutineScope = rememberCoroutineScope()

                templateToDelete?.let { template ->
                    if (usagesToDelete.isEmpty()) {
                        AlertDialog(
                            onDismissRequest = { templateToDelete = null },
                            title = { Text(stringResource(R.string.template_delete_title)) },
                            text = { Text(stringResource(R.string.template_delete_confirm, template.name)) },
                            confirmButton = {
                                Button(
                                    onClick = {
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
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Löschen")
                                }
                            },
                            dismissButton = {
                                Button(
                                    onClick = { templateToDelete = null },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.textButtonColors()
                                ) {
                                    Text("Abbrechen")
                                }
                            }
                        )
                    } else {
                        AlertDialog(
                            onDismissRequest = { 
                                templateToDelete = null
                                usagesToDelete = emptyList()
                            },
                            title = { Text("Vorlage wird verwendet") },
                            text = { 
                                Column {
                                    Text("Die Vorlage \"${template.name}\" wurde zur Erstellung folgender Seiten verwendet:")
                                    usagesToDelete.forEach { usage ->
                                        Text("• Seite: ${usage.name}", modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                                    }
                                    Text("\nBeim Löschen der Vorlage wird die Verknüpfung in diesen Seiten aufgehoben.", style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        templateViewModel.deleteTemplate(template, clearUsages = true)
                                        templateToDelete = null
                                        usagesToDelete = emptyList()
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Trotzdem Löschen")
                                }
                            },
                            dismissButton = {
                                Button(
                                    onClick = { 
                                        templateToDelete = null
                                        usagesToDelete = emptyList()
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.textButtonColors()
                                ) {
                                    Text(stringResource(CoreR.string.action_cancel))
                                }
                            }
                        )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_create_new)) },
        text = {
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
        },
        confirmButton = {
            Button(
                onClick = {
                    // Standardmäßig 4x4
                    if (name.isNotBlank()) {
                        onConfirm(name, 4, 4)
                    }
                },
                shape = MaterialTheme.shapes.medium,
                enabled = name.isNotBlank()
            ) {
                Text("Erstellen")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.textButtonColors()
            ) {
                Text("Abbrechen")
            }
        }
    )
}
