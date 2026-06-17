# Plan: Page-Overview / Editor — Sammel-Politur & offene Punkte

Konsolidierter Plan für Gemini. Bündelt die neuen Eingaben mit den noch offenen Punkten aus früheren
Plänen. **Gemini setzt um, Claude reviewt. Keine Zwischen-Commits** — der Nutzer testet am Stück, dann
ein Commit.

---

## A — Hintergrund von Focused- und Global-Editor vereinheitlichen

**Ist:** Der globale Overview (`StructureOverviewCanvas`) zeichnet auf der nackten `Box` (transparent →
Scaffold-`surface`). Der fokussierte Graph (`StructureGraphView`) liegt in einer **`Card`** mit
`containerColor = surfaceVariant.copy(alpha = 0.3f)` (`StructureGraphView.kt:182-183`). → sichtbar
unterschiedlicher Untergrund beim Zoomwechsel.

**Soll:** Beide Ebenen teilen denselben Canvas-Hintergrund.
- Eine gemeinsame Hintergrundbehandlung festlegen (Empfehlung: die dezente `surfaceVariant α0.3`-Fläche
  als gemeinsamer „Canvas-Untergrund" für **beide**, oder bewusst beide auf plain `surface` — eine
  Entscheidung, konsistent anwenden).
- Im Overview die `Box` mit derselben Hintergrundfarbe hinterlegen (`Modifier.background(...)`), die der
  fokussierte Graph nutzt — bzw. umgekehrt die `Card`-Tönung im Graph entfernen. Wichtig: **gleiche
  Farbe + gleiche Form/Padding-Anmutung** auf beiden Ebenen, damit der Crossfade nahtlos wirkt.

**Dateien:** `StructureOverviewCanvas.kt` (äußere `Box` ~Z. 225), `StructureGraphView.kt` (`Card` Z. 182).

---

## B — Orphans im globalen Editor in eigenen Bereich + Kanten-Rauschen reduzieren

**Ist:** Verwaiste Seiten (`graph.orphans()`, nicht von der Startseite erreichbar) erscheinen nur als
separate Komponenten irgendwo unterhalb des Hauptbaums (`calculateOverviewLayout`,
`StructureOverviewCanvas.kt:612-757`) und zeichnen ihre **ausgehenden Kanten immer** mit.

**Soll:**
1. **Eigener, einklappbarer Orphan-Bereich:** Orphans noch deutlicher abseits halten — idealerweise in
   einem **aufklappbaren Container** (standardmäßig **eingeklappt**), z. B. ein Overlay-Panel/Chip
   „Verwaiste Seiten (N) ▸" (Anmutung wie `StructureLegend`), das aufgeklappt die Orphan-Seiten zeigt
   (Liste oder kleiner Cluster). Eingeklappt sind sie komplett aus der Karte heraus → maximale
   Ruhe. Tippt man dort eine Orphan-Seite an, wird sie fokussiert (und zeigt dann gemäß Punkt 2 ihre
   Kanten). Hinweis: Der Overview ist ein Pan/Zoom-Canvas — ein **Overlay-Container** (wie Legende/HUD)
   ist hier sauberer als ein In-Canvas-„Bereich".
2. **Weniger Kanten-Noise:** Für Orphans **keine ausgehenden Kanten** zeichnen — **außer** der Orphan
   ist gerade fokussiert (`focusedPageId`). Dann seine Kanten normal zeigen.
   - Andockpunkt: `edgesToDraw`-Berechnung (`StructureOverviewCanvas.kt:144-170`). Regel ergänzen:
     wenn `sourceId in orphans && sourceId != focusedPageId` → Kante überspringen. (Analog zur
     bestehenden Sonderbehandlung der Backward-Edges, die nur bei Fokus gezeichnet werden.)
3. Orphans behalten ihr ⚠-Badge; die Legende (`StructureLegend`) erklärt es weiterhin.

**Ergebnis:** Übersicht bleibt aufgeräumt; Orphans sind als Gruppe auffindbar, erzeugen aber kein
Liniengewirr, solange man sie nicht gezielt fokussiert.

**Dateien:** `StructureOverviewCanvas.kt` (`calculateOverviewLayout` für den Bereich; `edgesToDraw` für
die Kanten-Regel; ggf. eine Bereichs-Überschrift im Nodes-Overlay).

---

## C — Focused-Editor: fokussiertes Element zentrieren (statt „oben kleben")

**Ist:** `StructureGraphView` scrollt nach Layout auf `scrollState.maxValue / 2`
(`StructureGraphView.kt:129-139`) — das zentriert die **Mitte des Scrollbereichs**, nicht den
fokussierten Knoten. Bei vielen Ein-/Ausgängen sitzt der Fokus-Knoten dann nicht mittig (wirkt „oben
klebend"). In der Karten-Ansicht (`StructureFocusCanvas`) gibt es gar keine Zentrierung — Start oben.

**Soll:** Beim Öffnen/Fokuswechsel wird das **fokussierte Element selbst vertikal (und im Querformat
horizontal) im Viewport zentriert**.
- **Graph (`StructureGraphView`):** Die tatsächliche Platzierung des Center-Knotens kennen
  (`centerPoint`/`centerPlaceable` aus dem `SubcomposeLayout`) und so scrollen, dass dessen Mittelpunkt
  in der Viewport-Mitte liegt: `scrollTo(centerY - viewportHeight/2)` bzw. analog X. Statt
  `maxValue/2`.
- **Karten (`StructureFocusCanvas`):** Nach Layout zur fokussierten Seiten-Karte scrollen, sodass sie
  zentriert ist (z. B. via `onGloballyPositioned`-Position der Mittel-Karte + `scrollState.animateScrollTo`).
- Auf `focusedPageId` gekeyt (nur bei echtem Fokuswechsel), Expand/Collapse soll **nicht** neu scrollen
  (bestehendes Verhalten beibehalten).

**Dateien:** `StructureGraphView.kt` (Center-Scroll), `StructureFocusCanvas.kt` (Karten-Scroll).

---

## D — (offen) Global-Overview: Viewport-Sprung beim Raus-Zoomen

Bereits beschrieben in **`docs/structure_overview_viewport_jump_fix_plan.md`** — **noch nicht umgesetzt**.
Kurz: Overview wird bei Zoom-out frisch gemountet, `offset` startet auf `Zero` und **snappt** erst nach
der Messung in die Zentrierung (`StructureOverviewCanvas.kt:182-194`). Fix: **vor dem ersten Zeichnen**
zentrieren (Initial-`offset` aus den Layout-Daten via `BoxWithConstraints`, oder „center-before-reveal").
Details im verlinkten Plan übernehmen.

> Hinweis: Hängt eng mit **C** zusammen (beides „fokussiertes Element zentriert"). Idealerweise
> dieselbe Hilfslogik für Overview und Graph nutzen.

---

## E — (offen) Adaptive Action-Bar v2

Bereits beschrieben in **`docs/editor_action_bar_adaptive_v2_plan.md`** — **noch nicht umgesetzt**.
Kurz: P2-Aktionen landen **immer** im Overflow (`AdaptiveActionBar.kt:95-96`), darum bleibt auf dem
Tablet trotz Platz fast alles im `⋮`; außerdem kennt die 50 %-Kappung die Switcher-Breite nicht. Fix:
Inline/Overflow rein nach Platz (Priorität = nur Reihenfolge), Switcher in einen gemeinsamen Messraum,
fixe 50 %-Kappung raus. Details im verlinkten Plan.

---

## F — Kleinere Cleanups (aus Review)

- `PageWorkbenchScreen`: `isReducedMotion` (Z. ~171) wird seit dem Crossfade-Umbau nicht mehr genutzt →
  toter Val + evtl. ungenutzte `scaleIn/scaleOut`-Imports entfernen.
- `AdaptiveActionBar`: `isTablet`-Parameter wird durchgereicht, aber **nicht benutzt** — im Zuge von **E**
  entweder für die Tablet-Icon+Text-Variante nutzen oder entfernen.
- `StructureGraphView`: nach Entfernen des Headers zwei leere Zeilen übrig (Z. ~205) — aufräumen.
- i18n: hartcodierte Labels („Baum", „Vorlagen", „Übersicht/Seite/Raster", „Mehr Optionen",
  „Karten-/Graph-Ansicht", „Assistent") perspektivisch auf `stringResource` ziehen.

---

## G — Sidebar-Tab-Stil vereinheitlichen + Code teilen

**Ist:** Es gibt zwei fast identische „Tree/Templates"-Seitenpanels mit **unterschiedlichem Stil**:
- **Grid-Editor** (`GridEditorContent.kt:278-340`): `SecondaryTabRow` + `Tab` (echte Material-Tabs),
  State als String `activeSidePanelTab` ("templates"/"tree"), Reihenfolge **Vorlagen → Seitenbaum**,
  Collapse-Pfeil in eigener Zeile **über** den Tabs, Labels `template_panel_title` / „Seitenbaum".
- **Struktur-Editor** (`StructureEditorContent.kt`, neu): `SingleChoiceSegmentedButtonRow` +
  `SegmentedButton`, State als Enum `SidePanelTab`, Reihenfolge **Baum → Vorlagen**, Collapse-Pfeil
  **in derselben Zeile** wie der Umschalter, Labels „Baum" / „Vorlagen".

**Soll — auf *einen* Stil vereinheitlichen und den Code teilen.**

- **Stil-Entscheidung (Empfehlung): Material-Tabs (`SecondaryTabRow`)** als gemeinsamer Stil. Begründung:
  Tabs sind das idiomatische Muster zum **Umschalten von Panel-Inhalt**; die Segmented-Buttons bleiben
  damit exklusiv dem **Zoom-Mode-Switcher** (Übersicht/Seite/Raster) vorbehalten → keine visuelle
  Verwechslung „Inhalt wechseln" vs. „Zoomstufe wechseln". (Zudem existiert der Tab-Stil im Grid bereits.)
- **Gemeinsame Komponente** `EditorSidePanel` (in `core-ui` oder gemeinsamem ui-Paket), von **beiden**
  Editoren genutzt:
  ```kotlin
  enum class SidePanelTab { TREE, TEMPLATES }   // aus structure-Paket hierher verschieben (Single Source)
  @Composable
  fun EditorSidePanel(
      selectedTab: SidePanelTab,
      onSelectTab: (SidePanelTab) -> Unit,
      onCollapse: () -> Unit,
      treeContent: @Composable () -> Unit,
      templatesContent: @Composable () -> Unit,
      modifier: Modifier = Modifier,
  )
  ```
  Rendert konsistent: `Card` (300dp, gleiche Farbe/Padding) → Collapse-Pfeil + `SecondaryTabRow`
  (eine festgelegte **Reihenfolge** + **gleiche Labels** in beiden Editoren) → Inhalts-`Box` mit
  `treeContent`/`templatesContent` als Slots.
- **Migration:**
  - Grid-Editor: String-State `activeSidePanelTab` → Enum `SidePanelTab`; `EditorSidePanel` mit seinen
    bestehenden `ButtonTemplatesPanel`/`StructureTreeNavigator`-Inhalten als Slots befüllen.
  - Struktur-Editor: das in dieser Iteration neu gebaute Segmented-Panel durch `EditorSidePanel` ersetzen
    (`state.sidePanelTab`/`sidePanelExpanded` weiterverwenden).
- **Reihenfolge + Labels** in beiden gleich (Vorschlag: **Baum/Seitenbaum** zuerst, dann **Vorlagen** —
  oder umgekehrt, aber identisch). Labels perspektivisch als `stringResource` (siehe **F**).

**Dateien:** neu `core-ui/.../EditorSidePanel.kt`; `GridEditorContent.kt`, `StructureEditorContent.kt`,
`StructureEditorState.kt` (Enum-Verschiebung).

---

## H — Tablet-Navigation („Nutzermodus / Inhalte / Statistik") wirkt deplatziert

**Ist:** Die Hauptnavigation nutzt `NavigationSuiteScaffold` (`BookShellScreen.kt:202-246`). Auf dem
Tablet/breiten Screen macht Material daraus automatisch eine **NavigationRail** (links): Items oben
angeheftet, kleine Icons, viel Leerraum darunter → „klebt oben, wirkt deplatziert" im Vergleich zur
Bottom-Bar des Telefons.

**Soll:** Tablet-Navigation bewusst gestalten statt Default hinnehmen. Optionen (eine wählen):
- **Rail anpassen:** Items vertikal **zentrieren** statt oben, größere Touch-Targets/Icons, ggf.
  Header/Logo oben — über die `NavigationSuiteScaffold`-Anpassung (eigene `NavigationSuiteItemColors`,
  Arrangement) bzw. eine **eigene `NavigationRail`** mit `verticalArrangement = Center`.
- **Oder Bottom-Bar beibehalten:** per `navigationSuiteType`/`layoutType` auch auf dem Tablet die
  `NavigationBar` erzwingen, damit die Optik 1:1 zum Telefon passt.
- Icon-/Label-Größen an die Telefon-Anmutung angleichen.

Entscheidung nach „was passt zum restlichen UI/UX" — Empfehlung: Rail mit **zentrierten** Items +
größeren Icons (nutzt den Tablet-Platz, bleibt aber Material-konform).

**Dateien:** `BookShellScreen.kt` (NavigationSuiteScaffold-Konfiguration).

## I — Icon für den globalen Editor vereinheitlichen (+ Statistik-Kollision auflösen)

**Ist:** Der globale Editor wird mit **zwei verschiedenen** Icons dargestellt, und eines kollidiert:
- Einstiegs-Button in `PageListScreen.kt:263` (und :429) → `GhostTalkIcons.Link` (Kettensymbol).
- Zoom-Pille „Übersicht" in `PageWorkbenchScreen.kt:140` → `GhostTalkIcons.BarChart`.
- **`BarChart` ist aber zugleich das Statistik-Nav-Icon** (`BookShellScreen.kt:241`) → Verwechslung.

**Soll:** **Ein** konsistentes Icon für den globalen Editor an **beiden** Stellen; `BarChart` bleibt
allein der Statistik vorbehalten.
- **Empfehlung: `GhostTalkIcons.Book`** (ganzes Buch = Gesamtübersicht). Verfügbar, semantisch passend
  und distinkt von Statistik (`BarChart`), Seite/Focused (`Sitemap`) und Raster (`GridView`).
  → Zoom-Schema dann: **Übersicht = Book, Seite = Sitemap, Raster = GridView.**
- Ändern: `PageWorkbenchScreen.kt:140` (Pille) **und** `PageListScreen.kt:263/429` (Einstieg) auf `Book`.
- (Die Icon-Auswahl in `LocalIcons.kt` ist begrenzt — falls ein „Map/Graph"-Glyph gewünscht ist, müsste
  ein neuer Vektor ergänzt werden. `Book` ist die beste vorhandene Option.)

## L — Öffnen des globalen Editors beschleunigen (besonders beim Wiederöffnen)

**Ist:** Die teuren Ableitungen sind **composition-scoped `remember`** und werden bei **jedem** Mount
des Editors **synchron auf dem Main-Thread** neu berechnet — kein Cache über Öffnen/Schließen hinweg:
- Graph-Build `BookNavigationGraph.from(...)` — `StructureEditorScreen.kt:75`.
- Overview-Layout `calculateOverviewLayout(...)` (DFS + Barycenter + `connectedComponents`) —
  `StructureOverviewCanvas.kt:112`.
- Problems `orphans()/deadEnds()` — `StructureBadges.kt:34`.
→ Zweites Öffnen rechnet alles erneut → spürbare Verzögerung/Jank.

**Soll:** Ergebnisse über die Composition hinaus **cachen** und **off-main** berechnen.
1. **Cache in überlebendem Scope:** Graph, Overview-Layout und Problems in einem Scope halten, der
   Editor-Öffnen/-Schließen übersteht — z. B. buch-/aktivitätsweite `ViewModel`-`StateFlow`s, gekeyt auf
   eine **Inhalts-Version** (Hash/Revision der Seitenliste). Solange sich der Inhalt nicht ändert, liefert
   das Wiederöffnen das **gecachte** Ergebnis → quasi instant.
2. **Off-Main:** Die Berechnung in einem Background-Dispatcher (`Dispatchers.Default`) erledigen (wie schon
   bei der Suche, vgl. async `StructureTreeNavigator`), Ergebnis als State exponieren → kein Main-Thread-Jank
   beim ersten Öffnen.
3. **Invalidierung:** Cache verwerfen, wenn sich die Inhalts-Version ändert (Seite hinzugefügt/gelöscht,
   Verbindungen/Buttons geändert).
4. Composables lesen nur noch das gecachte Ergebnis (kein `remember { schwereBerechnung }` mehr im UI).

**Dateien:** `StructureEditorScreen.kt`, `StructureOverviewCanvas.kt`, `StructureBadges.kt` + ein
ViewModel/Repository als Cache-Halter (z. B. `PageViewModel` oder dedizierter `OverviewLayoutCache`).

> Hinweis: harmoniert mit **D** (Viewport vor erstem Zeichnen zentrieren) — wenn Layout bereits
> gecacht/fertig vorliegt, ist auch die initiale Zentrierung ohne Snap einfacher.

## M — Graph-Layout schöner aufteilen (nach Orphan-Extraktion)

**Kontext:** Sobald die verwaisten Seiten samt Kanten aus dem Canvas raus sind (**B**), enthält
`calculateOverviewLayout` (`StructureOverviewCanvas.kt:588`) nur noch den **erreichbaren** Graphen +
echte (mehrknotige) getrennte Komponenten — kein Streu-Rauschen aus Einzel-Orphans mehr. Das lässt sich
deutlich aufgeräumter anordnen.

**Ist heute:** Hauptkomponente wird gelayoutet, danach werden **alle anderen Komponenten untereinander
gestapelt** mit festem `+100f`-Abstand (`~Z. 739-754`). Einzel-Orphans landen so als viele kleine
Cluster weit unten → viel leerer Pan-Raum, unausgewogenes Bild.

**Soll:**
1. **Orphans ganz aus dem Layout nehmen** (sie leben im aufklappbaren Container aus **B**) — d. h.
   `calculateOverviewLayout` ignoriert Orphan-Knoten komplett, statt sie als Mini-Komponenten zu stapeln.
2. **Restliche getrennte Komponenten kompakter anordnen:** statt strikt untereinander mit fixem Abstand
   z. B. **nebeneinander/als Raster** packen (Platz nutzen, weniger vertikale Leere), Größe-/Abstands-
   Heuristik an die tatsächliche Komponentenzahl koppeln.
3. **Hauptkomponente besser zentrieren/balancieren:** vertikale Leere reduzieren, Spalten-Gaps und
   Barycenter-Ordering nach dem Wegfall der Orphans neu bewerten (gleichmäßigeres Gesamtbild).
4. Bestehende Routing-/Staffelungs-Logik der Kanten beibehalten (kein Regress beim Linien-Layout —
   vgl. Memory „Struktur-Graph-Routing").

**Dateien:** `StructureOverviewCanvas.kt` (`calculateOverviewLayout`, Komponenten-Anordnung).

> Reihenfolge: **nach B** (Orphan-Extraktion) umsetzen — vorher bringt das Aufräumen wenig.

## N — E-Nachbesserung: Switcher & Overflow überlappen im Portrait

**Status:** E (Action-Bar v2) ist umgesetzt (A–F erledigt, uncommitted, Build grün) und deutlich besser.
**Aber:** Im **Phone-Portrait überlappen sich Mode-Switcher und ⋮-Overflow** (siehe Screenshot).

**Ursache** (`AdaptiveActionBar.kt`, `AdaptiveEditorBar`):
- Linke Gruppe (Titel + Switcher) wird ab `x=0` platziert (Z. ~190-201), rechte Gruppe (Inline-Actions
  + ⋮ + ×) von `maxW` nach links (Z. ~204-227) — **ohne gegenseitige Kollisionsprüfung**.
- Der Titel wird mit `finalTitleMaxWidth.coerceAtLeast(minTitleWidth = 72.dp)` gemessen (Z. 121/177).
  Reicht der Platz für `Switcher + Exit + Overflow + 72dp-Titel` nicht (schmales Portrait + breiter
  3-Segment-Switcher + **gleichzeitig ⋮ und ×**), wird das Budget negativ; die Rücknahme-Schleife
  (Z. 162-167) entfernt aber nur **Inline-Actions** — sind keine da, bleibt das Defizit und die rechte
  Gruppe überlappt den Switcher.

**Fix:**
1. **Titel zuerst nachgeben lassen:** Titel mit `…coerceAtLeast(0)` statt `72.dp` messen (Titel
   ellipsiert/schrumpft, bevor irgendetwas überlappt).
2. **Überlappung hart ausschließen:** im `layout`-Block sicherstellen, dass die linke Kante der rechten
   Gruppe **≥** rechte Kante von (Titel + Switcher) ist (`rightX` gegen `currentX` clampen). Was nicht
   passt, geht in den Overflow.
3. **Redundanten Exit (×) sparen:** Wenn „Editor beenden" ohnehin im ⋮-Menü liegt, den **separaten
   ×-Button bei Platzmangel weglassen** (oder generell) — spart ~48dp und ist die häufigste Ursache,
   dass es eng wird.
4. **Sekundär (falls Switcher + rechte Gruppe selbst ohne Titel nicht passen):** kompakter
   Switcher-Modus im Portrait (engere/kleinere Segmente). Erst nötig, wenn 1–3 nicht reichen.

**Datei:** `core-ui/.../AdaptiveActionBar.kt` (`AdaptiveEditorBar`).

## O — NavBar umstrukturieren: globaler Editor prominent + Vorlagen-Tab

**Ist:** `BookShellScreen` hat `enum BookShellTab { Inhalte, Statistik }` + den „Nutzermodus"-Eintrag
als Aktion. NavBar = **Nutzermodus · Inhalte (Seitenliste) · Statistik**. Button-Vorlagen werden über
eine separate Route `"templates"` (`onNavigateToTemplates`) bearbeitet.

**Soll — neue NavBar-Reihenfolge:**
1. **Nutzermodus** (wie bisher)
2. **Globaler Editor** (NEU, **prominent zwischen Nutzermodus und Seiten** platziert) → öffnet die
   Gesamtübersicht (Editor im `global`-Modus). Icon `GhostTalkIcons.Book` (konsistent zu **I**).
3. **Seiten** (= heutiges „Inhalte", **umbenannt**) → Seitenliste (`PageListScreen`).
4. **Vorlagen** (NEU) → ein Screen, in dem **Seiten-Vorlagen (`PageTemplate`)** *und*
   **Button-Vorlagen (`ButtonTemplate`)** bearbeitbar sind (bestehende `"templates"`-Bearbeitung
   einbinden/erweitern, plus Seiten-Vorlagen).
5. **Statistik** (wie bisher, Icon `BarChart`).

**Umsetzung:**
- `BookShellTab` erweitern/umbenennen: `Inhalte → Seiten`, neue Tabs `GlobalerEditor` und `Vorlagen`.
  Hinweis: `BookShellTabSaver` nutzt `valueOf` mit Fallback (`try/catch → default`) — alte gespeicherte
  „Inhalte"-Werte fallen sauber auf den neuen Default zurück (Migration unkritisch).
- Strings: `nav_content` „Inhalte" → „Seiten"; neue `nav_overview`/`nav_editor` („Übersicht"/„Editor")
  und `nav_templates` („Vorlagen").
- Globaler-Editor-Eintrag: entweder als Shell-Tab den Editor (`PageWorkbench` GLOBAL) einbetten **oder**
  zur bestehenden Editor-Route (`mode=global`) navigieren (wie heute `onOpenStructureEditor`). Decision
  bei Umsetzung; Einbetten = konsistenter mit den anderen Tabs.
- „Vorlagen"-Screen: Seiten- und Button-Vorlagen in einem Screen (z. B. zwei Sektionen oder die
  bestehende `EditorSidePanel`/`ButtonTemplatesPanel`-Bausteine wiederverwenden).
- Icons: Nutzermodus `PlayArrow`, Global `Book`, Seiten `List`, Vorlagen (Templates-Icon, z. B.
  `Description`/passendes), Statistik `BarChart`.

**Beziehung zu anderen Punkten:**
- **Ersetzt K** (Einstellung „Inhalte"-Einstiegspunkt): Statt eines Settings-Toggles bekommt der globale
  Editor einen **eigenen** prominenten Tab; „Seiten" bleibt separat. K entfällt damit (oder reduziert sich
  auf „welcher Tab ist der Start-Tab").
- Ergänzt **J** (Seiten-Vorlagen in den graphischen Editoren): der Vorlagen-Tab ist der zentrale Ort, an
  dem Seiten-Vorlagen gepflegt werden.

**Dateien:** `BookShellScreen.kt` (NavBar + `BookShellTab` + Content-`when`), `GhosTTalkNavHost.kt`
(Routing/Templates), `strings.xml`, neuer/erweiterter Vorlagen-Screen.

## P — Orphan-Container an sinnvolle (fixe) Position (B/M-Nachbesserung)

**Ist:** Der „Verwaiste Seiten"-Container ist **im pan-/zoombaren Canvas** platziert
(`StructureOverviewCanvas.kt:510-551`, innerhalb des `graphicsLayer`-transformierten Nodes-Box, Z. 424)
und an `orphansMinY - 48.dp` verankert — nach dem `+300f`-Gap (**M**) liegt das **ganz unten im Layout**
und wandert/zoomt mit → der Container ist „extrem weit weg" und schwer erreichbar.

**Soll:** Den Container als **fixes Overlay** an den Viewport heften (wie Legende `Alignment.BottomStart`
Z. 619 und Zoom-HUD `Alignment.BottomEnd` Z. 557) — **nicht** mehr im transformierten Canvas. Dann ist
er unabhängig von Pan/Zoom **immer an derselben, gut erreichbaren Stelle**.
- Position so wählen, dass er Legende (unten links) und HUD (unten rechts) **nicht überlappt** —
  z. B. **oben links/oben rechts** des Canvas, eingeklappt als Chip „Verwaiste Seiten (N) ▸".
- **Aufklappen** zeigt die Orphans als **Liste im Overlay** (Seitennamen, scrollbar); Tippen auf einen
  Eintrag → `onFocus`/`onZoomInto` (zentriert den Canvas auf die Seite, ihre Kanten erscheinen dann
  gemäß **B**). Damit ist die Position der Orphans im Layout egal — der Zugriff läuft über das Overlay.
- Optional: das `+300f`-Gap und das Verstecken/Layouten der Orphan-Knoten im Canvas kann dann
  reduziert/entfallen, da der Zugriff über das Overlay erfolgt (vereinfacht **M**).

**Datei:** `StructureOverviewCanvas.kt` (Orphan-Container aus dem transformierten Box heraus in ein
Viewport-festes Overlay verschieben).

## J — Seiten-Vorlagen auch im globalen Editor anbieten

**Ist:** Der **fokussierte** Editor kann Seiten aus Vorlagen erstellen — `onCreatePage(name, rows, cols,
templateId, callback)` (`StructureEditorScreen.kt:349`) + Template-Picker (`TargetPageSelectionDialog`,
`templates: List<PageTemplate>`), ausgelöst über den FAB „Zielseite verbinden" in `StructureFocusCanvas`
(gilt für CARDS **und** GRAPH). Der **globale** `StructureOverviewCanvas` hat **keinen** solchen
Einstieg — er bekommt weder `templates` noch `onCreatePage` (Signatur Z. 94-106) und keine Add-Affordance.

**Soll:** Seiten-Erstellung (inkl. **aus Seiten-Vorlage**) auch im globalen Editor.
- `StructureOverviewCanvas` um `templates: List<PageTemplate>` + `onCreatePage(...)` erweitern
  (`StructureEditorContent` reicht beides bereits an den Focused-Canvas durch — analog an den Overview
  geben).
- **Add-Affordance** im Overview: ein FAB „Seite hinzufügen" (eigene Ecke, kollidiert nicht mit Zoom-HUD
  `BottomEnd` / Legende `BottomStart` / Orphan-Overlay aus **P**). Klick → derselbe Picker wie im
  Focused-Editor (blanko **oder** aus `PageTemplate`).
- Neu erstellte Seite ist zunächst **ohne eingehende Kante** → erscheint als Orphan im Orphan-Overlay
  (**B/P**); von dort fokussierbar und per „Zielseite verbinden" einbindbar. (Konsistentes Verhalten,
  kein Sonderfall nötig.)
- Picker/__Dialog__ über beide Editoren **identisch** (gleiche Komponente) — keine zweite Variante bauen.
- Bezug zu **O**: die Seiten-Vorlagen, die hier verwendet werden, werden im neuen **Vorlagen**-Tab gepflegt.

**Dateien:** `StructureOverviewCanvas.kt` (Params + FAB + Picker), `StructureEditorContent.kt`
(Durchreichen), ggf. `TargetPageSelectionDialog` wiederverwenden.

## K — durch O ersetzt
Statt eines Settings-Toggles für den „Inhalte"-Einstiegspunkt bekommt der globale Editor in **O** einen
eigenen prominenten NavBar-Tab; „Seiten" bleibt separat. K entfällt (höchstens optional: welcher Tab ist
Start-Tab — bei Bedarf später).

## Weitere Style-/UX-Beobachtungen (Sammlung)
- `BarChart`-Doppelnutzung (Statistik vs. Global) → durch **I** gelöst.
- Hartcodierte Labels überall (Switcher, Tabs, Action-Labels) → konsolidiert in **F** (i18n).
- Collapse-Pfeil-Platzierung & Tab-Stil zwischen Grid- und Struktur-Sidebar → vereinheitlicht in **G**.
- Hintergründe Global vs. Focused → **A**.

## Reihenfolge (Vorschlag)
1. **A** Hintergrund + **I** Global-Icon (kleine, sofort sichtbare Konsistenzgewinne).
2. **C + D** Zentrierung Focused & Overview gemeinsam (geteilte Logik).
3. **B** Orphan-Bereich + Kanten-Regel → dann **M** Graph-Layout schöner aufteilen.
4. **G** Sidebar-Tab-Stil vereinheitlichen + gemeinsame `EditorSidePanel`.
5. **H** Tablet-Navigation.
6. **L** Performance/Caching globaler Editor (gut mit **D** zu kombinieren).
7. **E** Action-Bar v2 → **N** Overlap-Nachbesserung (Switcher/Overflow Portrait).
8. **F** Cleanups (laufend miterledigen).

> **Stand:** A–N sind von Gemini umgesetzt. In Arbeit/offen, in dieser Reihenfolge:
9. **N/B-M-Nachbesserungen** aus dem Review (Exit-Overflow-Menüfilter; einheitliche Orphan-Menge) — in Arbeit.
10. **P** Orphan-Container an fixe Overlay-Position (klein, behebt „extrem weit weg").
11. **J** Seiten-Vorlagen auch im globalen Editor (FAB + Picker, Params durchreichen).
12. **O** NavBar umstrukturieren (Globaler Editor prominent · „Seiten" · „Vorlagen"-Tab) — größter Brocken, am Ende.

## Test (manuell, vor dem Commit)
- Zoomwechsel Global↔Focused: **gleicher Hintergrund**, kein Sprung, fokussiertes Element jeweils zentriert.
- Focused (Graph **und** Karten): fokussierte Seite startet zentriert; Expand/Collapse scrollt nicht weg.
- Global: Orphans in eigenem Bereich, **keine** ausgehenden Kanten — außer der Orphan ist fokussiert.
- Tablet: Action-Bar zeigt Aktionen inline (nicht alles im `⋮`), Switcher nie geklemmt.
- Phone-Portrait: Switcher sichtbar, Overflow sauber.
- Sidebar in **Grid- und Struktur-Editor identisch** (gleicher Tab-Stil, gleiche Reihenfolge/Labels, gleiche `EditorSidePanel`).
- Tablet-Navigation wirkt stimmig (nicht „oben klebend"), Icons angemessen groß.
- Global-Editor zeigt **überall dasselbe** Icon (`Book`); Statistik behält `BarChart` exklusiv.
- Globalen Editor 2×/3× öffnen: zweites Öffnen **spürbar schneller** (gecacht), kein Main-Thread-Jank.
- Orphans standardmäßig **eingeklappt** im aufklappbaren Container; aufklappen → Orphans sichtbar/fokussierbar.
- Orphan-Container an **fixer, gut erreichbarer** Position (Overlay, nicht im Canvas), überlappt Legende/HUD nicht.
- Graph nach Orphan-Wegfall **kompakter/balancierter** angeordnet (weniger leerer Pan-Raum), Kanten-Routing ohne Regress.

## Arbeitsteilung
Gemini setzt um, Claude reviewt — phasenweise, **ohne** Zwischen-Commits.
