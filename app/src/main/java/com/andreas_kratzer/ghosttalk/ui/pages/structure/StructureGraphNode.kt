package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.ui.components.ChipDragDropState
import com.andreas_kratzer.ghosttalk.ui.components.DraggableChip
import com.andreas_kratzer.ghosttalk.ui.components.chipDropTarget

@Composable
fun StructureGraphNode(
    pageId: String,
    pageName: String,
    isCenter: Boolean,
    isExpanded: Boolean,
    pages: List<Page>,
    graph: BookNavigationGraph,
    dragDropState: ChipDragDropState,
    onToggleExpand: () -> Unit,
    onFocus: () -> Unit,
    onMoveButton: (String, Int, String) -> Unit,
    onMoveClick: (String, Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onFocus,
        shape = MaterialTheme.shapes.medium,
        color = if (isCenter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isCenter) 2.dp else 1.dp,
            color = if (isCenter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = if (isCenter) 4.dp else 2.dp,
        modifier = modifier.chipDropTarget(dragDropState, pageId)
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
                    Text(
                        text = pageName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Einklappen",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                val page = pages.find { it.id == pageId }
                val pageOutgoingIndices = graph.outgoing[pageId]?.map { it.sourceButtonIndex }?.toSet() ?: emptySet()
                val effectiveStartPageId = graph.startPageId ?: pages.minByOrNull { it.orderIndex }?.id
                val activeButtons = if (page != null) {
                    navigableButtons(page, pageOutgoingIndices, effectiveStartPageId)
                } else emptyList()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    activeButtons.forEach { (btnIdx, btn) ->
                        val dragKey = "${pageId}_${btnIdx}"
                        DraggableChip(
                            label = btn.label,
                            action = btn.buttonAction,
                            isDragged = dragDropState.draggedKey == dragKey,
                            onDragStart = { initialCenter, size ->
                                dragDropState.onDragStart(dragKey, btn.label, pageId, initialCenter, size)
                            },
                            onDrag = { amount -> dragDropState.onDrag(amount) },
                            onDragEnd = {
                                if (dragDropState.draggedKey == dragKey) {
                                    val targetPageId = dragDropState.targetBounds.entries.find { entry ->
                                        entry.value.contains(dragDropState.dragGlobalPos)
                                    }?.key
                                    if (targetPageId != null && targetPageId != pageId) {
                                        onMoveButton(pageId, btnIdx, targetPageId)
                                    }
                                    dragDropState.clear()
                                }
                            },
                            onDragCancel = {
                                if (dragDropState.draggedKey == dragKey) {
                                    dragDropState.clear()
                                }
                            },
                            onClick = {
                                onMoveClick(pageId, btnIdx, btn.label)
                            }
                        )
                    }
                    if (dragDropState.draggedKey != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    MaterialTheme.shapes.small
                                )
                                .chipDropTarget(dragDropState, pageId),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                Text(
                    text = pageName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Ausklappen",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
