@file:Suppress("UNUSED_PARAMETER", "UNUSED_VALUE")
package com.andreas_kratzer.ghosttalk.ui.pages


import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase.CategoryProposal
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase.PageSplitProposal
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import kotlin.math.roundToInt

/**
 * Dialog zur Abfrage der Datenschutz-Zustimmung.
 */
@Composable
fun PageSplitOptInDialog(
    onConfirmCloud: (rememberDecision: Boolean) -> Unit,
    onConfirmManual: () -> Unit,
    onDismiss: () -> Unit
) {
    var rememberDecision by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seiten-Kategorisierung (Datenschutz)", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Um diese Seite automatisch aufzuteilen, können wir die Bezeichnungen der Tasten anonymisiert an die Google Cloud API senden. Es werden dabei keinerlei persönliche Daten oder Benutzer-IDs übertragen.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Tipp: Unter Einstellungen -> KI kannst du einen eigenen Gemini API Key hinterlegen, um sicherzustellen, dass die Daten ausschließlich in deinem eigenen Google Cloud Projekt verarbeitet werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rememberDecision,
                        onCheckedChange = { rememberDecision = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Entscheidung merken (Opt-In speichern)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCloud(rememberDecision) }
            ) {
                Text("Cloud API nutzen")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    onClick = onConfirmManual
                ) {
                    Text("Manuell (Copy/Paste)")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        }
    )
}

/**
 * Dialog für das manuelle Kopieren des Prompts und Einfügen der KI-Antwort.
 */
@Composable
fun PageSplitManualPromptDialog(
    promptText: String,
    onEvaluateResponse: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var pastedJson by remember { mutableStateOf("") }
    val parseError = remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manuelle KI-Kategorisierung", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Kopiere den generierten Prompt und füge ihn in eine KI deiner Wahl (z.B. ChatGPT, Gemini Web) ein. Kopiere die Antwort der KI und füge sie unten ein.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Prompt Text Display (Read-Only)
                OutlinedTextField(
                    value = promptText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("1. Prompt kopieren") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Prompt", promptText)))
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Prompt kopieren")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Input for LLM JSON Response
                OutlinedTextField(
                    value = pastedJson,
                    onValueChange = { 
                        pastedJson = it 
                        parseError.value = null
                    },
                    label = { Text("2. KI-Antwort (JSON) einfügen") },
                    placeholder = { Text("Füge hier das von der KI generierte JSON-Objekt ein...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 10,
                    isError = parseError.value != null
                )
                if (parseError.value != null) {
                    Text(
                        text = parseError.value ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        onEvaluateResponse(pastedJson)
                    } catch (e: Exception) {
                        parseError.value = "Ungültiges JSON-Format. Bitte stelle sicher, dass die Struktur genau dem Prompt entspricht."
                    }
                },
                enabled = pastedJson.isNotBlank()
            ) {
                Text("Antwort auswerten")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

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

/**
 * Ein flexibler, via Long-Press ziehbarer Chip für die Kachel-Labels.
 */
@Composable
fun DraggableChip(
    label: String,
    isDragged: Boolean,
    onDragStart: (center: Offset, size: Offset) -> Unit, // liefert die initiale globale Mitte und Größe
    onDrag: (Offset) -> Unit,      // liefert das Drag-Delta
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit = {}
) {
    var globalPos by remember { mutableStateOf(Offset.Zero) }
    var chipSize by remember { mutableStateOf(Offset.Zero) }

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isDragged) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = 0.dp,
        modifier = Modifier
            .onGloballyPositioned { layoutCoordinates ->
                val bounds = layoutCoordinates.boundsInRoot()
                globalPos = bounds.center
                chipSize = Offset(bounds.width, bounds.height)
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> currentOnDragStart(globalPos, chipSize) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() }
                )
            }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (isDragged) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
