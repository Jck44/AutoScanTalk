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

### Phase 5 — Einstieg konsolidieren & `BulkReorderDialog` entfernen

**Entscheidung (2026-06-13):** `BulkReorderDialog` wird **sofort entfernt** (nicht gestaffelt).
Der „Organisieren"-Einstieg im Raster-Editor wird auf den neuen Struktur-Editor (fokussiert auf die
aktuelle Seite) umgeleitet.

> Konsequenz: Die ursprünglich geplante Vereinheitlichung der Drag&Drop-Mechanik (gemeinsamer
> `ChipDragDropState`-Baustein) **entfällt** — nach dem Entfernen des Dialogs gibt es nur noch
> *einen* Drag&Drop-Konsumenten (`StructureFocusCanvas`), also nichts mehr zu vereinheitlichen.
> Die Drag-Logik bleibt dort, wo sie ist.

#### AP5.1 — Deep-Link: Struktur-Editor mit initialem Fokus

Voraussetzung für den umgeleiteten Einstieg.

- Route erweitern: `structure_editor?focus={pageId}` (optionales `navArgument`, `nullable=true`,
  `defaultValue=null`) in `GhosTTalkNavHost`. Bestehende `composable("structure_editor")` durch die
  parametrisierte Route ersetzen; `backStackEntry.arguments?.getString("focus")` auslesen.
- `StructureEditorScreen` bekommt `initialFocusPageId: String? = null`. Initialisierung von
  `focusedPageId`: `initialFocusPageId?.takeIf { id -> pages.any { it.id == id } }` **vor** der
  bestehenden Startseiten-/`orderIndex`-Fallback-Logik. Der `LaunchedEffect`-Reparaturpfad bleibt
  unverändert (greift nur bei leer/gelöscht).
- Einstieg aus der Seitenübersicht (`onOpenStructureEditor`) ruft weiterhin ohne `?focus=` auf
  → Fokus = Startseite wie bisher.

**Review-Checkliste AP5.1**
- [ ] Aufruf ohne Argument unverändert (Startseite im Fokus).
- [ ] Aufruf mit `?focus=X` startet auf X (sofern existent), sonst sauberer Fallback.
- [ ] Rotation/Restore: Fokus bleibt erhalten (rememberSaveable greift weiterhin).

#### AP5.2 — „Organisieren"-Einstieg auf den Struktur-Editor umlenken

- `StructureEditorScreen`-Aufruf in `GhosTTalkNavHost` (parametrisierte Route) durchreichen.
- `PageEditorScreen` bekommt einen neuen Callback `onOpenStructureEditor: (pageId: String) -> Unit`;
  in `GhosTTalkNavHost` verdrahten auf `navController.safeNavigate("structure_editor?focus=$pageId")`.
- In [PageEditorScreen.kt:232](app/src/main/java/com/andreas_kratzer/ghosttalk/ui/pages/PageEditorScreen.kt)
  den bisherigen Menüpunkt „Organisieren (Bulk Reorder)" so umbauen, dass er
  `onOpenStructureEditor(page.id)` aufruft (Label beibehalten oder auf `structure_editor_open`
  vereinheitlichen).

**Review-Checkliste AP5.2**
- [ ] „Organisieren" im Raster-Editor öffnet den Struktur-Editor fokussiert auf die aktuelle Seite.
- [ ] Security-Verhalten unverändert (Route ist bereits unter `isSecurityRequiredForEdit`).

#### AP5.3 — `BulkReorderDialog` + Dead Code entfernen

Erst NACH AP5.2 (sonst kompiliert PageEditor nicht). Konkrete Streichliste (per grep verifiziert):

- **Datei löschen:** `app/.../ui/pages/bulkreorder/BulkReorderDialog.kt`
  (enthält auch `ReorderCategory` / `ReorderButtonItem`).
- **`PageEditorScreen.kt`:** entfernen — Import (`:47`), `var showBulkReorderDialog` (`:91`),
  der `if (showBulkReorderDialog) { BulkReorderDialog(...) }`-Block (`:425-445`); der Menüpunkt ist
  in AP5.2 bereits umgebaut.
- **`GridEditorViewModel.kt`:** `fun executeBulkMove(...)` (`:181-185`) und der Import
  `...bulkreorder.ReorderCategory` (`:16`) werden tot → entfernen.
- **`PageManagementDelegate.kt`:** `fun moveButtonsToPages(...)` (`:425`) wird tot (nur von
  `executeBulkMove` genutzt) → entfernen. (Der ausführliche Sequentiell-Kommentar entfällt mit.)
- **Strings:** Die hartcodierten Dialog-Texte verschwinden mit der Datei. Prüfen, ob durch das
  Entfernen verwaiste String-Keys entstehen (vermutlich keine, da der Dialog Texte inline hatte).
- Abschließend `:app:compileDebugKotlin` → es dürfen **keine** ungenutzten Referenzen/Imports
  übrigbleiben (Compiler-Warnings beachten).

**Review-Checkliste AP5.3**
- [ ] `BulkReorderDialog.kt` gelöscht, kein verbleibender Import/Aufruf irgendwo (`grep`).
- [ ] `executeBulkMove` + `moveButtonsToPages` entfernt, keine toten Referenzen.
- [ ] App kompiliert ohne neue Warnings zu ungenutztem Code.
- [ ] Raster-Editor öffnet/funktioniert weiterhin; „Organisieren" führt zum Struktur-Editor.

> Reihenfolge-Hinweis für Gemini: **AP5.1 → AP5.2 → AP5.3**, jeweils einzeln committen
> (kompilieren nach jedem Schritt). AP5.3 erst, wenn AP5.2 den Einstieg ersetzt hat — sonst bricht
> der Build. Nicht in einem einzigen Commit erschlagen, damit Review und ggf. Revert handhabbar bleiben.

#### AP5.4 — Magic Wizard auf den gemeinsamen Drag&Drop-Baustein umstellen

Hintergrund: `PageSplitWizardDialog` enthält eine **dritte** handgerollte Drag&Drop-Kopie
(eigene `WizardButtonItem`/`WizardCategory`, `boundsInRoot`-Tracking, Drag-Overlay). Nach AP5.3
ist der `BulkReorderDialog` weg → `ChipDragDropState` hat mit Canvas + Wizard **zwei echte
Konsumenten**, was die Extraktion rechtfertigt. (Korrigiert die frühere Annahme „nur ein Konsument".)

- Internes Drag&Drop in `PageSplitWizardDialog` durch `rememberChipDragDropState()` /
  `Modifier.chipDropTarget(state, key)` / `ChipDragDropContainer` ersetzen (wie beim Canvas).
- Drag-Key = `WizardButtonItem.buttonId`; Drop-Ziele = Kategorien (`key = category.name`).
- **Modal bleibt**, `onConfirm(PageSplitProposal)` unverändert — rein verhaltenserhaltend.

**Review-Checkliste AP5.4**
- [ ] Buttons zwischen Kategorien verschieben verhält sich identisch.
- [ ] Keine eigene Bounds-/Overlay-Logik mehr im Wizard (dritte Kopie entfernt).
- [ ] `onConfirm` liefert dieselbe `PageSplitProposal`-Struktur.

---

### Phase 6 — KI-Seiten-Split in den Struktur-Editor einbetten

Ziel: Den modalen Magic-Wizard durch einen **„Vorschlags-Modus" im Struktur-Editor** ersetzen — die
KI-Kategorien werden zu **provisorischen Zielzonen** im Fokus-Canvas, in die man Buttons mit demselben
Drag&Drop-Paradigma einsortiert; „Übernehmen" ruft das bestehende `applyPageSplit`. Eigene Phase,
eigene Commits. **Voraussetzung: AP5.4** (Wizard nutzt dann bereits den Baustein → DnD-Zonen-Logik
ist wiederverwendbar).

**Kernhürde:** provisorische Ziele — die Kategorien sind noch *keine* echten Seiten und dürfen
NICHT in `pages`/`BookNavigationGraph` gelangen, bis bestätigt wird.

#### AP6.1 — Vorschlags-Modus & Trigger
- „✨ Vorschlag"-Aktion im Canvas (für die fokussierte Seite). Den bestehenden
  `PageSplitOptInDialog` (Cloud/Token-Warnung) vorschalten — **nicht** umgehen.
- `pageSplitViewModel.generatePageSplitProposal(focusedPageId)` → `PageSplitProposal` in lokalem
  Screen-State halten (`proposal: PageSplitProposal?`), nicht persistieren.

#### AP6.2 — Provisorische Zielzonen im Canvas
- Bei aktivem Proposal zusätzlich zu den echten „führt zu"-Zielen die Kategorien als
  **provisorische Drop-Zonen** rendern (`chipDropTarget(state, key = "proposal:<catName>")`),
  visuell klar als Vorschlag markiert (z. B. gestrichelt).
- Drag eines Button-Chips in eine Kategorie → **lokale** Proposal-Mutation (kein DB-Write).
- Drop-Auswertung in `onDragEnd()`: Key-Präfix `proposal:` → Proposal anpassen statt `moveButtonToPage`.

#### AP6.3 — Übernehmen / Verwerfen
- „Übernehmen" → `pageSplitViewModel.applyPageSplit(focusedPageId, proposal)` (legt Unterseiten +
  Nav-Buttons an + verschiebt — **bestehende Logik wiederverwenden, nichts neu bauen**).
- „Verwerfen" → Proposal-State leeren. Danach aktualisiert sich Graph/Canvas automatisch über die Flows.

#### AP6.4 — Alten Wizard-Einstieg umlenken
- Im `PageLayoutAssistantDialog` den „Seiten-Split"-Einstieg auf den Vorschlags-Modus des
  Struktur-Editors umleiten (analog AP5.2, via `?focus=` + Modus-Flag).
- Danach kann `PageSplitWizardDialog` entfernt werden (separater Commit). Die **Nicht-DnD-Tools**
  des Assistenten (`magicCleanup`, `shrinkGridToMinimum`, `insertHomeNavigationEveryX`,
  `reorderByClickStats`, `deleteDeactivatedButtons`) bleiben im Assistenten — sie sind One-Shot-
  Aktionen, kein Drag&Drop.

**Review-Schwerpunkte Phase 6**
- [ ] Provisorische Ziele tauchen NICHT im Graph/Baum/`pages` auf, bis „Übernehmen".
- [ ] Opt-in/Token-Pfad wird nicht umgangen.
- [ ] `applyPageSplit` unverändert wiederverwendet; Ergebnis identisch zum alten Wizard.
- [ ] Drop unterscheidet sauber echte Ziele (`moveButtonToPage`) von provisorischen (`proposal:`).

**Bewusst NICHT in Phase 6:** die übrigen Assistent-One-Shot-Tools in den Editor holen (separat,
falls überhaupt gewünscht).

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
