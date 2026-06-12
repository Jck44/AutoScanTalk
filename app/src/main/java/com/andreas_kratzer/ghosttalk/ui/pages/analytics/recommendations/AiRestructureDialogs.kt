package com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal
import com.andreas_kratzer.ghosttalk.core.model.HierarchyPageNode

@Composable
fun AiPageSelectionDialog(
    state: AiRestructureState,
    actions: AiRestructureActions,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.analytics_ai_page_selection_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { actions.onSelectActivePagesOnly() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.analytics_ai_only_active_pages))
                    }
                    TextButton(
                        onClick = { actions.onSelectAllPages() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.analytics_ai_all_pages))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.unfilteredPages.forEach { page ->
                        val isSelected = state.selectedPageIds.contains(page.id)
                        val isActive = state.activeTargetPageIds.contains(page.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { actions.onTogglePageSelection(page.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { actions.onTogglePageSelection(page.id) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = page.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                val badgeText = if (isActive) {
                                    stringResource(R.string.analytics_ai_active_badge)
                                } else {
                                    stringResource(R.string.analytics_ai_inactive_badge)
                                }
                                val badgeColor = if (isActive) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                }
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun AiHierarchyNodeEditDialog(
    node: HierarchyPageNode,
    hierarchy: BookHierarchyProposal,
    actions: AiRestructureActions,
    onDismiss: () -> Unit
) {
    var editingNodeNewName by remember { mutableStateOf(node.name) }
    var editingNodeNewDesc by remember { mutableStateOf(node.description) }
    var editingNodeNewSubpages by remember { mutableStateOf(node.subpages.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seite bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editingNodeNewName,
                    onValueChange = { editingNodeNewName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editingNodeNewDesc,
                    onValueChange = { editingNodeNewDesc = it },
                    label = { Text("Beschreibung / Begründung") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Unterseiten verknüpfen:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                val otherPages = hierarchy.pages.filter { it.name != node.name && it.name != "Hauptseite" }
                Column(
                    modifier = Modifier.height(150.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    otherPages.forEach { other ->
                        val isLinked = editingNodeNewSubpages.contains(other.name)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                editingNodeNewSubpages = if (isLinked) {
                                    editingNodeNewSubpages - other.name
                                } else {
                                    editingNodeNewSubpages + other.name
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isLinked,
                                onCheckedChange = {
                                    editingNodeNewSubpages = if (isLinked) {
                                        editingNodeNewSubpages - other.name
                                    } else {
                                        editingNodeNewSubpages + other.name
                                    }
                                }
                            )
                            Text(other.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedPages = hierarchy.pages.map { page ->
                        if (page.name == node.name) {
                            page.copy(
                                name = editingNodeNewName,
                                description = editingNodeNewDesc,
                                subpages = editingNodeNewSubpages.toList()
                            )
                        } else {
                            val newSubpages = page.subpages.map { subName ->
                                if (subName == node.name) editingNodeNewName else subName
                            }
                            page.copy(subpages = newSubpages)
                        }
                    }
                    actions.onUpdateHierarchyManualEdit(BookHierarchyProposal(updatedPages))
                    onDismiss()
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun AiTokenWarningDialog(
    actions: AiRestructureActions,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.analytics_ai_token_warning_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.analytics_ai_token_warning_desc),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    actions.onSetScope("full")
                    onDismiss()
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
