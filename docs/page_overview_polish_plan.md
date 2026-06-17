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
1. **Eigener Orphan-Bereich:** Alle Orphans in einen klar abgegrenzten, gemeinsamen Bereich
   gruppieren (z. B. eigene Spalte/Cluster am Rand oder unten, optisch getrennt + kleine Überschrift
   „Verwaiste Seiten"). Nicht mehr zwischen die normalen Komponenten gemischt.
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

## Reihenfolge (Vorschlag)
1. **A** Hintergrund (klein, sofort sichtbarer Konsistenzgewinn).
2. **C + D** Zentrierung Focused & Overview gemeinsam (geteilte Logik).
3. **B** Orphan-Bereich + Kanten-Regel.
4. **E** Action-Bar v2.
5. **F** Cleanups (laufend miterledigen).

## Test (manuell, vor dem Commit)
- Zoomwechsel Global↔Focused: **gleicher Hintergrund**, kein Sprung, fokussiertes Element jeweils zentriert.
- Focused (Graph **und** Karten): fokussierte Seite startet zentriert; Expand/Collapse scrollt nicht weg.
- Global: Orphans in eigenem Bereich, **keine** ausgehenden Kanten — außer der Orphan ist fokussiert.
- Tablet: Action-Bar zeigt Aktionen inline (nicht alles im `⋮`), Switcher nie geklemmt.
- Phone-Portrait: Switcher sichtbar, Overflow sauber.

## Arbeitsteilung
Gemini setzt um, Claude reviewt — phasenweise, **ohne** Zwischen-Commits.
