package com.andreas_kratzer.ghosttalk.ui.pages.pagesplit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase.CategoryProposal
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase.PageSplitProposal
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.ui.components.DraggableChip
import kotlin.math.roundToInt

/**
 * Dialog zur Vorschau des Seiten-Splits mit Drag & Drop Support.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageSplitWizardDialog(
    proposal: PageSplitProposal?,
    allAvailableButtons: List<ButtonConfig>,
    isLoading: Boolean,
    onConfirm: (PageSplitProposal) -> Unit,
    onDismiss: () -> Unit
) {
    // Unique data structures for safe Drag & Drop without label conflicts
    data class WizardButtonItem(val buttonId: String, val label: String)
    data class WizardCategory(val name: String, val items: List<WizardButtonItem>)

    // Map all source buttons to unique items with locally generated unique IDs!
    val allItems = remember(allAvailableButtons) {
        allAvailableButtons.mapIndexed { index, button ->
            WizardButtonItem(
                buttonId = "btn_${index}_${java.util.UUID.randomUUID()}",
                label = button.label
            )
        }
    }

    // Distribute source buttons into categories based on proposal labels, keeping duplicates intact
    val initialData = remember(proposal, allItems) {
        val remainingItems = allItems.toMutableList()
        val cats = proposal?.categories?.map { catProposal ->
            val catItems = mutableListOf<WizardButtonItem>()
            catProposal.buttonLabels.forEach { label ->
                val matchIndex = remainingItems.indexOfFirst { it.label == label }
                if (matchIndex != -1) {
                    catItems.add(remainingItems.removeAt(matchIndex))
                }
            }
            WizardCategory(catProposal.name, catItems)
        } ?: emptyList()
        
        Pair(cats, remainingItems.toList())
    }

    // Editable category proposals state
    var categoryProposals by remember(initialData) {
        mutableStateOf(initialData.first)
    }

    // Editable unassigned buttons state (Ursprungsseite / Hauptseite)
    var unassignedList by remember(initialData) {
        mutableStateOf(initialData.second)
    }

    // Drag-Drop Koordinaten-Tracking (holds category name to Rect mapping)
    val categoryBounds = remember { mutableMapOf<String, Rect>() }
    var draggedItemId by remember { mutableStateOf<String?>(null) } // unique item buttonId
    var dragSourceCategory by remember { mutableStateOf<String?>(null) } // null = Unassigned
    val dragStartCenter = remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedSize by remember { mutableStateOf(Offset.Zero) }
    var rootBoxBounds by remember { mutableStateOf<Rect?>(null) }
    val dragGlobalPos = dragStartCenter.value + dragOffset

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seitenaufteilung (Vorschau & Anpassen)", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 500.dp)
                    .onGloballyPositioned { layoutCoordinates ->
                        rootBoxBounds = layoutCoordinates.boundsInRoot()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Klassifiziere Buttons...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Column {
                        Text(
                            "Ziehe die Tasten (Gedrückthalten zum Ziehen) in die gewünschten Kategorien, um den Vorschlag anzupassen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Ursprungsseite (Hauptseite) - verbleibende Tasten (immer anzeigen, damit Zurückschieben möglich ist)
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (draggedItemId != null && dragSourceCategory == null) 10f else 1f)
                                        .onGloballyPositioned { layoutCoordinates ->
                                            categoryBounds["_unassigned"] = layoutCoordinates.boundsInRoot()
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "Ursprungsseite (Hauptseite) - Verbleibende Tasten",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (unassignedList.isEmpty()) {
                                                Text(
                                                    "Alle Tasten werden verschoben. Ziehe Tasten hierher, um sie auf der Hauptseite zu belassen.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            } else {
                                                unassignedList.forEach { item ->
                                                    key(item.buttonId) {
                                                        DraggableChip(
                                                            label = item.label,
                                                            isDragged = draggedItemId == item.buttonId,
                                                            onDragStart = { initialCenter, size ->
                                                                draggedItemId = item.buttonId
                                                                dragSourceCategory = null
                                                                dragStartCenter.value = initialCenter
                                                                draggedSize = size
                                                                dragOffset = Offset.Zero
                                                            },
                                                            onDrag = { amount ->
                                                                dragOffset += amount
                                                            },
                                                            onDragEnd = {
                                                                if (draggedItemId == item.buttonId) {
                                                                    val targetCategory = categoryBounds.entries.find { entry ->
                                                                        val rect = entry.value
                                                                        dragGlobalPos.y >= rect.top && dragGlobalPos.y <= rect.bottom
                                                                    }?.key
                                                                    
                                                                    android.util.Log.d("DragDrop", "Drop unassigned: item=${item.label}, dragGlobalPos=$dragGlobalPos, targetCategory=$targetCategory")
                                                                    categoryBounds.forEach { (name, rect) ->
                                                                        android.util.Log.d("DragDrop", "  Bound: name=$name, rect=$rect, containsY=${dragGlobalPos.y >= rect.top && dragGlobalPos.y <= rect.bottom}")
                                                                    }
                                                                    
                                                                    if (targetCategory != null && targetCategory != "_unassigned") {
                                                                        // Verschiebe von Unassigned in die Zielkategorie
                                                                        unassignedList = unassignedList.filter { it.buttonId != item.buttonId }
                                                                        categoryProposals = categoryProposals.map { cat ->
                                                                            if (cat.name == targetCategory) {
                                                                                cat.copy(items = cat.items + item)
                                                                            } else cat
                                                                        }
                                                                    }
                                                                    draggedItemId = null
                                                                }
                                                            },
                                                            onDragCancel = {
                                                                if (draggedItemId == item.buttonId) {
                                                                    draggedItemId = null
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

                            // 2. Reguläre vorgeschlagene Kategorien
                            items(categoryProposals) { category ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (draggedItemId != null && dragSourceCategory == category.name) 10f else 1f)
                                        .onGloballyPositioned { layoutCoordinates ->
                                            categoryBounds[category.name] = layoutCoordinates.boundsInRoot()
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (category.items.isEmpty()) {
                                                Text(
                                                    "Zieh Tasten hierher",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            } else {
                                                category.items.forEach { item ->
                                                    key(item.buttonId) {
                                                        DraggableChip(
                                                            label = item.label,
                                                            isDragged = draggedItemId == item.buttonId,
                                                            onDragStart = { initialCenter, size ->
                                                                draggedItemId = item.buttonId
                                                                dragSourceCategory = category.name
                                                                dragStartCenter.value = initialCenter
                                                                draggedSize = size
                                                                dragOffset = Offset.Zero
                                                            },
                                                            onDrag = { amount ->
                                                                dragOffset += amount
                                                            },
                                                            onDragEnd = {
                                                                if (draggedItemId == item.buttonId) {
                                                                    val targetCategory = categoryBounds.entries.find { entry ->
                                                                        val rect = entry.value
                                                                        dragGlobalPos.y >= rect.top && dragGlobalPos.y <= rect.bottom
                                                                    }?.key
                                                                    
                                                                    android.util.Log.d("DragDrop", "Drop category item: item=${item.label}, from=${category.name}, dragGlobalPos=$dragGlobalPos, targetCategory=$targetCategory")
                                                                    categoryBounds.forEach { (name, rect) ->
                                                                        android.util.Log.d("DragDrop", "  Bound: name=$name, rect=$rect, containsY=${dragGlobalPos.y >= rect.top && dragGlobalPos.y <= rect.bottom}")
                                                                    }
                                                                    
                                                                    if (targetCategory != null && targetCategory != category.name) {
                                                                        if (targetCategory == "_unassigned") {
                                                                            // Verschiebe von Kategorie zurück in Unassigned (sicher, da unterschiedliche States)
                                                                            categoryProposals = categoryProposals.map { cat ->
                                                                                if (cat.name == category.name) {
                                                                                    cat.copy(items = cat.items.filter { it.buttonId != item.buttonId })
                                                                                } else cat
                                                                            }
                                                                            unassignedList = unassignedList + item
                                                                        } else {
                                                                            // Verschiebe zwischen zwei Kategorien in einem einzigen, transaktionalen State-Update!
                                                                            categoryProposals = categoryProposals.map { cat ->
                                                                                when (cat.name) {
                                                                                    category.name -> cat.copy(items = cat.items.filter { it.buttonId != item.buttonId })
                                                                                    targetCategory -> cat.copy(items = cat.items + item)
                                                                                    else -> cat
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                    draggedItemId = null
                                                                }
                                                            },
                                                            onDragCancel = {
                                                                if (draggedItemId == item.buttonId) {
                                                                    draggedItemId = null
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
                    }

                    if (draggedItemId != null && rootBoxBounds != null) {
                        val draggedLabel = allItems.find { it.buttonId == draggedItemId }?.label ?: ""
                        val relativeX = dragGlobalPos.x - rootBoxBounds!!.left - draggedSize.x / 2
                        val relativeY = dragGlobalPos.y - rootBoxBounds!!.top - draggedSize.y / 2
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset {
                                    IntOffset(
                                        relativeX.roundToInt(),
                                        relativeY.roundToInt()
                                    )
                                }
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 8.dp
                            ) {
                                Text(
                                    text = draggedLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val finalProposal = PageSplitProposal(
                        categories = categoryProposals.map { cat ->
                            CategoryProposal(cat.name, cat.items.map { it.label })
                        }
                    )
                    onConfirm(finalProposal) 
                },
                enabled = !isLoading && proposal != null
            ) {
                Text("Anwenden")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
