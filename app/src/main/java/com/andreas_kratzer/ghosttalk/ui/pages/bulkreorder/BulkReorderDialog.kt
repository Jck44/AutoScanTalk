package com.andreas_kratzer.ghosttalk.ui.pages.bulkreorder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.DialogProperties
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.components.DraggableChip
import com.andreas_kratzer.ghosttalk.ui.pages.actions.NavigationActionFields
import kotlin.math.roundToInt

data class ReorderButtonItem(val buttonId: String, val config: ButtonConfig, val originalIndex: Int)
data class ReorderCategory(val pageId: String, val pageName: String, val items: List<ReorderButtonItem>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkReorderDialog(
    currentPage: Page,
    allAvailablePages: List<Page>,
    templates: List<PageTemplate>,
    onConfirm: (List<ReorderCategory>) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToPage: (String) -> Unit,
    onCreateNavigationButton: (Int, ButtonConfig) -> Unit,
    onCreatePage: ((String, Int, Int, String?, (String) -> Unit) -> Unit)? = null
) {
    // Detect responsive layout context
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Unique data structures for Drag & Drop. We remember without key to keep assignments stable.
    val allItems = remember {
        currentPage.buttonConfigs.mapIndexedNotNull { index, button ->
            if (button != null && button.isActive && button.label.isNotBlank()) {
                ReorderButtonItem(
                    buttonId = "btn_${index}_${java.util.UUID.randomUUID()}",
                    config = button,
                    originalIndex = index
                )
            } else null
        }
    }

    val initialCategories = remember {
        val targetPageIds = currentPage.buttonConfigs
            .filterNotNull()
            .mapNotNull { (it.buttonAction as? NavigateToPageButtonAction)?.pageId }
            .filter { it.isNotEmpty() }
            .distinct()
        
        targetPageIds.map { pageId ->
            val pageName = allAvailablePages.find { it.id == pageId }?.name ?: "Seite $pageId"
            ReorderCategory(
                pageId = pageId,
                pageName = pageName,
                items = emptyList()
            )
        }
    }

    var categoryProposals by remember { mutableStateOf(initialCategories) }
    var unassignedList by remember { mutableStateOf(allItems) }

    // Toggle for adding new navigation buttons
    var showAddNavigationSection by remember { mutableStateOf(false) }
    var newNavigationPageId by remember { mutableStateOf("") }

    // Drag-Drop Koordinaten-Tracking (holds category pageId to Rect mapping)
    val categoryBounds = remember { mutableMapOf<String, Rect>() }
    var draggedItemId by remember { mutableStateOf<String?>(null) } // unique item buttonId
    var dragSourceCategory by remember { mutableStateOf<String?>(null) } // null = Unassigned
    val dragStartCenter = remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedSize by remember { mutableStateOf(Offset.Zero) }
    var rootBoxBounds by remember { mutableStateOf<Rect?>(null) }
    val dragGlobalPos = dragStartCenter.value + dragOffset

    @Composable
    fun CurrentPageCard() {
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
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Aktuelle Seite (${currentPage.name})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (unassignedList.isEmpty()) {
                        Text(
                            "Alle Tasten werden verschoben.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        unassignedList.forEach { item ->
                            key(item.buttonId) {
                                DraggableChip(
                                    label = item.config.label,
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
                                                rect.contains(dragGlobalPos)
                                            }?.key
                                            
                                            if (targetCategory != null && targetCategory != "_unassigned") {
                                                unassignedList = unassignedList.filter { it.buttonId != item.buttonId }
                                                categoryProposals = categoryProposals.map { cat ->
                                                    if (cat.pageId == targetCategory) {
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

    @Composable
    fun TargetCategoryItem(category: ReorderCategory) {
        val existingButtons = remember(allAvailablePages, category.pageId) {
            allAvailablePages.find { it.id == category.pageId }?.buttonConfigs
                ?.filterNotNull()
                ?.filter { it.isActive && it.label.isNotBlank() }
                ?: emptyList()
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (draggedItemId != null && dragSourceCategory == category.pageId) 10f else 1f)
                .onGloballyPositioned { layoutCoordinates ->
                    categoryBounds[category.pageId] = layoutCoordinates.boundsInRoot()
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = category.pageName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Render existing buttons first as non-draggable indicator chips
                    existingButtons.forEach { existing ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(existing.label, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.alpha(0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        )
                    }

                    // Render dragged buttons
                    category.items.forEach { item ->
                        key(item.buttonId) {
                            DraggableChip(
                                label = item.config.label,
                                isDragged = draggedItemId == item.buttonId,
                                onDragStart = { initialCenter, size ->
                                    draggedItemId = item.buttonId
                                    dragSourceCategory = category.pageId
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
                                            rect.contains(dragGlobalPos)
                                        }?.key
                                        
                                        if (targetCategory != null && targetCategory != category.pageId) {
                                            if (targetCategory == "_unassigned") {
                                                categoryProposals = categoryProposals.map { cat ->
                                                    if (cat.pageId == category.pageId) {
                                                        cat.copy(items = cat.items.filter { it.buttonId != item.buttonId })
                                                    } else cat
                                                }
                                                unassignedList = unassignedList + item
                                            } else {
                                                categoryProposals = categoryProposals.map { cat ->
                                                    when (cat.pageId) {
                                                        category.pageId -> cat.copy(items = cat.items.filter { it.buttonId != item.buttonId })
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

                    if (category.items.isEmpty() && existingButtons.isEmpty()) {
                        Text(
                            "Zieh Tasten hierher",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .heightIn(min = 200.dp, max = 650.dp),
        title = { Text("Organisieren (Bulk Reorder)", fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 500.dp)
                    .onGloballyPositioned { layoutCoordinates ->
                        rootBoxBounds = layoutCoordinates.boundsInRoot()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column {
                    Text(
                        "Ziehe die Tasten (Gedrückthalten zum Ziehen) in die gewünschten Zielseiten. Bereits existierende Tasten der Zielseiten sind ausgegraut dargestellt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (isTablet) {
                        // Side-by-Side layout for Tablet screens
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.weight(0.4f)) {
                                CurrentPageCard()
                            }
                            LazyColumn(
                                modifier = Modifier.weight(0.6f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(categoryProposals) { category ->
                                    TargetCategoryItem(category)
                                }
                            }
                        }
                    } else {
                        // Standard stacked scrollable list for Phones
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                CurrentPageCard()
                            }
                            items(categoryProposals) { category ->
                                TargetCategoryItem(category)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Navigation Add Button/Fields
                    if (!showAddNavigationSection) {
                        Button(
                            onClick = { showAddNavigationSection = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Zielseite hinzufügen")
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "Zielseite hinzufügen",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                NavigationActionFields(
                                    navigateToPageId = newNavigationPageId,
                                    onPageSelected = { selectedPageId ->
                                        newNavigationPageId = selectedPageId
                                        val pageName = allAvailablePages.find { it.id == selectedPageId }?.name ?: "Seite $selectedPageId"
                                        if (categoryProposals.none { it.pageId == selectedPageId }) {
                                            categoryProposals = categoryProposals + ReorderCategory(
                                                pageId = selectedPageId,
                                                pageName = pageName,
                                                items = emptyList()
                                            )
                                            
                                            // Insert button config directly in database
                                            val firstFreeIndex = currentPage.buttonConfigs.indexOfFirst { it == null || !it.isActive }
                                            val targetIndex = if (firstFreeIndex != -1) firstFreeIndex else currentPage.buttonConfigs.size
                                            val newConfig = ButtonConfig(
                                                id = java.util.UUID.randomUUID().toString(),
                                                label = pageName,
                                                spokenText = "Öffne $pageName",
                                                buttonAction = NavigateToPageButtonAction(pageId = selectedPageId),
                                                auditoryCue = AuditoryCue.TextToSpeechCue("Öffne $pageName")
                                            )
                                            onCreateNavigationButton(targetIndex, newConfig)
                                        }
                                        showAddNavigationSection = false
                                        newNavigationPageId = ""
                                    },
                                    availablePages = allAvailablePages,
                                    templates = templates,
                                    onNavigateToPage = { targetPageId ->
                                        // Save and navigate!
                                        onConfirm(categoryProposals)
                                        onDismiss()
                                        onNavigateToPage(targetPageId)
                                    },
                                    onCreatePage = onCreatePage,
                                    onDismissDialog = {
                                        showAddNavigationSection = false
                                    },
                                    onAutoSave = {}
                                )
                            }
                        }
                    }
                }

                // Drag Overlay
                if (draggedItemId != null && rootBoxBounds != null) {
                    val draggedItem = allItems.find { it.buttonId == draggedItemId }
                    val draggedLabel = draggedItem?.config?.label ?: ""
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
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(categoryProposals) }
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
