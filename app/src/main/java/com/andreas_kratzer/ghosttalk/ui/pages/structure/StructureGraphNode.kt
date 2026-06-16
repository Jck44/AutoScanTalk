package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.sp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.ui.components.DraggableChip
import com.andreas_kratzer.ghosttalk.ui.components.LocalDragDropState
import com.andreas_kratzer.ghosttalk.ui.components.StructureButtonDrag
import com.andreas_kratzer.ghosttalk.ui.components.StructureNodeTarget
import com.andreas_kratzer.ghosttalk.ui.components.StructureSlotTarget
import com.andreas_kratzer.ghosttalk.ui.components.dragSource
import com.andreas_kratzer.ghosttalk.ui.components.dropTarget

@Composable
private fun WarningBadges(
    isOrphan: Boolean,
    isDeadEnd: Boolean,
    modifier: Modifier = Modifier
) {
    if (isOrphan || isDeadEnd) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOrphan) {
                val orphanDesc = stringResource(R.string.structure_warning_orphan)
                Text(
                    text = "⚠",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics {
                        contentDescription = orphanDesc
                    }
                )
            }
            if (isDeadEnd) {
                val deadEndDesc = stringResource(R.string.structure_warning_dead_end)
                Text(
                    text = "⛔",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics {
                        contentDescription = deadEndDesc
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StructureGraphNode(
    pageId: String,
    pageName: String,
    isCenter: Boolean,
    isExpanded: Boolean,
    pages: List<Page>,
    graph: BookNavigationGraph,
    onToggleExpand: () -> Unit,
    onFocus: () -> Unit,
    onEditButton: (String, Int) -> Unit,
    onAddButton: (String) -> Unit,
    modifier: Modifier = Modifier,
    isMultiSelectMode: Boolean = false,
    selectedIndices: Set<Int> = emptySet(),
    isOrphan: Boolean = false,
    isDeadEnd: Boolean = false
) {
    val dragDropState = LocalDragDropState.current
    val isNodeHovered = dragDropState.currentHoveredTarget == StructureNodeTarget(pageId)

    Surface(
        onClick = { if (!isMultiSelectMode) onFocus() },
        shape = MaterialTheme.shapes.medium,
        color = if (isCenter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isCenter || isNodeHovered) 2.dp else 1.dp,
            color = if (isCenter || isNodeHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = if (isCenter) 4.dp else 2.dp,
        modifier = modifier.dropTarget(key = StructureNodeTarget(pageId))
    ) {
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .padding(6.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pageName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        WarningBadges(isOrphan, isDeadEnd)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isMultiSelectMode) {
                            IconButton(
                                onClick = { onAddButton(pageId) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.button_add),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.content_desc_collapse),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                val page = pages.find { it.id == pageId }
                val pageOutgoingIndices = graph.outgoing[pageId]?.map { it.sourceButtonIndex }?.toSet() ?: emptySet()
                val effectiveStartPageId = graph.startPageId ?: pages.minByOrNull { it.orderIndex }?.id
                val activeButtons = if (page != null) {
                    navigableButtons(page, pageOutgoingIndices, effectiveStartPageId)
                } else emptyList()

                // Chips flow left-to-right and wrap within the node's fixed width — same
                // arrangement as the card editor, instead of one chip per row.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeButtons.forEach { (btnIdx, btn) ->
                        val dragItem = StructureButtonDrag(pageId, btnIdx, btn.label, btn.buttonAction)
                        DraggableChip(
                            label = btn.label,
                            action = btn.buttonAction,
                            selected = isMultiSelectMode && selectedIndices.contains(btnIdx),
                            isDragged = dragDropState.isDragging && dragDropState.dragItem == dragItem,
                            modifier = if (isMultiSelectMode) Modifier else Modifier.dragSource(item = dragItem),
                            onClick = {
                                onEditButton(pageId, btnIdx)
                            }
                        )
                    }

                    // Ghost slot placeholder when node is hovered during drag.
                    // Use the first free/inactive slot (else append) so dropping on the
                    // placeholder matches the node/template/'+' paths, which all use this logic.
                    val targetSlotIndex = page?.buttonConfigs?.indexOfFirst { it == null || !it.isActive }
                        ?.takeIf { it >= 0 } ?: (page?.buttonConfigs?.size ?: activeButtons.size)
                    val isSlotHovered = dragDropState.currentHoveredTarget == StructureSlotTarget(pageId, targetSlotIndex)
                    val showPlaceholder = dragDropState.isDragging && (dragDropState.dragItem is StructureButtonDrag || dragDropState.dragItem is com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate) && (isNodeHovered || isSlotHovered)
                    if (showPlaceholder) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 48.dp)
                                .height(32.dp)
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                    MaterialTheme.shapes.small
                                )
                                .dropTarget(key = StructureSlotTarget(pageId, targetSlotIndex))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pageName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    WarningBadges(isOrphan, isDeadEnd)
                }
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.content_desc_expand),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
