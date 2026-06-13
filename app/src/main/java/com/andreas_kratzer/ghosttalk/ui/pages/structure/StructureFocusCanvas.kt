package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.components.DraggableChip
import com.andreas_kratzer.ghosttalk.ui.components.rememberChipDragDropState
import com.andreas_kratzer.ghosttalk.ui.components.chipDropTarget
import com.andreas_kratzer.ghosttalk.ui.pages.actions.NavigationActionFields
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val MAX_VISIBLE_TARGETS = 12
private const val MAX_VISIBLE_SOURCES = 12

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StructureFocusCanvas(
    focusedPageId: String,
    pages: List<Page>,
    templates: List<PageTemplate>,
    graph: BookNavigationGraph,
    pageNames: Map<String, String>,
    proposal: SplitPageUseCase.PageSplitProposal? = null,
    isSplitLoading: Boolean = false,
    onTriggerSplit: () -> Unit = {},
    onApplySplit: (SplitPageUseCase.PageSplitProposal) -> Unit = {},
    onDiscardSplit: () -> Unit = {},
    onFocus: (String) -> Unit,
    onEditPageInGrid: (String) -> Unit,
    onMoveButton: (fromIndex: Int, targetPageId: String) -> Unit,
    onAddConnection: (targetPageId: String) -> Unit,
    onRemoveConnection: (buttonIndex: Int, targetPageName: String) -> Unit,
    onCreatePage: (name: String, rows: Int, cols: Int, templateId: String?, onCreated: (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val page = remember(pages, focusedPageId) { pages.find { it.id == focusedPageId } }
    val incomingSources = remember(graph, focusedPageId) { graph.incoming[focusedPageId] ?: emptyList() }
    val outgoingEdges = remember(graph, focusedPageId) { graph.outgoing[focusedPageId] ?: emptyList() }

    val dragDropState = rememberChipDragDropState(focusedPageId to proposal)

    data class WizardButtonItem(val buttonId: String, val label: String)
    data class WizardCategory(val name: String, val items: List<WizardButtonItem>)

    val activeButtons = remember(page) {
        page?.buttonConfigs?.filter { it != null && it.isActive && it.label.isNotBlank() }?.map { it!! } ?: emptyList()
    }

    val allItems = remember(activeButtons) {
        activeButtons.mapIndexed { index, button ->
            WizardButtonItem(
                buttonId = "btn_${index}_${java.util.UUID.randomUUID()}",
                label = button.label
            )
        }
    }

    val initialData = remember(proposal, allItems) {
        if (proposal == null) Pair(emptyList(), emptyList())
        else {
            val remainingItems = allItems.toMutableList()
            val cats = proposal.categories.map { catProposal ->
                val catItems = mutableListOf<WizardButtonItem>()
                catProposal.buttonLabels.forEach { label ->
                    val matchIndex = remainingItems.indexOfFirst { it.label == label }
                    if (matchIndex != -1) {
                        catItems.add(remainingItems.removeAt(matchIndex))
                    }
                }
                WizardCategory(catProposal.name, catItems)
            }
            Pair(cats, remainingItems.toList())
        }
    }

    var categoryProposals by remember(initialData) {
        mutableStateOf(initialData.first)
    }
    var unassignedList by remember(initialData) {
        mutableStateOf(initialData.second)
    }

    var showAddNavigationSection by remember(focusedPageId) { mutableStateOf(false) }
    var newNavigationPageId by remember(focusedPageId) { mutableStateOf("") }

    var showAllSources by rememberSaveable(focusedPageId) { mutableStateOf(false) }
    var showAllTargets by rememberSaveable(focusedPageId) { mutableStateOf(false) }

    if (page == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.structure_no_page_selected),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { layoutCoordinates ->
                dragDropState.rootBoxBounds = layoutCoordinates.boundsInRoot()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Section: Incoming
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = stringResource(R.string.structure_incoming),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (incomingSources.isEmpty()) {
                        Text(
                            text = stringResource(R.string.structure_start_page_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val visibleSources = if (showAllSources || incomingSources.size <= MAX_VISIBLE_SOURCES) {
                            incomingSources
                        } else {
                            incomingSources.take(MAX_VISIBLE_SOURCES)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            visibleSources.forEach { sourceId ->
                                val sourceName = pageNames[sourceId] ?: sourceId
                                InputChip(
                                    selected = false,
                                    onClick = { onFocus(sourceId) },
                                    label = { Text(sourceName) }
                                )
                            }
                        }
                        if (incomingSources.size > 12) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = { showAllSources = !showAllSources },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (showAllSources) {
                                        stringResource(R.string.structure_show_less)
                                    } else {
                                        stringResource(R.string.structure_show_more, incomingSources.size - MAX_VISIBLE_SOURCES)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Middle Section: Focused Page Details and buttons
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (proposal != null) Modifier.chipDropTarget(dragDropState, "_unassigned")
                        else Modifier
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = page.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.structure_grid_size, page.rows, page.columns),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (proposal == null) {
                            Button(
                                onClick = { onEditPageInGrid(page.id) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.structure_open_in_grid))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val hasValidButtons = remember(page) {
                        page.buttonConfigs.any { it != null && it.isActive && it.label.isNotBlank() }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (proposal != null) "Aufteilung (Hauptseite) - Verbleibende Tasten" else stringResource(R.string.structure_buttons_on_page),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (proposal == null && hasValidButtons) {
                            TextButton(
                                onClick = onTriggerSplit,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Seite aufteilen (KI)", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isSplitLoading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Vorschlag wird generiert...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else if (proposal != null) {
                        // Proposal Mode unassigned list
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (unassignedList.isEmpty()) {
                                Text(
                                    text = "Alle Tasten werden verschoben. Ziehe Tasten hierher, um sie auf der Hauptseite zu behalten.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                unassignedList.forEach { item ->
                                    key(item.buttonId) {
                                        DraggableChip(
                                            label = item.label,
                                            isDragged = dragDropState.draggedKey == item.buttonId,
                                            onDragStart = { initialCenter, size ->
                                                dragDropState.onDragStart(item.buttonId, item.label, null, initialCenter, size)
                                            },
                                            onDrag = { amount ->
                                                dragDropState.onDrag(amount)
                                            },
                                            onDragEnd = {
                                                if (dragDropState.draggedKey == item.buttonId) {
                                                    val targetCategory = dragDropState.targetBounds.entries.find { entry ->
                                                        val rect = entry.value
                                                        dragDropState.dragGlobalPos.y >= rect.top && dragDropState.dragGlobalPos.y <= rect.bottom
                                                    }?.key

                                                    if (targetCategory != null && targetCategory != "_unassigned") {
                                                        unassignedList = unassignedList.filter { it.buttonId != item.buttonId }
                                                        categoryProposals = categoryProposals.map { cat ->
                                                            if (cat.name == targetCategory) {
                                                                cat.copy(items = cat.items + item)
                                                            } else cat
                                                        }
                                                    }
                                                    dragDropState.clear()
                                                }
                                            },
                                            onDragCancel = {
                                                if (dragDropState.draggedKey == item.buttonId) {
                                                    dragDropState.clear()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Normal mode button configs list
                        if (!hasValidButtons) {
                            Text(
                                text = stringResource(R.string.structure_no_active_buttons),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                page.buttonConfigs.forEachIndexed { index, btn ->
                                    if (btn != null && btn.isActive && btn.label.isNotBlank()) {
                                        key(btn.id) {
                                            DraggableChip(
                                                label = btn.label,
                                                isDragged = dragDropState.draggedKey == index.toString(),
                                                onDragStart = { initialCenter, size ->
                                                    dragDropState.onDragStart(index.toString(), btn.label, null, initialCenter, size)
                                                },
                                                onDrag = { amount ->
                                                    dragDropState.onDrag(amount)
                                                },
                                                onDragEnd = {
                                                    if (dragDropState.draggedKey == index.toString()) {
                                                        val targetPageId = dragDropState.targetBounds.entries.find { entry ->
                                                            entry.value.contains(dragDropState.dragGlobalPos)
                                                        }?.key

                                                        if (targetPageId != null) {
                                                            onMoveButton(index, targetPageId)
                                                        }

                                                        dragDropState.clear()
                                                    }
                                                },
                                                onDragCancel = {
                                                    if (dragDropState.draggedKey == index.toString()) {
                                                        dragDropState.clear()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Section: Outgoing / Targets
            if (proposal != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Vorgeschlagene neue Seiten (Zielzonen)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        categoryProposals.forEach { category ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (dragDropState.draggedKey != null && dragDropState.dragSourceCategory == category.name) 10f else 1f)
                                    .chipDropTarget(dragDropState, category.name),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (category.items.isEmpty()) {
                                            Text(
                                                text = "Zieh Tasten hierher",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        } else {
                                            category.items.forEach { item ->
                                                key(item.buttonId) {
                                                    DraggableChip(
                                                        label = item.label,
                                                        isDragged = dragDropState.draggedKey == item.buttonId,
                                                        onDragStart = { initialCenter, size ->
                                                            dragDropState.onDragStart(item.buttonId, item.label, category.name, initialCenter, size)
                                                        },
                                                        onDrag = { amount ->
                                                            dragDropState.onDrag(amount)
                                                        },
                                                        onDragEnd = {
                                                            if (dragDropState.draggedKey == item.buttonId) {
                                                                val targetCategory = dragDropState.targetBounds.entries.find { entry ->
                                                                    val rect = entry.value
                                                                    dragDropState.dragGlobalPos.y >= rect.top && dragDropState.dragGlobalPos.y <= rect.bottom
                                                                }?.key

                                                                if (targetCategory != null && targetCategory != category.name) {
                                                                    if (targetCategory == "_unassigned") {
                                                                        categoryProposals = categoryProposals.map { cat ->
                                                                            if (cat.name == category.name) {
                                                                                cat.copy(items = cat.items.filter { it.buttonId != item.buttonId })
                                                                            } else cat
                                                                        }
                                                                        unassignedList = unassignedList + item
                                                                    } else {
                                                                        categoryProposals = categoryProposals.map { cat ->
                                                                            when (cat.name) {
                                                                                category.name -> cat.copy(items = cat.items.filter { it.buttonId != item.buttonId })
                                                                                targetCategory -> cat.copy(items = cat.items + item)
                                                                                else -> cat
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                dragDropState.clear()
                                                            }
                                                        },
                                                        onDragCancel = {
                                                            if (dragDropState.draggedKey == item.buttonId) {
                                                                dragDropState.clear()
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val finalProposal = SplitPageUseCase.PageSplitProposal(
                                        categories = categoryProposals.map { cat ->
                                            SplitPageUseCase.CategoryProposal(cat.name, cat.items.map { it.label })
                                        }
                                    )
                                    onApplySplit(finalProposal)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Aufteilung anwenden")
                            }
                            OutlinedButton(
                                onClick = onDiscardSplit,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Abbrechen")
                            }
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.structure_outgoing),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (outgoingEdges.isEmpty()) {
                            Text(
                                text = stringResource(R.string.structure_no_outgoing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val visibleEdges = if (showAllTargets || outgoingEdges.size <= MAX_VISIBLE_TARGETS) {
                                outgoingEdges
                            } else {
                                outgoingEdges.take(MAX_VISIBLE_TARGETS)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                visibleEdges.forEach { edge ->
                                    val targetName = pageNames[edge.targetPageId] ?: edge.targetPageId
                                    InputChip(
                                        selected = false,
                                        onClick = { onFocus(edge.targetPageId) },
                                        modifier = Modifier.chipDropTarget(dragDropState, edge.targetPageId),
                                        label = {
                                            Column {
                                                Text(targetName, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = stringResource(R.string.structure_via_button, page.buttonConfigs.getOrNull(edge.sourceButtonIndex)?.label ?: ""),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { onRemoveConnection(edge.sourceButtonIndex, targetName) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.structure_remove_connection_desc),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            if (outgoingEdges.size > 12) {
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = { showAllTargets = !showAllTargets },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = if (showAllTargets) {
                                            stringResource(R.string.structure_show_less)
                                        } else {
                                            stringResource(R.string.structure_show_more, outgoingEdges.size - MAX_VISIBLE_TARGETS)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (showAddNavigationSection) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.structure_add_connection_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    NavigationActionFields(
                                        navigateToPageId = newNavigationPageId,
                                        onPageSelected = { selectedPageId ->
                                            onAddConnection(selectedPageId)
                                            showAddNavigationSection = false
                                            newNavigationPageId = ""
                                        },
                                        availablePages = pages.filter { it.id != focusedPageId },
                                        templates = templates,
                                        onNavigateToPage = null,
                                        onCreatePage = onCreatePage,
                                        onDismissDialog = {
                                            showAddNavigationSection = false
                                            newNavigationPageId = ""
                                        }
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = { showAddNavigationSection = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.structure_add_connection_btn))
                            }
                        }
                    }
                }
            }
        }

        // Floating Drag Overlay
        if (dragDropState.draggedKey != null && dragDropState.rootBoxBounds != null) {
            val relativeX = dragDropState.dragGlobalPos.x - dragDropState.rootBoxBounds!!.left - dragDropState.draggedSize.x / 2
            val relativeY = dragDropState.dragGlobalPos.y - dragDropState.rootBoxBounds!!.top - dragDropState.draggedSize.y / 2

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            relativeX.roundToInt(),
                            relativeY.roundToInt()
                        )
                    }
                    .zIndex(100f)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp
                ) {
                    Text(
                        text = dragDropState.draggedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
