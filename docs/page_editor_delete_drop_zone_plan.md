# Plan: Löschen-Drop-Target im Page Editor (Drag & Drop)

**Ziel:** Beim Verschieben eines Buttons per Drag & Drop im Page Editor soll ein
Lösch-Bereich (Drop Target) eingeblendet werden. Lässt der Nutzer den Button dort
fallen, wird er aus der Seite entfernt – mit Undo-Snackbar wie beim bisherigen Löschen.

Erstellt für: Umsetzung durch Gemini, Review durch Claude.
Datum: 2026-06-13.

---

## 1. Ausgangslage / relevante Architektur

Das Drag-&-Drop-System ist generisch über `DragDropState` + `dropTarget(key)` +
`dragSource(item)` aufgebaut.

- **`DragDropManager.kt`**
  - `DragDropState`: hält `isDragging`, `dragItem`, `currentHoveredTarget`, registriert
    Target-Bounds in `targets: Map<Any, Rect>`.
  - `onDrag()` ermittelt das gehoverte Target als das **flächenkleinste** Target,
    dessen Rect den Fingerpunkt enthält → mehrere überlappende Targets sind unkritisch,
    solange die Lösch-Zone räumlich getrennt liegt.
  - `onDragEnd()` ruft `onDropCallback(item, target)` auf.
  - `DragDropContainer`: rendert `content()` + Floating-Preview (zIndex 9999), löst
    bei `currentHoveredTarget != null` Haptik aus.
  - `Modifier.dropTarget(key)`: registriert Bounds via `onGloballyPositioned`,
    `unregisterTarget` via `DisposableEffect`. **Wichtig:** Targets, die nur während
    `isDragging` komponiert werden (z. B. die `InsertTarget`-Boxen), registrieren sich
    erst nach dem ersten Layout-Frame – das ist bereits gängiges Muster und funktioniert.

- **`GridEditorContent.kt`**
  - Target-/Item-Typen sind hier definiert (Z. 59–62):
    `GridCellTarget(index)`, `InsertTarget(index)`, `object TemplatesPanelTarget`,
    `DraggedGridCell(index, config)`.
  - `DragDropContainer(...)` mit `onDrop = onDrop` (delegiert an `GridDragDropHandler`),
    `floatingPreview` für `ButtonTemplate` und `DraggedGridCell`.
  - Im `content { ... }`: `Row` (Grid + optionales `ButtonTemplatesPanel`), der
    `SnackbarHost` (BottomCenter, `padding(paddingLarge)`), Dialoge.
  - `showUndoSnackbar(msg)` existiert bereits hier und wird an den Handler übergeben.

- **`GridDragState.kt` → `GridDragDropHandler.handleDrop(...)`**
  - `when (draggedItem)` → `is DraggedGridCell`:
    - Ziel `GridCellTarget` → `moveButton`
    - Ziel `InsertTarget` → `moveButtonWithInsert`
    - Ziel `TemplatesPanelTarget|TemplateDropTarget|CategoryHeaderDropTarget` →
      `onSaveAsTemplate(config)`

- **`GridEditorGrid.kt` → `EditorButtonCell`**
  - Quelle der gezogenen Buttons: `dragSource(item = DraggedGridCell(globalIndex, buttonConfig), longPress = true)`.
  - Hover-Feedback pro Zelle über `dragDropState.currentHoveredTarget == GridCellTarget(...)`.

- **Löschen heute** (`GridEditorDialogs.kt:123`):
  `actions.updateButtonConfig(item.id, index, null)` + Undo-Snackbar „Button gelöscht".
  → Dieselbe Action nutzen wir für den Drop.

- **Konventionen:**
  - Drag-Snackbar-Texte im Handler sind **hardcodiert deutsch** („Button verschoben",
    „Vorlage platziert"). Für die Snackbar-Meldung halten wir uns daran.
  - Sichtbare UI-Labels / `contentDescription` → **String-Resource** (in
    `values/strings.xml` **und** `values-en/strings.xml`).
  - Lösch-Icon: `Icons.Default.Delete` (Material Filled, bereits projektweit genutzt).

---

## 2. Design-Entscheidungen

1. **Neuer Target-Typ:** `object DeleteTarget` (analog zu `TemplatesPanelTarget`),
   definiert in `GridEditorContent.kt` neben den anderen Targets.

2. **Sichtbarkeit der Zone:** Die Lösch-Zone wird **nur** eingeblendet, wenn
   `dragDropState.isDragging == true` **und** `dragDropState.dragItem is DraggedGridCell`.
   → Sie erscheint also nur beim Ziehen eines bestehenden Buttons, nicht beim Ziehen
   einer Vorlage aus dem Template-Panel. (Vorlagen-Löschen ist bewusst out of scope.)

3. **Position:** Fixierte Leiste am **oberen** Bildschirmrand (TopCenter) innerhalb des
   `DragDropContainer`-Contents.
   - Begründung: Der Finger/Floating-Preview liegt beim Long-Press-Drag in der
     Bildschirmmitte/unten; der `SnackbarHost` sitzt bereits unten (BottomCenter).
     Oben kollidiert die Zone weder mit dem Finger noch mit der Snackbar.
   - Räumlich vom Grid getrennt → keine Overlap-Konflikte mit `GridCellTarget`/`InsertTarget`.

4. **Visuelles Feedback:**
   - Standard: dezenter Container (`errorContainer` mit reduzierter Alpha) + Delete-Icon
     + Label „Zum Löschen hierher ziehen".
   - Gehovert (`currentHoveredTarget == DeleteTarget`): kräftige `error`-Farbe, dickerer
     Rand / leichte Skalierung, Icon-Tint `onError`/`error`. Haptik kommt automatisch
     aus `DragDropContainer`.

5. **Drop-Verhalten:** `DraggedGridCell` auf `DeleteTarget`
   → `actions.updateButtonConfig(itemId, draggedItem.index, null)`
   → `showUndoSnackbar("Button gelöscht")` (identisch zur Dialog-Löschung, inkl. Undo).

6. **zIndex:** Die Zone liegt über dem Grid, aber unter der Floating-Preview
   (Preview = 9999). Ein `zIndex` ~1–10 genügt; Hauptsache die Preview bleibt sichtbar
   über der Zone.

---

## 3. Umsetzungsschritte

### Schritt 1 – Target-Typ definieren
**Datei:** `app/src/main/java/.../ui/components/GridEditorContent.kt` (bei Z. 59–62)

```kotlin
data class GridCellTarget(val index: Int)
data class InsertTarget(val index: Int)
object TemplatesPanelTarget
object DeleteTarget                 // NEU
data class DraggedGridCell(val index: Int, val config: ButtonConfig)
```

### Schritt 2 – Drop im Handler behandeln
**Datei:** `app/src/main/java/.../ui/components/GridDragState.kt`
Im `is DraggedGridCell`-Zweig **vor** dem bestehenden `TemplatesPanelTarget`-Else-Zweig
(Reihenfolge wichtig, da `DeleteTarget` ein eigener Fall ist):

```kotlin
is DraggedGridCell -> {
    if (target is DeleteTarget) {
        actions.updateButtonConfig(itemId, draggedItem.index, null)
        showUndoSnackbar("Button gelöscht")
    } else if (target is GridCellTarget) {
        if (draggedItem.index != target.index) {
            actions.moveButton(itemId, draggedItem.index, target.index)
            showUndoSnackbar("Button verschoben")
        }
    } else if (target is InsertTarget) {
        actions.moveButtonWithInsert(itemId, draggedItem.index, target.index)
        showUndoSnackbar("Button verschoben")
    } else if (target is TemplatesPanelTarget || target is TemplateDropTarget || target is CategoryHeaderDropTarget) {
        onSaveAsTemplate(draggedItem.config)
    }
}
```
> Hinweis: `updateButtonConfig(..., null)` ist exakt die im Dialog verwendete
> Lösch-Action und ist undo-fähig (siehe `GridEditorDialogs.kt:123`).

### Schritt 3 – Lösch-Zone als Composable
**Datei:** neu `app/src/main/java/.../ui/components/DeleteDropZone.kt`
(oder als `private @Composable` direkt in `GridEditorContent.kt`).

```kotlin
@Composable
fun DeleteDropZone(modifier: Modifier = Modifier) {
    val state = LocalDragDropState.current
    val isHovered = state.currentHoveredTarget == DeleteTarget

    val containerColor = if (isHovered) MaterialTheme.colorScheme.error
                         else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
    val contentColor = if (isHovered) MaterialTheme.colorScheme.onError
                       else MaterialTheme.colorScheme.onErrorContainer
    val scale by animateFloatAsState(if (isHovered) 1.05f else 1f, label = "deleteZoneScale")

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.large)
            .background(containerColor)
            .border(
                width = if (isHovered) 2.dp else 0.dp,
                color = if (isHovered) MaterialTheme.colorScheme.onError else Color.Transparent,
                shape = MaterialTheme.shapes.large
            )
            .dropTarget(key = DeleteTarget)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = null,
            tint = contentColor
        )
        Text(
            text = stringResource(R.string.page_editor_delete_drop_zone),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
```

### Schritt 4 – Zone in den Container einhängen
**Datei:** `GridEditorContent.kt`, innerhalb des `DragDropContainer { ... }`-Contents,
z. B. direkt vor dem `SnackbarHost`-Box (Z. ~301). Nur bei aktivem Drag eines
Grid-Buttons rendern:

```kotlin
if (dragDropState.isDragging && dragDropState.dragItem is DraggedGridCell) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = dimensions.paddingLarge)
            .zIndex(10f),
        contentAlignment = Alignment.TopCenter
    ) {
        DeleteDropZone()
    }
}
```
> Optional (Polish): Ein-/Ausblenden mit `AnimatedVisibility` + `slideInVertically`
> von oben für eine ruhigere Optik.

### Schritt 5 – String-Resources
**Dateien:** `app/src/main/res/values/strings.xml` und `values-en/strings.xml`

```xml
<!-- values/strings.xml -->
<string name="page_editor_delete_drop_zone">Zum Löschen hierher ziehen</string>
```
```xml
<!-- values-en/strings.xml -->
<string name="page_editor_delete_drop_zone">Drag here to delete</string>
```

### Schritt 6 – Imports
In `DeleteDropZone.kt`/`GridEditorContent.kt` ergänzen: `Icons.Default.Delete`,
`animateFloatAsState`, `graphicsLayer`, `clip`, `background`, `border`, `zIndex`,
`stringResource`, `R`. (Viele davon sind in `GridEditorContent.kt` bereits vorhanden.)

---

## 4. Edge Cases & Hinweise

- **Nur Grid-Buttons:** Zone erscheint nicht beim Ziehen von `ButtonTemplate` (Bedingung
  `dragItem is DraggedGridCell`). Falls später auch Templates per Drop löschbar sein
  sollen → separater Fall im `is ButtonTemplate`-Zweig (out of scope).
- **Index-Stabilität:** `draggedItem.index` ist der `globalIndex` der Quellzelle und seit
  Drag-Start unverändert → korrektes Ziel für `updateButtonConfig`.
- **Undo:** Über `actions.undo` bereits abgedeckt; Snackbar-Verhalten identisch zum Dialog.
- **Leere Zelle:** Aus leeren Zellen startet kein Drag (`dragSource` nur bei
  `buttonConfig != null`), also kein Löschen einer leeren Zelle möglich.
- **Edit-Preview-Modus:** Drag ist dort deaktiviert (`isEditPreviewActive`), daher erscheint
  die Zone dort gar nicht – nichts weiter zu tun.
- **Landscape/Tablet mit Template-Panel:** Zone liegt oben mittig über dem Grid-Bereich;
  Kollision mit dem rechten Panel (`TemplatesPanelTarget`) ist unkritisch, da Bounds
  getrennt sind und `onDrag` das flächenkleinste Target wählt.
- **Keine Doppel-Registrierung:** Nur **eine** `DeleteDropZone` rendern (nicht pro Zelle).

---

## 5. Tests / Verifikation

- **Manuell (`/run` bzw. App starten):**
  1. Page Editor öffnen, Button lange drücken → Lösch-Leiste erscheint oben.
  2. Button auf die Leiste ziehen → Leiste hebt sich hervor (Haptik), Loslassen → Button weg.
  3. „Rückgängig" in Snackbar → Button kehrt zurück.
  4. Button woanders hin ziehen (nicht auf Leiste) → normales Verschieben, kein Löschen.
  5. Vorlage aus Panel ziehen → Lösch-Leiste erscheint **nicht**.
  6. Row-by-Row-Layout: gleiche Schritte gegenprüfen.
- **Unit-Test (optional):** `GridDragDropHandler.handleDrop` mit
  `DraggedGridCell` + `DeleteTarget` → erwartet `updateButtonConfig(itemId, index, null)`
  und `showUndoSnackbar`-Aufruf. Bestehende Handler-Tests als Vorlage nutzen
  (siehe `PageViewModelTest.kt` / vorhandene Drag-Tests).
- **Build:** `./gradlew :app:assembleDebug` (JAVA_HOME aus Android-Studio-JBR setzen,
  siehe Memory „Build Java Home").

---

## 6. Review-Checkliste (für Claude)

- [ ] `DeleteTarget` definiert, kein Namens-/Import-Konflikt.
- [ ] Handler: `DeleteTarget`-Fall **vor** dem generischen Template-Else; `moveButton`-
      und `moveButtonWithInsert`-Pfade unverändert funktionsfähig.
- [ ] Lösch-Action = `updateButtonConfig(itemId, index, null)` (undo-fähig), Snackbar „Button gelöscht".
- [ ] Zone nur bei `isDragging && dragItem is DraggedGridCell` sichtbar.
- [ ] Hover-State korrekt (`currentHoveredTarget == DeleteTarget`), Haptik greift.
- [ ] Strings in **beiden** `strings.xml` vorhanden, keine hardcodierten sichtbaren Labels.
- [ ] zIndex: Floating-Preview bleibt über der Zone sichtbar.
- [ ] Keine Layout-Regression in Portrait/Landscape/Tablet & Row-by-Row.
- [ ] Keine Mehrfach-Registrierung von `DeleteTarget`; sauberes `unregisterTarget` beim Drag-Ende.

---

## 7. Betroffene Dateien (Zusammenfassung)

| Datei | Änderung |
|---|---|
| `ui/components/GridEditorContent.kt` | `object DeleteTarget`, Zone im Container rendern |
| `ui/components/GridDragState.kt` | `DeleteTarget`-Fall in `handleDrop` |
| `ui/components/DeleteDropZone.kt` (neu) | Composable der Lösch-Zone |
| `res/values/strings.xml` | DE-String |
| `res/values-en/strings.xml` | EN-String |
| (optional) Test für `GridDragDropHandler` | Drop-auf-Delete-Fall |
