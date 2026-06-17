package com.andreas_kratzer.ghosttalk.ui.pages.structure

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
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
    graph: BookNavigationGraph,
    overviewLayout: Map<String, androidx.compose.ui.geometry.Offset>,
    pageNames: Map<String, String>,
    onFocus: (String) -> Unit,
    onNavigateToGraph: (String) -> Unit,
    matchingPageIds: Set<String>,
    modifier: Modifier = Modifier,
    onZoomInto: (String) -> Unit,
    selection: Map<String, Set<Int>> = emptyMap(),
    templates: List<PageTemplate> = emptyList(),
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)? = null
) {
    val problems = rememberStructureProblems(graph)
    val orphans = problems.orphans
    val deadEnds = problems.deadEnds

    val nodeWidth = 160.dp
    val nodeHeight = 54.dp

    val density = LocalDensity.current
    val nodeWidthPx = with(density) { nodeWidth.toPx() }
    val nodeHeightPx = with(density) { nodeHeight.toPx() }
    val horizontalGapPx = with(density) { 100.dp.toPx() }
    val verticalGapPx = with(density) { 32.dp.toPx() }

    val layout = remember(overviewLayout) {
        val scale = density.density
        overviewLayout.mapValues { (_, dpOffset) ->
            Offset(dpOffset.x * scale, dpOffset.y * scale)
        }
    }

    val currentLayout by rememberUpdatedState(layout)
    val currentFocusedPageId by rememberUpdatedState(focusedPageId)
    val currentOnFocus by rememberUpdatedState(onFocus)
    val currentOnZoomInto by rememberUpdatedState(onZoomInto)
    val interactionSources = remember { mutableMapOf<String, MutableInteractionSource>() }
    LaunchedEffect(graph.allPageIds) {
        val activeIds = graph.allPageIds
        interactionSources.keys.retainAll { it in activeIds }
    }

    val context = LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    var orphansExpanded by remember { mutableStateOf(false) }

    val rootId = remember(graph) {
        graph.startPageId?.takeIf { it in graph.allPageIds }
            ?: overviewLayout.keys.firstOrNull() // pages removed so use fallback
    }

    val edgesToDraw = remember(graph, layout, focusedPageId, rootId, orphansExpanded) {
        val focusedForward = mutableListOf<OverviewEdge>()
        val unfocusedForward = mutableListOf<OverviewEdge>()
        val backward = mutableListOf<OverviewEdge>()
        val seen = mutableSetOf<Pair<String, String>>()
        graph.outgoing.forEach { (sourceId, edges) ->
            if (sourceId == "__orphans_container__") return@forEach
            if (sourceId in orphans && !orphansExpanded && sourceId != focusedPageId) return@forEach
            val sourcePos = layout[sourceId] ?: return@forEach
            edges.forEach { edge ->
                val targetId = edge.targetPageId
                if (targetId == "__orphans_container__") return@forEach
                if (targetId == rootId) return@forEach
                
                val targetPos = layout[targetId] ?: return@forEach
                
                if (!seen.add(sourceId to targetId)) return@forEach
                
                if (targetPos.x < sourcePos.x) {
                    // Backward edge: only draw if sourceId or targetId is focused
                    if (sourceId == focusedPageId || targetId == focusedPageId) {
                        backward.add(OverviewEdge(sourceId, targetId, sourcePos, targetPos, isBackward = true))
                    }
                } else {
                    // Forward edge: always draw
                    val overviewEdge = OverviewEdge(sourceId, targetId, sourcePos, targetPos, isBackward = false)
                    if (sourceId == focusedPageId || targetId == focusedPageId) {
                        focusedForward.add(overviewEdge)
                    } else {
                        unfocusedForward.add(overviewEdge)
                    }
                }
            }
        }
        Triple(focusedForward, unfocusedForward, backward)
    }

    var scale by remember { mutableStateOf(0.9f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val scope = rememberCoroutineScope()
    val decaySpec = remember { exponentialDecay<Offset>() }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var hasCentered by remember(focusedPageId) { mutableStateOf(false) }

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
            hasCentered = true
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clipToBounds()
            .graphicsLayer { alpha = if (hasCentered) 1f else 0f }
            .onGloballyPositioned { layoutCoordinates ->
                canvasSize = layoutCoordinates.size
            }
            .pointerInput(Unit) {
                detectTransformGesturesWithFling(
                    onGestureStart = {
                        flingJob?.cancel()
                        flingJob = null
                    },
                    onGestureEnd = { velocity ->
                        if (!isReducedMotion) {
                            flingJob = scope.launch {
                                val animatable = Animatable(offset, Offset.VectorConverter)
                                animatable.animateDecay(velocity, decaySpec) {
                                    offset = this.value
                                }
                            }
                        }
                    },
                    onGesture = { centroid, pan, zoom, _ ->
                        // The rotation parameter is ignored because rotation is not supported on this map overview canvas
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(0.15f, 3.0f)
                        offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                        scale = newScale
                    }
                )
            }
            .pointerInput(Unit) {
                // Maps a screen position back to a node id, accounting for the current pan/zoom.
                fun hitTest(pos: Offset): String? {
                    val canvasX = (pos.x - offset.x) / scale
                    val canvasY = (pos.y - offset.y) / scale
                    return currentLayout.entries.firstOrNull { (pageId, nodeOffset) ->
                        pageId != "__orphans_container__" &&
                        canvasX >= nodeOffset.x && canvasX <= nodeOffset.x + nodeWidthPx &&
                        canvasY >= nodeOffset.y && canvasY <= nodeOffset.y + nodeHeightPx
                    }?.key
                }
                detectTapGestures(
                    onPress = { pos ->
                        flingJob?.cancel()
                        flingJob = null
                        val pageId = hitTest(pos)
                        if (pageId != null) {
                            val interactionSource = interactionSources.getOrPut(pageId) { MutableInteractionSource() }
                            val press = PressInteraction.Press(pos)
                            interactionSource.emit(press)
                            val released = tryAwaitRelease()
                            interactionSource.emit(
                                if (released) PressInteraction.Release(press)
                                else PressInteraction.Cancel(press)
                            )
                        }
                    },
                    // Tap an unfocused node to focus/centre it; tap the already-focused
                    // node again to open the next zoom level (its focused graph).
                    onTap = { pos ->
                        val pageId = hitTest(pos)
                        if (pageId != null) {
                            if (pageId == currentFocusedPageId) currentOnZoomInto(pageId)
                            else currentOnFocus(pageId)
                        }
                    }
                )
            }
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val orphansOutlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        val orphansBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

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
            val widthFocused = 3.5.dp.toPx()
            val widthUnfocused = 1.5.dp.toPx()
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

            val strokeFocusedForward = Stroke(width = widthFocused)
            val strokeFocusedBackward = Stroke(width = widthFocused, pathEffect = dashEffect)
            val strokeUnfocusedForward = Stroke(width = widthUnfocused)
            val strokeUnfocusedBackward = Stroke(width = widthUnfocused, pathEffect = dashEffect)

            val arrowLengthFocused = 10.dp.toPx()
            val arrowWidthFocused = 6.dp.toPx()
            val arrowLengthUnfocused = 7.dp.toPx()
            val arrowWidthUnfocused = 4.dp.toPx()

            fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEdgeGroup(
                edges: List<OverviewEdge>,
                isFocusedConnection: Boolean
            ) {
                if (edges.isEmpty()) return

                // Group by sourceId, target column x coordinate, and direction (isBackward)
                val groups = edges.groupBy { edge ->
                    val isSameColumn = edge.sourcePos.x == edge.targetPos.x
                    val endX = if (isSameColumn) {
                        edge.targetPos.x
                    } else if (edge.isBackward) {
                        edge.targetPos.x + nodeWidthPx
                    } else {
                        edge.targetPos.x
                    }
                    Triple(edge.sourceId, endX, edge.isBackward)
                }

                groups.forEach { (key, groupEdges) ->
                    val (sourceId, endX, isBackward) = key
                    val firstEdge = groupEdges.first()
                    val isSameColumn = firstEdge.sourcePos.x == firstEdge.targetPos.x
                    val startX = if (isSameColumn) {
                        firstEdge.sourcePos.x
                    } else if (isBackward) {
                        firstEdge.sourcePos.x
                    } else {
                        firstEdge.sourcePos.x + nodeWidthPx
                    }
                    val startY = firstEdge.sourcePos.y + nodeHeightPx / 2f

                    val midX = if (isSameColumn) {
                        firstEdge.sourcePos.x - 20.dp.toPx()
                    } else {
                        startX + (endX - startX) / 2f
                    }

                    val groupStroke = if (isFocusedConnection) {
                        if (isBackward) strokeFocusedBackward else strokeFocusedForward
                    } else {
                        if (isBackward) strokeUnfocusedBackward else strokeUnfocusedForward
                    }

                    val alpha = if (isFocusedConnection) {
                        if (isBackward) 0.85f else 0.8f
                    } else {
                        0.15f
                    }

                    // 1. Draw shared horizontal and vertical segments of elbow path
                    val sharedPath = Path().apply {
                        moveTo(startX, startY)
                        lineTo(midX, startY)
                        val endYs = groupEdges.map { it.targetPos.y + nodeHeightPx / 2f }
                        val minY = minOf(endYs.minOrNull() ?: startY, startY)
                        val maxY = maxOf(endYs.maxOrNull() ?: startY, startY)
                        moveTo(midX, minY)
                        lineTo(midX, maxY)
                    }

                    drawPath(
                        path = sharedPath,
                        color = primaryColor.copy(alpha = alpha),
                        style = groupStroke
                    )

                    // 2. Draw individual horizontal stubs and arrowheads for each edge
                    groupEdges.forEach { edge ->
                        val endY = edge.targetPos.y + nodeHeightPx / 2f
                        val stubPath = Path().apply {
                            moveTo(midX, endY)
                            lineTo(endX, endY)
                        }
                        drawPath(
                            path = stubPath,
                            color = primaryColor.copy(alpha = alpha),
                            style = groupStroke
                        )

                        // Draw arrowhead
                        val arrowLength = if (isFocusedConnection) arrowLengthFocused else arrowLengthUnfocused
                        val arrowWidth = if (isFocusedConnection) arrowWidthFocused else arrowWidthUnfocused
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
                }
            }

            val (focusedForward, unfocusedForward, backwardEdges) = edgesToDraw

            // Draw all unfocused edges first
            drawEdgeGroup(unfocusedForward, isFocusedConnection = false)

            // Draw all focused edges on top
            drawEdgeGroup(focusedForward, isFocusedConnection = true)
            drawEdgeGroup(backwardEdges, isFocusedConnection = true)
            
            // Orphans Container Border (Moved to Canvas to avoid Box clipping)
            if (orphans.isNotEmpty() && orphansExpanded) {
                val containerOffset = layout["__orphans_container__"]
                if (containerOffset != null) {
                    val maxOrphanY = orphans.mapNotNull { layout[it]?.y }.maxOrNull()
                    val paddingPx = 12.dp.toPx()
                    
                    val groupHeightPx = if (maxOrphanY != null) {
                        (maxOrphanY + nodeHeightPx) - containerOffset.y
                    } else {
                        val N = orphans.size
                        nodeHeightPx + 40.dp.toPx() + (nodeHeightPx + 32.dp.toPx()) * ((N - 1).coerceAtLeast(0).toFloat()) + nodeHeightPx
                    }
                    
                    
                    val rectTopLeft = Offset(containerOffset.x - paddingPx, containerOffset.y - paddingPx)
                    val rectSize = androidx.compose.ui.geometry.Size(nodeWidthPx + paddingPx * 2, groupHeightPx + paddingPx * 2)
                    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    
                    drawRoundRect(
                        color = orphansBgColor,
                        topLeft = rectTopLeft,
                        size = rectSize,
                        cornerRadius = cornerRadius
                    )
                    
                    val densityVal = density.density
                    val borderStroke = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(12f * densityVal, 8f * densityVal),
                            0f
                        )
                    )
                    
                    drawRoundRect(
                        color = orphansOutlineColor,
                        topLeft = rectTopLeft,
                        size = rectSize,
                        cornerRadius = cornerRadius,
                        style = borderStroke
                    )
                }
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
                if (pageId == "__orphans_container__") return@forEach
                if (pageId in orphans && !orphansExpanded && pageId != focusedPageId) {
                    return@forEach
                }
                val pageName = pageNames[pageId] ?: pageId
                val isFocused = pageId == focusedPageId
                val isMatched = matchingPageIds.contains(pageId)
                val hasSelectedButtons = selection[pageId]?.isNotEmpty() == true
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
                            hasSelectedButtons -> MaterialTheme.colorScheme.secondaryContainer
                            isMatched -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(
                            width = when {
                                isFocused -> 2.dp
                                hasSelectedButtons -> 2.dp
                                isMatched -> 2.dp
                                else -> 1.dp
                            },
                            color = when {
                                isFocused -> MaterialTheme.colorScheme.primary
                                hasSelectedButtons -> MaterialTheme.colorScheme.secondary
                                isMatched -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        tonalElevation = if (isFocused || hasSelectedButtons || isMatched) 6.dp else 2.dp,
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
                                        hasSelectedButtons -> MaterialTheme.colorScheme.onSecondaryContainer
                                        isMatched -> MaterialTheme.colorScheme.onTertiaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                WarningBadges(
                                    isOrphan = pageId in orphans,
                                    isDeadEnd = pageId in deadEnds,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            if (orphans.isNotEmpty()) {
                val containerOffset = layout["__orphans_container__"]
                if (containerOffset != null) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(containerOffset.x.roundToInt(), containerOffset.y.roundToInt()) }
                    ) {
                        Surface(
                            onClick = { orphansExpanded = !orphansExpanded },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.size(nodeWidth, nodeHeight)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "⚠",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Verwaist (${orphans.size})",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (orphansExpanded) "▾" else "▸",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

        }

        var showAddPageDialog by remember { mutableStateOf(false) }

        if (showAddPageDialog) {
            com.andreas_kratzer.ghosttalk.ui.pages.TargetPageSelectionDialog(
                availablePages = emptyList(),
                templates = templates,
                onPageSelected = {},
                onCreatePage = { name, rows, cols, templateId, onCreated ->
                    onCreatePage?.invoke(name, rows, cols, templateId) { newPageId ->
                        onFocus(newPageId)
                        onCreated(newPageId)
                    }
                    showAddPageDialog = false
                },
                onDismiss = { showAddPageDialog = false }
            )
        }

        // Control HUD (Zoom +/- & Reset)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Add Page FAB
            FloatingActionButton(
                onClick = { showAddPageDialog = true },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Seite hinzufügen"
                )
            }
            // Zoom In
            FloatingActionButton(
                onClick = {
                    flingJob?.cancel()
                    flingJob = null
                    scale = (scale * 1.2f).coerceAtMost(3.0f)
                },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            // Zoom Out
            FloatingActionButton(
                onClick = {
                    flingJob?.cancel()
                    flingJob = null
                    scale = (scale / 1.2f).coerceAtLeast(0.15f)
                },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            // Recenter
            FloatingActionButton(
                onClick = {
                    flingJob?.cancel()
                    flingJob = null
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

        StructureLegend(
            showOrphan = orphans.isNotEmpty(),
            showDeadEnd = deadEnds.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}

fun calculateOverviewLayout(
    graph: BookNavigationGraph,
    pages: List<Page>,
    nodeWidthPx: Float,
    nodeHeightPx: Float,
    horizontalGapPx: Float,
    verticalGapPx: Float
): Map<String, Offset> {
    val orphansSet = graph.orphans().toSet()
    val components = graph.connectedComponents()
    val reachableComponents = components.map { it - orphansSet }.filter { it.isNotEmpty() }

    val rootId = graph.startPageId?.takeIf { it in graph.allPageIds }
        ?: pages.minByOrNull { it.orderIndex }?.id

    val mainComponent = reachableComponents.find { rootId in it } ?: emptySet()
    val otherComponents = reachableComponents.filter { it != mainComponent }

    val positions = mutableMapOf<String, Offset>()
    var currentYOffset = 0f

    fun layoutComponent(component: Set<String>, startNode: String, startY: Float): Float {
        if (component.isEmpty()) return startY

        val visitedDfs = mutableSetOf<String>()
        val stack = mutableSetOf<String>()
        val backEdges = mutableSetOf<Pair<String, String>>()

        fun dfs(node: String) {
            visitedDfs.add(node)
            stack.add(node)
            val edges = graph.outgoing[node] ?: emptyList()
            for (edge in edges) {
                val target = edge.targetPageId
                if (target in component) {
                    if (target in stack) {
                        backEdges.add(node to target)
                    } else if (target !in visitedDfs) {
                        dfs(target)
                    }
                }
            }
            stack.remove(node)
        }

        dfs(startNode)
        for (node in component) {
            if (node !in visitedDfs) {
                dfs(node)
            }
        }

        val memoLevels = mutableMapOf<String, Int>()
        val visiting = mutableSetOf<String>()

        fun getLongestPathLevel(node: String): Int {
            if (node == startNode) return 0
            memoLevels[node]?.let { return it }
            if (!visiting.add(node)) return 0
            
            val parents = graph.incoming[node] ?: emptyList()
            var maxParentLevel = -1
            for (parent in parents) {
                if (parent in component && (parent to node) !in backEdges) {
                    val parentLevel = getLongestPathLevel(parent)
                    if (parentLevel > maxParentLevel) {
                        maxParentLevel = parentLevel
                    }
                }
            }
            visiting.remove(node)
            val lvl = if (maxParentLevel == -1) 0 else maxParentLevel + 1
            memoLevels[node] = lvl
            return lvl
        }

        val levels = mutableListOf<MutableList<String>>()
        component.forEach { node ->
            val lvl = getLongestPathLevel(node)
            while (levels.size <= lvl) {
                levels.add(mutableListOf())
            }
            levels[lvl].add(node)
        }

        // Barycenter sorting (Teil 5.1)
        for (l in 1 until levels.size) {
            val prevLevel = levels[l - 1]
            val prevLevelIndices = prevLevel.withIndex().associate { it.value to it.index }
            
            val barycenters = levels[l].associateWith { node ->
                val parentsInPrev = prevLevel.filter { parent ->
                    graph.outgoing[parent]?.any { it.targetPageId == node } == true
                }
                if (parentsInPrev.isNotEmpty()) {
                    parentsInPrev.map { prevLevelIndices[it]!! }.average()
                } else {
                    null
                }
            }
            
            val originalIndices = levels[l].withIndex().associate { it.value to it.index }
            levels[l] = levels[l].sortedWith(Comparator { a, b ->
                val bA = barycenters[a]
                val bB = barycenters[b]
                val valA = bA ?: originalIndices[a]!!.toDouble()
                val valB = bB ?: originalIndices[b]!!.toDouble()
                val cmp = valA.compareTo(valB)
                if (cmp != 0) cmp else originalIndices[a]!!.compareTo(originalIndices[b]!!)
            }).toMutableList()
        }

        val maxLevelHeight = levels.maxOfOrNull { it.size } ?: 0
        val totalComponentHeight = maxLevelHeight * (nodeHeightPx + verticalGapPx)

        levels.forEachIndexed { levelIndex, nodesInLevel ->
            val colX = horizontalGapPx * 0.48f + levelIndex * (nodeWidthPx + horizontalGapPx)
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

    val col0X = horizontalGapPx * 0.48f
    val startNodeOffset = rootId?.let { positions[it] }

    val containerY = if (startNodeOffset != null) {
        startNodeOffset.y + nodeHeightPx + 100f
    } else {
        currentYOffset + 100f
    }

    if (orphansSet.isNotEmpty()) {
        positions["__orphans_container__"] = Offset(col0X, containerY)
        
        var nextOrphanY = containerY + nodeHeightPx + 40f
        val sortedOrphans = orphansSet.sortedBy { pages.find { p -> p.id == it }?.orderIndex ?: 0 }
        sortedOrphans.forEach { pageId ->
            positions[pageId] = Offset(col0X, nextOrphanY)
            nextOrphanY += nodeHeightPx + verticalGapPx
        }
    }

    return positions
}

suspend fun PointerInputScope.detectTransformGesturesWithFling(
    panZoomLock: Boolean = false,
    onGestureStart: () -> Unit,
    onGestureEnd: (velocity: Offset) -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false
        
        val velocityTracker = VelocityTracker()

        awaitFirstDown(requireUnconsumed = false)
        onGestureStart()
        
        var lastActivePointerCount = 1
        var hasMultiplePointers = false
        var gestureLocked = false
        
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                // Add pointer input change to tracker for all movements, so velocity is accurate
                event.changes.forEach { change ->
                    if (change.positionChanged()) {
                        velocityTracker.addPointerInputChange(change)
                    }
                }

                val activePointerCount = event.changes.count { it.pressed }
                if (activePointerCount > 1) {
                    hasMultiplePointers = true
                }
                if (hasMultiplePointers && activePointerCount < 2) {
                    gestureLocked = true
                }

                var zoomChange = event.calculateZoom()
                var rotationChange = event.calculateRotation()
                var panChange = event.calculatePan()

                if (gestureLocked || activePointerCount != lastActivePointerCount) {
                    lastActivePointerCount = activePointerCount
                    zoomChange = 1f
                    rotationChange = 0f
                    panChange = Offset.Zero
                }

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                        lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
                    if (effectiveRotation != 0f ||
                        zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        onGesture(centroid, panChange, zoomChange, effectiveRotation)
                    }
                    
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
        
        if (pastTouchSlop && !hasMultiplePointers) {
            val velocity = velocityTracker.calculateVelocity()
            onGestureEnd(Offset(velocity.x, velocity.y))
        }
    }
}


