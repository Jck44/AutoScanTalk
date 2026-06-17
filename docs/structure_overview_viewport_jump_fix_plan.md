# Plan: Viewport-Sprung beim Raus-Zoomen auf die globale Übersicht beheben

## Context / Symptom

Beim Raus-Zoomen vom **fokussierten Graph (L1)** auf die **globale Übersicht (L0)** springt der
Viewport sichtbar, bevor er sich auf die fokussierte Seite zentriert.

## Ursache

`PageWorkbenchScreen` rendert die Ebenen über `AnimatedContent`. GLOBAL und FOCUSED sind **getrennte
`when`-Zweige** → beim Wechsel wird `StructureOverviewCanvas` **neu gemountet**. Dadurch:

1. `scale`/`offset`/`canvasSize` sind nur `remember` (nicht gehoben/saveable) → starten auf Default:
   `scale = 0.9f`, `offset = Offset.Zero`, `canvasSize = IntSize.Zero`
   (`StructureOverviewCanvas.kt:173-175`).
2. Erste Frames: Inhalt wird bei `offset = Zero` (oben links) gezeichnet.
3. `onGloballyPositioned` setzt `canvasSize` → die Auto-Center-`LaunchedEffect`
   (`StructureOverviewCanvas.kt:181-193`) feuert **eine Frame später** und setzt `offset`
   **schlagartig** auf die zentrierte Position.
4. Ergebnis: harter Sprung von „oben links" zur zentrierten Seite — zusätzlich überlagert von der
   `scaleIn/scaleOut`-Transition des `AnimatedContent`.

Kurz: Die Zentrierung passiert **nach** dem ersten Zeichnen statt **davor**.

## Ziel

Die Übersicht erscheint **von der ersten Frame an** auf der fokussierten Seite zentriert — kein
nachträglicher Snap. Manuelles Pan/Zoom danach unverändert.

## Betroffene Datei
- `app/.../ui/pages/structure/StructureOverviewCanvas.kt` (Viewport-State + Auto-Center)
- (Kontext: `app/.../ui/pages/PageWorkbenchScreen.kt` `AnimatedContent`)

## Lösungsweg

### Ansatz A (empfohlen): Zentrieren **vor** dem ersten Zeichnen
Die Viewport-Größe schon zur Composition kennen, damit `offset` von Anfang an korrekt ist.

1. Den Canvas in **`BoxWithConstraints`** legen (oder die vorhandene Box ersetzen), sodass
   `constraints.maxWidth/maxHeight` bereits während der Composition verfügbar sind — kein separater
   `canvasSize`-State + Mess-Frame nötig.
2. Den **Initialwert** von `offset` direkt zentriert berechnen:
   ```kotlin
   val initialOffset = remember(focusedPageId, layout, viewportW, viewportH, scale) {
       layout[focusedPageId]?.let { p ->
           val c = Offset(p.x + nodeWidthPx / 2f, p.y + nodeHeightPx / 2f)
           Offset(viewportW / 2f - c.x * scale, viewportH / 2f - c.y * scale)
       } ?: Offset.Zero
   }
   var offset by remember { mutableStateOf(initialOffset) }
   ```
   So ist die erste gezeichnete Frame schon zentriert.
3. Auto-Center bei **Wechsel der fokussierten Seite** weiterhin erlauben, aber ohne Sprung beim Mount:
   z. B. `LaunchedEffect(focusedPageId)`, das nur bei *echtem* Fokuswechsel re-zentriert (Mount-Fall ist
   durch den Initialwert schon abgedeckt).

**Fallback in A (falls `BoxWithConstraints`-Umbau zu groß):** „Center-before-reveal" —
Inhalt erst sichtbar machen, wenn einmal zentriert wurde:
```kotlin
var hasCentered by remember(focusedPageId) { mutableStateOf(false) }
// im Auto-Center-Effekt nach dem Setzen von offset: hasCentered = true
Modifier.graphicsLayer { alpha = if (hasCentered) 1f else 0f }
```
Beseitigt den sichtbaren Sprung (eine Frame unsichtbar statt springend). Einfacher, aber minimal weniger
elegant als der Initialwert.

### Ansatz B (Politur, optional, additiv): Re-Zentrieren animieren
Für *echte* Fokuswechsel innerhalb der Übersicht den Re-Center per `Animatable.animateTo` statt
Instant-Set weich fahren (nicht für den Mount-Fall — der ist über Ansatz A bereits sprungfrei).
Reduzierte Bewegung respektieren (`isReducedMotion` ist im Canvas schon vorhanden,
`StructureOverviewCanvas.kt:127-137`).

### Hinweis zur AnimatedContent-Transition
Mit Ansatz A ist der Inhalt beim Einblenden bereits korrekt positioniert; die `scaleIn/scaleOut`-Animation
wirkt dann sauber „aus der Mitte". Den Transition-Pivot (derzeit Bildschirmmitte) **nicht** zwingend
ändern — erst nach dem Test bewerten, ob es noch nötig ist.

## Phasen (keine Zwischen-Commits)
> Der Nutzer testet am Stück, dann erst ein Commit.
1. Auto-Center auf Initialwert umstellen (Ansatz A, ggf. Fallback) — Sprung weg.
2. Optional: Re-Center bei Fokuswechsel weich animieren (Ansatz B).

## Test (manuell, vor dem Commit)
- L1 → L0 raus-zoomen: Übersicht erscheint **sofort** auf der fokussierten Seite zentriert, kein Sprung.
- Mehrfach rein/raus zoomen (verschiedene Seiten): immer sprungfrei.
- Suche mit Einzeltreffer zentriert weiterhin korrekt.
- Manuelles Pan/Zoom + „Recenter"-FAB unverändert.
- „Reduzierte Bewegung" aktiv: kein ruckartiges/animiertes Verhalten.

## Arbeitsteilung
Gemini setzt um, Claude reviewt — ohne Zwischen-Commits.
