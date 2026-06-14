# GhosTTalk – StructureFocusCanvas entzerren + Größen-Fix (Plan, Rev. 1, 2026-06-14)

**Zielgruppe:** Gemini (ausführend). **Arbeitsmodus:** Claude reviewt/plant, Gemini setzt um, schrittweise.

Anlass: Nach Umsetzung des Graph-Editors (`docs/structure_graph_editor_plan.md`, committet `588bd46d`) ist `StructureFocusCanvas.kt` auf **2136 Zeilen** angewachsen (hält **zwei** Editoren: Karten-Fokus *und* Graph + Knoten + Kanten-Canvas + Mini-Button-Grid + Connect-Dialog + Move-Logik). Außerdem ist die **Höhenberechnung aufgeklappter Graph-Knoten** fehlerhaft (mal zu groß, mal zu klein).

Dieser Plan macht zwei Dinge: **(A) Bug-Fix** der Knotengröße und **(B) verhaltenserhaltende Zerlegung** der Datei.

## 0. Regeln (verbindlich)

1. **Ein Schritt = ein Commit** (E1 → E4). Nach jedem Schritt `./gradlew assembleDebug` grün (`JAVA_HOME` = JBR).
2. **Verhaltenserhaltend** (außer dem expliziten Größen-Fix in E3): reine Datei-/Funktions-Extraktion, **keine** Logik-/Semantikänderung an ViewModels, Repos, `core-scanning`, Sync.
3. **Beide Editoren bleiben funktionsfähig** — Karten-Modus *und* Graph-Modus; der „Karten | Graph"-Umschalter unverändert.
4. **`testTag`s erhalten** (Tests in `app/src/androidTest` hängen daran). Extrahierte Composables behalten ihre Tags.
5. **Reuse statt Duplikat** — gemeinsame Logik in **eine** Funktion, von Karten- und Graph-Modus genutzt.
6. **Strings** zweisprachig; nichts neu hartcodieren.
7. **Größenziel:** `StructureFocusCanvas.kt` < ~400 Z.; keine neue Datei > ~450 Z.

## A. Bug-Fix: Höhe aufgeklappter Graph-Knoten

**Root Cause (zirkuläre Höhen-Constraint):** Der Knoten bekommt eine **feste** Höhe (`…width(nodeWidth).height(nodeHeight)`, ~Z. 1353–1354) mit `nodeHeight = nodeHeights[id] ?: fallback`. Darin `Surface(Modifier.fillMaxSize())` (~Z. 1383) → der Inhalt wird **auf die feste Knotenhöhe geklemmt**, und genau diese geklemmte Höhe wird via `onSizeChanged` (~Z. 1392–1398) zurückgemessen → die Messung kann den echten Inhalt nie überschreiten → „zu klein"; mit anderem Fallback vorher „zu groß". Das `+12.dp`/Fallback-Spiel macht es zusätzlich zittrig.

**Fix — bevorzugt (in E3 umsetzen): `SubcomposeLayout`.**
Das manuelle `offset{}` + `Canvas`-Kantenlayout durch ein `SubcomposeLayout` ersetzen: Knoten zuerst mit ihrer **intrinsischen** Größe messen, dann in **einem** Durchgang platzieren **und** die Kanten (Pfeile) aus den gemessenen Platzierungen zeichnen. Damit entfällt die `nodeHeights`-State-Map komplett; kein Lag, kein Flackern, korrekte Höhe.

**Fix — minimal (falls SubcomposeLayout zu groß ist, als Zwischenschritt):**
Aufgeklappte Knoten **nicht** in der Höhe festnageln:
- Knoten-`Box`: `.height(nodeHeight)` → `.wrapContentHeight()` (Breite fix bleibt).
- `Surface`: `.fillMaxWidth().wrapContentHeight()` statt `.fillMaxSize()`.
- Gemessene Höhe (`onGloballyPositioned`/`onSizeChanged`) **nur** für (a) Platzieren der **anderen** Knoten und (b) Kanten-Endpunkte verwenden — **nie** zum Begrenzen des Knotens selbst.
Damit ist die Rückkopplung weg (Knoten = inhaltsgroß; 1-Frame-Lag nur bei Kanten/Platzierung).

**Fertig wenn:** Aufgeklappter Knoten ist exakt inhaltsgroß (kein Abschneiden, kein Überschuss), in beiden Orientierungen; Kanten zeigen weiter korrekt auf die Knoten; kein Flackern beim Auf-/Zuklappen.

## B. Zerlegung (verhaltenserhaltend)

### E1 — `ConnectPageDialog.kt` extrahieren (klein, zuerst)
Den durchsuchbaren Verbinden-Dialog (FAB → schlanker, durchsuchbarer Picker) in eine eigene, wiederverwendbare Composable-Datei herauslösen. Wird von **Karten- und Graph-Modus** geteilt (P2) → ein gemeinsamer Connect-Flow, sofortiger Reuse-Gewinn.
- **Fertig wenn:** beide Modi nutzen `ConnectPageDialog`; Suche + „bereits verbunden"-Check unverändert; Build grün.

### E2 — `StructureGraphView.kt` extrahieren
Den gesamten Graph-Modus (Knoten-Layout + Kanten, ab ~Z. 1040 bis Ende) in eine eigene Datei verschieben. `StructureFocusCanvas` ruft sie im Graph-Zweig auf. Signaturen 1:1 durchreichen (`pages`, `graph`, `dragDropState`, `onMoveButton`, `onFocus`, `onAddConnection`, …).
- Dabei den **toten Import `NavigationActionFields`** aus `StructureFocusCanvas` entfernen (E1-Rest).
- **Fertig wenn:** Graph-Modus unverändert; `StructureFocusCanvas` enthält nur noch Karten-Modus + Aufruf von `StructureGraphView`; kein toter Import; Build grün.

### E3 — `StructureGraphNode.kt` extrahieren + Größen-Fix + Layout-Neuaufbau
Das Knoten-Rendering (eingeklappt/aufgeklappt + Mini-Button-Grid + Expand/Collapse-Icon) als eigenes Composable. **Hier den Größen-Fix aus Abschnitt A** sowie den **Layout-Neuaufbau** umsetzen — das alte `offset{}`+`Canvas`+`nodeHeights`-Map-Layout wird durch ein **`SubcomposeLayout`** ersetzt, das die Knoten intrinsisch misst, platziert und die Kanten in einem Durchgang zeichnet.

**Layout-Anforderungen (aus Andreas-Feedback 2026-06-14):**
- **State strikt vom Layout trennen.** Interaktions-State — `focusedPageId`, `expandedPageIds`, „mehr"-Toggles, das „scharfgestellte ×" (P1) — in **`rememberSaveable`** halten (orientierungsunabhängig, übersteht Rotation). `expandedPageIds` ist aktuell nur `remember` → auf `rememberSaveable` umstellen (Set als List speichern / eigener Saver). Das Layout liest denselben State → **ein** State, zwei Darstellungen.
- **Adaptive Orientierung:** **Portrait = top-down** (Eingehend oben → Fokus → Ausgehend unten, vertikales Scrollen — mobil-natürlich, konsistent mit dem Karten-Editor); **Landscape = left-right** (kommt von ← Fokus → führt zu). Beide Achsen teilen sich dieselben Knoten-/Kanten-Composables; nur die Anordnungsachse hängt von `LocalConfiguration.orientation` (bzw. WindowSizeClass) ab.
- **Leeres Band weglassen:** Fehlt eine Seite (z. B. Startseite ohne Eingehende), darf die Eingehend-Spalte/-Reihe **keinen** Platz reservieren → der Fokus-Knoten beginnt am Rand, kein leerer Bereich.
- **Auf den Fokus scrollen:** Bei Fokuswechsel den Fokus-Knoten ins Sichtfeld zentrieren (kein manuelles Erst-Scrollen nötig).
- **Reihenfolge zur Risiko-Reduktion:** **E3a** top-down (Default; löst leeres Band + Portrait), **E3b** left-right (Landscape) ergänzen → adaptiv.

- **Fertig wenn:** Knoten-Code isoliert; Höhe korrekt (Abschnitt A); Portrait top-down / Landscape left-right; keine leere reservierte Spalte bei fehlendem Parent; Fokus-Knoten beim Wechsel im Bild; Expansion/Fokus überstehen Rotation; `testTag`s erhalten; Build grün.

### E4 — Reine Helfer extrahieren (Dedup + testbar)
Doppelte/komplexe Logik in **pure** Funktionen (z. B. eigene Datei `StructureCanvasLogic.kt` in `…/structure/`):
- „aktive Navigations-Buttons einer Seite (ohne ausgehende + ohne Self-Loop)" — liegt doppelt: Karten-Modus (~Z. 335, `hasValidButtons`) **und** Graph-Knoten (~Z. 1428, `activeButtons`). Eine gemeinsame pure Funktion `navigableButtons(page, outgoingIndices, effectiveStartPageId)`.
- **`SearchablePagePicker` vereinheitlichen (aus E1-Review):** `ConnectPageDialog` und `MoveButtonDialog` teilen dieselbe durchsuchbare Picker-UI → ein generisches Composable (Param: Titel, optionaler Untertitel, `excludePageId`, `onSelected`); beide darauf umstellen. `MoveButtonDialog`-Strings („Button verschieben…", „Wähle die Ziel-Seite…") dabei nach `strings.xml` (de+en).
- Kanten-/Spalten-Geometrie (Spaltenanzahl, Chunking, Trunk-X) als pure Helfer.
- **Tests:** mind. ein Unit-Test für `navigableButtons` (Self-Loop, ausgehender Index, inaktiv/leeres Label werden korrekt ausgeschlossen).
- **Fertig wenn:** keine duplizierte Button-Filter-Logik mehr; Helfer unit-getestet; Build + `testDebugUnitTest` grün.

### E5 (optional) — Karten-Sektionen splitten
Falls `StructureFocusCanvas` nach E1–E4 noch > ~400 Z.: `IncomingSection`/`PageCard`/`OutgoingSection` als eigene Composables.

## Reihenfolge & Verifikation

E1 → E2 → E3 (mit Größen-Fix) → E4 (→ E5 bei Bedarf). Nach jedem Schritt: `assembleDebug` grün; manuell beide Modi + Umschalter prüfen; nach E4 zusätzlich `testDebugUnitTest`.

## Review-Schwerpunkte (Claude)
- [ ] Reine Extraktion, keine Semantikänderung (außer Größen-Fix E3); `testTag`s erhalten.
- [ ] Knotengröße korrekt (kein Clipping/Überschuss), Kanten treffen die Knoten, kein Flackern.
- [ ] Portrait = top-down, Landscape = left-right; kein leeres Band bei fehlendem Parent; Fokus beim Wechsel im Bild; Expansion/Fokus überstehen Rotation (`rememberSaveable`).
- [ ] `ConnectPageDialog` von beiden Modi genutzt (kein Duplikat); `navigableButtons` entdupliziert + getestet.
- [ ] `StructureFocusCanvas` < ~400 Z.; neue Dateien < ~450 Z.

## Review-Notizen (Claude)

### E1 reviewt+abgenommen (2026-06-14, uncommitted)
`ConnectPageDialog.kt` (118 Z.) extrahiert: durchsuchbar (Name-Filter, sortiert, Leerzustand `page_none_found`), als **ein geteilter** FAB+Dialog für **beide** Modi (FAB im Root-Box, modus-übergreifend); `onPageSelected → onAddConnection` (dup-Check erhalten); neue Strings de+en. Build+Tests grün; StructureFocusCanvas 2136→2045.
**Mitnehmen:**
- E2: toter Import `NavigationActionFields` in StructureFocusCanvas entfernen.
- E4: `MoveButtonDialog` (Z. ~1950) dupliziert die durchsuchbare Picker-UI von `ConnectPageDialog` → in **einen** generischen `SearchablePagePicker` (Titel/Untertitel/`excludePageId`/`onSelected`) vereinen; dessen hartcodierte Strings („Button verschieben…", „Wähle die Ziel-Seite…") → i18n.
- Größen-Bug weiterhin offen → E3 (erwartet).

### E2–E4 reviewt (2026-06-14, uncommitted) — Build+Tests grün
Dateien: `StructureGraphView.kt` (873), `StructureGraphNode.kt` (182), `StructureCanvasLogic.kt` (31) + `StructureCanvasLogicTest`; `StructureFocusCanvas` 2136→918.
- **E2 ✅** Graph ausgelagert. **Offen:** toter Import `NavigationActionFields` (FocusCanvas:90) noch da → entfernen.
- **E3 ✅ (Kern):** `SubcomposeLayout` (GraphView:175), alter `nodeHeights`/`onSizeChanged`/`.height()`-Hack **entfernt**, adaptiv (`isLandscape`), `rememberSaveable`+Saver für `expandedPageIds`, Scroll-to-Center. Größen-Bug strukturell behoben — **Gerätesicht offen** (Knotengröße, beide Orientierungen, leeres Band Startseite, Rotation).
- **E4 ⚠️ teilweise:** `navigableButtons` entdupliziert (Node+FocusCanvas) + 1 Test ✅. **Offen:** `SearchablePagePicker` nicht erstellt; `MoveButtonDialog` dupliziert weiter die Picker-UI + hartcodierte Strings (GraphView:800/813) → vereinen + i18n. Test um Fälle erweitern.
- **Größenziele verfehlt:** FocusCanvas 918 (<400), GraphView 873 (<450) → optional E5 (weitere Splits).

**Restbatch „E6" (klein):** (1) toten Import raus, (2) `SearchablePagePicker` + MoveButton i18n, (3) optional E5-Splits + mehr Testfälle.

### E6-Rest reviewt+abgenommen (2026-06-14, uncommitted) — Build+Tests grün
- ✅ Toter `NavigationActionFields`-Import entfernt.
- ✅ `SearchablePagePicker.kt` (generisch: title/subtitle/excludePageId/onPageSelected) — genutzt von Connect (FocusCanvas:906) **und** MoveButton (GraphView:766); `ConnectPageDialog.kt` gelöscht → kein Picker-Duplikat mehr.
- ✅ MoveButton-Strings → `strings.xml` de+en (`structure_move_button_dialog_*`).
**Gesamter Refactor E1–E6 abgenommen.** Offen nur: optional E5 (Größenziele FocusCanvas 918/<400, GraphView 778/<450) + Gerätesicht des Größen-Fixes (E3).
