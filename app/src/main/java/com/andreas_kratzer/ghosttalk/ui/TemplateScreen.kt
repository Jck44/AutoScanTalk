package com.andreas_kratzer.ghosttalk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.model.SortOrder
import com.andreas_kratzer.ghosttalk.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.ui.components.rememberReorderableState
import com.andreas_kratzer.ghosttalk.ui.components.reorderableItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    templateViewModel: TemplateViewModel,
    onNavigateBack: () -> Unit,
    onTemplateClick: (String) -> Unit
) {
    val templates by templateViewModel.templates.collectAsState()
    val experimentalSorting by templateViewModel.experimentalManualSorting.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<PageTemplate?>(null) }
    val reorderState = rememberReorderableState { from, to ->
        templateViewModel.reorderTemplates(from, to)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.template_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button_content_description))
                    }
                },
                actions = {
                    var showSortMenu by remember { mutableStateOf(false) }
                    val templateSortOrder by templateViewModel.settingsRepository.templateSortOrderFlow.collectAsState("MANUAL")
                    
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sortieren")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOrder.values().filter { it != SortOrder.MANUAL || experimentalSorting }.forEach { order ->
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
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.template_create_new))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(templates.size, key = { index -> templates[index].id }) { index ->
                val template = templates[index]
                GhostTalkCard(
                    title = template.name,
                    subtitle = "Raster: ${template.rows}x${template.columns} " + if (template.isBuiltIn) "(${stringResource(R.string.template_built_in_label)})" else "(${stringResource(R.string.template_custom_label)})",
                    icon = Icons.Default.GridView,
                    onClick = { onTemplateClick(template.id) },
                    modifier = if (experimentalSorting) {
                        Modifier.reorderableItem(
                            state = reorderState,
                            index = index,
                            onDrag = {
                                reorderState.findTargetIndexForList(listState)?.let { targetIndex ->
                                    templateViewModel.reorderTemplates(index, targetIndex)
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
                                onClick = { templateToDelete = template }
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

        templateToDelete?.let { template ->
            AlertDialog(
                onDismissRequest = { templateToDelete = null },
                title = { Text(stringResource(R.string.template_delete_title)) },
                text = { Text(stringResource(R.string.template_delete_confirm, template.name)) },
                confirmButton = {
                    Button(
                        onClick = {
                            templateViewModel.deleteTemplate(template)
                            templateToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Löschen")
                    }
                },
                dismissButton = {
                    Button(onClick = { templateToDelete = null }, colors = ButtonDefaults.textButtonColors()) {
                        Text("Abbrechen")
                    }
                }
            )
        }

        if (showAddDialog) {
            AddTemplateDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, rows, cols ->
                    templateViewModel.createTemplate(name, rows, cols)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun AddTemplateDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, rows: Int, columns: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var rowsStr by remember { mutableStateOf("4") }
    var columnsStr by remember { mutableStateOf("4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_create_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.template_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rowsStr,
                        onValueChange = { rowsStr = it },
                        label = { Text("Zeilen (max 6)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = columnsStr,
                        onValueChange = { columnsStr = it },
                        label = { Text("Spalten (max 6)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rows = rowsStr.toIntOrNull() ?: 4
                    val cols = columnsStr.toIntOrNull() ?: 4
                    if (name.isNotBlank() && rows in 1..6 && cols in 1..6) {
                        onConfirm(name, rows, cols)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Erstellen")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.textButtonColors()) {
                Text("Abbrechen")
            }
        }
    )
}
