package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight

@Composable
fun TargetPageSelectionDialog(
    availablePages: List<Page>,
    onPageSelected: (Page) -> Unit,
    onDismiss: () -> Unit,
    currentPageId: String? = null
) {
    val dimensions = LocalDimensions.current
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredPages = remember(searchQuery, availablePages) {
        if (searchQuery.isBlank()) availablePages
        else availablePages.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    val currentPage = remember(currentPageId, availablePages) {
        if (currentPageId != null) availablePages.find { it.id == currentPageId } else null
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.button_move_target_title)) },
        text = {
            Column {
                if (currentPage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = dimensions.paddingMedium)
                            .clickable { onPageSelected(currentPage) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimensions.paddingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Auf aktueller Seite duplizieren",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Erstellt ein Duplikat auf dieser Seite (${currentPage.name})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.search_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.paddingMedium),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done)
                )

                if (filteredPages.isEmpty()) {
                    Text(
                        stringResource(R.string.page_none_found),
                        modifier = Modifier.padding(vertical = dimensions.paddingMedium)
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(filteredPages) { page ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPageSelected(page) }
                                    .padding(vertical = dimensions.paddingMedium)
                            ) {
                                Text(page.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.page_grid_info, page.rows, page.columns),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(CoreR.string.action_cancel))
            }
        }
    )
}

@Composable
fun MoveHiddenPromptDialog(
    requiredRows: Int,
    requiredCols: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    
    val info = when {
        requiredRows > 0 && requiredCols > 0 -> stringResource(R.string.button_move_hidden_prompt_add_both)
        requiredRows > 0 -> stringResource(R.string.button_move_hidden_prompt_add_row)
        else -> stringResource(R.string.button_move_hidden_prompt_add_col)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.button_move_hidden_prompt_title)) },
        text = {
            Text(stringResource(R.string.button_move_hidden_prompt_message, info))
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
