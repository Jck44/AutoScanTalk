package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.domain.pages.TreeNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructureTreeNavigator(
    graph: BookNavigationGraph,
    pageNames: Map<String, String>,
    focusedPageId: String,
    onFocus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rootNode = remember(graph) { graph.buildTree() }
    var expandedNodes by rememberSaveable { 
        mutableStateOf(rootNode?.let { setOf(it.pageId) } ?: emptySet()) 
    }

    var isOrphansExpanded by rememberSaveable { mutableStateOf(false) }

    fun toggleExpand(pageId: String) {
        expandedNodes = if (pageId in expandedNodes) {
            expandedNodes - pageId
        } else {
            expandedNodes + pageId
        }
    }

    fun flattenTree(node: TreeNode): List<TreeNode> {
        val result = mutableListOf<TreeNode>()
        result.add(node)
        if (node.pageId in expandedNodes && !node.isReference) {
            node.children.forEach { child ->
                result.addAll(flattenTree(child))
            }
        }
        return result
    }

    val visibleNodes = remember(rootNode, expandedNodes) {
        rootNode?.let { flattenTree(it) } ?: emptyList()
    }

    val orphans = remember(graph) { graph.orphans() }

    LazyColumn(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (rootNode == null) {
            item {
                Text(
                    text = stringResource(R.string.structure_no_graph),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(visibleNodes) { node ->
                val pageName = pageNames[node.pageId] ?: node.pageId
                val isFocused = node.pageId == focusedPageId
                val hasChildren = node.children.isNotEmpty() && !node.isReference
                val isExpanded = node.pageId in expandedNodes

                Surface(
                    color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (node.depth * 16).dp)
                            .clickable { onFocus(node.pageId) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasChildren) {
                            IconButton(
                                onClick = { toggleExpand(node.pageId) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) GhostTalkIcons.KeyboardArrowDown else GhostTalkIcons.ArrowForward,
                                    contentDescription = if (isExpanded) stringResource(R.string.structure_collapse) else stringResource(R.string.structure_expand),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(24.dp))
                        }

                        Text(
                            text = pageName,
                            style = if (isFocused) {
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            modifier = Modifier.weight(1f)
                        )

                        if (node.isReference) {
                            SuggestionChip(
                                onClick = { onFocus(node.pageId) },
                                label = { Text(stringResource(R.string.structure_reference_badge), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        } else if (node.children.isNotEmpty()) {
                            Badge {
                                Text("${node.children.size}")
                            }
                        }
                    }
                }
            }
        }

        if (orphans.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isOrphansExpanded = !isOrphansExpanded }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOrphansExpanded) GhostTalkIcons.KeyboardArrowDown else GhostTalkIcons.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "${stringResource(R.string.structure_unconnected)} (${orphans.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (isOrphansExpanded) {
                items(orphans) { orphanId ->
                    val pageName = pageNames[orphanId] ?: orphanId
                    val isFocused = orphanId == focusedPageId
                    Surface(
                        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                                .clickable { onFocus(orphanId) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(24.dp))
                            Text(
                                text = pageName,
                                style = if (isFocused) {
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
