package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.domain.pages.NavEdge
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.ui.components.ChipDragDropState

private val expandedPageIdsSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() }
)

@Composable
fun StructureGraphView(
    focusedPageId: String,
    focusedPageName: String,
    incomingSources: List<String>,
    outgoingEdges: List<NavEdge>,
    graph: BookNavigationGraph,
    pageNames: Map<String, String>,
    onFocus: (String) -> Unit,
    onRemoveConnection: ((pageId: String, buttonIndex: Int, targetPageName: String) -> Unit)? = null,
    isFullView: Boolean = false,
    modifier: Modifier = Modifier,
    pages: List<Page>,
    dragDropState: ChipDragDropState,
    onMoveButton: (String, Int, String) -> Unit
) {
    var expandedPageIds: Set<String> by rememberSaveable(stateSaver = expandedPageIdsSaver) {
        mutableStateOf(emptySet<String>())
    }
    var activeMoveButtonInfo by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    var selectedEdgeForDeletion by remember { mutableStateOf<NavEdge?>(null) }

    // Reset armed connection delete overlay when focused page or expanded pages change (G2)
    LaunchedEffect(expandedPageIds, focusedPageId) {
        selectedEdgeForDeletion = null
    }

    val distinctIncoming = incomingSources.distinct()
    val distinctOutgoing = outgoingEdges.map { it.targetPageId }.distinct()

    val maxIncomingRows = if (isFullView) 8 else 4
    var incomingLimit by remember(focusedPageId) { mutableStateOf(maxIncomingRows) }
    val showIncomingMore = distinctIncoming.size > incomingLimit
    val visibleIncoming = if (showIncomingMore) distinctIncoming.take(incomingLimit - 1) else distinctIncoming
    val incomingNodeIds = if (showIncomingMore) visibleIncoming + "more_incoming" else visibleIncoming

    val maxOutgoingRows = if (isFullView) 8 else 4
    val maxOutgoingColumns = if (isFullView) 2 else 1
    val maxOutgoingTotal = maxOutgoingRows * maxOutgoingColumns
    var outgoingTotalLimit by remember(focusedPageId) { mutableStateOf(maxOutgoingTotal) }
    val showOutgoingMore = distinctOutgoing.size > outgoingTotalLimit
    val visibleOutgoing = if (showOutgoingMore) distinctOutgoing.take(outgoingTotalLimit - 1) else distinctOutgoing
    val outgoingNodeIds = if (showOutgoingMore) visibleOutgoing + "more_outgoing" else visibleOutgoing
    // Cap the number of columns at maxOutgoingColumns by growing the rows per column
    // instead of opening another column — the threaded-arrow routing only works for ≤2 columns.
    val outgoingRowsPerColumn = maxOf(
        maxOutgoingRows,
        (outgoingNodeIds.size + maxOutgoingColumns - 1) / maxOutgoingColumns
    )
    val outgoingColumns = outgoingNodeIds.chunked(outgoingRowsPerColumn)

    val scrollStateX = rememberScrollState()
    val scrollStateY = rememberScrollState()

    // Center the focused node once, after layout settles. Keyed on focusedPageId only,
    // so expanding/collapsing a card (which changes maxValue) does NOT re-scroll the view.
    LaunchedEffect(focusedPageId) {
        // Wait until the layout has been measured (at least one axis has scroll range).
        snapshotFlow { scrollStateX.maxValue to scrollStateY.maxValue }
            .first { (x, y) -> x > 0 || y > 0 }
        if (scrollStateX.maxValue > 0) {
            scrollStateX.animateScrollTo(scrollStateX.maxValue / 2)
        }
        if (scrollStateY.maxValue > 0) {
            scrollStateY.animateScrollTo(scrollStateY.maxValue / 2)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        modifier = if (isFullView) modifier.fillMaxSize() else modifier.fillMaxWidth()
    ) {
        Column(
            modifier = if (isFullView) Modifier.fillMaxSize().padding(12.dp) else Modifier.padding(12.dp)
        ) {
            Text(
                text = "Visueller Navigations-Graph",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullView) Modifier.weight(1f) else Modifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        selectedEdgeForDeletion = null
                    }
                    .horizontalScroll(scrollStateX)
                    .verticalScroll(scrollStateY)
                    // Bottom scroll buffer so the last node can be scrolled clear of the
                    // floating "Zielseite verbinden" button (only present in the full editor).
                    .then(if (isFullView) Modifier.padding(bottom = 88.dp) else Modifier)
            ) {
                val density = LocalDensity.current

                val incomingWidthDp = if (incomingNodeIds.isEmpty()) 0.dp else 200.dp
                val centerWidthDp = 240.dp
                val outgoingColWidthDp = if (outgoingNodeIds.isEmpty()) 0.dp else 210.dp
                val horizontalSpacing = 40.dp
                val verticalSpacing = 16.dp

                val targetColumnsCount = outgoingColumns.size.coerceAtLeast(1)

                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val errorColor = MaterialTheme.colorScheme.error

                SubcomposeLayout { constraints ->
                    // 1. Compose & Measure Center Node
                    val centerPlaceables = subcompose("center") {
                        StructureGraphNode(
                            pageId = focusedPageId,
                            pageName = focusedPageName,
                            isCenter = true,
                            isExpanded = expandedPageIds.contains(focusedPageId),
                            pages = pages,
                            graph = graph,
                            dragDropState = dragDropState,
                            onToggleExpand = {
                                expandedPageIds = if (expandedPageIds.contains(focusedPageId)) {
                                    expandedPageIds - focusedPageId
                                } else {
                                    expandedPageIds + focusedPageId
                                }
                            },
                            onFocus = {},
                            onMoveButton = onMoveButton,
                            onMoveClick = { src, idx, lbl -> activeMoveButtonInfo = Triple(src, idx, lbl) }
                        )
                    }.map { it.measure(Constraints.fixedWidth(with(density) { centerWidthDp.roundToPx() })) }
                    val centerPlaceable = centerPlaceables.first()

                    // 2. Compose & Measure Incoming Nodes
                    val incomingPlaceables = subcompose("incoming") {
                        incomingNodeIds.forEach { sourceId ->
                            if (sourceId.startsWith("more_")) {
                                val moreCount = distinctIncoming.size - (incomingLimit - 1)
                                Surface(
                                    onClick = { incomingLimit += 8 },
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "+ $moreCount weitere",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                StructureGraphNode(
                                    pageId = sourceId,
                                    pageName = pageNames[sourceId] ?: sourceId,
                                    isCenter = false,
                                    isExpanded = expandedPageIds.contains(sourceId),
                                    pages = pages,
                                    graph = graph,
                                    dragDropState = dragDropState,
                                    onToggleExpand = {
                                        expandedPageIds = if (expandedPageIds.contains(sourceId)) {
                                            expandedPageIds - sourceId
                                        } else {
                                            expandedPageIds + sourceId
                                        }
                                    },
                                    onFocus = { onFocus(sourceId) },
                                    onMoveButton = onMoveButton,
                                    onMoveClick = { src, idx, lbl -> activeMoveButtonInfo = Triple(src, idx, lbl) }
                                )
                            }
                        }
                    }.map { it.measure(Constraints.fixedWidth(with(density) { incomingWidthDp.roundToPx() })) }

                    // 3. Compose & Measure Outgoing Nodes
                    val outgoingPlaceables = subcompose("outgoing") {
                        outgoingNodeIds.forEach { targetId ->
                            if (targetId.startsWith("more_")) {
                                val moreCount = distinctOutgoing.size - (outgoingTotalLimit - 1)
                                Surface(
                                    onClick = { outgoingTotalLimit += 8 },
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "+ $moreCount weitere",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                StructureGraphNode(
                                    pageId = targetId,
                                    pageName = pageNames[targetId] ?: targetId,
                                    isCenter = false,
                                    isExpanded = expandedPageIds.contains(targetId),
                                    pages = pages,
                                    graph = graph,
                                    dragDropState = dragDropState,
                                    onToggleExpand = {
                                        expandedPageIds = if (expandedPageIds.contains(targetId)) {
                                            expandedPageIds - targetId
                                        } else {
                                            expandedPageIds + targetId
                                        }
                                    },
                                    onFocus = { onFocus(targetId) },
                                    onMoveButton = onMoveButton,
                                    onMoveClick = { src, idx, lbl -> activeMoveButtonInfo = Triple(src, idx, lbl) }
                                )
                            }
                        }
                    }.map { it.measure(Constraints.fixedWidth(with(density) { outgoingColWidthDp.roundToPx() })) }

                    // Layout size and coordinates calculation
                    val spacingPx = with(density) { verticalSpacing.roundToPx() }
                    val hSpacingPx = with(density) { horizontalSpacing.roundToPx() }

                    val incomingMaxHeight = incomingPlaceables.maxOfOrNull { it.height } ?: 0
                    val centerMaxHeight = centerPlaceable.height
                    val outgoingMaxHeight = outgoingPlaceables.maxOfOrNull { it.height } ?: 0

                    val layoutWidth: Int
                    val layoutHeight: Int

                    val incomingPoints = mutableListOf<Pair<Float, Float>>()
                    val outgoingPoints = mutableListOf<Pair<Float, Float>>()
                    var centerPoint = Pair(0f, 0f)

                    if (isLandscape) {
                        // Landscape arrangement: Left-to-Right
                        val incomingTotalHeight = if (incomingPlaceables.isEmpty()) 0 else {
                            incomingPlaceables.sumOf { it.height } + spacingPx * (incomingPlaceables.size - 1)
                        }
                        val centerTotalHeight = centerPlaceable.height

                        val outgoingColumnsPlaceables = outgoingPlaceables.chunked(outgoingRowsPerColumn)
                        val outgoingColumnHeights = outgoingColumnsPlaceables.map { col ->
                            col.sumOf { it.height } + spacingPx * (col.size - 1)
                        }
                        val maxOutgoingHeight = outgoingColumnHeights.maxOrNull() ?: 0

                        // Offset every other target column by half a row so the outer column's
                        // chips sit in the gaps of the inner column — the long connector lines to
                        // the outer chips then thread between the inner chips instead of over them.
                        val rowOffsetPx = if (targetColumnsCount > 1) ((outgoingPlaceables.firstOrNull()?.height ?: 0) + spacingPx) / 2 else 0

                        val maxColumnHeight = maxOf(incomingTotalHeight, centerTotalHeight, maxOutgoingHeight)
                        layoutHeight = if (isFullView) maxOf(maxColumnHeight + rowOffsetPx, with(density) { 450.dp.roundToPx() })
                                       else maxOf(maxColumnHeight + rowOffsetPx, with(density) { 240.dp.roundToPx() })

                        val incomingColWidth = with(density) { incomingWidthDp.roundToPx() }
                        val centerColWidth = with(density) { centerWidthDp.roundToPx() }
                        val outgoingColWidth = with(density) { outgoingColWidthDp.roundToPx() }

                        val col1Width = if (incomingPlaceables.isNotEmpty()) incomingColWidth else 0
                        val col2Width = centerColWidth
                        val col3Width = if (outgoingPlaceables.isNotEmpty()) outgoingColWidth * targetColumnsCount + hSpacingPx * (targetColumnsCount - 1) else 0

                        val spacingCount = (if (col1Width > 0) 1 else 0) + (if (col3Width > 0) 1 else 0)
                        layoutWidth = col1Width + col2Width + col3Width + spacingCount * hSpacingPx

                        val centerY = layoutHeight / 2f

                        // Compute points
                        val col1StartX = 0f
                        if (incomingPlaceables.isNotEmpty()) {
                            val startY = (layoutHeight - incomingTotalHeight) / 2f
                            var currentY = startY
                            incomingPlaceables.forEach { p ->
                                val cy = currentY + p.height / 2f
                                incomingPoints.add(Pair(col1StartX + col1Width / 2f, cy))
                                currentY += p.height + spacingPx
                            }
                        }

                        val col2StartX = if (incomingPlaceables.isNotEmpty()) (col1Width + hSpacingPx).toFloat() else 0f
                        centerPoint = Pair(col2StartX + col2Width / 2f, centerY)

                        val col3StartX = col2StartX + col2Width + hSpacingPx
                        if (outgoingPlaceables.isNotEmpty()) {
                            var flatIndex = 0
                            outgoingColumnsPlaceables.forEachIndexed { colIdx, colPls ->
                                val colStagger = if (colIdx % 2 == 0) -rowOffsetPx / 2f else rowOffsetPx / 2f
                                // Center every column on the SAME (tallest) baseline so rows align to
                                // a common grid — otherwise columns with different row counts (after
                                // "+ weitere") drift and the half-row offset no longer lands in the gaps.
                                val startY = (layoutHeight - maxOutgoingHeight) / 2f + colStagger
                                var currentY = startY
                                colPls.forEach { p ->
                                    val cx = col3StartX + colIdx * (outgoingColWidth + hSpacingPx) + outgoingColWidth / 2f
                                    val cy = currentY + p.height / 2f
                                    outgoingPoints.add(Pair(cx, cy))
                                    currentY += p.height + spacingPx
                                    flatIndex++
                                }
                            }
                        }
                    } else {
                        // Portrait arrangement: Top-to-Bottom, strictly vertical stacking (G1)
                        val incomingColWidth = with(density) { incomingWidthDp.roundToPx() }
                        val centerColWidth = with(density) { centerWidthDp.roundToPx() }
                        val outgoingColWidth = with(density) { outgoingColWidthDp.roundToPx() }
                        
                        // Rooted-tree layout: a vertical trunk at the focused node's centre,
                        // child nodes offset to the right with elbow connectors (uses the
                        // horizontal space and avoids a bundle of overlapping vertical curves).
                        val branchGapPx = with(density) { 36.dp.roundToPx() }
                        val trunkX = centerColWidth / 2f
                        val childLeftX = trunkX + branchGapPx
                        layoutWidth = maxOf(
                            centerColWidth,
                            (childLeftX + incomingColWidth).toInt(),
                            (childLeftX + outgoingColWidth).toInt()
                        )

                        val incomingTotalHeight = if (incomingPlaceables.isEmpty()) 0 else {
                            incomingPlaceables.sumOf { it.height } + spacingPx * (incomingPlaceables.size - 1)
                        }
                        val centerTotalHeight = centerPlaceable.height
                        val outgoingTotalHeight = if (outgoingPlaceables.isEmpty()) 0 else {
                            outgoingPlaceables.sumOf { it.height } + spacingPx * (outgoingPlaceables.size - 1)
                        }

                        val rowSpacingCount = (if (incomingPlaceables.isNotEmpty()) 1 else 0) + (if (outgoingPlaceables.isNotEmpty()) 1 else 0)
                        layoutHeight = incomingTotalHeight + centerTotalHeight + outgoingTotalHeight + rowSpacingCount * spacingPx

                        // Compute points
                        var currentY = 0f
                        if (incomingPlaceables.isNotEmpty()) {
                            incomingPlaceables.forEach { p ->
                                val cy = currentY + p.height / 2f
                                incomingPoints.add(Pair(childLeftX + incomingColWidth / 2f, cy))
                                currentY += p.height + spacingPx
                            }
                        }

                        centerPoint = Pair(trunkX, currentY + centerTotalHeight / 2f)
                        currentY += centerTotalHeight + spacingPx

                        if (outgoingPlaceables.isNotEmpty()) {
                            outgoingPlaceables.forEach { p ->
                                val cy = currentY + p.height / 2f
                                outgoingPoints.add(Pair(childLeftX + outgoingColWidth / 2f, cy))
                                currentY += p.height + spacingPx
                            }
                        }
                    }

                    // Compose Canvas Curves & Tap handler directly on line (G3)
                    val canvasPlaceables = subcompose("canvas") {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(focusedPageId, incomingPoints.size, outgoingPoints.size) {
                                    detectTapGestures { tapOffset ->
                                        var closestEdge: NavEdge? = null
                                        var minDistance = Float.MAX_VALUE
                                        val threshold = 24.dp.toPx()

                                        var flatIndex = 0
                                        outgoingColumns.forEach { colNodes ->
                                            colNodes.forEach { targetId ->
                                                if (flatIndex < outgoingPoints.size) {
                                                    val pt = outgoingPoints[flatIndex]
                                                    val startX: Float
                                                    val startY: Float
                                                    val endX: Float
                                                    val endY: Float

                                                    if (isLandscape) {
                                                        // Horizontal stub of the bus connector (unique per edge).
                                                        startX = centerPoint.first + (centerWidthDp / 2).toPx() + 24.dp.toPx()
                                                        startY = pt.second
                                                        endX = pt.first - (outgoingColWidthDp / 2).toPx()
                                                        endY = pt.second
                                                    } else {
                                                        // Horizontal stub of the elbow connector (unique per edge).
                                                        startX = centerPoint.first
                                                        startY = pt.second
                                                        endX = pt.first - (outgoingColWidthDp / 2).toPx()
                                                        endY = pt.second
                                                    }

                                                    val dist = distanceToSegment(
                                                        tapOffset,
                                                        Offset(startX, startY),
                                                        Offset(endX, endY)
                                                    )
                                                    val matchingEdge = outgoingEdges.find { it.targetPageId == targetId }
                                                    if (matchingEdge != null && dist < threshold && dist < minDistance) {
                                                        minDistance = dist
                                                        closestEdge = matchingEdge
                                                    }
                                                }
                                                flatIndex++
                                            }
                                        }

                                        if (closestEdge != null) {
                                            selectedEdgeForDeletion = closestEdge
                                        } else {
                                            selectedEdgeForDeletion = null
                                        }
                                    }
                                }
                        ) {
                            val arrowLength = 8.dp.toPx()
                            val arrowWidth = 5.dp.toPx()

                            // 1. Draw curves from incoming nodes to center
                            incomingNodeIds.forEachIndexed { index, sourceId ->
                                if (index < incomingPoints.size) {
                                    val pt = incomingPoints[index]
                                    val isMoreNode = sourceId.startsWith("more_")

                                    val path = androidx.compose.ui.graphics.Path()
                                    if (isLandscape) {
                                        val startX = pt.first + (incomingWidthDp / 2).toPx()
                                        val startY = pt.second
                                        val endX = centerPoint.first - (centerWidthDp / 2).toPx()
                                        val endY = centerPoint.second

                                        path.moveTo(startX, startY)
                                        path.cubicTo(
                                            startX + (endX - startX) * 0.6f, startY,
                                            endX - (endX - startX) * 0.6f, endY,
                                            endX, endY
                                        )
                                    } else {
                                        // Elbow: from the source's left edge across to the trunk, then down to the centre node.
                                        val startX = pt.first - (incomingWidthDp / 2).toPx()
                                        val startY = pt.second
                                        val trunkX = centerPoint.first
                                        val endY = centerPoint.second - centerPlaceable.height / 2f

                                        path.moveTo(startX, startY)
                                        path.lineTo(trunkX, startY)
                                        path.lineTo(trunkX, endY)
                                    }

                                    val stroke = if (isMoreNode) {
                                        androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                                intervals = floatArrayOf(10f, 10f),
                                                phase = 0f
                                            )
                                        )
                                    } else {
                                        androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                    }

                                    drawPath(
                                        path = path,
                                        color = primaryColor.copy(alpha = if (isMoreNode) 0.4f else 0.6f),
                                        style = stroke
                                    )
                                }
                            }

                            // 2. Draw curves from center to outgoing nodes
                            var flatIndex = 0
                            outgoingColumns.forEach { colNodes ->
                                colNodes.forEach { targetId ->
                                    if (flatIndex < outgoingPoints.size) {
                                        val pt = outgoingPoints[flatIndex]
                                        val isMoreNode = targetId.startsWith("more_")

                                        val matchingEdge = outgoingEdges.find { it.targetPageId == targetId }
                                        val isSelected = selectedEdgeForDeletion == matchingEdge

                                        val path = androidx.compose.ui.graphics.Path()
                                        val startX: Float
                                        val startY: Float
                                        val endX: Float
                                        val endY: Float
                                        val c2x: Float
                                        val c2y: Float

                                        if (isLandscape) {
                                            // Orthogonal bus: exit the centre horizontally to a shared
                                            // vertical bus, run along it to the target's row, then a
                                            // horizontal stub into the chip. The stub for the outer column
                                            // threads through the gaps between the inner column's chips.
                                            startX = centerPoint.first + (centerWidthDp / 2).toPx()
                                            startY = centerPoint.second
                                            val busX = startX + 24.dp.toPx()
                                            endX = pt.first - (outgoingColWidthDp / 2).toPx()
                                            endY = pt.second

                                            // Control point left of the end so the arrowhead points horizontally into the node.
                                            c2x = busX
                                            c2y = endY
                                            path.moveTo(startX, startY)
                                            path.lineTo(busX, startY)
                                            path.lineTo(busX, endY)
                                            path.lineTo(endX, endY)
                                        } else {
                                            // Elbow: trunk down from the centre node, then a short stub into the target's left edge.
                                            startX = centerPoint.first
                                            startY = centerPoint.second + centerPlaceable.height / 2f
                                            endX = pt.first - (outgoingColWidthDp / 2).toPx()
                                            endY = pt.second

                                            // Control point left of the end so the arrowhead points horizontally into the node.
                                            c2x = startX
                                            c2y = endY
                                            path.moveTo(startX, startY)
                                            path.lineTo(startX, endY)
                                            path.lineTo(endX, endY)
                                        }

                                        val stroke = if (isMoreNode) {
                                            androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = 1.5.dp.toPx(),
                                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                                    intervals = floatArrayOf(10f, 10f),
                                                    phase = 0f
                                                )
                                            )
                                        } else {
                                            androidx.compose.ui.graphics.drawscope.Stroke(width = (if (isSelected) 3.5.dp else 2.dp).toPx())
                                        }

                                        val color = if (isSelected) errorColor else primaryColor.copy(alpha = if (isMoreNode) 0.4f else 0.6f)

                                        drawPath(
                                            path = path,
                                            color = color,
                                            style = stroke
                                        )

                                        if (!isMoreNode) {
                                            // arrowhead oriented along the curve's end tangent (end - c2)
                                            var tx = endX - c2x
                                            var ty = endY - c2y
                                            val tl = kotlin.math.sqrt(tx * tx + ty * ty).coerceAtLeast(0.0001f)
                                            tx /= tl; ty /= tl
                                            val baseX = endX - tx * arrowLength
                                            val baseY = endY - ty * arrowLength
                                            val arrowPath = androidx.compose.ui.graphics.Path()
                                            arrowPath.moveTo(endX, endY)
                                            arrowPath.lineTo(baseX - ty * arrowWidth, baseY + tx * arrowWidth)
                                            arrowPath.lineTo(baseX + ty * arrowWidth, baseY - tx * arrowWidth)
                                            arrowPath.close()
                                            drawPath(arrowPath, color = color.copy(alpha = 0.8f))
                                        }
                                    }
                                    flatIndex++
                                }
                            }
                        }
                    }.map { it.measure(Constraints.fixed(layoutWidth, layoutHeight)) }

                    // 5. Compose active deletion tooltip dialog triggers at exact midpoint (0.5f) (G3)
                    val tooltipPlaceables = subcompose("tooltip") {
                        if (onRemoveConnection != null && selectedEdgeForDeletion != null) {
                            val matchingEdge = selectedEdgeForDeletion!!
                            val targetId = matchingEdge.targetPageId
                            val outgoingFlatIndex = outgoingNodeIds.indexOf(targetId)
                            if (outgoingFlatIndex != -1 && outgoingFlatIndex < outgoingPoints.size) {
                                val pt = outgoingPoints[outgoingFlatIndex]
                                val startX: Float
                                val startY: Float
                                val endX: Float
                                val endY: Float

                                if (isLandscape) {
                                    startX = centerPoint.first + with(density) { (centerWidthDp / 2).toPx() + 24.dp.toPx() }
                                    startY = pt.second
                                    endX = pt.first - with(density) { (outgoingColWidthDp / 2).toPx() }
                                    endY = pt.second
                                } else {
                                    startX = centerPoint.first
                                    startY = pt.second
                                    endX = pt.first - with(density) { (outgoingColWidthDp / 2).toPx() }
                                    endY = pt.second
                                }

                                val curveEndX = startX + (endX - startX) * 0.5f
                                val endCurveY = startY + (endY - startY) * 0.5f

                                val tooltipWidth = with(density) { 36.dp.toPx() }
                                val tooltipHeight = with(density) { 24.dp.toPx() }

                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                (curveEndX - tooltipWidth / 2).toInt(),
                                                (endCurveY - tooltipHeight - 16f).toInt()
                                            )
                                        }
                                        .size(36.dp, 24.dp)
                                        .zIndex(10f)
                                ) {
                                    Surface(
                                        onClick = {
                                            onRemoveConnection(focusedPageId, matchingEdge.sourceButtonIndex, pageNames[targetId] ?: targetId)
                                            selectedEdgeForDeletion = null
                                        },
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        tonalElevation = 6.dp
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Verbindung löschen",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }.map { it.measure(constraints) }

                    layout(layoutWidth, layoutHeight) {
                        canvasPlaceables.forEach { it.place(0, 0) }

                        // Place incoming
                        var incPlIdx = 0
                        if (incomingPlaceables.isNotEmpty()) {
                            incomingPlaceables.forEach { p ->
                                val pt = incomingPoints[incPlIdx]
                                p.place(
                                    (pt.first - p.width / 2f).toInt(),
                                    (pt.second - p.height / 2f).toInt()
                                )
                                incPlIdx++
                            }
                        }

                        // Place center
                        centerPlaceable.place(
                            (centerPoint.first - centerPlaceable.width / 2f).toInt(),
                            (centerPoint.second - centerPlaceable.height / 2f).toInt()
                        )

                        // Place outgoing
                        var outPlIdx = 0
                        if (outgoingPlaceables.isNotEmpty()) {
                            outgoingPlaceables.forEach { p ->
                                val pt = outgoingPoints[outPlIdx]
                                p.place(
                                    (pt.first - p.width / 2f).toInt(),
                                    (pt.second - p.height / 2f).toInt()
                                )
                                outPlIdx++
                            }
                        }

                        tooltipPlaceables.forEach { it.place(0, 0) }
                    }
                }
            }
        }
    }

    val info = activeMoveButtonInfo
    if (info != null) {
        SearchablePagePicker(
            title = stringResource(R.string.structure_move_button_dialog_title, info.third),
            subtitle = stringResource(R.string.structure_move_button_dialog_select_target),
            excludePageId = info.first,
            pages = pages,
            onDismissRequest = { activeMoveButtonInfo = null },
            onPageSelected = { targetPageId: String ->
                onMoveButton(info.first, info.second, targetPageId)
                activeMoveButtonInfo = null
            }
        )
    }
}
