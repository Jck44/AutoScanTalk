# Plan: Graphischen Struktur-Editor zu einem vollwertigen Editor aufwerten

## Context

Der Struktur-Editor (Graph- und Karten-Ansicht) erlaubt dem Caregiver bisher nur eingeschränkte
Bearbeitung: Button-Chips lassen sich per Long-Press ziehen (verschieben zwischen Seiten), und
Verbindungen werden durch Antippen der Kanten-Linie gelöscht. Es fehlen die Komfort-Features, die
der **Grid-Editor** bereits hat. Ziel: den Struktur-Editor näher an einen vollständigen Editor
bringen, mit konsistentem Verhalten zum Grid-Editor.

**Kernursache der Lücke:** Es existieren **zwei getrennte Drag&Drop-Systeme**:

| | Grid-Editor (gut) | Struktur-Editor (primitiv) |
|---|---|---|
| Datei | `ui/components/DragDropManager.kt` | `ui/components/ChipDragDropState.kt` + `DraggableChip.kt` |
| Haptik | `LONG_PRESS` bei Start (Z. 185), `VIRTUAL_KEY` bei Hover über neues Ziel (Z. 127) | keine |
| Drop-Ziel-Highlight | `currentHoveredTarget` (kleinstes umschließendes Rect) | keiner |
| Schwebende Vorschau | `DragDropContainer.floatingPreview` (Skalierung 1.05, Alpha 0.85) | nur Text-Label |
| Templates | `dragSource`/`dropTarget` + `GridDragState.handleDrop` | nicht unterstützt |

**Entscheidung (mit Nutzer abgestimmt):** Beide Struktur-Ansichten (Graph **und** Karten) auf das
gemeinsame `DragDropManager`-System umstellen. Templates kommen über ein **Panel auf dem Tablet /
Bottom-Sheet auf dem Telefon**. Zusätzlich alle vier Komfort-Extras (Delete-Drop-Zone, '+'-Button
pro Knoten, Geister-Platzhalter, Auto-Pan).

## Bestehende Bausteine, die wiederverwendet werden

- **`DragDropManager.kt`** — `rememberDragDropState()`, `DragDropContainer(state, onDrop, floatingPreview, content)`, `Modifier.dragSource(item, onDragStart, onDragEnd)`, `Modifier.dropTarget(key)`. Liefert Haptik + Hover-Highlight + Vorschau geschenkt.
- **`ButtonConfigDialog`** (`ui/pages/ButtonConfigDialog.kt:49`) — `(buttonConfig, onSave, onDismiss, onTest, onSuggestLabel?, onSaveAsTemplate?, …)`. Wird bereits aus `GridEditorContent.kt:539` und `GridEditorDialogs.kt:100` aufgerufen → für Klick-zum-Editieren und '+'-Anlegen wiederverwenden.
- **`ButtonTemplatesPanel`** (`ui/templates/ButtonTemplatesPanel.kt:61`) — fertiges Template-Panel mit `dragSource`/`dropTarget` und Klick (`onEditTemplate`). Direkt einbettbar.
- **`GridDragState.handleDrop`** (`ui/components/GridDragState.kt:9`) — Muster: `ButtonTemplate` → `buttonConfig.copy(id = UUID())` → `actions.insertButtonConfig(...)`. Vorlage für `handleStructureDrop`.
- **VM-Funktionen** (`GridEditorViewModel.kt`): `insertButtonConfig` (Z. 78), `updateButtonConfig` (Z. 74), `moveButtonToPage` (Z. 182), `duplicateButtonToPage` (Z. 192), `saveButtonAsTemplate` (Z. 58), `buttonTemplates`-Flow.
- **Freier-Slot-Logik** (`StructureEditorScreen.kt:261`) — `indexOfFirst { it == null || !it.isActive }` → für '+' und Template-Add.
- **Auto-Scroll-Muster** (`StructureFocusCanvas.kt:111-134`) — Vorlage für Auto-Pan im Graph.
- **Tablet-Erkennung** (`StructureEditorScreen.kt:189`) — `configuration.screenWidthDp >= 600`.
- **`navigableButtons()`** (`StructureCanvasLogic.kt:10`) — liefert Chip-Liste pro Knoten.

## Umsetzung

### Phase 1 — Drag-System vereinheitlichen (Fundament)
Beide Ansichten auf `DragDropManager` umstellen, damit Haptik, Hover-Highlight & Vorschau überall greifen.

1. **Neue Drag/Drop-Typen** (in `DragDropManager.kt` neben `TemplateDropTarget` ergänzen):
   - `data class StructureButtonDrag(val pageId: String, val index: Int, val label: String, val action: ButtonAction?)`
   - `data class StructureNodeTarget(val pageId: String)`
   - `object StructureDeleteTarget`
   - `data class StructureSlotTarget(val pageId: String, val index: Int)` (für Geister-Platzhalter/präzises Einfügen)
2. **`DragDropContainer` als gemeinsamer Wrapper:** in `StructureEditorScreen.kt` den Hauptbereich (Canvas + Template-Panel + ggf. Tree-Panel) in **einen** `DragDropContainer` hüllen, damit Drags zwischen Template-Panel und Canvas/Graph laufen. `rememberDragDropState()` ersetzt `rememberChipDragDropState()`.
3. **`DraggableChip` umstellen:** das eigene `detectDragGesturesAfterLongPress` (Z. 74-84) durch `Modifier.dragSource(item = StructureButtonDrag(...))` ersetzen; `onClick` (bereits vorhanden, Z. 38) für den Edit-Dialog freigeben. Visuelles Chip (Surface + Label + `ActionVisualTokens`-Farben) bleibt. Highlight bei Hover über sein Zielknoten via `state.currentHoveredTarget`.
4. **`handleStructureDrop(item, target, …)`** (neue Datei `ui/pages/structure/StructureDragDropHandler.kt`, Muster `GridDragState`):
   - `StructureButtonDrag` → `StructureNodeTarget` (anderer Knoten): `moveButtonToPage(...)` + Undo-Snackbar.
   - `StructureButtonDrag` → `StructureDeleteTarget`: `updateButtonConfig(pageId, index, null)` + Undo (konsistent zum bestehenden Remove-Connection-Dialog `StructureEditorScreen.kt:643`).
   - `ButtonTemplate` → `StructureNodeTarget`: `buttonConfig.copy(id = UUID())` → `insertButtonConfig` am ersten freien Slot.
   - `StructureSlotTarget`: präzises Einfügen an Index.
5. **`StructureFocusCanvas` (Karten) & `StructureGraphNode`/`StructureGraphView` (Graph)** auf die neuen Modifier/Targets umziehen; den manuellen `ChipDragDropState`-Code (Floating-Label `StructureFocusCanvas.kt:815`, Bounds-Erkennung in `onDragEnd`) durch das Container-System ersetzen. `ChipDragDropState.kt` entfällt anschließend.

**Damit erfüllt:** gleiche Haptik wie Grid-Editor + „wohin ziehe ich gerade" (Knoten-Highlight).

### Phase 2 — Chip-Klick öffnet Edit-Dialog & Buttons hinzufügen
1. **Klick auf Chip → `ButtonConfigDialog`:** Edit-State im `StructureEditorScreen` halten (`editTarget: Pair<pageId, index>?`). `onClick` des Chips setzt ihn; `onSave` → `updateButtonConfig`. Aufruf-Muster aus `GridEditorContent.kt:539` übernehmen (inkl. `onTest`, `onSuggestLabel`, `onSaveAsTemplate`).
   - Long-Press = ziehen, Tap = editieren (Gesten schließen sich nicht aus, `dragSource` nutzt Long-Press).
2. **'+'-Button pro Knoten:** im aufgeklappten `StructureGraphNode` (Header, neben Ein-/Ausklappen) und in der Karten-Ansicht am Seiten-Block ein '+'. Öffnet `ButtonConfigDialog` mit leerer `ButtonConfig`; `onSave` → `insertButtonConfig` am ersten freien Slot (Logik aus `StructureEditorScreen.kt:261`). Bei voller Seite: bestehende „Seite voll"-Snackbar (`R.string.structure_page_full`).

### Phase 3 — Templates im Graph (Drag & Klick)
1. **Template-Panel einbetten:** `ButtonTemplatesPanel` wiederverwenden.
   - **Tablet** (`isTablet`): als ein-/ausklappbare **rechte Seitenleiste** im Editor (Toggle-Button in der `EditorTopBar`, `StructureEditorScreen.kt:309`). Muss innerhalb des `DragDropContainer` liegen.
   - **Telefon:** als `ModalBottomSheet`, geöffnet über Top-Bar-Action (analog zum bestehenden Tree-Bottom-Sheet `StructureEditorScreen.kt:597`).
2. **Drag:** Template aus dem Panel auf einen Knoten ziehen → `handleStructureDrop` (Phase 1.4).
3. **Klick (wie „unverwendete Seiten als Link"):** Tap auf ein Template fügt es in den **fokussierten** Knoten ein (`insertButtonConfig` am ersten freien Slot). Vorbild: Orphan-Add-Flow `StructureEditorScreen.kt:679`.
4. **Template bearbeiten über Stift-Icon:** Der bisherige Tap-zum-Bearbeiten (`onEditTemplate`, `ButtonTemplatesPanel.kt:273` `clickable`) wird ersetzt: Tap = „hinzufügen" (3.), Bearbeiten läuft über ein **Pencil-Icon** in der `TemplateItemCard` (`ButtonTemplatesPanel.kt:261`), neben dem bestehenden Delete-Icon (Z. 332). `onEditTemplate` wird an dieses Icon gehängt.

### Phase 4 — Komfort-Extras
1. **Delete-Drop-Zone im Graph:** rote Zone (wie Karten-Ansicht `StructureFocusCanvas.kt:762`), nur während eines aktiven Drags eingeblendet (`state.isDragging`), als `dropTarget(StructureDeleteTarget)`. Skaliert/färbt bei Hover (über `currentHoveredTarget`).
2. **Geister-Platzhalter:** wenn `currentHoveredTarget is StructureNodeTarget`, im Zielknoten einen gestrichelten Platzhalter-Slot einblenden (es gibt bereits eine Pseudo-Drop-Anzeige in `StructureGraphNode.kt:133` — ausbauen, dass sie nur im gehoverten Knoten erscheint).
3. **Auto-Pan beim Ziehen an den Rand:** `LaunchedEffect` auf `state.dragOffset`/Position, das `scrollStateX`/`scrollStateY` (`StructureGraphView.kt:114`) verschiebt, wenn die Position nahe einer Kante ist. Geschwindigkeits-Rampe aus `StructureFocusCanvas.kt:111-134` übernehmen.

### Phase 5 — Tablet/Telefon-Feinschliff & i18n
- Template-Panel-Layout wie oben (Seitenleiste vs. Sheet). Auf Tablet zusätzlich Knoten etwas breiter / mehr standardmäßig aufgeklappt (optional, klein halten).
- Hartkodierte deutsche Strings, die angefasst werden, nach `R.string.*` ziehen (z. B. `StructureGraphNode.kt:86,173`, `StructureFocusCanvas.kt:344,499`).

## Kritische Dateien

- `ui/components/DragDropManager.kt` — neue Drag/Drop-Typen.
- `ui/pages/structure/StructureDragDropHandler.kt` — **neu**, `handleStructureDrop`.
- `ui/components/DraggableChip.kt` — auf `dragSource` umstellen, `onClick`=Edit.
- `ui/pages/structure/StructureEditorScreen.kt` — `DragDropContainer`-Wrapper, Edit-/Add-Dialog-State, Template-Panel/Sheet, Top-Bar-Toggles.
- `ui/pages/structure/StructureFocusCanvas.kt` & `StructureGraphView.kt` & `StructureGraphNode.kt` — auf gemeinsames System, '+'-Button, Delete-Zone, Geister-Platzhalter, Auto-Pan.
- `ui/components/ChipDragDropState.kt` — **entfällt** nach Migration.
- `ui/templates/ButtonTemplatesPanel.kt` — Tap = hinzufügen, Bearbeiten an neues Pencil-Icon.
- Wiederverwendet (unverändert): `ButtonConfigDialog.kt`, `GridEditorViewModel.kt`, `GridDragState.kt` (als Vorlage).

## Verifikation

1. **Build:** `./gradlew :app:assembleDebug` (JAVA_HOME aus Android-Studio-JBR).
2. **Manuell, Graph-Ansicht, Telefon + Tablet:**
   - Chip antippen → Edit-Dialog öffnet, Änderung speichern → Chip aktualisiert.
   - Chip lange drücken → Haptik (Long-Press) spürbar, schwebende Vorschau, Zielknoten leuchtet beim Überfahren auf (Haptik bei jedem neuen Ziel), Geister-Platzhalter im Zielknoten.
   - Chip auf Delete-Zone → Verbindung/Button entfernt (Undo-Snackbar).
   - '+' am Knoten → leerer Edit-Dialog → Button landet am ersten freien Slot.
   - Template per Drag in Knoten **und** per Tap in fokussierten Knoten anlegen; Pencil-Icon öffnet Template-Bearbeitung.
   - Chip an Bildschirmrand ziehen → Graph pannt automatisch mit.
   - Tablet: Template-Panel als Seitenleiste; Telefon: als Bottom-Sheet.
3. **Karten-Ansicht:** gleiche Haptik/Highlight, keine Regression bei vorhandenem Verschieben/Löschen.
4. **Undo/Redo:** Move/Insert/Delete sind rückgängig machbar.
