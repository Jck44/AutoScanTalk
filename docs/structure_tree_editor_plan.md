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

### Phase 4 — Skalierung & Politur (für 181-Seiten-Bücher)

Ziel: Der Editor muss bei echten Buchgrößen (126 verwendete / 181 gesamt, Startseite = Hub mit
hohem Ausgangsgrad) übersichtlich und flüssig bleiben. Reine UI-Ergänzungen, keine neuen
Schreiboperationen, keine Datenmodell-Änderungen.

#### AP4.1 — Suche/Filter im Baum-Navigator

- In `StructureTreeNavigator` oben ein `OutlinedTextField` (Such-Icon, Clear-Icon) — Muster aus
  `PageListScreen` (dortige Such-`OutlinedTextField`), aber mit **lokalem** State:
  `var query by rememberSaveable { mutableStateOf("") }`.
- Verhalten bei nicht-leerem `query`:
  - **Flache Trefferliste statt Baum**: alle Seiten (aus `pageNames`/`graph.allPageIds`), deren Name
    `query` enthält (case-insensitive), als flache, antippbare Liste. Tap = `onFocus(id)`.
  - Bei leerem `query`: bestehende Baum-/Orphan-Ansicht unverändert.
- Treffer-Markierung optional (fett); kein Aufwand für Highlighting nötig.
- Neue Strings: `structure_search` (Placeholder), `structure_search_no_results`.

**Review-Checkliste AP4.1**
- [ ] Filtern ist rein lokal (kein ViewModel/Flow), übersteht Rotation.
- [ ] Tap auf Treffer fokussiert die Seite; Suche bleibt stehen (oder wird bewusst geleert — dokumentieren).
- [ ] Leeres Feld → exakt die bisherige Baum-Ansicht.

#### AP4.2 — Hub-Entlastung: „führt zu" einklappbar

Die Startseite hat viele ausgehende Kanten → die `FlowRow` in `StructureFocusCanvas` wird sehr hoch.

- Schwellwert `MAX_VISIBLE_TARGETS = 12` (Konstante). Bei `outgoingEdges.size > 12`:
  - nur die ersten 12 Chips rendern,
  - darunter ein `TextButton` „+N weitere anzeigen" / „Weniger anzeigen", State
    `var showAllTargets by rememberSaveable(focusedPageId) { mutableStateOf(false) }`
    (Reset bei Fokuswechsel, damit man nicht auf jeder Hub-Seite alles aufgeklappt hat).
- **Wichtig:** Der Schwellwert betrifft nur die Anzeige. Beim Drag&Drop muss jede Zielkarte ein
  gültiges Drop-Ziel bleiben — wenn eingeklappt, kann nur auf die sichtbaren 12 gedroppt werden;
  das ist akzeptabel (Nutzer klappt vorher auf). Im Review prüfen, dass eingeklappte Ziele KEINE
  veralteten `targetBounds`-Einträge hinterlassen (Map ist bereits `remember(focusedPageId)`-scoped).
- Neue Strings: `structure_show_more` (`%1$d`), `structure_show_less`.

**Review-Checkliste AP4.2**
- [ ] Seite mit ≤12 Zielen sieht unverändert aus.
- [ ] „+N weitere" klappt auf/zu; Zustand resettet bei Fokuswechsel.
- [ ] Drop funktioniert auf alle aktuell sichtbaren Ziele; keine stale Drops auf eingeklappte.

#### AP4.3 — „kommt von" zusammenfassen

Analog für hohen Eingangsgrad (z. B. eine Seite, die von vielen erreicht wird):

- Schwellwert `MAX_VISIBLE_SOURCES = 12`. Bei mehr: erste 12 `InputChip`s + „+N weitere" zeigt den Rest.
- Kein Drag&Drop hier (eingehend ist read-only/Navigation), daher unkritisch.

**Review-Checkliste AP4.3**
- [ ] Lange „kommt von"-Liste wird zusammengefasst, Rest per Toggle sichtbar.

#### AP4.4 — Fokus übersteht Rücksprung aus dem Raster-Editor

Beim „Im Raster-Editor öffnen" wird `page_editor/{id}` auf den Back-Stack gelegt; `structure_editor`
bleibt liegen. `focusedPageId` ist `rememberSaveable` → sollte beim Zurück erhalten bleiben.

- **Verifizieren** (vermutlich bereits korrekt): Fokus auf Seite X → Raster öffnen → bearbeiten →
  zurück → Fokus weiterhin X.
- Sicherstellen, dass der `LaunchedEffect(initialFocusedId, pages)`-Reparatur-Effekt den Fokus NUR
  bei gelöschter/leerer Seite zurücksetzt (aktuell korrekt) — nach Button-Edit darf er NICHT auf die
  Wurzel springen.

**Review-Checkliste AP4.4**
- [ ] Round-Trip Struktur→Raster→zurück behält `focusedPageId`.
- [ ] Nach Button-Edit im Raster kein ungewollter Sprung zur Startseite.

#### AP4.5 — Performance-Verifikation (kein Umbau erwartet)

- Bestätigen, dass der Graph nur bei `pages`/`startPageId`-Änderung neu gebaut wird
  (`remember(pages, startPageId)` in `StructureEditorScreen` — bereits gegeben), und
  `buildTree()`/`orphans()` im Navigator `remember(graph)`-memoisiert sind (bereits gegeben).
- An einem echten 181-Seiten-Buch grob gegentesten: Öffnen, Scrollen, Fokuswechsel flüssig.
- Falls ruckelig: prüfen, ob `onMoveButton` (nicht `remember`t, siehe Phase-2-Review) unnötige
  Recompositions auslöst — ggf. `remember`n. Sonst keine Änderung.

**Review-Checkliste AP4.5**
- [ ] Kein Graph-Neuaufbau pro Recomposition.
- [ ] 181-Seiten-Buch fühlt sich flüssig an.

---

### Phase 5 — Vereinheitlichung von Drag&Drop & Einstieg

Ziel: Die zweifach vorhandene, handgerollte Drag&Drop-Mechanik (in `BulkReorderDialog` und
`StructureFocusCanvas`) in einen gemeinsamen, wiederverwendbaren Baustein ziehen und den
„Organisieren"-Einstieg konsolidieren. **Riskanteste Phase**, weil sie getesteten Code anfasst —
daher strikt verhaltenserhaltend + Smoke-Tests beider Oberflächen.

#### AP5.1 — Gemeinsamen Drag&Drop-Baustein extrahieren

Die Duplikation umfasst: Drag-State (`draggedId`, `dragStartCenter`, `dragOffset`, `draggedSize`,
`rootBoxBounds`, `targetBounds`, abgeleitet `dragGlobalPos`), das Hit-Testing beim Drop und das
schwebende Drag-Overlay.

Neue Datei `app/.../ui/components/dragdrop/ChipDragDropState.kt`:

```kotlin
class ChipDragDropState internal constructor() {
    var draggedKey by mutableStateOf<String?>(null); internal set
    internal var draggedLabel by mutableStateOf("")
    internal val targetBounds = mutableMapOf<String, Rect>()
    // dragStartCenter, dragOffset, draggedSize, rootBoxBounds ...
    val dragGlobalPos: Offset get() = dragStartCenter + dragOffset

    fun onDragStart(key: String, label: String, center: Offset, size: Offset) { ... }
    fun onDrag(delta: Offset) { ... }
    /** liefert den getroffenen Ziel-Key (oder null) und setzt den State zurück */
    fun onDragEnd(): String? { ... }
    fun onDragCancel() { ... }
    fun clearTargets() { targetBounds.clear() }   // bei Fokus-/Kontextwechsel aufrufen
}

@Composable fun rememberChipDragDropState(resetKey: Any?): ChipDragDropState
// resetKey (z.B. focusedPageId) -> remember(resetKey){...} + clearTargets bei Wechsel

// Modifier: registriert/aktualisiert die Bounds eines Drop-Ziels
fun Modifier.chipDropTarget(state: ChipDragDropState, key: String): Modifier

// Root-Container, der rootBoxBounds erfasst und das Overlay rendert
@Composable fun ChipDragDropContainer(state: ChipDragDropState, modifier: Modifier, content: @Composable BoxScope.() -> Unit)
```

- `DraggableChip` bleibt unverändert und wird weiter genutzt; der State-Holder kapselt nur die
  Koordinaten-/Overlay-Logik. Das in Phase 2 gefixte „Bounds bei Fokuswechsel leeren" wird hier
  zur `clearTargets()`/`resetKey`-Mechanik (eine Quelle der Wahrheit).
- Lage in `app` (beide Nutzer liegen in `app`); kein core-Modul nötig.

**Review-Checkliste AP5.1**
- [ ] Reiner Extraktions-Baustein, keine Verhaltensänderung an sich.
- [ ] `resetKey` leert `targetBounds` zuverlässig (kein stale-Drop-Bug, vgl. Phase-2-Fix).

#### AP5.2 — `StructureFocusCanvas` auf den Baustein umstellen

- Lokale Drag-Felder durch `rememberChipDragDropState(focusedPageId)` ersetzen, Zielkarten mit
  `Modifier.chipDropTarget(state, edge.targetPageId)`, Drop über `state.onDragEnd()`.
- Verhalten 1:1 erhalten (Sofort-Move + Undo + TargetFull).

**Review-Checkliste AP5.2**
- [ ] Move/Undo/Voll-Fall verhalten sich identisch zu Phase 2/3.

#### AP5.3 — `BulkReorderDialog` auf den Baustein umstellen

- Den Block in [BulkReorderDialog.kt:112-120](app/src/main/java/com/andreas_kratzer/ghosttalk/ui/pages/bulkreorder/BulkReorderDialog.kt)
  (manuelles Bounds-/Drag-Tracking) und das Drag-Overlay (`:435-465`) durch den Baustein ersetzen;
  `CurrentPageCard`/`TargetCategoryItem` als Drop-Ziele über `chipDropTarget` registrieren.
- **Verhaltenserhaltend**: Der Dialog sammelt Moves bis „Anwenden" (`categoryProposals`), während der
  Canvas sofort anwendet — diese Logik bleibt im jeweiligen Aufrufer; nur Drag-Mechanik wird geteilt.
- Bestehende Funktion penibel gegenprüfen (chip von „Aktuelle Seite" → Zielseite, zurückziehen ins
  „Unassigned", „+ Zielseite hinzufügen").

**Review-Checkliste AP5.3**
- [ ] Alle bisherigen Dialog-Interaktionen funktionieren unverändert (manueller Smoke-Test).
- [ ] Kein Regress bei „Anwenden" / abgebrochenem Drag.

#### AP5.4 — Deep-Link: Struktur-Editor mit initialem Fokus

Voraussetzung für AP5.5.

- Route erweitern: `structure_editor?focus={pageId}` (optionales `navArgument`, `nullable=true`).
- `StructureEditorScreen` bekommt `initialFocusPageId: String? = null`; falls gesetzt und in `pages`
  vorhanden, wird `focusedPageId` initial darauf gesetzt (vor der bestehenden Fallback-Logik).
- Bestehender Einstieg aus der Seitenübersicht ruft weiter ohne Argument auf (Fokus = Startseite).

**Review-Checkliste AP5.4**
- [ ] Aufruf ohne Argument unverändert (Startseite im Fokus).
- [ ] Aufruf mit `?focus=X` startet auf X (sofern existent), sonst sauberer Fallback.

#### AP5.5 — „Organisieren"-Einstieg konsolidieren

- In [PageEditorScreen.kt:232](app/src/main/java/com/andreas_kratzer/ghosttalk/ui/pages/PageEditorScreen.kt)
  den `BulkReorderDialog`-Aufruf optional ersetzen durch Navigation zu
  `structure_editor?focus={currentPageId}`.
- **Entscheidung für später (nicht blind umsetzen):** ob `BulkReorderDialog` danach entfernt wird.
  Empfehlung: erst beide Wege koexistieren lassen, nach positivem Caregiver-Feedback zum Struktur-
  Editor den Dialog in einem separaten Schritt entfernen (eigener Commit, damit reviewbar/reverttbar).

**Review-Checkliste AP5.5**
- [ ] „Organisieren" im Raster-Editor öffnet den Struktur-Editor fokussiert auf die aktuelle Seite.
- [ ] Falls Dialog entfernt wird: keine toten Referenzen/Strings; Security-Verhalten unverändert.

> Reihenfolge-Hinweis für Gemini: AP5.1 → AP5.2 → AP5.3 (jeweils kompilieren + Smoke-Test), dann
> AP5.4 → AP5.5. Phase 5 nicht in einem einzigen Commit erschlagen — pro AP committen, damit der
> Review (und ggf. ein Revert) handhabbar bleibt.

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
