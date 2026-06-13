# Plan: Struktur-Editor (Vollbild, navigierbarer Baum)

> Status: ENTWURF zur Umsetzung durch Gemini, Review durch Claude.
> Erstellt: 2026-06-13. Branch: `develop`.
> Workflow: Gemini setzt eine Phase um → committet → Claude reviewt die Phase → nächste Phase.

## 1. Kontext & Ziel

Caregiver sollen die **Navigationsstruktur über alle Seiten hinweg** grafisch bearbeiten
können – nicht nur eine Seite tief. Heute gibt es dafür nur:

- den **Raster-Editor** pro Seite (`PageEditorScreen` → `GridEditorContent`) und
- den modalen **„Organisieren (Bulk Reorder)"**-Dialog (`BulkReorderDialog`), erreichbar
  aus dem Raster-Editor. Dieser zeigt die aktuelle Seite + ihre direkten Navigationsziele
  und erlaubt Buttons per Drag&Drop zwischen Seiten zu verschieben. Er ist aber ein
  `AlertDialog` (max. 650dp) und reicht **nur einen Sprung weit**.

Ziel: ein **neuer Vollbild-Screen „Struktur-Editor"**, erreichbar von der **Seitenübersicht**
(`PageListScreen`), mit dem man:

1. den Buchaufbau als **navigierbaren Baum** überblickt,
2. durch den Baum **wandert** (Fokus wechseln, ohne den Editor zu verlassen),
3. Buttons zwischen Seiten **verschiebt** und Navigations-Verknüpfungen **anlegt/entfernt**.

Er ergänzt den Raster-Editor (Layout *innerhalb* einer Seite), ersetzt ihn nicht.

## 2. Festgelegte Design-Entscheidungen (nicht neu verhandeln)

Diese wurden vorab geklärt und sind bindend:

- **Single Source of Truth bleibt `buttonConfigs`.** Es gibt KEINEN separaten Kantenspeicher.
  Der Graph wird jedes Mal aus den `NavigateToPageButtonAction.pageId` der Buttons abgeleitet.
- **Fokus-Ansicht statt globaler Riesen-Graph.** Bei 181 Seiten ist eine Gesamtkarte unbrauchbar.
  Es steht immer **eine Seite im Fokus**; Nachbarschaft = „kommt von" (eingehend) + „führt zu" (ausgehend).
- **Der Baum NAVIGIERT, er ist KEIN Drop-Ziel.** Drag&Drop bleibt auf die wenigen Karten der
  Fokus-Ansicht beschränkt (bounded, wie heute). Auf einen scrollenden 181-Knoten-Baum zu droppen
  ist bewusst ausgeschlossen (Auto-Scroll/Off-Screen-Drops).
- **Neue Navigation landet in der ersten freien Zelle**, Auto-Label „Öffne X" – exakt wie heute
  in `BulkReorderDialog` (`firstFreeIndex`).
- **Dynamische Sprünge** (`NavigateBackButtonAction`, `NavigateToStartPageButtonAction`) sind
  KEINE festen Kanten – im Baum/Graph ignorieren bzw. nur als Icon andeuten.
- **Buch ist ein DAG, kein reiner Baum.** Eine Seite kann von mehreren Seiten erreichbar sein und
  es kann Zyklen geben (A→B→A). Die Baum-Darstellung muss bereits besuchte Knoten als
  **Referenz** markieren statt endlos aufzuklappen.

## 3. Wiederzuverwendende Bausteine (NICHT neu bauen)

| Zweck | Symbol / Datei |
|---|---|
| Button zwischen Seiten verschieben | `GridEditorViewModel.moveButtonToPage(fromPageId, fromIndex, toPageId, forceMove, onResult)` |
| Mehrere Moves auf einmal | `GridEditorViewModel.executeBulkMove(fromPageId, categoryMoves)` |
| Nav-Button einfügen (erste freie Zelle) | `GridEditorViewModel.insertButtonConfig(itemId, index, config, forceShift, onResult)` |
| Button löschen (Kante entfernen) | `GridEditorViewModel.updateButtonConfig(itemId, index, null)` |
| Undo | `GridEditorViewModel.undo(onSuccess)` |
| Neue Seite anlegen | `GridEditorViewModel.createNewPage(name, rows, cols, bookId, templateId, callback)` |
| Seitendaten inkl. `buttonConfigs` | `PageViewModel.unfilteredPages` (StateFlow) |
| Templates | `PageViewModel.templates` |
| aktives Buch | `PageViewModel.activeBookId` |
| Startseite | bestehende `defaultStartPageId`-Quelle (wie in `PageEditorScreen` via `pageSplitViewModel.defaultStartPageId`) |
| Ziehbarer Chip | `ui/components` → `DraggableChip` |
| Zielseiten-Auswahl | `NavigationActionFields` |
| Aktion / Cue | `NavigateToPageButtonAction(pageId)`, `AuditoryCue.TextToSpeechCue(...)` |

**`BulkReorderDialog` bleibt vorerst unangetastet.** Wir bauen den neuen Canvas frisch auf den
o. g. Low-Level-Bausteinen auf, um den getesteten Dialog nicht zu destabilisieren. Eine spätere
Vereinheitlichung ist als Phase 5 (optional) vermerkt.

## 4. Architektur-Überblick

```
PageListScreen (Seitenübersicht)
   └─ Toolbar-Action "Struktur" ──navigiert──▶ route "structure_editor"
                                                  │
NavHost: composable("structure_editor")          │
   └─ StructureEditorScreen(pageViewModel, gridEditorViewModel, onEditPageInGrid, onBack)
        ├─ State: focusedPageId (rememberSaveable; default = startPage)
        ├─ derived: BookNavigationGraph.from(unfilteredPages)   ◀── reine Domain-Logik
        ├─ StructureTreeNavigator   (links)  → tap: focusedPageId = it   [KEIN Drop-Ziel]
        └─ StructureFocusCanvas     (rechts) → "kommt von" / Seite / "führt zu" + Drag&Drop
```

Neue Dateien:

- `core/.../core/domain/pages/BookNavigationGraph.kt` — reine, unit-testbare Graph-Ableitung.
- `app/.../ui/pages/structure/StructureEditorScreen.kt` — Vollbild-Screen + Layout.
- `app/.../ui/pages/structure/StructureTreeNavigator.kt` — Baum-Komponente.
- `app/.../ui/pages/structure/StructureFocusCanvas.kt` — Fokus-/Drag&Drop-Komponente.
- `core/.../test/.../BookNavigationGraphTest.kt` — Unit-Tests.

---

## 5. Arbeitspakete (phasenweise)

### Phase 0 — Domain: `BookNavigationGraph` (+ Unit-Tests)

Reine Kotlin-Logik, keine UI. Zuerst, weil sie isoliert reviewbar/testbar ist.

`BookNavigationGraph.kt` (in `core`, package `...core.domain.pages`):

```kotlin
data class NavEdge(val sourcePageId: String, val sourceButtonIndex: Int, val targetPageId: String)

data class TreeNode(
    val pageId: String,
    val depth: Int,
    val children: List<TreeNode>,
    val isReference: Boolean // true = bereits anderswo im Baum gezeigt (Zyklus/Mehrfach-Elternteil)
)

class BookNavigationGraph private constructor(
    val outgoing: Map<String, List<NavEdge>>,   // pageId -> ausgehende Kanten
    val incoming: Map<String, List<String>>,    // pageId -> Quell-pageIds
    val allPageIds: Set<String>,
    val startPageId: String?
) {
    fun orphans(): List<String> // Seiten ohne eingehende Kante und != startPage
    fun buildTree(): TreeNode?  // ab startPageId, zyklensicher (visited-Set), isReference markieren

    companion object {
        fun from(pages: List<Page>, startPageId: String?): BookNavigationGraph
    }
}
```

Regeln:
- Kanten NUR aus `NavigateToPageButtonAction`. `NavigateBack`/`NavigateToStartPage` ignorieren.
- Nur aktive Buttons mit nicht-leerem Label berücksichtigen (analog `BulkReorderDialog`-Filter).
- `targetPageId` muss in `allPageIds` existieren (verwaiste Ziele auf gelöschte Seiten ausfiltern).
- `buildTree`: DFS ab `startPageId`; ist ein Knoten schon im aktuellen Pfad ODER bereits global
  expandiert, als `isReference=true` ohne Kinder einhängen.
- Fallback `startPageId == null`: Seite mit kleinstem `orderIndex` als Wurzel.

**Acceptance / Review-Checkliste Phase 0**
- [ ] `from()` ist O(Σ buttons), kein verschachtelter Page-Scan.
- [ ] Zyklus A→B→A terminiert, B (oder A) korrekt als `isReference`.
- [ ] Mehrfach-Elternteil: Zielseite erscheint einmal voll + sonst als Referenz.
- [ ] `orphans()` listet die nicht-referenzierten Seiten (erwartet ~55 im echten Buch).
- [ ] Unit-Tests decken ab: leeres Buch, Linearkette, Zyklus, Mehrfach-Elternteil, Orphans,
      gelöschte Zielseite, `startPageId == null`.

---

### Phase 1 — Vollbild-Screen + Baum + Fokus (NUR Lesen/Navigieren)

Layout und Navigation zuerst, ohne Schreiboperationen → entkoppelt Risiko.

1. **Route & Einstieg**
   - In `GhosTTalkNavHost`: `composable("structure_editor") { StructureEditorScreen(...) }`.
   - In der Security-Prüfung `navigateWithSecurity` die Route bei `isSecurityRequiredForEdit()`
     einreihen (zur Liste mit `page_list`, `page_editor` etc.).
   - In `PageListScreen`-Toolbar (`actions`) eine `IconButton`-Action „Struktur" (Icon:
     `GhostTalkIcons` Baum/Hierarchie, sonst `Icons.Default.AccountTree`) →
     `onOpenStructureEditor()`-Callback, in NavHost auf `safeNavigate("structure_editor")`.
   - Neue String-Ressourcen (de + values-en): `structure_editor_title`, `structure_editor_open`,
     `structure_incoming`, `structure_outgoing`, `structure_unconnected`, `structure_open_in_grid`.

2. **`StructureEditorScreen`**
   - Signatur:
     ```kotlin
     fun StructureEditorScreen(
         pageViewModel: PageViewModel,
         gridEditorViewModel: GridEditorViewModel = hiltViewModel(),
         onEditPageInGrid: (pageId: String) -> Unit,
         onNavigateBack: () -> Unit
     )
     ```
   - `val pages by pageViewModel.unfilteredPages.collectAsState()`
   - `val graph by remember(pages, startPageId) { derivedStateOf { BookNavigationGraph.from(pages, startPageId) } }`
   - `var focusedPageId by rememberSaveable { mutableStateOf(startPageId ?: pages.firstByOrder()) }`
   - In `GhostTalkScaffold` mit Titel `structure_editor_title`, Back = `onNavigateBack`.
   - Responsives Layout via `BoxWithConstraints`/`isTablet` (Muster aus `BulkReorderDialog`):
     - **Tablet/Landscape:** `Row` — links `StructureTreeNavigator` (~34%), rechts `StructureFocusCanvas` (~66%).
     - **Phone:** Canvas als Hauptbereich; Baum in einem ausklappbaren `ModalBottomSheet`/Drawer,
       getriggert durch eine Toolbar-Action „Baum".

3. **`StructureTreeNavigator` (read-only in dieser Phase)**
   - Rendert `graph.buildTree()` als eingerückte, auf-/zuklappbare Liste (`LazyColumn`).
     Jeder Knoten: Einrückung nach `depth`, Chevron (nur wenn Kinder), Seitenname,
     Kinderanzahl-Badge, Referenz-Badge falls `isReference`.
   - `focusedPageId` hervorheben.
   - Tap auf Knoten → `onFocus(pageId)`. **Kein Drop-Ziel.**
   - Darunter Abschnitt „Nicht verbunden (N)" (`graph.orphans()`), aufklappbar, Tap = fokussieren.
   - Aufklapp-Zustand in `rememberSaveable` (Set von pageIds), Wurzel initial offen.

4. **`StructureFocusCanvas` (read-only in dieser Phase)**
   - Drei Bereiche zur fokussierten Seite (`pages.find { it.id == focusedPageId }`):
     - **Kommt von:** Chips aus `graph.incoming[focusedPageId]`; Tap = `onFocus(quelle)`.
       Leer → Hinweis „Startseite / wird über ‚Zur Startseite' erreicht".
     - **Mitte:** Seitenname + Button-Chips der Seite (read-only Anzeige; aktiv + Label).
     - **Führt zu:** Karten aus `graph.outgoing[focusedPageId]` (Zielseiten);
       Tap auf Karte = `onFocus(ziel)` (re-zentrieren, NICHT verlassen).
   - Action „Diese Seite im Raster bearbeiten" → `onEditPageInGrid(focusedPageId)`.

**Acceptance / Review-Checkliste Phase 1**
- [ ] Screen über Seitenübersicht erreichbar, Security greift wie bei `page_editor`.
- [ ] Baum baut sich aus echten Daten; Referenz-Knoten klappen nicht weiter auf.
- [ ] Tap im Baum UND auf „führt zu"/„kommt von" verschiebt den Fokus, ohne den Screen zu verlassen.
- [ ] `focusedPageId` übersteht Rotation (`rememberSaveable`).
- [ ] Keine Schreiboperation in dieser Phase (reines Navigieren/Anschauen).
- [ ] Phone- und Tablet-Layout funktionieren.

---

### Phase 2 — Buttons verschieben (Drag&Drop im Fokus-Canvas)

Schreibende Drag&Drop-Operation, beschränkt auf die Fokus-Ansicht.

- In `StructureFocusCanvas`: Button-Chips der Mitte werden `DraggableChip`; Drop-Ziele sind die
  „führt zu"-Karten (Zielseiten) **und** eine optionale „zurück zur Mitte"-Zone.
- Wiederverwendung des bewährten Bounds-Trackings (`onGloballyPositioned` + `boundsInRoot` +
  Drag-Overlay) aus `BulkReorderDialog`. Mechanik 1:1 übernehmen, aber:
- **Wichtige Verhaltensänderung ggü. Dialog:** Moves werden **sofort beim Drop angewandt**
  (nicht erst per „Anwenden"-Button), denn beim Fokuswechsel gingen Pending-Moves sonst verloren.
  → Drop ruft `gridEditorViewModel.moveButtonToPage(focusedPageId, fromIndex, zielPageId, forceMove=false) { ok -> ... }`.
- Nach erfolgreichem Move: `Snackbar` mit Aktion „Rückgängig" → `gridEditorViewModel.undo { ... }`
  (Muster wie `PageEditorScreen` Magic-Cleanup-Snackbar).
- `forceMove`-Fall (Zielseite voll) abfangen: `onResult(false)` → Snackbar „Zielseite ist voll –
  Raster vergrößern oder anders platzieren?".
- Da der Graph aus `unfilteredPages` abgeleitet ist, aktualisieren sich Baum & Canvas nach dem
  Move automatisch über den StateFlow.

**Acceptance / Review-Checkliste Phase 2**
- [ ] Drag eines Button-Chips auf eine „führt zu"-Karte verschiebt ihn dorthin (DB-persistiert).
- [ ] Undo stellt den vorigen Zustand her.
- [ ] Volle Zielseite → klare Meldung statt stillem Fehlschlag.
- [ ] Es kann NUR im Fokus-Canvas gezogen/gedroppt werden, NICHT auf den Baum.
- [ ] Nach Move sind Baum + Canvas konsistent (keine veraltete Ableitung).

---

### Phase 3 — Verknüpfungen anlegen/entfernen & Orphans verbinden

Strukturbearbeitung an den Kanten selbst.

1. **Zielseite hinzufügen (Kante anlegen)** — Logik aus `BulkReorderDialog` (`:368-411`) übernehmen:
   - „+ Zielseite verbinden" → `NavigationActionFields` zur Auswahl.
   - Erzeugt `NavigateToPageButtonAction(pageId)`-Button in der **ersten freien Zelle** der
     fokussierten Seite (`firstFreeIndex`), Label = Zielseitenname, Cue „Öffne X",
     via `gridEditorViewModel.insertButtonConfig(focusedPageId, targetIndex, newConfig, false) {}`.
   - Vollständig belegte Seite → Meldung (siehe Phase 2).
2. **Kante entfernen** — in „führt zu"-Karte ein „×":
   - Index des zugehörigen Nav-Buttons aus `graph.outgoing[focusedPageId]` (`sourceButtonIndex`).
   - Bestätigungsdialog („Navigation zu X entfernen? Der Button wird gelöscht.").
   - `gridEditorViewModel.updateButtonConfig(focusedPageId, sourceButtonIndex, null)` + Undo-Snackbar.
3. **Orphan verbinden** — im Baum-Bereich „Nicht verbunden":
   - Eintrag antippen → „mit aktueller Seite verbinden?" → erzeugt Nav-Button auf
     `focusedPageId` (wie 3.1). Orphan-Liste aktualisiert sich automatisch.

**Acceptance / Review-Checkliste Phase 3**
- [ ] Neue Verknüpfung landet als Nav-Button in erster freier Zelle, Auto-Label „Öffne X".
- [ ] „×" entfernt genau den richtigen Nav-Button (korrekter Index), mit Bestätigung + Undo.
- [ ] Verbundener Orphan verschwindet aus „Nicht verbunden" und taucht als Kante auf.
- [ ] Kein Doppel-Anlegen, wenn die Kante bereits existiert.

---

### Phase 4 — Skalierungs-/Politur-Feinschliff (optional)

- [ ] Suche/Filter im Baum-Navigator (Seitenname).
- [ ] Hub-Seiten: „führt zu" scrollbar + collapsible bei vielen Zielen.
- [ ] „kommt von" bei sehr hohem Eingangsgrad zusammenfassen („+40 Seiten").
- [ ] Graph-Index memoisieren (nur bei `pages`-Änderung neu bauen — via `remember`/`derivedStateOf` bereits gegeben; prüfen).
- [ ] Rücksprung aus Raster-Editor behält `focusedPageId`.

### Phase 5 — Vereinheitlichung (optional, später)

- [ ] `BulkReorderDialog` und `StructureFocusCanvas` teilen sich die Drag&Drop-Bausteine
      (gemeinsame Composables im `bulkreorder`/`structure`-Package).
- [ ] Ggf. den modalen „Organisieren"-Einstieg im Raster-Editor auf den neuen Screen umlenken.

---

## 6. Bewusst NICHT in diesem Plan

- Frei platzierbare Mindmap mit gespeicherten x/y-Koordinaten (würde Schema-Migration +
  Sync-Auswirkung bedeuten → bewusst ausgeschlossen, der Baum wird berechnet).
- Globaler All-Seiten-Graph mit gezeichneten Linien.
- Änderungen am `Page`-Datenmodell oder an der Sync-Schicht.
- Umbau des handgerollten Drag-Trackings auf eine Bibliothek (erst falls es sich als instabil erweist).

## 7. Querschnittliche Risiken & Review-Schwerpunkte

- **Datenquelle:** Graph MUSS aus `unfilteredPages` kommen (diese tragen `buttonConfigs`), nicht
  aus `allPages` (gefiltert). Prüfen.
- **Index-Korrektheit bei Kante entfernen:** `sourceButtonIndex` muss der echte Index in
  `buttonConfigs` sein (inkl. `null`-Lücken), nicht der Index einer gefilterten Liste.
- **Sofort-Anwenden + Undo** ist die zentrale Verhaltensänderung ggü. dem Dialog — im Review
  gezielt auf Konsistenz (StateFlow-Aktualisierung) und Undo-Korrektheit achten.
- **Performance:** Bei jedem `pages`-Emit wird der Graph neu gebaut – bei 181 Seiten unkritisch,
  aber sicherstellen, dass nicht pro Composable-Recomposition gebaut wird (`remember(pages,...)`).
- **DAG/Zyklen:** Baum darf nicht endlos rekursieren – durch Phase-0-Tests abgesichert.

## 8. Teststrategie

- **Unit:** `BookNavigationGraphTest` (Phase 0) – Pflicht, da reine Logik.
- **Manuell pro Phase** an einem echten Buch (126 verwendete / 181 gesamt Seiten):
  Navigieren, Verschieben, Verbinden, Trennen, Orphan-Anbindung, Undo, volle Seite.
- Bestehender Raster-Editor und `BulkReorderDialog` müssen unverändert weiterfunktionieren
  (Regressionscheck).
