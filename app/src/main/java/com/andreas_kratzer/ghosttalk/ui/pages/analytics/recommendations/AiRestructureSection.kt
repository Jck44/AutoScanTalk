@file:Suppress("UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_ASSIGNMENT", "unused", "AssignedValueIsNeverRead")
package com.andreas_kratzer.ghosttalk.ui.pages.analytics.recommendations

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.andreas_kratzer.ghosttalk.core.model.HierarchyPageNode
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("AssignedValueIsNeverRead")
@Composable
fun AiRestructureSection(
    context: Context,
    coroutineScope: CoroutineScope,
    state: AiRestructureState,
    actions: AiRestructureActions,
    onNavigateBack: () -> Unit
) {
    var showPageSelectionDialog by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<HierarchyPageNode?>(null) }
    var showTokenWarningDialog by remember { mutableStateOf(false) }

    if (showPageSelectionDialog) {
        AiPageSelectionDialog(
            state = state,
            actions = actions,
            onDismiss = { showPageSelectionDialog = false }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.analytics_ai_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (state.proposal != null && !state.isLoading) {
            TextButton(onClick = { actions.onClearProposal() }) {
                Text(stringResource(R.string.analytics_ai_reset))
            }
        }
    }

    if (state.isGeminiEnabled) {
        if (state.error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { actions.onClearError() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showTokenWarningDialog) {
            AiTokenWarningDialog(
                actions = actions,
                onDismiss = { showTokenWarningDialog = false }
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.analytics_ai_scope_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val scopes = listOf(
                    "quick" to R.string.analytics_ai_scope_quick,
                    "detailed" to R.string.analytics_ai_scope_detailed,
                    "full" to R.string.analytics_ai_scope_full
                )
                scopes.forEach { (scopeKey, labelRes) ->
                    val isSelected = state.restructureScope == scopeKey
                    val containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    val borderColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (scopeKey == "full") {
                                    showTokenWarningDialog = true
                                } else {
                                    actions.onSetScope(scopeKey)
                                }
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = containerColor,
                        contentColor = contentColor,
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        val selectedPageCount = state.selectedPageIds.size
        val totalPageCount = state.unfilteredPages.size
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.analytics_ai_page_selection_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.analytics_ai_btn_select_pages, selectedPageCount, totalPageCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Button(
                    onClick = { showPageSelectionDialog = true },
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Auswählen")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        val hierarchy = state.hierarchy
        if (hierarchy == null) {
            Button(
                onClick = { actions.onGenerateHierarchyProposal(null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isHierarchyLoading
            ) {
                if (state.isHierarchyLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Berechne Struktur...")
                } else {
                    Text("Gesamtkonzept erstellen")
                }
            }
        } else {
            if (editingNode != null) {
                AiHierarchyNodeEditDialog(
                    node = editingNode!!,
                    hierarchy = hierarchy,
                    actions = actions,
                    onDismiss = { editingNode = null }
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                hierarchy.pages.forEach { node ->
                    AiHierarchyProposalCard(
                        node = node,
                        hierarchy = hierarchy,
                        state = state,
                        actions = actions,
                        onEditClick = { editingNode = node }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val allLayoutsLoaded = hierarchy.pages.all { state.pageLayouts.containsKey(it.name) }
                
                if (!allLayoutsLoaded) {
                    Button(
                        onClick = { actions.onLoadAllPageLayoutProposals {} },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = !state.isHierarchyLoading && state.isLoadingPageLayout.isEmpty()
                    ) {
                        Text(stringResource(R.string.analytics_ai_load_all_layouts))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                AiFeedbackCard(
                    actions = actions,
                    isAiHierarchyLoading = state.isHierarchyLoading
                )

                var isSavingAndLoadingLayouts by remember { mutableStateOf(false) }
                var savingProgressText by remember { mutableStateOf("") }
                if (isSavingAndLoadingLayouts) {
                    GhostTalkDialog(
                        title = "Struktur anwenden...",
                        onDismiss = {},
                        confirmText = "",
                        onConfirm = {}
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Text(savingProgressText)
                        }
                    }
                }
                
                Button(
                    onClick = {
                        val missingNodes = hierarchy.pages.filter { !state.pageLayouts.containsKey(it.name) }
                        if (missingNodes.isNotEmpty()) {
                            isSavingAndLoadingLayouts = true
                            coroutineScope.launch {
                                actions.onApplyHierarchyProposal { _ ->
                                    isSavingAndLoadingLayouts = false
                                    Toast.makeText(context, state.toastApplied, Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                actions.onApplyHierarchyProposal { _ ->
                                    Toast.makeText(context, state.toastApplied, Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !state.isHierarchyLoading && !state.isLoading
                ) {
                    Text(stringResource(R.string.analytics_ai_btn_save_test))
                }
            }
        }
    }
}
