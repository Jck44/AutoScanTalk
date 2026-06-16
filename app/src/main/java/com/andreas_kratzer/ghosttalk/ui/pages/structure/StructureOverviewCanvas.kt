package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.domain.pages.SearchPagesUseCase
import com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlin.math.roundToInt

data class OverviewEdge(
    val sourceId: String,
    val targetId: String,
    val sourcePos: Offset,
    val targetPos: Offset,
    val isBackward: Boolean
)

@Composable
fun StructureOverviewCanvas(
    focusedPageId: String,
    pages: List<Page>,
    graph: BookNavigationGraph,
    pageNames: Map<String, String>,
    onFocus: (String) -> Unit,
    onNavigateToGraph: (String) -> Unit,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val orphans = remember(graph) { graph.orphans().toSet() }
    val deadEnds = remember(graph) { graph.deadEnds().toSet() }

    val nodeWidth = 160.dp
    val nodeHeight = 54.dp

    val density = LocalDensity.current
    val nodeWidthPx = with(density) { nodeWidth.toPx() }
    val nodeHeightPx = with(density) { nodeHeight.toPx() }
    val horizontalGapPx = with(density) { 100.dp.toPx() }
    val verticalGapPx = with(density) { 32.dp.toPx() }

    val layout = remember(graph, pages, pageNames) {
        calculateOverviewLayout(
            graph = graph,
            pages = pages,
            pageNames = pageNames,
            nodeWidthPx = nodeWidthPx,
            nodeHeightPx = nodeHeightPx,
            horizontalGapPx = horizontalGapPx,
            verticalGapPx = verticalGapPx
        )
    }

    val currentLayout by rememberUpdatedState(layout)
    val currentFocusedPageId by rememberUpdatedState(focusedPageId)
    val currentOnFocus by rememberUpdatedState(onFocus)
    val currentOnNavigateToGraph by rememberUpdatedState(onNavigateToGraph)
    val interactionSources = remember { mutableMapOf<String, MutableInteractionSource>() }

    val matchingPageIds = remember(searchQuery, pages) {
        if (searchQuery.isBlank()) {
            emptySet()
        } else {
            val useCase = SearchPagesUseCase()
            useCase.execute(pages, searchQuery).map { it.pageId }.toSet()
        }
    }

    val rootId = remember(graph, pages) {
        graph.startPageId?.takeIf { it in graph.allPageIds }
            ?: pages.minByOrNull { it.orderIndex }?.id
    }

    val edgesToDraw = remember(graph, layout, pages, focusedPageId, rootId) {
        val forward = mutableListOf<OverviewEdge>()
        val backward = mutableListOf<OverviewEdge>()
        graph.outgoing.forEach { (sourceId, edges) ->
            val sourcePos = layout[sourceId] ?: return@forEach
            edges.forEach { edge ->
                val targetId = edge.targetPageId
                if (targetId == rootId) return@forEach
                
                val targetPos = layout[targetId] ?: return@forEach
                
                if (targetPos.x < sourcePos.x) {
                    // Backward edge: only draw if sourceId or targetId is focused
                    if (sourceId == focusedPageId || targetId == focusedPageId) {
                        backward.add(OverviewEdge(sourceId, targetId, sourcePos, targetPos, isBackward = true))
                    }
                } else {
                    // Forward edge: always draw
                    forward.add(OverviewEdge(sourceId, targetId, sourcePos, targetPos, isBackward = false))
                }
            }
        }
        forward to backward
    }

    var scale by remember { mutableStateOf(0.9f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Auto-center on focused page
    LaunchedEffect(focusedPageId, layout, canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val targetPos = layout[focusedPageId]
            if (targetPos != null) {
                val targetCenter = Offset(targetPos.x + nodeWidthPx / 2f, targetPos.y + nodeHeightPx / 2f)
                offset = Offset(
                    canvasSize.width / 2f - targetCenter.x * scale,
                    canvasSize.height / 2f - targetCenter.y * scale
                )
            }
        }
    }

    // Auto-center on single search match
    LaunchedEffect(matchingPageIds, layout, canvasSize) {
        if (matchingPageIds.size == 1 && canvasSize.width > 0 && canvasSize.height > 0) {
            val matchedId = matchingPageIds.first()
            val targetPos = layout[matchedId]
            if (targetPos != null) {
                val targetCenter = Offset(targetPos.x + nodeWidthPx / 2f, targetPos.y + nodeHeightPx / 2f)
                offset = Offset(
                    canvasSize.width / 2f - targetCenter.x * scale,
                    canvasSize.height / 2f - targetCenter.y * scale
                )
            }
        }
    }

    if (graph.allPageIds.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.structure_no_graph),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { layoutCoordinates ->
                canvasSize = layoutCoordinates.size
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(0.15f, 3.0f)
                    offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    var pressedNodeId: String? = null
                    var pressInteraction: PressInteraction.Press? = null
                    
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        
                        val canvasDownX = (down.position.x - offset.x) / scale
                        val canvasDownY = (down.position.y - offset.y) / scale
                        
                        val hitNode = currentLayout.entries.find { (pageId, nodeOffset) ->
                            canvasDownX >= nodeOffset.x && canvasDownX <= nodeOffset.x + nodeWidthPx &&
                            canvasDownY >= nodeOffset.y && canvasDownY <= nodeOffset.y + nodeHeightPx
                        }
                        
                        if (hitNode != null) {
                            val pageId = hitNode.key
                            val interactionSource = interactionSources.getOrPut(pageId) { MutableInteractionSource() }
                            launch {
                                val press = PressInteraction.Press(down.position)
                                interactionSource.emit(press)
                                pressInteraction = press
                                pressedNodeId = pageId
                            }
                        }
                        
                        var isClick = true
                        var pointerCount = 1
                        val touchSlop = viewConfiguration.touchSlop
                        
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                pointerCount = maxOf(pointerCount, event.changes.size)
                                
                                if (pointerCount > 1) {
                                    isClick = false
                                }
                                
                                val hasMovedPastSlop = event.changes.any { change ->
                                    (change.position - down.position).getDistance() > touchSlop
                                }
                                if (hasMovedPastSlop) {
                                    isClick = false
                                }
                                
                                if (!isClick && pressedNodeId != null) {
                                    val pageId = pressedNodeId
                                    val interactionSource = interactionSources[pageId]
                                    val press = pressInteraction
                                    if (interactionSource != null && press != null) {
                                        launch {
                                            interactionSource.emit(PressInteraction.Cancel(press))
                                        }
                                    }
                                    pressedNodeId = null
                                    pressInteraction = null
                                }
                                
                                val allUp = event.changes.all { it.changedToUp() }
                                if (allUp) {
                                    if (isClick && pointerCount == 1 && pressedNodeId != null) {
                                        val pageId = pressedNodeId!!
                                        val interactionSource = interactionSources[pageId]
                                        val press = pressInteraction
                                        if (interactionSource != null && press != null) {
                                            launch {
                                                interactionSource.emit(PressInteraction.Release(press))
                                            }
                                        }
                                        
                                        if (pageId == currentFocusedPageId) {
                                            currentOnNavigateToGraph(pageId)
                                        } else {
                                            currentOnFocus(pageId)
                                        }
                                    }
                                    break
                                }
                            }
                        } finally {
                            val pageId = pressedNodeId
                            val interactionSource = pageId?.let { interactionSources[it] }
                            val press = pressInteraction
                            if (interactionSource != null && press != null) {
                                launch {
                                    interactionSource.emit(PressInteraction.Cancel(press))
                                }
                            }
                            pressedNodeId = null
                            pressInteraction = null
                        }
                    }
                }
            }
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEdge(
                edge: OverviewEdge,
                isFocusedConnection: Boolean
            ) {
                val isBackward = edge.isBackward
                val startX = if (isBackward) edge.sourcePos.x else edge.sourcePos.x + nodeWidthPx
                val startY = edge.sourcePos.y + nodeHeightPx / 2f

                val endX = if (isBackward) edge.targetPos.x + nodeWidthPx else edge.targetPos.x
                val endY = edge.targetPos.y + nodeHeightPx / 2f

                val path = Path().apply {
                    moveTo(startX, startY)
                    val midX = startX + (endX - startX) / 2f
                    lineTo(midX, startY)
                    lineTo(midX, endY)
                    lineTo(endX, endY)
                }

                val strokeWidth = if (isFocusedConnection) 3.5.dp.toPx() else 1.5.dp.toPx()
                val stroke = if (isBackward) {
                    Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                } else {
                    Stroke(width = strokeWidth)
                }

                val alpha = if (isFocusedConnection) {
                    if (isBackward) 0.85f else 0.8f
                } else {
                    0.15f
                }

                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = alpha),
                    style = stroke
                )

                // Draw arrowhead
                val arrowLength = if (isFocusedConnection) 10.dp.toPx() else 7.dp.toPx()
                val arrowWidth = if (isFocusedConnection) 6.dp.toPx() else 4.dp.toPx()
                val arrowPath = Path().apply {
                    moveTo(endX, endY)
                    if (isBackward) {
                        lineTo(endX + arrowLength, endY - arrowWidth)
                        lineTo(endX + arrowLength, endY + arrowWidth)
                    } else {
                        lineTo(endX - arrowLength, endY - arrowWidth)
                        lineTo(endX - arrowLength, endY + arrowWidth)
                    }
                    close()
                }
                drawPath(
                    path = arrowPath,
                    color = primaryColor.copy(alpha = if (isFocusedConnection) alpha else alpha + 0.15f)
                )
            }

            val forwardEdges = edgesToDraw.first
            val backwardEdges = edgesToDraw.second

            val (focusedForward, unfocusedForward) = forwardEdges.partition {
                it.sourceId == focusedPageId || it.targetId == focusedPageId
            }
            val (focusedBackward, unfocusedBackward) = backwardEdges.partition {
                it.sourceId == focusedPageId || it.targetId == focusedPageId
            }

            // Draw all unfocused edges first
            unfocusedForward.forEach { edge ->
                drawEdge(edge, isFocusedConnection = false)
            }
            unfocusedBackward.forEach { edge ->
                drawEdge(edge, isFocusedConnection = false)
            }

            // Draw all focused edges on top
            focusedForward.forEach { edge ->
                drawEdge(edge, isFocusedConnection = true)
            }
            focusedBackward.forEach { edge ->
                drawEdge(edge, isFocusedConnection = true)
            }
        }

        // Nodes Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            layout.forEach { (pageId, nodeOffset) ->
                val pageName = pageNames[pageId] ?: pageId
                val isFocused = pageId == focusedPageId
                val isMatched = matchingPageIds.contains(pageId)
                val interactionSource = remember(pageId) {
                    interactionSources.getOrPut(pageId) { MutableInteractionSource() }
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(nodeOffset.x.roundToInt(), nodeOffset.y.roundToInt()) }
                ) {
                    Surface(
                        color = when {
                            isFocused -> MaterialTheme.colorScheme.primaryContainer
                            isMatched -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(
                            width = when {
                                isFocused -> 2.dp
                                isMatched -> 2.dp
                                else -> 1.dp
                            },
                            color = when {
                                isFocused -> MaterialTheme.colorScheme.primary
                                isMatched -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        tonalElevation = if (isFocused || isMatched) 6.dp else 2.dp,
                        modifier = Modifier
                            .size(nodeWidth, nodeHeight)
                            .indication(interactionSource, LocalIndication.current)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = pageName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = when {
                                        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                                        isMatched -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                if (pageId in orphans || pageId in deadEnds) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (pageId in orphans) {
                                            Text(
                                                text = "⚠",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (pageId in deadEnds) {
                                            Text(
                                                text = "⛔",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
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

        // Control HUD (Zoom +/- & Reset)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom In
            FloatingActionButton(
                onClick = { scale = (scale * 1.2f).coerceAtMost(3.0f) },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            // Zoom Out
            FloatingActionButton(
                onClick = { scale = (scale / 1.2f).coerceAtLeast(0.15f) },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            // Recenter
            FloatingActionButton(
                onClick = {
                    scale = 0.9f
                    val targetPos = layout[rootId]
                    if (targetPos != null && canvasSize.width > 0) {
                        val targetCenter = Offset(targetPos.x + nodeWidthPx / 2f, targetPos.y + nodeHeightPx / 2f)
                        offset = Offset(
                            canvasSize.width / 2f - targetCenter.x * scale,
                            canvasSize.height / 2f - targetCenter.y * scale
                        )
                    } else {
                        offset = Offset.Zero
                    }
                },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Recenter"
                )
            }
        }
    }
}

private fun calculateOverviewLayout(
    graph: BookNavigationGraph,
    pages: List<Page>,
    pageNames: Map<String, String>,
    nodeWidthPx: Float,
    nodeHeightPx: Float,
    horizontalGapPx: Float,
    verticalGapPx: Float
): Map<String, Offset> {
    val components = graph.connectedComponents()

    val rootId = graph.startPageId?.takeIf { it in graph.allPageIds }
        ?: pages.minByOrNull { it.orderIndex }?.id

    val mainComponent = components.find { rootId in it } ?: emptySet()
    val otherComponents = components.filter { it != mainComponent }

    val positions = mutableMapOf<String, Offset>()
    var currentYOffset = 0f

    fun layoutComponent(component: Set<String>, startNode: String, startY: Float): Float {
        if (component.isEmpty()) return startY

        val levels = mutableListOf<MutableList<String>>()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<String, Int>>()

        queue.add(startNode to 0)
        visited.add(startNode)

        while (queue.isNotEmpty()) {
            val (node, level) = queue.removeFirst()
            while (levels.size <= level) {
                levels.add(mutableListOf())
            }
            levels[level].add(node)

            val edges = graph.outgoing[node] ?: emptyList()
            for (edge in edges) {
                val target = edge.targetPageId
                if (target in component && target !in visited) {
                    visited.add(target)
                    queue.add(target to level + 1)
                }
            }
        }

        val remaining = component - visited
        if (remaining.isNotEmpty()) {
            if (levels.isEmpty()) levels.add(mutableListOf())
            levels[0].addAll(remaining)
        }

        val maxLevelHeight = levels.maxOfOrNull { it.size } ?: 0
        val totalComponentHeight = maxLevelHeight * (nodeHeightPx + verticalGapPx)

        levels.forEachIndexed { levelIndex, nodesInLevel ->
            val colX = levelIndex * (nodeWidthPx + horizontalGapPx)
            val levelHeight = nodesInLevel.size * (nodeHeightPx + verticalGapPx)
            val startYForLevel = startY + (totalComponentHeight - levelHeight) / 2f

            nodesInLevel.forEachIndexed { nodeIndex, pageId ->
                val nodeY = startYForLevel + nodeIndex * (nodeHeightPx + verticalGapPx)
                positions[pageId] = Offset(colX, nodeY)
            }
        }

        return startY + totalComponentHeight + 100f
    }

    if (rootId != null && rootId in mainComponent) {
        currentYOffset = layoutComponent(mainComponent, rootId, currentYOffset)
    } else if (mainComponent.isNotEmpty()) {
        val firstNode = mainComponent.first()
        currentYOffset = layoutComponent(mainComponent, firstNode, currentYOffset)
    }

    otherComponents.forEach { comp ->
        if (comp.isNotEmpty()) {
            val bestRoot = comp.minByOrNull { pageId ->
                val incomingCount = graph.incoming[pageId]?.count { it in comp } ?: 0
                incomingCount * 10000 + (pages.find { it.id == pageId }?.orderIndex ?: 0)
            } ?: comp.first()
            currentYOffset = layoutComponent(comp, bestRoot, currentYOffset)
        }
    }

    return positions
}


