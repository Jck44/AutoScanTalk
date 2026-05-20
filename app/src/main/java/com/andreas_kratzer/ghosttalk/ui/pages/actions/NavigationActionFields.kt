package com.andreas_kratzer.ghosttalk.ui.pages.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.pages.AddPageDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationActionFields(
    navigateToPageId: String,
    onPageSelected: (String) -> Unit,
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    onNavigateToPage: ((String) -> Unit)?,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)?,
    onBeforeCreatePage: (() -> Boolean)? = null,
    onDismissDialog: () -> Unit,
    onAutoSave: () -> Unit = {}
) {
    var expandedPageSelect by remember { mutableStateOf(false) }
    var pageSearchQuery by remember { mutableStateOf("") }
    val showAddPageDialogState = remember { mutableStateOf(false) }

    val dimensions = LocalDimensions.current
    val focusManager = LocalFocusManager.current

    val filteredPages = remember(pageSearchQuery, availablePages) {
        val trimmedQuery = pageSearchQuery.trim()
        if (trimmedQuery.isBlank()) availablePages
        else availablePages.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ExposedDropdownMenuBox(
                expanded = expandedPageSelect,
                onExpandedChange = { expandedPageSelect = !expandedPageSelect }
            ) {
                val selectedPageName = availablePages.find { it.id == navigateToPageId }?.name 
                    ?: stringResource(R.string.button_no_page_selected)
                
                OutlinedTextField(
                    value = if (expandedPageSelect) pageSearchQuery else selectedPageName,
                    onValueChange = { if (expandedPageSelect) pageSearchQuery = it },
                    readOnly = !expandedPageSelect,
                    label = { Text(stringResource(R.string.button_target_page_label)) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (navigateToPageId.isNotEmpty() && onNavigateToPage != null) {
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        onDismissDialog()
                                        onNavigateToPage(navigateToPageId)
                                    }
                                ) {
                                    Icon(
                                        imageVector = com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.Edit,
                                        contentDescription = stringResource(R.string.page_manage_target),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (onCreatePage != null) {
                                androidx.compose.material3.IconButton(
                                    onClick = { 
                                        if (onBeforeCreatePage == null || onBeforeCreatePage()) {
                                            showAddPageDialogState.value = true 
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.page_create_new_target),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPageSelect)
                        }
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                )
                
                ExposedDropdownMenu(
                    expanded = expandedPageSelect,
                    onDismissRequest = { 
                        expandedPageSelect = false
                        pageSearchQuery = ""
                    }
                ) {
                    filteredPages.forEach { pageOption ->
                        DropdownMenuItem(
                            text = { Text(pageOption.name, style = MaterialTheme.typography.bodyLarge) },
                            onClick = {
                                onPageSelected(pageOption.id)
                                focusManager.clearFocus()
                                expandedPageSelect = false
                                pageSearchQuery = ""
                                onAutoSave()
                            }
                        )
                    }
                    if (filteredPages.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.page_none_found), style = MaterialTheme.typography.bodyLarge) },
                            onClick = { },
                            enabled = false
                        )
                    }
                }
            }
        }
    }

    if (showAddPageDialogState.value) {
        AddPageDialog(
            templates = templates,
            onDismiss = { showAddPageDialogState.value = false },
            onConfirm = { name, rows, cols, templateId ->
                onCreatePage?.invoke(name, rows, cols, templateId) { newId ->
                    onPageSelected(newId)
                    showAddPageDialogState.value = false
                    onAutoSave()
                }
            }
        )
    }
}
