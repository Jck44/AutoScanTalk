# Struktur-Editor — Gesamtplan (Suche · Buchweiter Graph · Split · Orphans/Dead-Ends)

> Übergreifender Plan, der vier Vorhaben in eine sinnvolle Reihenfolge bringt.
> **Arbeitsteilung:** Gemini setzt phasenweise um, Claude reviewt jede Phase einzeln.
> Jede Phase ist ein abgeschlossenes, separat reviewbares Paket.

## Ausgangslage (Ist-Stand, recherchiert 2026-06-16)

**Domäne (`core`-Modul) — `BookNavigationGraph.kt`**
- Hat bereits: `outgoing`, `incoming`, `allPageIds`, `reachablePageIds()`, **`orphans()`** (von Startseite nicht erreichbar) und **`buildTree()`** (volle Buchtiefe inkl. Zyklen-Erkennung via `isReference`).
- Test-gedeckt: `core/.../BookNavigationGraphTest.kt`.
- **Fehlt noch:** Dead-End-Erkennung (Seiten ohne ausgehende Nav-Kante) und Cluster/Components für ein buchweites Layout.

**UI (`app/.../ui/pages/structure/`)** — es gibt bereits **zwei** Darstellungen:
- `StructureFocusCanvas` → `StructureGraphView` (780 LOC): **fokus-basiert** — eine Seite zentriert, eingehende/ausgehende Nachbarn drumherum. Routing der Linien ist bewusst gestaffelt (siehe Memory „Struktur-Graph-Routing" — nicht begradigen).
- `StructureTreeNavigator` (407 LOC): buchweite **Baum-Liste** (nutzt `buildTree()`).
- `StructureEditorScreen.kt` (**1352 LOC**): Host/Scaffold, State-Verkabelung, alle Dialoge, Toolbar, Mode-Umschaltung (`StructureViewMode { CARDS, GRAPH }`).

**Suche (Ist):** `ListUtils.filterAndSort` filtert **nur Seitennamen** (`it.name.contains`). Button-Labels werden nicht durchsucht.

**Erledigt / nicht Teil dieses Plans:** Undo/Redo ist bereits committet.

---

## Reihenfolge & Begründung

> Fundament legen → Platz schaffen → billiger Mehrwert/Vertrauen → Navigations-Primitive → großes Feature obendrauf.

| Phase | Inhalt | Warum hier | Risiko |
|------|--------|-----------|--------|
| **0** | Domänen-Fundament (`core`): `deadEnds()`, Cluster | Rein logisch, test-bar, speist Phase 2 + 4, kein UI-Risiko | Sehr gering |
| **1** | `StructureEditorScreen` splitten (reiner Refactor) | Entsperrer — Phase 2 & 4 fassen denselben Screen an | Gering (Verhalten unverändert) |
| **2** | Verwaiste Seiten & Dead-Ends sichtbar machen | Billiger Mehrwert auf frisch geteiltem Code, nutzt Phase 0 | Gering |
| **3** | Globale Suche (Seiten **+ Button-Labels**) | Liefert das „Springen"-Primitive, das Phase 4 braucht | Mittel |
| **4** | Buchweiter Zoom-Graph („Übersicht") | Größtes/riskantestes Stück, baut auf 0–3 | Hoch |

---

## Phase 0 — Domänen-Fundament (`core`)

**Ziel:** Alle Graph-Auswertungen, die Phase 2 + 4 brauchen, rein in der Domäne + test-gedeckt.

**Aufgaben**
1. `BookNavigationGraph.deadEnds(): List<String>` — Seiten in `allPageIds`, die **keine** ausgehende Nav-Kante haben (`outgoing[id].isNullOrEmpty()`). Bewusst getrennt von `orphans()`.
2. `BookNavigationGraph.connectedComponents(): List<Set<String>>` — schwach zusammenhängende Komponenten (für Layout abgekoppelter Cluster in Phase 4). Optional, kann auch erst zu Phase 4 gezogen werden.
3. Tests in `BookNavigationGraphTest.kt`: Dead-End (Seite ohne Buttons / nur Nicht-Nav-Buttons), Mischfälle (Seite ist gleichzeitig orphan **und** dead-end), Zyklus ohne Dead-End.

**Akzeptanz:** Nur `core`, keine UI-Änderung, alle Tests grün.

---

## Phase 1 — `StructureEditorScreen` splitten (reiner Refactor)

**Ziel:** Die 1352-LOC-Datei aufteilen, **ohne Verhaltensänderung**, mit klarer Naht für die spätere dritte Ansicht.

**Vorschlag Schnitt** (`app/.../ui/pages/structure/`)
- `StructureEditorScreen.kt` — nur noch Host/Scaffold + State-Verkabelung.
- `StructureEditorContent.kt` — die Mode-Umschaltung (CARDS / GRAPH / TREE). **Das ist die Naht, an der Phase 4 die `OVERVIEW`-Ansicht einhängt.**
- `StructureEditorTopBar.kt` — Toolbar + Overflow-Menü.
- `StructureEditorDialogs.kt` — alle Dialoge (ButtonConfig, IncomingReferences, TargetPageSelection, PageSplit*-Dialoge …).
- `rememberStructureEditorState.kt` — State-Holder (`focusedPageId`, `focusHistory`, `navigateToPage`, lokale Namens-Edits).

**Regeln**
- Keine Verhaltensänderung, keine neuen Features. Reine Bewegung von Code + Hochziehen von Parametern.
- Alle drei bestehenden Ansichten (Cards/Graph/Tree) müssen pixelgleich funktionieren.
- Keine neuen Public-APIs außer datei-internen Composables.

**Akzeptanz:** Build grün, manueller Smoke-Test aller drei Ansichten + Dialoge identisch, Diff besteht im Wesentlichen aus Verschiebungen.

---

## Phase 2 — Verwaiste Seiten & Dead-Ends sichtbar machen

**Ziel:** `orphans()` (vorhanden) + `deadEnds()` (Phase 0) im UI nutzbar machen.

**Aufgaben**
1. **Markierung** an Knoten in Fokus-Graph **und** Tree: z. B. ⚠ „nicht erreichbar" (orphan), ⛔ „Sackgasse" (dead-end). Memoized über `graph`, kein Re-Compute pro Frame.
2. **„Probleme"-Panel/Filter:** Liste der betroffenen Seiten; Tippen fokussiert die Seite (nutzt vorhandene `navigateToPage`).
3. **Optional Quick-Fix:** „Mit Startseite verbinden" / „Verbindung hinzufügen" (nutzt bestehende Connect-Logik in `StructureEditorScreen`, Zeile ~366 ff.).

**Akzeptanz:** Erkennung korrekt (durch Phase-0-Tests gedeckt), Markierung in beiden vorhandenen Ansichten, kein spürbarer Performance-Hit bei großen Büchern.

---

## Phase 3 — Globale Suche (Seiten + Button-Labels)

**Ziel:** Über das ganze Buch suchen — auch nach Button-Beschriftungen — und zur Trefferseite springen.

**Aufgaben**
1. **`core`: `SearchPagesUseCase`** — Treffer-Modell `PageSearchResult(pageId, matchedOnName: Boolean, buttonHits: List<ButtonHit(index, label)>)`. Sucht in `page.name` **und** `page.buttonConfigs[].label` (nur aktive, `ignoreCase`). Test-gedeckt.
2. **Wiederverwendbare Such-Komponente** (Suchfeld + Trefferliste), nutzbar im Struktur-Editor und perspektivisch in `PageListScreen`.
3. **Sprung:** Tippen auf Treffer fokussiert die Seite (im Struktur-Editor) bzw. öffnet sie (Liste); optional Button-Highlight.

> Hinweis: bestehende `pageViewModel.searchQuery` (nur Name) **nicht** aufbohren, sondern den neuen Use-Case daneben stellen, damit die schmale Listen-Filterung unverändert bleibt.

**Akzeptanz:** Label-Treffer werden gefunden, Sprung funktioniert, Tests grün.

---

## Phase 4 — Buchweiter Zoom-Graph („Übersicht")

**Ziel:** Eine **neue, dritte** Graph-Ansicht, die ALLE Seiten + Kanten gleichzeitig auf einem zoom-/pan-baren Canvas zeigt — **ohne** Fokus-Graph/Cards/Tree zu verändern.

**Aufgaben (Unterphasen)**
- **4a — Mode + Read-only-Layout:** `StructureViewMode` um `OVERVIEW` erweitern, in der Phase-1-Naht (`StructureEditorContent`) einhängen. Layout aus `buildTree()` + `connectedComponents()` (abgekoppelte Cluster separat platzieren). Gerichtete Kanten; Routing-Prinzip aus `StructureGraphView` wiederverwenden (gestaffelt, **nicht** begradigen — Memory).
- **4b — Zoom/Pan/Tap:** Pinch-Zoom + Pan; Tap = Seite fokussieren (zurück in Fokus-Graph) oder öffnen. Reduced-Motion respektieren (Memory: via `ANIMATOR_DURATION_SCALE`).
- **4c — Highlights + Suche:** Orphan/Dead-End-Markierung (Phase 2) und Suche (Phase 3) zentrieren/heben den Treffer im Overview hervor. Optional Minimap.
- **4d (optional) — Editier-Gesten:** Knoten verschieben / Verbindungen ziehen direkt im Overview (erst wenn 4a–c stabil).

**Regeln**
- Cards/Graph/Tree bleiben unangetastet — Overview ist additiv.
- Große Bücher: Layout memoized, Kanten-Zeichnung performant (Canvas, kein Recompose-Sturm).

**Akzeptanz:** Umschalten auf „Übersicht" zeigt das ganze Buch, Zoom/Pan flüssig, Sprung/Highlight funktioniert, andere Ansichten unverändert.

---

## Hand-off-Checkliste pro Phase (für Gemini)
- [ ] Nur den Scope der Phase anfassen.
- [ ] Bestehende Ansichten/Verhalten nicht brechen (Cards/Graph/Tree).
- [ ] Domänen-Logik test-gedeckt (`core`-Tests), wo zutreffend.
- [ ] `JAVA_HOME` = Android-Studio-JBR für Gradle (Memory „Build Java Home").
- [ ] Claude-Review vor Merge.
