# Plan: Struktur-Editor als durchgängiger semantischer Zoom (Global → Focused → Grid)

## Context

Der Editor besteht konzeptionell aus **drei Zoom-Stufen**, die heute aber als vier gleichrangige
Tabs präsentiert werden — mentales Modell und UI widersprechen sich.

| Zoom-Stufe | View heute | `(mode, structureView)` | Knoten / Inhalt |
|---|---|---|---|
| **L0 Global** | Übersicht | `STRUKTUR` + `OVERVIEW` | Seiten des ganzen Buchs |
| **L1 Focused** | Graph | `STRUKTUR` + `GRAPH` | Seite + 1-Hop-Nachbarn, Tasten-Chips |
| **L2 Grid** | Raster | `RASTER` | das echte Tastenraster einer Seite |

**Karten** (`STRUKTUR` + `CARDS`) ist **keine eigene Zoom-Stufe**, sondern eine alternative
Darstellung von L1 (gleiche Daten, Liste statt Graph). Es bleibt funktional erhalten, wird aber im
Zoom-Modell als optionaler **Stil-Umschalter innerhalb von L1** behandelt — nicht als vierter Tab.

**Kernbefund — der Zoom ist nur halb verdrahtet:**

- **L0 → L1 (rein):** existiert, aber als Zwei-Tap-Sonderfall — erst Tap fokussiert, ein zweiter Tap
  auf den *bereits fokussierten* Knoten zoomt rein (`StructureOverviewCanvas.kt:318-332`, Sprung über
  `onNavigateToGraph`).
- **L1 → L2 (rein):** **tot.** Der zentrale Knoten im Graph hat `onFocus = {}`
  (`StructureGraphView.kt:247`). Zum Raster kommt man nur über den Segmented-Switcher.
- **Raus-Zoom (L2→L1→L0):** existiert als Geste **gar nicht** — nur der flache 4er-Tab
  (`PageWorkbenchScreen.kt:44-100`).
- **Kontext (Suche, Multiselect)** geht beim Stufenwechsel teils verloren: Suche highlightet nur auf
  L0 (`matchingPageIds` nur an `StructureOverviewCanvas` übergeben, `StructureEditorContent.kt:210-222`);
  Selektion ist auf L0 unsichtbar; beides überlebt die L2-Grenze (`RASTER`) gar nicht, weil
  `StructureEditorState` beim Wechsel zu `PageEditorScreen` zerstört wird.

**Ziel:** Die drei Stufen zu *einem* durchgängigen Zoom-Raum machen — konsistente Rein-/Raus-Gesten,
mitwanderndem Kontext und räumlicher Übergangs-Animation. Deckt sich mit „P4 buchweiter Zoom-Graph"
aus dem `structure_editor_master_plan.md`.

Die vier Flow-Bausteine dieses Plans:

1. **Einheitliche Rein-Zoom-Geste** auf allen Stufen.
2. **Echte Raus-Zoom-Geste + Breadcrumb** statt vier flacher Tabs.
3. **Kontext (Suche + Multiselect) über den Zoom mittragen.**
4. **Räumliche Zoom-Animation** zwischen den Stufen.
5. **Kontextabhängige Legende** für die Warn-Symbole (⚠ Orphan / ⛔ Dead-End), eingeblendet nur wenn gerade sichtbar.

---

## Kernkonzept: eine Zoom-Achse als Single Source of Truth

Heute ist der Zustand auf zwei `rememberSaveable`-Variablen in `PageWorkbenchScreen` verteilt
(`mode: EditorMode`, `structureView: StructureViewMode`, Z. 37-38). Das macht „eine Stufe rein/raus"
umständlich. Wir führen eine **abgeleitete Zoom-Achse** ein, die beide kapselt.

```kotlin
// Neuer Typ, z. B. in PageWorkbenchScreen.kt oder eigener Datei ui/pages/ZoomLevel.kt
enum class ZoomLevel { GLOBAL, FOCUSED, GRID }
```

Mapping (eine Funktion, keine doppelte Wahrheit):

- `GLOBAL`  → `mode = STRUKTUR`, `structureView = OVERVIEW`
- `FOCUSED` → `mode = STRUKTUR`, `structureView = GRAPH` (oder `CARDS`, wenn Stil = Karten)
- `GRID`    → `mode = RASTER`

Zentrale Helfer in `PageWorkbenchScreen` (Single Source of Truth für Navigation zwischen Stufen):

```kotlin
fun zoomInto(pageId: String)   // fokussiert pageId UND geht eine Stufe tiefer
fun zoomOut()                  // geht eine Stufe höher, fokussierte Seite bleibt erhalten
fun setLevel(level: ZoomLevel) // direktes Anspringen (für Breadcrumb)
```

`focusedPageId` (bereits in `PageWorkbenchScreen`, Z. 39) bleibt die durchgehend mitgeführte
„aktuelle Seite" über alle Stufen.

---

## Bestehende Bausteine (Wiederverwendung)

- **`PageWorkbenchScreen`** (`ui/pages/PageWorkbenchScreen.kt`) — hostet bereits beide Host-Screens und
  den `modeSwitcher` (Z. 44-100). Hier wird die Zoom-Achse zentralisiert.
- **`focusedPageId`-Durchreichung** — `onFocusedPageChanged` (Z. 129) und `onNavigateToGraph`
  (Z. 132-135) verbinden L0/L1 schon mit dem Host.
- **`StructureEditorState`** (`ui/pages/structure/StructureEditorState.kt`) — `focusedPageId` (Z. 23),
  `focusHistory` + `goBackHistory()` (Z. 24/72), `navigateToPage()` (Z. 65), `searchQuery` (Z. 36),
  `isMultiSelectMode`/`selection` (Z. 50-51). Selektion/Suche müssen für Block 3 nach oben gehoben werden.
- **`BackHandler`** in `StructureEditorScreen.kt:85-86` — poppt heute nur `focusHistory`. Wird für
  Block 2 erweitert (raus-zoomen, wenn History leer).
- **L0-Tap-Logik** — `StructureOverviewCanvas.kt:318-332` (Tap = fokussieren, Tap-fokussiert =
  `onNavigateToGraph`). Basis für Block 1.
- **L1-Knoten-Tap** — `StructureGraphNode.kt:68` (`onClick = onFocus`), Center-Hook
  `StructureGraphView.kt:247` (`onFocus = {}`). Hier wird L1→L2 belebt.
- **Suche/Matching** — `StructureEditorContent.kt:88-102` (`SearchPagesUseCase`, `matchingPageIds`),
  Eingabe nur in `StructureTreeNavigator.kt:86-89`.
- **`modeSwitcher`-Slot** — `EditorTopBar.kt:29/42`; aktueller 4er-Switcher in `PageWorkbenchScreen.kt:44`.

---

## Umsetzung

> **Wichtig:** Diese Phasen sind eine reine Arbeits-Reihenfolge. **Kein** Commit zwischen den Phasen —
> der gesamte Flow wird am Ende vom Nutzer **einmal manuell getestet**, bevor irgendetwas committet
> wird. (Siehe Abschnitt „Test".)

### Phase 1 — Zoom-Achse zentralisieren (Fundament, kein sichtbares Verhalten)

Reines Refactoring, damit die folgenden Blöcke sauber andocken.

1. `ZoomLevel`-Enum einführen und in `PageWorkbenchScreen` einen abgeleiteten `currentLevel`
   bestimmen (aus `mode` + `structureView`).
2. `zoomInto(pageId)`, `zoomOut()`, `setLevel(level)` implementieren (setzen `mode`, `structureView`,
   `focusedPageId` zusammen). Bestehende `onClick`-Logik des Switchers darüber leiten.
3. `onNavigateToGraph` (Z. 132-135) auf `zoomInto(it)` umstellen — Verhalten bleibt zunächst identisch.

**Damit erfüllt:** eine einzige Stelle, an der „eine Stufe rein/raus" lebt. Kein UX-Unterschied bisher.

### Phase 2 — Block 1: Einheitliche Rein-Zoom-Geste

**Gesten-Regel (überall identisch):**
- **Einzel-Tap auf Knoten** = Seite *fokussieren/auswählen* (innerhalb der Stufe; zentriert die Ansicht).
- **Doppel-Tap auf Knoten** = Seite fokussieren **und eine Stufe tiefer zoomen** (`zoomInto(pageId)`).

1. **L0 (Übersicht):** Tap-Logik in `StructureOverviewCanvas.kt:316-333` umbauen:
   - Einzel-Tap → `onFocus(pageId)` (zentrieren, bleibt L0). Der bisherige Zwei-Tap-Sonderfall
     (Tap-fokussiert → Graph) entfällt.
   - Doppel-Tap → neuer Callback `onZoomInto(pageId)` → `zoomInto` (L0→L1).
   - Doppel-Tap im bestehenden `awaitEachGesture`-Block erkennen (zweiter `down` innerhalb
     `viewConfiguration.doubleTapTimeoutMillis` auf demselben Knoten); Single-Tap erst nach Ablauf des
     Timeouts auslösen, damit sich die beiden nicht überlagern.
2. **L1 (Graph):** Center-Knoten beleben — `StructureGraphView.kt:233-254`, `onFocus = {}` → Doppel-Tap
   auf den Center → `zoomInto(focusedPageId)` (L1→L2). Nachbar-Knoten: Einzel-Tap behält `onFocus`
   (re-fokussieren, bleibt L1), Doppel-Tap → `zoomInto(neighborId)` (fokussiert + L2).
   - `StructureGraphNode.kt:68` `onClick` um eine Doppel-Tap-Erkennung erweitern bzw. einen
     `onZoomInto`-Callback einführen (über `StructureGraphView` durchreichen).
3. **Neue Callback-Kette:** `onZoomInto: (String) -> Unit` von `PageWorkbenchScreen.zoomInto` durch
   `StructureEditorScreen` → `StructureEditorContent` → `StructureOverviewCanvas` /
   `StructureFocusCanvas`/`StructureGraphView`/`StructureGraphNode`.

**Damit erfüllt:** dieselbe Geste auf jeder Stufe; L1→L2 ist erstmals direkt erreichbar; kein
Zwei-Tap-Sonderfall mehr.

### Phase 3 — Block 2: Raus-Zoom-Geste + Breadcrumb

1. **Switcher zum Zoom-Indikator umbauen** (`PageWorkbenchScreen.kt:44-100`):
   - Drei Zoom-Segmente statt vier Tabs: **Übersicht · [Seitenname] · Raster**, jeweils
     `setLevel(GLOBAL/FOCUSED/GRID)`. Das mittlere Segment zeigt den fokussierten Seitennamen
     (gekürzt) — als Breadcrumb-Charakter.
   - **Karten/Graph-Stil-Toggle** nur sichtbar, wenn `currentLevel == FOCUSED` (kleiner
     Icon-Umschalter `Graph ↔ Karten`, setzt `structureView` zwischen `GRAPH`/`CARDS`). Damit
     verschwindet „Karten" als gleichrangiger Tab, bleibt aber optional erreichbar.
2. **Raus-Zoom per Back-Geste:** `StructureEditorScreen.kt:85-86` `BackHandler` erweitern. Reihenfolge:
   (a) wenn `focusHistory` nicht leer → `goBackHistory()` (wie bisher, lateral); (b) sonst, wenn
   `currentLevel != GLOBAL` → `zoomOut()`; (c) sonst → `onNavigateBack()` (Editor verlassen). Auf L2
   (`PageEditorScreen`) analog einen `BackHandler` ergänzen, der zuerst `zoomOut()` nach L1 macht.
   - Die fokussierte Seite bleibt beim Raus-Zoom zentriert: L0 zentriert bereits auf `focusedPageId`
     (`StructureOverviewCanvas.kt:181-192`), L1 ebenso (`StructureGraphView.kt:129-139`).
3. **(Optional, kann zuletzt)** Pinch-Out im freien Canvas-Bereich als zusätzliche Raus-Geste auf
   L0/L1 — nur wenn es nicht mit Pan/Zoom-Pinch (`StructureOverviewCanvas`) kollidiert. Im Zweifel weglassen.

**Damit erfüllt:** Zoom funktioniert erstmals in *beide* Richtungen als Geste; der Tab-Streifen wird
zum verständlichen Zoom-/Breadcrumb-Indikator; „Karten" ist entschärft.

### Phase 4 — Block 3: Kontext (Suche + Multiselect) über den Zoom mittragen

Damit Kontext die L2-Grenze überlebt, müssen `searchQuery` und `selection` aus `StructureEditorState`
**in `PageWorkbenchScreen` hochgezogen** und in beide Host-Screens hineingereicht werden (sie
überleben sonst den Wechsel `STRUKTUR ↔ RASTER` nicht).

1. **Suche überall:**
   - `searchQuery` nach `PageWorkbenchScreen` heben; `StructureEditorState.searchQuery` (Z. 36) liest/schreibt
     hochgehobenen Wert (oder Eingabe an `StructureTreeNavigator.kt:86-89` schreibt nach oben).
   - `matchingPageIds` (`StructureEditorContent.kt:100-102`) **auch an `StructureGraphView` übergeben**
     und dort Knoten highlighten (Muster: `StructureGraphNode` bekommt `isMatched`, gleiches Visual wie
     `StructureOverviewCanvas.kt:491-507`).
   - Suchfeld an einer über alle Stufen sichtbaren Stelle anbieten (z. B. TopBar-Action), nicht nur im Tree.
2. **Multiselect überall:**
   - `selection`/`isMultiSelectMode` (Z. 50-51) ebenfalls nach oben heben, damit sie L0/L1/L2 überleben.
   - **L0-Sichtbarkeit:** Knoten, deren Seite selektierte Tasten enthält (`selection[pageId]` nicht leer),
     auf der Übersicht markieren (Badge/Border, analog `matched`).
   - `BulkActionTopBar` (`StructureEditorTopBar.kt:44-51`) auch auf L0 verfügbar machen, solange Selektion
     besteht.
   - **Scope-Grenze:** Selektion *innerhalb* des Grid-Editors (Tasten im Raster anwählen) ist ein
     größerer Eingriff — in diesem Plan nur sicherstellen, dass eine auf L1 begonnene Selektion über
     L2 hinweg *erhalten bleibt und sichtbar ist*, nicht das Grid selbst zur Selektionsquelle machen.

**Damit erfüllt:** Suche highlightet auf allen Stufen; eine Selektion geht beim Zoomen nicht verloren
und ist auf der Karte sichtbar/aktionsfähig.

### Phase 5 — Block 4: Räumliche Zoom-Animation (Politur)

1. **Übergang L0↔L1↔L2** als `AnimatedContent` (oder `Crossfade` + Scale) in `PageWorkbenchScreen`,
   gekeyt auf `currentLevel`: Rein-Zoom = leicht hochskalieren + einblenden, Raus-Zoom = umgekehrt, so
   dass „tiefer = näher" fühlbar wird.
2. **Pivot/Origin** möglichst am fokussierten Knoten: dessen Position aus dem Layout
   (`StructureOverviewCanvas` `layout[focusedPageId]`, `StructureGraphView` `centerPoint`) als
   `TransformOrigin` für die Skalierung verwenden, damit der Zoom „aus dem Knoten heraus" wirkt
   (Annäherung an Shared-Element, ohne echtes Shared-Element über zwei Host-Composables).
3. `isReducedMotion` respektieren (`StructureOverviewCanvas.kt:127-137` hat das Muster bereits) — bei
   reduzierter Bewegung nur Crossfade ohne Scale.

**Damit erfüllt:** Die drei Screens fühlen sich wie *ein* zusammenhängender Raum an. Reine Politur —
darf bei Zeitdruck nach hinten oder entfällt.

### Phase 6 — Legende für die Warn-Symbole (kontextabhängig eingeblendet)

Unabhängig vom Zoom-Umbau, aber im selben Durchlauf umzusetzen/zu testen. Die Knoten tragen zwei
Warn-Symbole, deren Bedeutung der Nutzer aktuell nicht erklärt bekommt (`StructureBadges.kt`):

| Symbol | Bedeutung | bestehende Ressource |
|---|---|---|
| **⚠** | Orphan — nicht erreichbar (keine Verbindung von der Startseite) | `R.string.structure_warning_orphan` |
| **⛔** | Dead-End — Sackgasse (keine ausgehenden Verbindungen) | `R.string.structure_warning_dead_end` |

**Verhalten:** Eine kompakte Legende wird **nur dann** eingeblendet, wenn im aktuell sichtbaren
Graph mindestens eines der Symbole vorkommt. Es werden **nur die Zeilen** gezeigt, deren Symbol
tatsächlich präsent ist (nur Waisen sichtbar → nur ⚠-Zeile; gemischt → beide).

1. **Neues Composable** `StructureLegend(showOrphan: Boolean, showDeadEnd: Boolean, modifier)` im
   `structure`-Paket (neben `StructureBadges.kt`). Kleines `Surface`/`Card` (dezent, `surfaceVariant`,
   abgerundet), pro aktiver Zeile: das Symbol + Kurztext aus den bestehenden Strings oben. Symbol-Farbe
   wie in `WarningBadges` (`colorScheme.error`). `semantics`/contentDescription analog setzen.
2. **Sichtbarkeits-Logik pro View** (die jeweilige View kennt ihren sichtbaren Knoten-Satz):
   - **L0 Übersicht** (`StructureOverviewCanvas`): `showOrphan = problems.orphans.isNotEmpty()`,
     `showDeadEnd = problems.deadEnds.isNotEmpty()` (es ist potenziell das ganze Buch sichtbar).
     `problems` liegt bereits vor (`StructureOverviewCanvas.kt:96-98`).
   - **L1 Graph** (`StructureGraphView`): nur über den sichtbaren Satz
     (`focusedPageId` + `visibleIncoming` + `visibleOutgoing`, vorhanden Z. 104/112) mit
     `orphans`/`deadEnds` schneiden → Booleans daraus ableiten.
   - **L1 Karten** (`StructureFocusCanvas`, optional): analog über fokussierte Seite + sichtbare
     Quellen/Ziele.
3. **Platzierung:** als Overlay im jeweiligen `Box` (z. B. unten links / `Alignment.BottomStart` mit
   `padding(16.dp)`), damit sie das Zoom-HUD (`Alignment.BottomEnd`, `StructureOverviewCanvas.kt:550`)
   und den FAB nicht überdeckt. Kein eigener Platzverbrauch, wenn keine Probleme vorliegen (Composable
   rendert dann nichts).
4. **(Optional)** kleine „×"-Schaltfläche zum manuellen Ausblenden für die laufende Sitzung; im Zweifel
   weglassen, da die Legende ohnehin nur bei vorhandenen Symbolen erscheint.

**Damit erfüllt:** Sobald ein ⚠ oder ⛔ sichtbar ist, weiß der Nutzer ohne Raten, was es bedeutet —
und die Legende verschwindet, wenn die Struktur sauber ist.

---

## Risiken / offene Entscheidungen

- **Hochheben von `searchQuery`/`selection`** (Phase 4) ist der invasivste Teil — berührt
  `StructureEditorState`, `StructureEditorScreen`, `StructureEditorContent` und `PageWorkbenchScreen`.
  `StructureEditorState.Saver` (Z. 88-108) ggf. anpassen, falls Felder umziehen.
- **Doppel-Tap vs. Pan/Pinch auf L0:** Die Gesten-Erkennung in `StructureOverviewCanvas` läuft über
  zwei parallele `pointerInput`-Blöcke (Transform + Tap, Z. 229/254). Doppel-Tap sauber in den
  Tap-Block integrieren, ohne Pan/Fling zu stören.
- **`CARDS` als Stil-Toggle:** Bestätigen, dass Karten wirklich nur noch als Stil von L1 erscheint und
  nicht mehr als eigener Tab (Nutzer: „kann optional bleiben").
- **Back-Geste auf L2:** `PageEditorScreen` hat bisher evtl. eigene Back-Logik — prüfen, dass der neue
  `zoomOut()`-`BackHandler` nicht mit ungespeicherten Grid-Änderungen kollidiert.

## Test (manuell, vor dem Commit)

Der Nutzer testet den **gesamten** Flow am Stück, bevor committet wird:

- Rein-Zoom per Doppel-Tap auf jeder Stufe (L0→L1→L2), Einzel-Tap fokussiert nur.
- Raus-Zoom per Back-Geste und per Breadcrumb-Segment; fokussierte Seite bleibt zentriert.
- Suche highlightet auf L0 **und** L1; Eingabe von jeder Stufe erreichbar.
- Selektion auf L1 starten, raus-/reinzoomen → bleibt erhalten und auf L0 sichtbar.
- Karten-Stil-Toggle nur auf L1; kein „Karten"-Tab mehr.
- Zoom-Animation fühlt sich richtig an (und respektiert „reduzierte Bewegung").
- Legende erscheint nur bei sichtbarem ⚠/⛔ (nur die zutreffenden Zeilen) und verschwindet bei sauberer Struktur; überdeckt weder Zoom-HUD noch FAB.

**Erst nach diesem Durchlauf** wird der Stand als ein Commit festgehalten.

## Arbeitsteilung

Gemini setzt um, Claude reviewt — Phase für Phase in der obigen Reihenfolge (Phase 1 zuerst als
Fundament, Phase 5 zuletzt/optional). Reviews finden statt, aber es wird **nicht** zwischendurch
committet.
