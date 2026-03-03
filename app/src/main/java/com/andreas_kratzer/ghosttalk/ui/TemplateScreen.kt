package com.andreas_kratzer.ghosttalk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.components.GhostTalkCard
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import androidx.compose.material.icons.filled.GridView

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.template_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button_content_description))
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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(templates) { template ->
                GhostTalkCard(
                    title = template.name,
                    subtitle = "Raster: ${template.rows}x${template.columns} " + if (template.isBuiltIn) "(${stringResource(R.string.template_built_in_label)})" else "(${stringResource(R.string.template_custom_label)})",
                    icon = Icons.Default.GridView,
                    onClick = { onTemplateClick(template.id) },
                    trailingAction = {
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
