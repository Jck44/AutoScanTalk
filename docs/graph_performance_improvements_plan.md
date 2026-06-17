# Plan: Performance-Politur der Graph-/Struktur-Editoren

Konsolidierter Performance-Plan für die Canvas-Editoren (`StructureOverviewCanvas`,
`StructureGraphView`). **Gemini setzt um, Claude reviewt. Keine Zwischen-Commits** — der Nutzer
testet am Stück, dann ein Commit.

Ausgangsfrage des Nutzers war, ob Kanten **mehrfach gezeichnet** werden und sich überlagern. Das wurde
geprüft → siehe Punkt 0 (kein Handlungsbedarf, nur dokumentiert). Die übrigen Punkte sind echte, beim
Prüfen gefundene Ineffizienzen.

---

## 0 — VERIFIZIERT: Kanten werden bereits dedupliziert (kein Task, nur Kontext)

Damit niemand das „nochmal absichert" und dabei die bewusste Routenführung kaputtmacht:

- **Overview:** `edgesToDraw` baut die Kantenliste mit einem `seen = mutableSetOf<Pair<String,String>>()`
  und `if (!seen.add(sourceId to targetId)) return@forEach` (`StructureOverviewCanvas.kt:160/170`).
  Mehrere Buttons von A→B erzeugen **eine** Linie. Richtungsabhängig gewollt (A→B und B→A bleiben
  getrennt).
- **Focused-Graph:** `distinctIncoming = incomingSources.distinct()` und
  `distinctOutgoing = outgoingEdges.map { it.targetPageId }.distinct()` (`StructureGraphView.kt:101-102`).
  Die Zeichenschleife läuft über `outgoingColumns`/`incomingNodeIds` (eindeutige IDs) → keine
  überlagerten Doppellinien.
- Das visuelle „Überlagern" gemeinsamer Streckenabschnitte ist die **gewollte Bus-/Elbow-Routenführung**
  (gestaffeltes Baum-/Bus-Routing). **Nicht** durch gerade Linien oder „Dedup" ersetzen — das ist ein
  bekannter Regressionspunkt.

→ **Hier nichts ändern.**

---

## Geltungsbereich je Canvas — WICHTIG: beide Graphen, nicht nur einen

Die zwei Editoren sind **getrennte Implementierungen ohne gemeinsamen Zeichencode**:
- `StructureOverviewCanvas` (**global**): Spalten-/Baum-Layout, Elbow-Routing über `midX`.
- `StructureGraphView` (**focused**): Radial um einen Center-Knoten, Bus-Routing.

Eine Änderung in der einen Datei wirkt **nicht** automatisch in der anderen. Darum pro Task explizit, wo
er greift:

| Task | Global (`StructureOverviewCanvas`) | Focused (`StructureGraphView`) |
|------|-----------------------------------|-------------------------------|
| 1 `find`→Map | – (kein `find`-Hotpath) | ✅ |
| 2 `Stroke`/`PathEffect` hoisten | ✅ (`:362-368`) | ✅ (`:565-575`, `:627-637`) |
| 3 `partition` memoisieren | ✅ | – (kein `partition`) |
| 4 `interactionSources` aufräumen | ✅ | – |
| 5 Incoming-Stil rechtwinklig | – (kein Incoming/Center-Konzept) | ✅ |
| 6 Trunk/Bus-Overdraw + Alpha-Stacking | ✅ (Elbow-Variante, s.u.) | ✅ (Bus-Variante, s.u.) |

Tasks 2 und 6 sind die, die **in beiden** Canvas umgesetzt werden müssen — nicht nur in einem.

---

## 1 — Lineare `find`-Suchen im Focused-Graph durch Map ersetzen

**Ist:** `outgoingEdges.find { it.targetPageId == targetId }` wird mehrfach pro Knoten ausgeführt:
- im Tap-Handler (`StructureGraphView.kt:523`),
- in der Zeichenschleife (`StructureGraphView.kt:590`),
- bei `outgoingNodeIds.indexOf(targetId)` (`StructureGraphView.kt:670`).

Das ist je O(n) über alle ausgehenden Kanten, pro Knoten, pro Recomposition/Tap → O(n²) bei vielen
Zielen.

**Soll:** Einmalig eine Lookup-Map aufbauen und wiederverwenden:
```kotlin
val outgoingEdgeByTarget = remember(outgoingEdges) {
    outgoingEdges.associateBy { it.targetPageId }
}
```
Alle `outgoingEdges.find { it.targetPageId == targetId }` → `outgoingEdgeByTarget[targetId]`. Für die
Index-Suche ggf. zusätzlich `outgoingNodeIds.withIndex().associate { it.value to it.index }` cachen, falls
`indexOf` in einem Hotpath liegt.

**Dateien:** `StructureGraphView.kt` (Konstruktion oben bei Z. 101 f.; Verwendungen Z. 523, 590, 670).

---

## 2 — Allokationen aus dem DrawScope hoisten (`Stroke`/`PathEffect`)

**Ist:** In beiden Canvas-Draw-Lambdas werden pro Kante `Path()`, `Stroke(...)` und
`PathEffect.dashPathEffect(...)` **neu** erzeugt:
- Overview: `StructureOverviewCanvas.kt:349` (Path), `:362-368` (Stroke + dashPathEffect), `:386` (arrowPath).
- GraphView: `StructureGraphView.kt:543/592/652` (Path), `:565-575` (Stroke + dashPathEffect).

`Path()` muss pro Kante neu sein (unterschiedliche Geometrie) — okay. Aber `Stroke` und der
`dashPathEffect` sind **konstant** (nur zwei Varianten: solide vs. gestrichelt, feste Breiten). Sie werden
pro Kante pro Redraw neu alloziert → unnötiger GC-Druck bei großen Graphen / während Drag.

**Soll:**
- Die zwei `Stroke`-Varianten (solide / dashed) und das `PathEffect`-Objekt **einmal** außerhalb der
  Schleife berechnen (Breiten via `density` umgerechnet, in einem `remember` oder lokal vor der
  `forEach`). Im Loop nur noch die passende Referenz auswählen.
- Beachten: In der Overview hängt die Breite an `isFocusedConnection` (3.5 vs. 1.5 dp). Also vier
  vorab-berechnete Strokes (focused/unfocused × solide/dashed) statt Neu-Allokation pro Kante.
- `arrowLength`/`arrowWidth` ebenfalls einmal pro Draw via `toPx()` berechnen statt pro Kante.

**Dateien:** `StructureOverviewCanvas.kt` (Draw-Block ab Z. 324), `StructureGraphView.kt` (Draw-Block ab Z. 535).

---

## 3 — `partition` aus dem Draw-Lambda in die memoisierte Kantenliste verschieben

**Ist:** Im Overview-Draw läuft pro Redraw:
```kotlin
val (focusedForward, unfocusedForward) = forwardEdges.partition {
    it.sourceId == focusedPageId || it.targetId == focusedPageId
}
```
(`StructureOverviewCanvas.kt:406`). Das ist eine vollständige Listen-Partitionierung + zwei neue Listen
**bei jedem Zeichnen**, obwohl sich Eingaben nur ändern, wenn `edgesToDraw`/`focusedPageId` wechseln.

**Soll:** Die Aufteilung in `unfocused` / `focused` / `backward` bereits im `remember(...)`-Block von
`edgesToDraw` (`StructureOverviewCanvas.kt:157`) erledigen und als Triple (oder kleine Datenklasse)
zurückgeben. Das Draw-Lambda iteriert dann nur noch fertige Listen — keine Allokation, keine
Partitionierung im Hotpath. (`focusedPageId` ist ohnehin schon Key des `remember`.)

**Dateien:** `StructureOverviewCanvas.kt` (`edgesToDraw` Z. 157-184; Draw-Block Z. 403-421).

---

## 4 — `interactionSources`-Map an die aktuelle Knotenmenge koppeln (geringe Prio)

**Ist:** `val interactionSources = remember { mutableMapOf<String, MutableInteractionSource>() }`
(`StructureOverviewCanvas.kt:135`) wird per `getOrPut(pageId)` befüllt und nie bereinigt. Innerhalb einer
Komposition unkritisch (begrenzt durch Seitenzahl), aber gelöschte/umbenannte Seiten hinterlassen tote
Einträge, solange der Editor offen bleibt.

**Soll:** Beim Wechsel der Seitenmenge auf vorhandene IDs eindampfen — z. B. in einem
`LaunchedEffect(graph.allPageIds)` Keys entfernen, die nicht mehr in `layout`/`allPageIds` sind. Niedrige
Priorität, eher Sauberkeit als messbarer Gewinn.

**Dateien:** `StructureOverviewCanvas.kt` (Z. 135, plus kleiner Aufräum-Effekt).

---

## 5 — Incoming-Kanten im Querformat auf rechtwinkligen Stil angleichen (Visual-Konsistenz)

**Ist:** Der Focused-Graph hat vier Zeichenfälle, drei sind rechtwinklig, **einer ist eine Kurve**:
- Incoming **Querformat**: `cubicTo` → Bézier-**Kurve** (`StructureGraphView.kt:550-554`).
- Incoming **Hochformat**: Elbow `lineTo` → rechtwinklig (`:561-563`).
- Outgoing **Querformat**: orthogonaler Bus `lineTo` → rechtwinklig (`:610-613`).
- Outgoing **Hochformat**: Elbow `lineTo` → rechtwinklig (`:623-625`).

→ Im Querformat schwingen die Incoming-Kanten als Kurve herein, die Outgoing laufen rechtwinklig.
Stilbruch.

**Soll:** Incoming-Querformat (`:544-554`) auf eine **gespiegelte Bus-Logik** umstellen, analog zum
Outgoing-Querformat:
- `startX = pt.first + (incomingWidthDp / 2).toPx()` (rechte Kante des Incoming-Chips),
- `busX = centerPoint.first - (centerWidthDp / 2).toPx() - 24.dp.toPx()` (vertikaler Bus links vom Center),
- `endX = centerPoint.first - (centerWidthDp / 2).toPx()`, `endY = centerPoint.second`,
- Pfad: `moveTo(startX, startY)` → `lineTo(busX, startY)` → `lineTo(busX, endY)` → `lineTo(endX, endY)`.

**Offene Frage an den Nutzer/Reviewer:** Die Incoming-Kanten zeichnen aktuell **keinen Pfeilkopf** (der
Arrowhead-Block `:644-658` liegt nur in der Outgoing-Schleife). Falls das kein Absicht ist, hier gleich
einen zum Center zeigenden Pfeilkopf ergänzen — sonst so lassen.

**Dateien:** `StructureGraphView.kt` (Incoming-Draw `:539-582`).

---

## 6 — Overdraw bei gemeinsamem Knoten beseitigen: Trunk/Bus einmal zeichnen (Fan-in/Fan-out)

> **Gilt für BEIDE Canvas** (unterschiedliche Geometrie, gleiche Ursache). Nicht nur im Focused-Graph
> umsetzen.

### 6a — Focused-Graph (`StructureGraphView`)

**Ist:** Alle Incoming-Kanten enden am selben Center-Knoten, alle Outgoing starten dort. Jede Kante
zeichnet ihren **kompletten** Pfad inkl. des **geteilten** Trunk-/Bus-Segments:
- Hochformat-Incoming (`:559-563`): `trunkX = centerPoint.first` und `endY` (Center-Oberkante) sind für
  **alle** Incoming-Kanten gleich → der vertikale Trunk wird N-mal übereinander gemalt.
- Querformat-Outgoing (`:602-613`): alle teilen denselben `busX` und das horizontale Einstiegssegment auf
  `centerY` → ebenfalls N-facher Overdraw.

Das sind **keine** Duplikate (die Kanten sind legitim verschieden) — es ist geteiltes Routing, das
mehrfach gemalt wird. Zwei Folgen:
1. **Alpha-Stacking → sichtbarer Artefakt:** Strokes haben `alpha = 0.6`; zwei überlagert ≈ 0.84. Der
   geteilte Trunk erscheint **dunkler** als die einzelnen Stubs. Bei gestrichelten „more"-Kanten wird's
   visuell unsauber.
2. **Fill-Rate-Verschwendung:** N volle Pfade über dieselben Pixel statt 1 Trunk + N kurze Stubs.

**Soll:** Routing in **geteiltes Segment** + **eindeutige Stubs** zerlegen:
- Den gemeinsamen Trunk/Bus **einmal** pro Seite (Incoming bzw. Outgoing) zeichnen, mit voller, einheitlicher
  Deckkraft — Länge = von der obersten bis zur untersten angebundenen Reihe.
- Pro Kante nur noch den **eindeutigen Stub** zeichnen (Knoten → Bus bzw. Bus → Center).
- **Selektions-Highlight** (Outgoing, error-Farbe + dicker, `:636/638`) bleibt am **Stub** — das deckt sich
  exakt mit dem, was der Tap-Hit-Test ohnehin als Segment nutzt (`:507-522`). Der gemeinsame Trunk bleibt
  immer in der Basisfarbe.
- „more"-Knoten: Stub gestrichelt wie bisher, der geteilte Trunk bleibt solide (Infrastruktur).

**Achtung:** Das ist visuell sensibel. Die gestaffelte Bus-/Baum-Routenführung ist **gewollt** (siehe
Memory `structure-graph-routing`) — Ziel hier ist **nur** Overdraw/Alpha-Stacking zu entfernen, **nicht**
die Routenform zu ändern. Vorher/Nachher-Screenshots vergleichen.

**Dateien:** `StructureGraphView.kt` (Incoming-Draw `:539-582`, Outgoing-Draw `:583-662`). Sinnvoll **nach**
bzw. zusammen mit Punkt 5 umzusetzen (beide fassen dieselbe Routing-Geometrie an).

### 6b — Globaler Overview (`StructureOverviewCanvas`)

**Ist:** Dasselbe Muster mit Elbow-Geometrie. Bei Fan-out (eine Quelle → mehrere Ziele derselben Spalte)
sind `startX`, `startY` und `endX` identisch → `midX` identisch (`StructureOverviewCanvas.kt:351-358`).
Damit ist das **erste horizontale Segment** (`moveTo(startX,startY)` → `lineTo(midX,startY)`, `:356`) für
alle diese Kanten deckungsgleich und wird N-fach gemalt; die vertikalen Segmente am gemeinsamen `midX`
überlappen nahe `startY`. Analog bei Fan-in nahe dem Ziel. Mit `alpha = 0.15` für unfokussierte Kanten
(`:374`) stapeln sich überlappende Strokes **sichtbar dunkler** (≈ 0.28 bei zweien).

**Soll:** Gleiche Idee wie 6a — geteilte Segmente nicht pro Kante wiederholen:
- Pro Quell-Spalten-Cluster das gemeinsame erste Horizontalsegment (und den geteilten `midX`-Vertikalast,
  soweit deckungsgleich) **einmal** zeichnen, dann nur die eindeutigen Reststücke pro Kante.
- Falls eine vollständige Bündelung zu invasiv ist: als Minimal-Variante die **Reihenfolge** so wählen,
  dass identische Segmente nicht durch Alpha-Stacking dunkeln — d. h. pro `(sourceId)`-Bündel das
  gemeinsame Segment **einmal** und die Stubs separat, statt N volle Pfade. Fokus-Kanten (alpha 0.8) wie
  bisher zuletzt obendrauf.

**Achtung:** Auch hier Routenform unverändert lassen (gestaffeltes Baum-/Bus-Routing ist gewollt, siehe
Memory `structure-graph-routing`). Ziel ist nur weniger Overdraw + kein dunkleres Sammelsegment.

**Dateien:** `StructureOverviewCanvas.kt` (`drawEdge` `:324-401`, Aufruf-Reihenfolge `:403-421`).

---

## Reihenfolge & Abnahme

Empfohlene Reihenfolge: **1 → 2 → 3 → 5 → 6 → 4** (1 hat den größten reinen Perf-Effekt; 5+6 fassen
dieselbe Routing-Geometrie an und gehören zusammen; 4 ist optional). Punkte 5 und 6 sind visuell sensibel —
**vor/nach mit Screenshots** in Quer- **und** Hochformat prüfen.

**Abnahmekriterien:**
- Punkte 1–4: visuell **identisches** Ergebnis — Knotenpositionen, Kantenführung (Bus/Elbow!), Pfeile,
  gestrichelte Backward/„more"-Kanten, Fokus-Hervorhebung unverändert.
- Punkt 5: Incoming-Querformat jetzt rechtwinklig wie Outgoing; Hochformat unverändert.
- Punkt 6: geteilter Trunk/Bus **gleichmäßig** eingefärbt (kein dunkleres Mittel-/Sammelsegment mehr),
  Routenform und Stub-Geometrie unverändert; Edge-Selektion (Highlight) funktioniert weiter über den Stub.
  **In beiden Canvas prüfen** (Focused 6a UND globaler Overview 6b) — bewusst gegen Fan-out/Fan-in mit
  vielen Kanten testen.
- Kein verändertes Tap-/Edge-Selektionsverhalten im Focused-Graph (Punkt 1 ändert nur das Lookup, nicht
  die Trefferlogik).
- Pan/Zoom im Overview weiterhin flüssig (läuft schon korrekt über `graphicsLayer` — **nicht** anfassen).
- Bestehende Tests grün: `StructureCanvasLogicTest`. Falls die Edge-Map (Punkt 1) in extrahierbare Logik
  wandert, gern ein kleiner Unit-Test dafür.
