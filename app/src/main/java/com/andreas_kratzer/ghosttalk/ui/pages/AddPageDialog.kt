package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@Suppress("UNUSED_VALUE", "AssignedValueDoubleCheck")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPageDialog(
    templates: List<PageTemplate>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, rows: Int, columns: Int, templateId: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<PageTemplate?>(null) }
    var expanded by remember { mutableStateOf(false) }

    val dimensions = LocalDimensions.current
    val focusManager = LocalFocusManager.current

    GhostTalkDialog(
        title = stringResource(R.string.page_dialog_new_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.action_create),
        onConfirm = {
            // Default grid size is 4x4 unless template specifies otherwise
            val rows = selectedTemplate?.rows ?: 4
            val cols = selectedTemplate?.columns ?: 4
            if (name.isNotBlank()) {
                onConfirm(name, rows, cols, selectedTemplate?.id)
            } else {
                isError = true
            }
        },
        dismissText = stringResource(CoreR.string.action_cancel)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensions.paddingLarge),
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            
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
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Leere Seite (Kein Template)", style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            selectedTemplate = null
                            focusManager.clearFocus()
                            expanded = false
                        }
                    )
                    templates.forEach { template ->
                        DropdownMenuItem(
                            text = { Text(template.name, style = MaterialTheme.typography.bodyLarge) },
                            onClick = {
                                selectedTemplate = template
                                focusManager.clearFocus()
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = name,
                onValueChange = { 
                    name = it 
                    if (it.isNotBlank()) isError = false
                },
                label = { Text(stringResource(R.string.page_name_field)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                isError = isError,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                supportingText = {
                    if (isError) {
                        Text(stringResource(R.string.error_page_name_required))
                    }
                }
            )

        }
    }
}
