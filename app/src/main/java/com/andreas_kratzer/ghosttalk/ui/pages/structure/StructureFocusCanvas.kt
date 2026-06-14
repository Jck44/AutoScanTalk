package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.domain.pages.NavEdge
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.components.DraggableChip
import com.andreas_kratzer.ghosttalk.ui.components.chipDropTarget
import com.andreas_kratzer.ghosttalk.ui.components.rememberChipDragDropState
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import androidx.compose.ui.unit.Dp
import com.andreas_kratzer.ghosttalk.ui.components.ChipDragDropState
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
    onMoveButton: (fromPageId: String, fromIndex: Int, toPageId: String) -> Unit,
    onAddConnection: (targetPageId: String) -> Unit,
    onRemoveConnection: (pageId: String, buttonIndex: Int, targetPageName: String) -> Unit,
    onCreatePage: (name: String, rows: Int, cols: Int, templateId: String?, onCreated: (String) -> Unit) -> Unit,
    viewMode: StructureViewMode = StructureViewMode.CARDS,
    modifier: Modifier = Modifier
) {
    val page = remember(pages, focusedPageId) { pages.find { it.id == focusedPageId } }
    val incomingSources = remember(graph, focusedPageId) { graph.incoming[focusedPageId] ?: emptyList() }
    val outgoingEdges = remember(graph, focusedPageId) { 
        (graph.outgoing[focusedPageId] ?: emptyList()).filter { it.targetPageId != focusedPageId }
    }
    val effectiveStartPageId = remember(graph, pages) {
        graph.startPageId ?: pages.minByOrNull { it.orderIndex }?.id
    }

    val dragDropState = rememberChipDragDropState(focusedPageId to proposal)
    val scrollState = rememberScrollState()

    LaunchedEffect(dragDropState.draggedKey) {
        if (dragDropState.draggedKey != null) {
            while (true) {
                val rootBounds = dragDropState.rootBoxBounds
                if (rootBounds != null) {
                    val globalY = dragDropState.dragGlobalPos.y
                    val threshold = 200f // in pixels
                    val distToBottom = rootBounds.bottom - globalY
                    val distToTop = globalY - rootBounds.top

                    if (distToBottom < threshold && scrollState.value < scrollState.maxValue) {
                        val speedFactor = ((threshold - distToBottom) / threshold).coerceIn(0f, 1f)
                        val scrollAmount = (25f * speedFactor).coerceAtLeast(8f)
                        scrollState.scrollBy(scrollAmount)
                    } else if (distToTop < threshold && scrollState.value > 0) {
                        val speedFactor = ((threshold - distToTop) / threshold).coerceIn(0f, 1f)
                        val scrollAmount = (25f * speedFactor).coerceAtLeast(8f)
                        scrollState.scrollBy(-scrollAmount)
                    }
                }
                kotlinx.coroutines.delay(16) // ~60fps
            }
        }
    }

    data class WizardButtonItem(val buttonId: String, val label: String, val action: com.andreas_kratzer.ghosttalk.core.model.ButtonAction?)
    data class WizardCategory(val name: String, val items: List<WizardButtonItem>)

    val activeButtons = remember(page) {
        page?.buttonConfigs?.filter { it != null && it.isActive && it.label.isNotBlank() }?.map { it!! } ?: emptyList()
    }

    val allItems = remember(activeButtons) {
        activeButtons.mapIndexed { index, button ->
            WizardButtonItem(
                buttonId = "btn_${index}_${java.util.UUID.randomUUID()}",
                label = button.label,
                action = button.buttonAction
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

    var showConnectDialog by remember { mutableStateOf(false) }

    var showAllSources by rememberSaveable(focusedPageId) { mutableStateOf(false) }
    var showAllTargets by rememberSaveable(focusedPageId) { mutableStateOf(false) }

    var expandedTargets by rememberSaveable(focusedPageId) { mutableStateOf(setOf<String>()) }
    var showMainButtons by rememberSaveable(focusedPageId) { mutableStateOf(true) }

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
        if (viewMode == StructureViewMode.GRAPH) {
            StructureGraphView(
                focusedPageId = focusedPageId,
                focusedPageName = page.name,
                incomingSources = incomingSources,
                outgoingEdges = outgoingEdges,
                graph = graph,
                pageNames = pageNames,
                onFocus = onFocus,
                onRemoveConnection = onRemoveConnection,
                isFullView = true,
                modifier = Modifier.fillMaxSize(),
                pages = pages,
                dragDropState = dragDropState,
                onMoveButton = onMoveButton
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isTablet = configuration.screenWidthDp >= 600
                val showGraph = isTablet && (incomingSources.isNotEmpty() || outgoingEdges.isNotEmpty())
                if (showGraph) {
                    StructureGraphView(
                        focusedPageId = focusedPageId,
                        focusedPageName = page.name,
                        incomingSources = incomingSources,
                        outgoingEdges = outgoingEdges,
                        graph = graph,
                        pageNames = pageNames,
                        onFocus = onFocus,
                        onRemoveConnection = null,
                        isFullView = false,
                        pages = pages,
                        dragDropState = dragDropState,
                        onMoveButton = onMoveButton
                    )
                }

            // Top Section: Incoming
            val dimensions = LocalDimensions.current
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
                    com.andreas_kratzer.ghosttalk.core.ui.components.SectionHeader(
                        title = stringResource(R.string.structure_incoming),
                        modifier = Modifier.padding(top = 0.dp, bottom = dimensions.paddingMedium)
                    )
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
                            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
                            verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
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
                            Spacer(modifier = Modifier.height(dimensions.paddingSmall))
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
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (proposal != null) Modifier.chipDropTarget(dragDropState, "_unassigned")
                        else Modifier.chipDropTarget(dragDropState, focusedPageId)
                    )
            ) {
                Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
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
                        }
                    }

                    Spacer(modifier = Modifier.height(dimensions.paddingLarge))

                    val outgoingButtonIndices = remember(outgoingEdges) {
                        outgoingEdges.map { it.sourceButtonIndex }.toSet()
                    }
                    val hasValidButtons = remember(page, outgoingButtonIndices, effectiveStartPageId) {
                        navigableButtons(page, outgoingButtonIndices, effectiveStartPageId).isNotEmpty()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (proposal == null && hasValidButtons) {
                                    Modifier.clickable { showMainButtons = !showMainButtons }
                                } else Modifier
                             ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (proposal == null && hasValidButtons) {
                                Icon(
                                    imageVector = if (showMainButtons) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = dimensions.paddingMedium)
                                )
                            }
                            Text(
                                text = if (proposal != null) "Aufteilung (Hauptseite) - Verbleibende Tasten" else stringResource(R.string.structure_buttons_on_page),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(dimensions.paddingMedium))

                    if (isSplitLoading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(dimensions.paddingExtraLarge), contentAlignment = Alignment.Center) {
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
                                            },
                                            action = item.action
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
                                if (showMainButtons) {
                                    page.buttonConfigs.forEachIndexed { index, btn ->
                                        val action = btn?.buttonAction
                                        val isSelfLoop = if (btn == null) false else {
                                            when (action) {
                                                is NavigateToPageButtonAction -> {
                                                    val target = action.pageId.ifEmpty { effectiveStartPageId }
                                                    target == focusedPageId
                                                }
                                                is NavigateToStartPageButtonAction -> {
                                                    effectiveStartPageId == focusedPageId
                                                }
                                                else -> false
                                            }
                                        }
                                        if (btn != null && btn.isActive && btn.label.isNotBlank() && index !in outgoingButtonIndices && !isSelfLoop) {
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
                                                            if (targetPageId == "delete") {
                                                                onRemoveConnection(focusedPageId, index, btn.label)
                                                            } else {
                                                                onMoveButton(focusedPageId, index, targetPageId)
                                                            }
                                                        }
                                                        dragDropState.clear()
                                                    }
                                                },
                                                onDragCancel = {
                                                    if (dragDropState.draggedKey == index.toString()) {
                                                        dragDropState.clear()
                                                    }
                                                },
                                                action = btn.buttonAction
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

            if (proposal != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(dimensions.paddingMedium),
                        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium)
                    ) {
                        com.andreas_kratzer.ghosttalk.core.ui.components.SectionHeader(
                            title = "Vorgeschlagene neue Seiten (Zielzonen)",
                            modifier = Modifier.padding(top = 0.dp, bottom = dimensions.paddingMedium)
                        )

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
                                                                            if (cat.name == category.name) {
                                                                                cat.copy(items = cat.items.filter { it.buttonId != item.buttonId })
                                                                            } else if (cat.name == targetCategory) {
                                                                                cat.copy(items = cat.items + item)
                                                                            } else cat
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
                                                        },
                                                        action = item.action
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
                        com.andreas_kratzer.ghosttalk.core.ui.components.SectionHeader(
                            title = stringResource(R.string.structure_outgoing),
                            modifier = Modifier.padding(top = 0.dp, bottom = dimensions.paddingMedium)
                        )
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
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                visibleEdges.forEach { edge ->
                                    val targetName = pageNames[edge.targetPageId] ?: edge.targetPageId
                                    val targetPage = pages.find { it.id == edge.targetPageId }
                                    val isExpanded = expandedTargets.contains(edge.targetPageId)
                                    val targetButtons = targetPage?.buttonConfigs?.filterNotNull()?.filter { it.isActive && it.label.isNotBlank() } ?: emptyList()

                                    ElevatedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .chipDropTarget(dragDropState, edge.targetPageId)
                                            .clickable { onFocus(edge.targetPageId) },
                                        colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(targetName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        text = stringResource(R.string.structure_via_button, page.buttonConfigs.getOrNull(edge.sourceButtonIndex)?.label ?: ""),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (targetButtons.isNotEmpty()) {
                                                    IconButton(onClick = {
                                                        expandedTargets = if (isExpanded) expandedTargets - edge.targetPageId else expandedTargets + edge.targetPageId
                                                    }) {
                                                        Icon(
                                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                            contentDescription = null
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { onRemoveConnection(focusedPageId, edge.sourceButtonIndex, targetName) }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = stringResource(R.string.structure_remove_connection_desc),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }

                                            if (isExpanded && targetButtons.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    targetButtons.forEach { btn ->
                                                        val btnIndex = targetPage?.buttonConfigs?.indexOf(btn) ?: -1
                                                        val dragKey = "target_${edge.targetPageId}_$btnIndex"
                                                        if (btnIndex != -1) {
                                                            key(btn.id) {
                                                                DraggableChip(
                                                                    label = btn.label,
                                                                    isDragged = dragDropState.draggedKey == dragKey,
                                                                    onDragStart = { initialCenter, size ->
                                                                        dragDropState.onDragStart(dragKey, btn.label, null, initialCenter, size)
                                                                    },
                                                                    onDrag = { amount ->
                                                                        dragDropState.onDrag(amount)
                                                                    },
                                                                    onDragEnd = {
                                                                        if (dragDropState.draggedKey == dragKey) {
                                                                            val dropTarget = dragDropState.targetBounds.entries.find { entry ->
                                                                                entry.value.contains(dragDropState.dragGlobalPos)
                                                                            }?.key
                                                                            if (dropTarget == focusedPageId) {
                                                                                onMoveButton(edge.targetPageId, btnIndex, focusedPageId)
                                                                            } else if (dropTarget == "delete") {
                                                                                onRemoveConnection(edge.targetPageId, btnIndex, btn.label)
                                                                            }
                                                                            dragDropState.clear()
                                                                        }
                                                                    },
                                                                    onDragCancel = {
                                                                        if (dragDropState.draggedKey == dragKey) {
                                                                            dragDropState.clear()
                                                                        }
                                                                    },
                                                                    action = btn.buttonAction
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
                    }
                }
            }
        }
        }

        // Delete Drop Target Overlay
        if (dragDropState.draggedKey != null && proposal == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
                    .zIndex(10f),
                contentAlignment = Alignment.TopCenter
            ) {
                val isHovered = dragDropState.targetBounds["delete"]?.contains(dragDropState.dragGlobalPos) == true
                val containerColor = if (isHovered) MaterialTheme.colorScheme.error
                                     else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                val contentColor = if (isHovered) MaterialTheme.colorScheme.onError
                                   else MaterialTheme.colorScheme.onErrorContainer
                val scale = if (isHovered) 1.05f else 1f

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = containerColor,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    border = BorderStroke(
                        width = if (isHovered) 2.dp else 1.dp,
                        color = if (isHovered) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(56.dp)
                        .scale(scale)
                        .chipDropTarget(dragDropState, "delete")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = contentColor
                            )
                            Text(
                                text = "Taste löschen (hier ablegen)",
                                color = contentColor,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
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

        // Floating Action Button for adding connection
        if (proposal == null) {
            FloatingActionButton(
                onClick = { showConnectDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.structure_add_connection_btn))
                }
            }
        }
    }

    if (showConnectDialog) {
        SearchablePagePicker(
            title = stringResource(R.string.structure_add_connection_title),
            subtitle = null,
            excludePageId = focusedPageId,
            pages = pages,
            onDismissRequest = { showConnectDialog = false },
            onPageSelected = { targetPageId ->
                onAddConnection(targetPageId)
                showConnectDialog = false
            }
        )
    }
}
