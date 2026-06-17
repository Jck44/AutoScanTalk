# Plan: Geteilte Kanten-Render-Schicht für die Struktur-Canvas (Vereinheitlichung)

**Gemini setzt um, Claude reviewt/plant.** Vorgeschaltet zu `docs/graph_performance_improvements_plan.md` —
damit die dort beidseitigen Tasks (**2** und **6**) **einmal** statt zweimal umgesetzt werden.

## Ziel & Abgrenzung (wichtig)

`StructureOverviewCanvas` (**global**, Spalten-/Baum-Layout, Elbow-Routing über `midX`) und
`StructureGraphView` (**focused**, Radial um Center-Knoten, Bus-Routing) sind heute **getrennte
Implementierungen ohne gemeinsamen Zeichencode**. Es gibt aber schon eine geteilte Pure-Helfer-Datei
`StructureCanvasLogic.kt` (`navigableButtons`, `distanceToSegment`) und die etablierte Konvention „Reuse
statt Duplikat" aus dem Canvas-Refactor (`docs/structure_canvas_refactor_plan.md`, E4).

**Vereinheitlicht wird die Kanten-Render-*Toolkit-Schicht*, nicht die beiden Screens.**
- ✅ Geteilt: Kanten-Stile, Pfeilkopf-Zeichnen, Segment-/Geometrie-Helfer, das „geteilten Trunk einmal +
  Stubs pro Kante"-Zeichenmuster.
- ❌ **Nicht** angefasst/zusammengelegt: die Layouts selbst (Spalten/Baum vs. Radial), Pan/Zoom/Gesten
  (nur Overview), die bewusst unterschiedliche Routenform (gestaffeltes Baum-/Bus-Routing ist gewollt,
  siehe Memory `structure-graph-routing`). Keine Screen-Fusion.

**Reihenfolge-Empfehlung:** **Diesen Plan VOR** den Perf-Tasks 2 und 6. Die canvas-spezifischen
Perf-Tasks (**1, 3, 4, 5**) sind unabhängig und können davor/danach/parallel laufen.

---

## Phase U1 — Geteilte Kanten-Stile (verhaltenserhaltend, absorbiert Perf-Task 2)

**Ist:** `Stroke`/`PathEffect`/Breiten/Alphas werden in beiden Canvas pro Kante neu erzeugt
(`StructureOverviewCanvas.kt:362-375`, `StructureGraphView.kt:565-575`, `:627-638`).

**Soll:** Eine geteilte, einmal pro Draw berechnete Stil-Quelle in `StructureCanvasLogic.kt` (oder neuer
`StructureEdgeRendering.kt`):
```kotlin
data class EdgeStyles(
    val solid: Stroke, val focusedSolid: Stroke,
    val dashed: Stroke, val focusedDashed: Stroke,
    val arrowLength: Float, val arrowWidth: Float
)
@Composable fun rememberEdgeStyles(): EdgeStyles  // via LocalDensity, remember
```
Beide Canvas konsumieren `rememberEdgeStyles()` und wählen im Loop nur noch die Referenz. Farben/Alphas
(`primaryColor.copy(alpha=…)`) bleiben Aufrufer-seitig (Theme-abhängig) — nur die Stroke-/PathEffect-
Objekte werden geteilt + gehoistet. **Damit ist Perf-Task 2 für beide erledigt.**

**Fertig wenn:** identisches Bild; keine `Stroke(...)`/`dashPathEffect(...)`-Allokation mehr im Draw-Loop
beider Canvas; Build grün.

---

## Phase U2 — Pfeilkopf-Zeichnen teilen (verhaltenserhaltend)

**Ist:** Der tangenten-basierte Pfeilkopf existiert nur im Focused-Outgoing (`StructureGraphView.kt:644-658`);
der Overview hat eine eigene Pfeil-Variante (`StructureOverviewCanvas.kt:383-400`). Incoming (focused) hat
gar keinen (siehe Perf-Task 5).

**Soll:** Eine geteilte Extension in `StructureCanvasLogic.kt`:
```kotlin
fun DrawScope.drawArrowhead(tip: Offset, dirX: Float, dirY: Float, length: Float, width: Float, color: Color)
```
Beide Canvas rufen sie auf (Richtung aus dem End-Tangens). Vereinheitlicht zugleich die leicht
abweichende Pfeil-Geometrie.

**Fertig wenn:** beide nutzen `drawArrowhead`; Pfeile sehen unverändert/konsistent aus; Build grün.

---

## Phase U3 — Geteiltes Segment-Modell + „Trunk einmal"-Primitive (Heimat von Perf-Task 6)

**Ist:** Beide Canvas berechnen ihre Kanten-Geometrie inline und malen pro Kante den **vollen** Pfad inkl.
geteiltem Trunk/Bus → Overdraw + Alpha-Stacking (Perf-Plan Punkt 6a/6b).

**Soll:** Ein gemeinsames, geometrie-unabhängiges Modell + Zeichen-Primitive:
```kotlin
// Ein Bündel = ein geteiltes Trunk-Segment + N eindeutige Stubs (eine Achse).
data class EdgeBundle(val trunk: Segment, val stubs: List<Stub>)
fun DrawScope.drawEdgeBundle(bundle: EdgeBundle, sharedStyle: Stroke, sharedColor: Color, ...)
// zeichnet trunk EINMAL, dann jeden stub (Stub kann eigenen Stil/Highlight haben).
```
Jeder Canvas baut aus **seiner** Geometrie (Elbow-`midX` bzw. Bus-`busX`) die `EdgeBundle`-Liste und ruft
`drawEdgeBundle`. Damit ist die **Overdraw-Beseitigung (Task 6) in EINER Primitive** — sie wirkt in beiden
Canvas, ein Review.

**Realismus / Fallback:** Sollten Elbow- und Bus-Geometrie sich einer **einzigen** `drawEdgeBundle`-Signatur
widersetzen, ist das Mindestziel: **geteiltes `Segment`/`Stub`-Modell + geteilte Dedup-/Reihenfolge-Logik**,
und Task 6 wird gegen dieses gemeinsame Modell in jedem Canvas umgesetzt (immer noch ein Mentalmodell, kein
voll dupliziertes Zeichnen). Diese Entscheidung fällt beim Umsetzen anhand des realen Codes — vorher nicht
erzwingen.

**Selektion:** Outgoing-Edge-Highlight (error-Farbe/dicker) bleibt am **Stub** (deckt sich mit Hit-Test,
`StructureGraphView.kt:507-522`); der geteilte Trunk immer Basisfarbe.

**Fertig wenn:** geteilter Trunk/Bus **gleichmäßig** eingefärbt (kein dunkleres Sammelsegment), Routenform
und Stub-Geometrie unverändert in beiden Canvas, Quer- **und** Hochformat; Edge-Selektion funktioniert;
Build + Tests grün.

---

## Verhältnis zum Perf-Plan (was wandert wohin)

| Perf-Task | Vor U | Nach U |
|-----------|-------|--------|
| 2 Stroke/PathEffect hoisten | 2× (beide Canvas) | **1× in U1** ✅ |
| 6 Overdraw/Alpha-Stacking | 2× (6a + 6b) | **1× in U3** ✅ |
| 1 `find`→Map | nur Focused | unverändert, **separat** |
| 3 `partition` memoisieren | nur Overview | unverändert, **separat** |
| 4 `interactionSources` | nur Overview | unverändert, **separat** |
| 5 Incoming-Stil + Pfeilkopf | nur Focused | Pfeilkopf via **U2**, Stil-Fix separat (Focused) |

→ Nach U sind die beiden „beidseitigen" Perf-Tasks aus dem Perf-Plan **erledigt** (U1, U3); im Perf-Plan
bleiben dann nur noch die canvas-spezifischen 1/3/4/5.

---

## Regeln & Reihenfolge

1. **U1 → U2 → U3.** Jede Phase verhaltenserhaltend außer der expliziten Overdraw-Änderung in U3.
2. **U1/U2 = reine Extraktion**, pixelidentisch. **U3** ist visuell sensibel → Vorher/Nachher-Screenshots
   in beiden Canvas, beide Orientierungen.
3. Geteilter Code nach `StructureCanvasLogic.kt` (klein halten) oder neue `StructureEdgeRendering.kt`
   (< ~450 Z., Projekt-Konvention).
4. **Keine Layout-/Screen-Fusion.** Pan/Zoom, Spalten-/Radial-Layout, Routenform bleiben unberührt.
5. Build nach jeder Phase grün (`JAVA_HOME` = JBR); `StructureCanvasLogicTest` grün; für neue Pure-Logik
   (Segment-Bau, Bündelung) gern Unit-Tests.

## Review-Schwerpunkte (Claude)
- [ ] U1/U2 pixelidentisch; keine Stroke/PathEffect-Allokation mehr im Draw-Loop beider Canvas.
- [ ] U3: kein dunkleres Sammelsegment mehr; Routenform/Stubs unverändert; Selektion über Stub intakt;
      Quer- + Hochformat in **beiden** Canvas geprüft.
- [ ] Keine Screen-/Layout-Fusion; Pan/Zoom (Overview) unangetastet.
- [ ] Geteilte Datei < ~450 Z.; keine Semantikänderung an ViewModels/Repos/Sync.
