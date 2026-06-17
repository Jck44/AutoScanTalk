package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.domain.pages.PageSearchResult
import com.andreas_kratzer.ghosttalk.core.domain.pages.SearchPagesUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.TreeNode
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext


// localSearchQuery is written in the uncontrolled (state == null) path and read via recomposition in
// `searchQuery` — the AssignedValueIsNeverRead inspection can't see the Compose-state read, so suppress it.
@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
fun StructureTreeNavigator(
    graph: BookNavigationGraph,
    pages: List<Page>,
    pageNames: Map<String, String>,
    focusedPageId: String,
    onFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: StructureEditorState? = null,
    searchResults: List<PageSearchResult> = emptyList(),
    onOrphanClick: ((String) -> Unit)? = null
) {
    val rootNode = remember(graph) { graph.buildTree() }
    var expandedNodes by rememberSaveable { 
        mutableStateOf(rootNode?.let { setOf(it.pageId) } ?: emptySet()) 
    }

    var isOrphansExpanded by rememberSaveable { mutableStateOf(false) }
    var isProblemsExpanded by rememberSaveable { mutableStateOf(false) }
    var localSearchQuery by rememberSaveable { mutableStateOf("") }
    val searchQuery = if (state != null) state.searchQuery else localSearchQuery
    val onSearchQueryChange: (String) -> Unit = {
        if (state != null) {
            state.searchQuery = it
        } else {
            localSearchQuery = it
        }
    }

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

    val problems = rememberStructureProblems(graph)
    val orphans = remember(problems) { problems.orphans.toList() }
    val orphansSet = problems.orphans
    val deadEndsSet = problems.deadEnds

    val problemPages = remember(graph, problems) {
        graph.allPageIds.filter { it in orphansSet || it in deadEndsSet }
            .sortedBy { pageNames[it] ?: it }
    }

    val localSearchResults: List<PageSearchResult> = if (state == null) {
        val searchPagesUseCase = remember { SearchPagesUseCase() }
        val produced by produceState(emptyList<PageSearchResult>(), searchQuery, pages) {
            snapshotFlow { searchQuery }
                .debounce(200)
                .mapLatest { q ->
                    if (q.isBlank()) emptyList()
                    else withContext(Dispatchers.Default) { searchPagesUseCase.execute(pages, q) }
                }
                .collect { value = it }
        }
        produced
    } else {
        searchResults
    }

    val sortedSearchResults = remember(localSearchResults, pageNames) {
        localSearchResults.sortedBy { pageNames[it.pageId] ?: it.pageId }
    }

    val dimensions = LocalDimensions.current

    Column(modifier = modifier.padding(dimensions.paddingMedium)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.structure_search), fontSize = 14.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
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
                if (sortedSearchResults.isEmpty()) {
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
                    items(sortedSearchResults) { result ->
                        val pageId = result.pageId
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
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onFocus(pageId)
                                            onSearchQueryChange("")
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
                                    WarningBadges(
                                        isOrphan = pageId in orphansSet,
                                        isDeadEnd = pageId in deadEndsSet,
                                        modifier = Modifier.padding(end = 8.dp),
                                        fontSize = 16.sp
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

                                if (result.buttonHits.isNotEmpty()) {
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        result.buttonHits.forEach { hit ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier.clickable {
                                                    onFocus(pageId)
                                                    if (state != null) {
                                                        state.editTarget = pageId to hit.index
                                                    }
                                                    onSearchQueryChange("")
                                                }
                                            ) {
                                                Text(
                                                    text = hit.label,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
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
                                WarningBadges(
                                    isOrphan = node.pageId in orphansSet,
                                    isDeadEnd = node.pageId in deadEndsSet,
                                    modifier = Modifier.padding(end = 8.dp),
                                    fontSize = 16.sp
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
                                    WarningBadges(
                                        isOrphan = orphanId in orphansSet,
                                        isDeadEnd = orphanId in deadEndsSet,
                                        modifier = Modifier.padding(end = 8.dp),
                                        fontSize = 16.sp
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

                if (problemPages.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isProblemsExpanded = !isProblemsExpanded }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isProblemsExpanded) GhostTalkIcons.KeyboardArrowDown else GhostTalkIcons.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "${stringResource(R.string.structure_problems_title)} (${problemPages.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (isProblemsExpanded) {
                        items(problemPages) { pageId ->
                            val pageName = pageNames[pageId] ?: pageId
                            val isFocused = pageId == focusedPageId
                            val isOrphan = pageId in orphansSet
                            val isDeadEnd = pageId in deadEndsSet

                            Surface(
                                color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp)
                                        .clickable { onFocus(pageId) }
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

                                    WarningBadges(
                                        isOrphan = isOrphan,
                                        isDeadEnd = isDeadEnd,
                                        modifier = Modifier.padding(end = 8.dp),
                                        fontSize = 16.sp
                                    )

                                    if (state != null) {
                                        IconButton(
                                            onClick = {
                                                if (isOrphan) {
                                                    state.orphanToConnectId = pageId
                                                } else if (isDeadEnd) {
                                                    state.quickConnectForPageId = pageId
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = GhostTalkIcons.Link,
                                                contentDescription = stringResource(R.string.structure_add_connection_btn),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
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
