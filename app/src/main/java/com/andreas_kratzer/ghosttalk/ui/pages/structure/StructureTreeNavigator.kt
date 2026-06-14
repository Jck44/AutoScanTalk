package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.domain.pages.TreeNode
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructureTreeNavigator(
    graph: BookNavigationGraph,
    pages: List<Page>,
    pageNames: Map<String, String>,
    focusedPageId: String,
    onFocus: (String) -> Unit,
    onOrphanClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rootNode = remember(graph) { graph.buildTree() }
    var expandedNodes by rememberSaveable { 
        mutableStateOf(rootNode?.let { setOf(it.pageId) } ?: emptySet()) 
    }

    var isOrphansExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

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

    val searchResults = remember(searchQuery, graph.allPageIds, pageNames) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            graph.allPageIds.filter { pageId ->
                val pageName = pageNames[pageId] ?: pageId
                pageName.contains(searchQuery, ignoreCase = true)
            }.sortedBy { pageNames[it] ?: it }
        }
    }

    val dimensions = LocalDimensions.current

    Column(modifier = modifier.padding(dimensions.paddingMedium)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.structure_search), fontSize = 14.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.structure_search_clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensions.paddingMedium)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.structure_search_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(searchResults) { pageId ->
                        val pageName = pageNames[pageId] ?: pageId
                        val isFocused = pageId == focusedPageId
                        val page = pages.find { it.id == pageId }
                        val hasSpeech = page?.buttonConfigs?.any { it != null && it.isActive && it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction } ?: false
                        val hasNav = page?.buttonConfigs?.any { it != null && it.isActive && (it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction || it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction || it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction) } ?: false

                        Surface(
                            color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onFocus(pageId)
                                        searchQuery = ""
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                                    if (hasSpeech) {
                                        val speechColor = if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.SpeakTextBadgeTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.SpeakTextBadgeTextLight
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(speechColor, shape = CircleShape)
                                        )
                                    }
                                    if (hasNav) {
                                        val navColor = if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.NavigateBadgeTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.NavigateBadgeTextLight
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(navColor, shape = CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
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

                                val page = pages.find { it.id == node.pageId }
                                val hasSpeech = page?.buttonConfigs?.any { it != null && it.isActive && it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction } ?: false
                                val hasNav = page?.buttonConfigs?.any { it != null && it.isActive && (it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction || it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction || it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction) } ?: false

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                                    if (hasSpeech) {
                                        val speechColor = if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.SpeakTextBadgeTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.SpeakTextBadgeTextLight
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(speechColor, shape = CircleShape)
                                        )
                                    }
                                    if (hasNav) {
                                        val navColor = if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.NavigateBadgeTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.NavigateBadgeTextLight
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(navColor, shape = CircleShape)
                                        )
                                    }
                                }

                                if (node.children.isNotEmpty()) {
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
                                        .clickable {
                                            if (onOrphanClick != null) {
                                                onOrphanClick(orphanId)
                                            } else {
                                                onFocus(orphanId)
                                            }
                                        }
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
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    val page = pages.find { it.id == orphanId }
                                    val hasSpeech = page?.buttonConfigs?.any { it != null && it.isActive && it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction } ?: false
                                    val hasNav = page?.buttonConfigs?.any { it != null && it.isActive && (it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction || it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction || it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction) } ?: false

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                                        if (hasSpeech) {
                                            val speechColor = if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.SpeakTextBadgeTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.SpeakTextBadgeTextLight
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(speechColor, shape = CircleShape)
                                            )
                                        }
                                        if (hasNav) {
                                            val navColor = if (isDark) com.andreas_kratzer.ghosttalk.core.ui.theme.NavigateBadgeTextDark else com.andreas_kratzer.ghosttalk.core.ui.theme.NavigateBadgeTextLight
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(navColor, shape = CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
