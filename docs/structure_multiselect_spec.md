# Spec: Knotenübergreifendes Multiselect im Struktur-Editor (Graph + Karten)

## Ziel
Die Multiselect-/Bulk-Funktionalität aus dem Grid-Editor in den Struktur-Editor übertragen —
**knotenübergreifend**: Button-Chips beliebiger Knoten (Center / Incoming / Outgoing) auswählbar,
Bulk-Aktionen **Verschieben / Kopieren / Löschen**. Gilt in **beiden** Ansichten (Graph + Karten).

## Vorbild (Grid-Editor) — wiederverwenden, nicht neu erfinden
- **`BulkActionTopBar`** (`ui/components/BulkActionTopBar.kt`) — fertige Top-Bar: `selectedCount`, `onCancel`, `onMove`, `onCopy`, `onDelete`. **Unverändert wiederverwenden.**
- **Auswahl-Toggle-Button**: Muster aus `PageEditorScreen.kt:200-222` (Icon `GhostTalkIcons.CheckCircle`, Hintergrund-Highlight bei aktivem Modus, `contentDescription = R.string.bulk_action_toggle_multi_select`).
- **TopBar-Umschaltung**: Muster aus `PageEditorScreen.kt:158-171` (`if (isMultiSelectMode) BulkActionTopBar(...) else EditorTopBar(...)`).
- **VM-Bulk-Funktionen** (alle single-source, `GridEditorViewModel`):
  - `bulkDeleteButtons(pageId, indices: List<Int>)`
  - `moveButtonToPage(fromPageId, fromIndices: List<Int>, toPageId, forceMove=false) { result -> }`
  - `duplicateButtonToPage(fromPageId, fromIndices: List<Int>, toPageId, forceMove=false) { result -> }`
  - Result-Typ: `MoveButtonToPageUseCase.MoveResult` (`Success` / `TargetFull`).
- **Ziel-Picker**: `SearchablePagePicker` (`ui/pages/structure/SearchablePagePicker.kt`) — `(title, subtitle?, excludePageId, pages, onDismissRequest, onPageSelected)`.
- **Strings** (existieren bereits): `bulk_action_selected_count` (`%1$d`), `bulk_action_hint_empty`, `bulk_action_cancel`, `bulk_action_move`, `bulk_action_copy`, `bulk_action_delete`, `bulk_action_confirm_delete`, `bulk_action_toggle_multi_select`, `button_move_success`, `button_duplicate_success`, `button_delete_success`, `structure_target_full`.

## Datenmodell (knotenübergreifend)
Auswahl als **`Map<String, Set<Int>>`** (pageId → ausgewählte buttonConfigs-Indizes).
- `selectedCount = selection.values.sumOf { it.size }`.
- **State lebt in `StructureEditorScreen`** (wo TopBar + alle Callbacks sitzen).
- `remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }` (Auswahl ist ephemer; kein `rememberSaveable` nötig).
- **Nicht** bei `focusedPageId`-Wechsel zurücksetzen — man navigiert zwischen Knoten, während man die Auswahl aufbaut. Zurücksetzen nur bei: Modus-Verlassen (Cancel/Toggle aus) und nach abgeschlossener Bulk-Aktion.

## Umsetzung pro Datei

### 1. `ui/components/DraggableChip.kt` — Auswahl-Visual
Neuen Parameter `selected: Boolean = false` ergänzen. Bei `selected`:
- `border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)` am `Surface`,
- optional leichter Tint (z. B. `bgColor` unverändert lassen, Border reicht; alternativ ein kleines Check-Overlay wie im Grid).

Import `BorderStroke` ergänzen. Bestehende `isDragged`-Logik unberührt lassen.

### 2. `ui/pages/structure/StructureGraphNode.kt` — Visual durchreichen
- Neue Parameter: `isMultiSelectMode: Boolean = false`, `selectedIndices: Set<Int> = emptySet()`.
- Beim Chip (FlowRow): `selected = isMultiSelectMode && selectedIndices.contains(btnIdx)` an `DraggableChip` geben.
- **Wichtig (knotenübergreifend):** gilt für **jeden** Knoten, nicht nur `isCenter`. Die Selektierbarkeit hängt am übergebenen `selectedIndices`.

### 3. `ui/pages/structure/StructureGraphView.kt` — Auswahl verteilen
- Neue Parameter: `isMultiSelectMode: Boolean = false`, `selection: Map<String, Set<Int>> = emptyMap()`.
- An **alle drei** `StructureGraphNode`-Aufrufe (center/incoming/outgoing) durchreichen:
  `isMultiSelectMode = isMultiSelectMode, selectedIndices = selection[pageId].orEmpty()`
  (jeweils mit der pageId des Knotens: `focusedPageId`, `sourceId`, `targetId`).

### 4. `ui/pages/structure/StructureFocusCanvas.kt` — Karten-Ansicht
- Neue Parameter: `isMultiSelectMode: Boolean = false`, `selection: Map<String, Set<Int>> = emptyMap()`.
- An `StructureGraphView` durchreichen.
- Focused-Page-Chips (`DraggableChip` bei Z. 459, onClick → `onEditButton(focusedPageId, index)`):
  `selected = isMultiSelectMode && selection[focusedPageId].orEmpty().contains(index)`.
- Outgoing-Target-Chips (`DraggableChip` bei Z. 664, onClick → `onEditButton(edge.targetPageId, btnIndex)`):
  `selected = isMultiSelectMode && selection[edge.targetPageId].orEmpty().contains(btnIndex)`.
- Den Split-Wizard-Chip (Z. 417) **nicht** anfassen (eigener Drag-Typ, keine Auswahl).

### 5. `ui/pages/structure/StructureEditorScreen.kt` — State, TopBar, Routing, Dialoge
**State** (bei den anderen `var`s, ~Z. 212):
```kotlin
var isMultiSelectMode by remember { mutableStateOf(false) }
var selection by remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }
var showBulkMoveDialog by remember { mutableStateOf(false) }
var showBulkCopyDialog by remember { mutableStateOf(false) }
var showBulkDeleteConfirm by remember { mutableStateOf(false) }
val selectedCount = selection.values.sumOf { it.size }
fun clearSelection() { selection = emptyMap(); isMultiSelectMode = false }
```

**TopBar** (`Scaffold.topBar`, Z. 425): umhüllen mit
`if (isMultiSelectMode) { BulkActionTopBar(selectedCount, onCancel = { clearSelection() }, onMove = { showBulkMoveDialog = true }, onCopy = { showBulkCopyDialog = true }, onDelete = { showBulkDeleteConfirm = true }) } else { EditorTopBar(...) }`.
`BulkActionTopBar` voll qualifizieren (`com.andreas_kratzer.ghosttalk.ui.components.BulkActionTopBar`) oder importieren.

**Toggle-Button** in `EditorTopBar`-`actions` (z. B. vor dem Undo-Button, Z. 458): CheckCircle-IconButton, togglet `isMultiSelectMode`; beim Ausschalten `selection = emptyMap()`.

**Toggle-Logik** — die an `StructureFocusCanvas` übergebene `onEditButton`-Lambda (aktuell Z. 764) ändern:
```kotlin
onEditButton = { pageId, idx ->
    if (isMultiSelectMode) {
        val cur = selection[pageId].orEmpty()
        val next = if (cur.contains(idx)) cur - idx else cur + idx
        selection = if (next.isEmpty()) selection - pageId else selection + (pageId to next)
    } else {
        editTarget = pageId to idx
    }
}
```
Außerdem `isMultiSelectMode = isMultiSelectMode, selection = selection` an `StructureFocusCanvas` übergeben.

**Bulk-Dialoge** (bei den übrigen Dialogen am Ende der Datei):
- **Löschen** — `AlertDialog` (Titel `bulk_action_delete`, Text `bulk_action_confirm_delete`). Bei Bestätigung:
  ```kotlin
  selection.forEach { (pageId, indices) -> gridEditorViewModel.bulkDeleteButtons(pageId, indices.toList()) }
  showSuccessSnackbarWithUndo(R.string.button_delete_success)
  clearSelection()
  ```
- **Verschieben** — `SearchablePagePicker(title = bulk_action_move, excludePageId = "", pages, ...)`. Bei Ziel-Auswahl, je Quellseite gruppiert:
  ```kotlin
  selection.forEach { (srcPageId, indices) ->
      if (srcPageId != targetId) {
          gridEditorViewModel.moveButtonToPage(srcPageId, indices.toList(), targetId, forceMove = false) { result ->
              if (result is MoveResult.TargetFull) { /* structure_target_full Snackbar */ }
          }
      }
  }
  showSuccessSnackbarWithUndo(R.string.button_move_success)
  showBulkMoveDialog = false; clearSelection()
  ```
- **Kopieren** — analog mit `duplicateButtonToPage(...)` + `button_duplicate_success`.

`excludePageId = ""` zeigt alle Seiten (mehrere Quellen → keine sinnvolle einzelne Ausschluss-Seite).

## Hinweise / bewusste Trade-offs
- **Mehrere Undo-Schritte:** Jeder `bulkDeleteButtons`/`moveButtonToPage`/`duplicateButtonToPage`-Aufruf ist ein eigenes History-Command (eines pro Quellseite). Akzeptiert; kein Sammel-Command nötig.
- **`TargetFull` pro Quelle:** Beim Verschieben/Kopieren kann eine einzelne Quelle scheitern (Ziel voll) → pro Aufruf prüfen und Snackbar zeigen; restliche Quellen laufen trotzdem.
- **Drag im Auswahlmodus:** `dragSource` (Long-Press) und Tap=Select schließen sich nicht aus → Drag kann aktiv bleiben (wie im Grid). Optional im Auswahlmodus deaktivieren, falls es stört.
- **Inkonsistenz vermeiden:** Auswahl nach jeder Bulk-Aktion und beim Modus-Verlassen leeren (`clearSelection()`), damit keine veralteten Indizes (nach Verschieben/Löschen/Undo) hängen bleiben.

## Verifikation
1. **Build:** `./gradlew :app:assembleDebug` (JAVA_HOME aus Android-Studio-JBR).
2. **Manuell (Graph + Karten, Telefon + Tablet):**
   - Auswahl-Toggle in TopBar → BulkActionTopBar erscheint mit „0 ausgewählt".
   - Chips **verschiedener** Knoten antippen → Border-Highlight, Zähler steigt; erneut tippen → Abwahl.
   - Verschieben → Ziel-Picker → ausgewählte Buttons (aus mehreren Quellen) landen auf Zielseite.
   - Kopieren → Duplikate auf Zielseite, Originale bleiben.
   - Löschen → Bestätigung → ausgewählte Buttons weg; Undo stellt wieder her.
   - Cancel/Toggle-aus → Auswahl geleert, normale TopBar zurück.
   - Tipp ohne Auswahlmodus → öffnet weiterhin den Edit-Dialog (keine Regression).

---

## Nachtrag: Auswahlmodus „einfriert" die Ansicht (Absicherung)

**Problem:** Aktuell überlebt die Auswahl einen Seitenwechsel, und im Auswahlmodus sind weiterhin
Navigation (Knoten-Tap), Chip-Drag, '+'-Hinzufügen und Template-Add möglich. Das führt zu
(A) Phantom-Auswahl (man verschiebt/löscht unsichtbare Chips einer anderen Seite) und
(B) veralteten Indizes (Drag/Add/Template verschieben `buttonConfigs`-Indizes → Bulk-Aktion trifft
falsche Buttons). Undo/Redo sind im Modus bereits unerreichbar (BulkActionTopBar hat sie nicht).

**Lösung (gewählt):** Im Auswahlmodus (`isMultiSelectMode == true`) ist die Ansicht **eingefroren** —
nur Chip-Tippen (Selektion togglen), Knoten **Auf-/Zuklappen** und die Bulk-Aktionen sind aktiv.
Alles, was navigiert oder `buttonConfigs` mutiert, wird deaktiviert. `isMultiSelectMode` liegt in
`StructureGraphNode` und `StructureFocusCanvas` bereits vor.

**Konkrete Gates (jeweils nur wenn `isMultiSelectMode`):**

`StructureGraphNode.kt`
- Knoten-Navigation: `Surface(onClick = { if (!isMultiSelectMode) onFocus() }, …)` (Z. 66).
- '+'-Button: nur rendern, wenn `!isMultiSelectMode` (IconButton bei Z. 100 in `if (!isMultiSelectMode){…}` hüllen).
- Chip-Drag: `modifier = if (isMultiSelectMode) Modifier else Modifier.dragSource(item = dragItem)` (Z. 143).
- **Nicht** anfassen: Auf-/Zuklappen (`onToggleExpand`) bleibt aktiv (nötig, um Nachbar-Chips zum Auswählen aufzuklappen).

`StructureFocusCanvas.kt`
- Incoming-Source-Chip: `onClick = { if (!isMultiSelectMode) onFocus(sourceId) }` (Z. 304).
- Focused-'+': IconButton nur wenn `!isMultiSelectMode` (Z. 358).
- Focused-Chip-Drag: dragSource konditional (Z. 468).
- Target-Card-Navigation: `.clickable { if (!isMultiSelectMode) onFocus(edge.targetPageId) }` (Z. 617).
- Target-Chip-Drag: dragSource konditional (Z. 674).
- **Nicht** anfassen: Split-Wizard-Chips (Z. 425/543) — Wizard ist ein eigener Modus.

`StructureEditorScreen.kt`
- Beim Einschalten des Modus zusätzlich `templatesPanelExpanded = false` setzen (Toggle bei Z. 460).
  Da im Modus die `BulkActionTopBar` die normale TopBar ersetzt, ist der Template-Toggle ohnehin
  verborgen → kein erneutes Öffnen, kein Template-Add/-Drag mehr möglich. Damit ist auch der
  Tablet-Seitenpanel-Fall abgedeckt (eingeklappt = keine Drag-Quelle).

**Verifikation (zusätzlich):**
- Im Auswahlmodus: Knoten-Tap navigiert **nicht**; '+' und Chip-Drag sind weg; Template-Panel zu.
- Auf-/Zuklappen eines Nachbar-Knotens funktioniert weiter; dessen Chips sind auswählbar.
- Modus verlassen → Navigation/Drag/'+'/Template wieder normal.
