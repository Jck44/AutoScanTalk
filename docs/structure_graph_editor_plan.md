# GhosTTalk – Interaktiver Struktur-Graph-Editor (Plan, Rev. 1, 2026-06-14)

**Zielgruppe:** Gemini (ausführend). **Arbeitsmodus:** Claude reviewt/plant, Gemini setzt um, phasenweise.

Ziel: eine **graphische** Alternative zum bestehenden Karten-Fokus-Editor (`StructureFocusCanvas`) im Struktureditor. Beide laufen **parallel** über einen Umschalter „Karten | Graph", damit Andreas live A/B-Feedback sammeln kann. Der **Karten-Editor bleibt Default und unangetastet** (Fallback). Mock-up: `structure_graph_editor_concept` (in der Session gezeigt).

**Kontext:** Der Struktureditor ist die **Betreuer**-Werkbank (nicht die Endnutzer-Sprechoberfläche) → Drag ist hier vertretbar, aber jeder Drag-Pfad hat einen **Nicht-Drag-Fallback** (Tippen/Button), weil Betreuer unterschiedliche motorische Fähigkeiten haben.

## Mit Andreas abgestimmte Entscheidungen

- **Umschalter „Karten | Graph"** im Struktureditor; Karten = Default/Fallback. State persistent (`rememberSaveable`/Setting) für stabiles Feedback.
- **Modell** bleibt „kommt von → Fokus-Seite → führt zu".
- **Navigieren:** Knoten antippen = Fokus wechseln. Der **Baum-Navigator bleibt reine Navigation** (kein Drag-Quelle; Tree→Graph-Drag wäre cross-pane + am Telefon (Sheet) unpraktikabel → höchstens später, nur Tablet).
- **Trennen:** **zweistufig** — Pfeil/Verbindung antippen → ein **×** erscheint → × antippen → löschen (nutzt den bestehenden Remove-Connection-Flow). Verhindert versehentliches Löschen.
- **Verbinden:** **ausschließlich** über einen **„Seite verbinden"-Floating-Button → schlanken, durchsuchbaren Dialog** (Suchfeld + gefilterte Seitenliste; Auswahl → `onAddConnection`). **Kein Drag&Drop, keine Seiten-Palette** — bei **~180 Seiten** nicht sauber/durchsuchbar im Canvas darstellbar; der Dialog mit Suche skaliert besser.
- **Ein geteilter FAB + Dialog für beide Modi:** Im **Karten-Modus** ersetzt derselbe Floating-Button den bisherigen Inline-„Seite verbinden"-Button (S1) → Wiederverwendung, ruhigeres UI, ein konsistenter Verlinken-Flow.
- **Button-Ebene = fester Stretch (P4):** Knoten/Karten **aufklappen** → Button-Chips der Seite sichtbar → Button-Chips per **Drag&Drop zwischen Seiten** verschieben (im graphischen Editor). Erst nach stabiler Seiten-Ebene (P0–P3). Hinweis: Page-Level-Drag bleibt verworfen (180 Seiten); Button-Level-Drag betrifft nur die wenigen **aufgeklappten, sichtbaren** Knoten → handhabbar. Tree→Graph-Drag verworfen.

## Regeln (verbindlich)

1. **Eine Phase = ein Commit**, Reihenfolge P0 → P4. Nach jeder Phase `assembleDebug` grün (`JAVA_HOME` = JBR).
2. **Karten-Editor (`StructureFocusCanvas`) bleibt funktionsfähig** — Graph ist additiv hinter dem Umschalter. Nichts am Karten-Pfad brechen.
3. **Bestehende Logik wiederverwenden, nicht duplizieren:** `onAddConnection`/`performAddConnection` (inkl. „bereits verbunden"-Check), den Remove-Connection-Dialog, `rememberChipDragDropState`/`chipDropTarget` (existiert vom KI-Split), `LocalNavigationViewGraph` (read-only Graph als Basis), `BookNavigationGraph` (Domain-Quelle). **Keine** ViewModel-Semantik ändern.
4. **Barrierefreiheit:** jeder Drag-Pfad hat einen Tipp-/Button-Fallback; Touchziele ≥ 48 dp; `contentDescription`.
5. **Strings** zweisprachig (`values/`+`values-en/`); neue Graph-Strings nicht hartcodieren.
6. `testTag`s erhalten; neue stabile Tags für Graph-Elemente vergeben.

## Phasen

### P0 — Umschalter + Read-only-Graph
- „Karten | Graph"-`SingleChoiceSegmentedButtonRow` oben im Struktureditor-Content; State `rememberSaveable` (später optional Setting). Karten = Default.
- Graph-Ansicht: `LocalNavigationViewGraph` zur **Vollansicht** ausbauen — Fokus-Knoten zentral, Eingehende links, Ausgehende rechts; **Knoten antippen = `onFocus`** (Fokus wechseln). Noch **kein** Verbinden/Trennen.
- **Fertig wenn:** Umschalter wechselt zwischen Karten und Graph; im Graph lässt sich per Tippen navigieren; Karten-Modus unverändert; Build grün.

### P1 — Trennen (zweistufig)
- Verbindung (Pfeil) antippbar machen → Tap zeigt ein **×** an der Kante; × antippen → bestehender Remove-Connection-Flow (`gridEditorViewModel.updateButtonConfig(pageId, index, null)` via vorhandener Bestätigungslogik).
- Nur eine Kante gleichzeitig im „×-Zustand"; Tippen woanders verwirft den Zustand.
- **Fertig wenn:** Verbindung im Graph zweistufig löschbar; identisches Ergebnis wie im Karten-Modus; Build grün.

### P2 — Verbinden (Floating-Button → schlanker, durchsuchbarer Dialog)
- **Floating-Button** „Seite verbinden" (FAB rechts unten) → öffnet einen **schlanken Dialog** mit **Suchfeld + gefilterter Seitenliste** (skaliert auf ~180 Seiten). Auswahl → `onAddConnection(targetPageId)` inkl. „bereits verbunden"-Check.
- **Kein** Drag / keine Palette.
- **Geteilte Komponente für beide Modi:** Im **Karten-Modus** ersetzt **derselbe FAB + Dialog** den bisherigen Inline-„Seite verbinden"-Button (S1). Eine wiederverwendbare Connect-Komponente (FAB + durchsuchbarer Picker), genutzt von Graph **und** Karten. Falls `NavigationActionFields` keine Suche hat: Suchfeld ergänzen bzw. schlanke durchsuchbare Seitenliste verwenden.
- **Fertig wenn:** Verbinden über den FAB-Dialog funktioniert in **beiden** Modi, Suche filtert die Seitenliste, „bereits verbunden"-Check greift, kein Inline-Connect-Button mehr im Karten-Modus; Build grün.

### P3 — Feinschliff
- Viele Verbindungen: Cap + „mehr"-Aufklappen (wie `MAX_VISIBLE_SOURCES` im Karten-Modus); Layout/Überlappung sauber.
- i18n der Graph-Strings; leere Zustände; Light/Dark + Hoch/Quer geprüft.
- **Fertig wenn:** Graph bleibt bei vielen Kanten lesbar; keine hartcodierten Graph-Strings; Build grün.

### P4 — Stretch (separat entscheiden)
- Knoten **aufklappen** → Button-Chips der Seite sichtbar → Button-Chips **zwischen Seiten** ziehen (verschmilzt Struktur- + Raster-Editing). Groß: Layout/Performance/Hit-Testing.
- Optional Tree→Graph-Drag (nur Tablet).
- **Erst nach stabilem P0–P3 und eigener Konzeptrunde.**

## Review-Schwerpunkte (Claude)
- [ ] Karten-Editor durchgehend unangetastet/funktionsfähig (Fallback).
- [ ] Reuse statt Duplikat (onAddConnection/Remove-Flow/Graph/DragState); keine ViewModel-Semantikänderung.
- [ ] Jeder Drag-Pfad hat Tipp-/Button-Fallback; Touchziele ≥ 48 dp.
- [ ] Zweistufiges Löschen ohne versehentliche Treffer; „bereits verbunden"-Check aktiv.

## Review-Notizen (Claude)
_(leer bis zur Umsetzung)_

---

## Gerätesicht-Fixes „G-Batch" (Andreas-Feedback 2026-06-14)

Nach Umsetzung E1–E6 am Gerät gefunden. Nur UI, in `app/.../ui/pages/structure/StructureGraphView.kt`. Ein Commit; `assembleDebug` grün; beide Modi/Orientierungen prüfen.

### G1 — Portrait vertikal stapeln (statt in die Breite)
**Ursache:** Der Portrait-Zweig (~Z. 364–403) ordnet **alle** Eingehenden bzw. Ausgehenden in **einer** horizontalen Reihe an (`currentX += width`) → bei vielen Zielen (Startseite: 22) massiver Breiten-Überlauf / abgeschnittene Karten. Der Landscape-Zweig (~303–363) chunkt dagegen in **Spalten** (vertikal) — daher sauber.
- Portrait **transponieren**: Eingehend **und** Ausgehend **vertikal stapeln** (ein Knoten pro Zeile, nach unten). `layoutWidth = max(Knotenbreite incoming/center/outgoing)`; `layoutHeight = Summe Zeilenhöhen + Spacing`. Block-Reihenfolge: Eingehend oben → Center → Ausgehend unten.
- Die Kanten-Pfade im Canvas (`if (isLandscape) … else …`) auf vertikal anpassen: Eingehend (oben) → Center-Oberkante; Center-Unterkante → jedes Ausgehende.
- Vertikales Scrollen (`scrollStateY`) + bestehende „mehr"-Begrenzung bleiben.
- **Fertig wenn:** Portrait läuft nicht mehr in die Breite (kein horizontaler Überlauf), Kinder stapeln nach unten, Pfeile treffen korrekt.

### G2 — Armed-× zuverlässig zurücksetzen
**Ursache:** `selectedEdgeForDeletion` (Z. 131) wird nur bei Tap/Löschen resettet (156/698), **nicht** beim Auf-/Zuklappen einer Karte → das × bleibt stehen und überlagert eine Karte.
- `selectedEdgeForDeletion = null` setzen, sobald sich `expandedPageIds` **oder** `focusedPageId` ändert (idealerweise auch bei Scroll), z. B. `LaunchedEffect(expandedPageIds, focusedPageId) { selectedEdgeForDeletion = null }`.
- **Fertig wenn:** Beim Auf-/Zuklappen / Fokuswechsel verschwindet das ×; überlagert nichts mehr.

### G3 — Löschen über die Verbindungslinie (Dots entfernen) — Andreas wählte Variante B
- **Dot-Indikatoren entfernen** (`drawCircle` ~Z. 546–583 + zugehörige Dot-Tap-Logik).
- **Kante tippbar** via `pointerInput { detectTapGestures }` über der Graph-Fläche: für die Tap-Position die **Distanz zu jeder Kante** berechnen (Segment `incoming/outgoingPoint ↔ centerPoint` — Geometrie liegt vor) und die nächste Kante **innerhalb ~24dp** als `selectedEdgeForDeletion` setzen (kein per-Pfad-Composable).
- **Feedback:** die ausgewählte Kante (`isSelected`) in `errorColor` + etwas dicker zeichnen (bestehende `isSelected`-Logik wiederverwenden). Dann erscheint das ×.
- Zweistufig: Linie tippen → Kante hervorgehoben + × → × tippen → `onRemoveConnection`. Tap ins Leere → reset.
- **Fertig wenn:** keine Dots mehr; Tippen nahe einer Verbindung hebt sie hervor + zeigt ×; Löschen funktioniert; G2-Reset greift.

### Review-Schwerpunkte G (Claude)
- [ ] Portrait stapelt nach unten, kein Breiten-Überlauf; Kanten/Pfeile korrekt in beiden Orientierungen.
- [ ] Linien-Hit-Test trifft zuverlässig (auch eng beieinanderliegende Kanten); kein versehentliches Löschen (Zweistufigkeit).
- [ ] × wird bei jeder anderen Interaktion zurückgesetzt; überlagert nie eine Karte.

### G-Batch reviewt+abgenommen (Claude 2026-06-14, uncommitted) — Build+Tests grün
- **G1 ✅** Portrait-Zweig „strictly vertical stacking" (`currentY += height`); Hit-Test/Kanten mit vertikaler Geometrie → kein Breiten-Überlauf.
- **G2 ✅** `LaunchedEffect(expandedPageIds, focusedPageId){ selectedEdgeForDeletion = null }`.
- **G3 ✅** keine Dots (`drawCircle` entfernt); Linie tippbar via `detectTapGestures` + `distanceToSegment` (pure Helfer in StructureCanvasLogic + 4 Testfälle, 24dp-Threshold); gewählte Kante errorColor+dicker (Feedback); Hit-Test korrekt nur auf ausgehende/löschbare Kanten.
- Stray `build_output.log` entfernt.
- Offen nur: **Gerätesicht** (Portrait stapelt nach unten, Linien-Tap trifft zuverlässig auch bei engen Kanten, × überlagert nichts).

### Crash-Fix (Claude, 2026-06-14) — Querformat + Wechsel auf Karten
**Stacktrace:** `IllegalStateException: Vertically scrollable component measured with infinity maximum height` (verschachtelte Scrolls). **Ursache:** Der Card-Modus (`StructureFocusCanvas`, else-Zweig) hatte nur im Querformat (`isTablet`) eine **eingebettete `StructureGraphView`** (eigener Scroll) **innerhalb** des `Column(verticalScroll)` → Überbleibsel aus der Zeit vor dem „Karten | Graph"-Umschalter. **Fix:** den `isTablet`/`showGraph`/`StructureGraphView`-Block aus dem Card-Modus entfernt (Graph-Modus-Render `if viewMode==GRAPH` bleibt); tote Imports LazyColumn/horizontalScroll raus. Build grün. **Gerätesicht:** Wechsel auf Karten im Querformat crasht nicht mehr.

### Pfeil-Ausrichtung-Fix (Claude, 2026-06-14, umgesetzt, Build grün)
Pfeilköpfe waren achsen-fest (Landscape waagerecht / Portrait senkrecht), die Cubic-Kurve näherte sich aber diagonal (erzwungen waagerechter End-Tangens via c2.y=endY) → Pfeil lag nicht auf der Linie. Fix in `StructureGraphView` (ausgehende Kanten): (1) c2 trägt jetzt Y-Anteil (`c2 = end − 0.4·(end−start)`) → diagonaler End-Anflug in Linienrichtung; (2) Pfeilkopf entlang Tangens `end−c2` rotiert (eine Formel statt Landscape/Portrait-Zweige) → liegt immer auf der Linie. Eingehende Kanten ohne Pfeil, unverändert. Gerätesicht: Pfeil-Optik gegenchecken.
