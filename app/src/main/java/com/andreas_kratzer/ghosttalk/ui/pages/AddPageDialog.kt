package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.model.PageTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPageDialog(
    templates: List<PageTemplate>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, rows: Int, columns: Int, templateId: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf<PageTemplate?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_dialog_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // Template Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedTemplate?.name ?: "Leere Seite (Kein Template)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Template (Optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Leere Seite (Kein Template)") },
                            onClick = {
                                selectedTemplate = null
                                expanded = false
                            }
                        )
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = { Text(template.name) },
                                onClick = {
                                    selectedTemplate = template
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.page_name_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Default grid size is 4x4 unless template specifies otherwise
                    val rows = selectedTemplate?.rows ?: 4
                    val cols = selectedTemplate?.columns ?: 4
                    if (name.isNotBlank()) {
                        onConfirm(name, rows, cols, selectedTemplate?.id)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors()
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
