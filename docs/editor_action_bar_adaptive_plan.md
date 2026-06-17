# Plan: Adaptive Editor-Icon-Leiste (dynamischer Overflow)

## Context / Problem

Die Editor-TopBar (`core-ui` `EditorTopBar.kt`) rendert in **einer** `TopAppBar`-Zeile:
`Back` + `titleContent` (+ Warn-Badges) + `modeSwitcher` (3 Zoom-Segmente + Stil-Toggle) + `actions`
(mehrere Icon-Buttons) + `⋮`-Overflow.

Im **Phone-Portrait** passt das nicht: zuletzt platzierte Aktionen werden abgeschnitten — konkret ist
der **Stil-Toggle (Graph/Karten)** unsichtbar, weshalb man die Karten-Ansicht nicht mehr erreicht.
Ursache: `TopAppBar` misst die `actions` mit voller Restbreite und lässt sie überlaufen, statt sie zu
begrenzen.

**Ziel:** Eine **priorisierte, adaptive** Leiste:
- Pflicht-Steuerelemente sind **immer** sichtbar.
- Der Rest wandert **dynamisch je nach verfügbarer Breite** in den `⋮`-Overflow.
- Auf **Tablet/breiten Screens** werden mehr (idealerweise alle) Aktionen inline gezeigt.
- Die Anzahl sichtbarer Buttons wird **gemessen**, nicht hart kodiert.

## Betroffene Dateien (Anker)

- `core-ui/.../components/EditorTopBar.kt` — `title = Row { titleContent(); modeSwitcher() }`, `actions: RowScope.() -> Unit`. Zentrale Stelle für Layout/Begrenzung.
- `app/.../ui/pages/PageWorkbenchScreen.kt` — `modeSwitcher` (`SingleChoiceSegmentedButtonRow` mit 3 Segmenten + Stil-Toggle `IconButton`, nur bei `currentLevel == FOCUSED`).
- `app/.../ui/pages/structure/StructureEditorTopBar.kt` — Struktur-Aktionen: Multiselect-Toggle, Assistent, Undo, Templates-Toggle, `⋮` (Redo, Tree-Toggle [nur Phone], History, Incoming Links, Rename, Exit).
- `app/.../ui/pages/PageEditorScreen.kt` — Grid-Aktionen (Assistent, Multiselect-Toggle, …, `⋮`). Gleiches Modell anwenden.

## Prioritätsklassen (verbindlich)

Jedes Steuerelement bekommt eine Priorität. Reihenfolge = Platzierungsreihenfolge.

**P0 — immer sichtbar (nie in den Overflow):**
- Back, `titleContent` + Warn-Badges
- Zoom-Switcher (3 Segmente Übersicht/Seite/Raster)

**P1 — inline solange Platz, sonst Overflow (in dieser Reihenfolge):**
1. Stil-Toggle Graph/Karten (nur auf `FOCUSED` relevant) ← **der heute verschwindende**
2. Assistent
3. Undo
4. Multiselect-Toggle
5. Templates-Toggle (nur Struktur)

**P2 — immer im `⋮`:**
- Redo, History, Incoming Links, Rename, Tree-Toggle (Phone), Exit

> Jedes P1-Element braucht **zwei Darstellungen**: inline als `IconButton` **und** als
> `DropdownMenuItem` (Icon + Text) für den Overflow. Der Stil-Toggle im Overflow z. B.
> „Ansicht: Karten" / „Ansicht: Graph".

## Architektur

### 1. Datenmodell `EditorAction` (neu, `core-ui`)
```kotlin
data class EditorAction(
    val key: String,
    val icon: ImageVector,
    val label: String,                 // für Overflow-Menüeintrag + a11y
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val tint: Color? = null,           // z. B. aktiver Multiselect-Status
    val testTag: String? = null,
    val priority: Int = 1              // 1 = P1 (adaptiv), 2 = P2 (immer Overflow)
)
```

### 2. `AdaptiveActionBar` (neu, `core-ui`) — die dynamische Berechnung
Misst die verfügbare Breite und platziert greedy so viele P1-Aktionen wie passen; der Rest plus alle
P2 landen in **einem** `⋮`-`DropdownMenu`.

```kotlin
@Composable
fun AdaptiveActionBar(
    actions: List<EditorAction>,
    overflowMenuExtra: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null, // P2-only-Items ohne Icon-Variante
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    SubcomposeLayout(modifier) { constraints ->
        val maxW = constraints.maxWidth
        val inline = actions.filter { it.priority == 1 }
        val forcedOverflow = actions.filter { it.priority == 2 }

        // 1) Overflow-Button vorab messen (Reservebreite, falls etwas überläuft)
        val overflowPlaceable = subcompose("overflow") { OverflowButton(onClick = { menuOpen = true }) }
            .first().measure(constraints)
        val overflowW = overflowPlaceable.width

        // 2) P1-Buttons messen, greedy platzieren bis Breite (ggf. minus Overflow) erschöpft
        val measured = inline.mapIndexed { i, a ->
            subcompose("a$i") { ActionIconButton(a) }.first().measure(constraints)
        }
        val visibleCount = greedyFit(measured.map { it.width }, maxW, overflowW,
                                     hasForcedOverflow = forcedOverflow.isNotEmpty())
        // visibleCount = wie viele P1 inline passen; Rest -> Overflow
        ...
        layout(usedWidth, height) { /* place visible inline + overflow button */ }
    }
}
```
- `greedyFit`: summiert Button-Breiten; sobald das nächste nicht mehr passt (und es gibt noch Reste
  **oder** P2-Items), wird `overflowW` reserviert und abgebrochen. Passen alle P1 **und** es gibt keine
  P2 → kein Overflow-Button.
- Das `⋮`-`DropdownMenu` enthält: übergelaufene P1 (als Menüeintrag mit Icon+Label) + alle P2
  (über `overflowMenuExtra` bzw. ihre `EditorAction`).

**Fallback (falls SubcomposeLayout zu aufwändig):** `BoxWithConstraints` + Annahme `IconButton ≈ 48.dp`,
`visibleCount = floor(maxWidth / 48.dp) - (if overflow) 1 else 0`. Weniger exakt (Assistent-Button ist
breiter), aber deutlich einfacher. Im Zweifel zuerst so, dann verfeinern.

### 3. Switcher adaptiv (Phone vs. Tablet)
- Der **Zoom-Switcher bleibt P0**, aber der **Stil-Toggle** ist ein P1-`EditorAction` (verschwindet bei
  Platzmangel in den `⋮`, statt abgeschnitten zu werden). Das löst das gemeldete Problem direkt.
- Optional Phone-Verschmälerung: Segmente nur als Icons (so wie jetzt, nach Icon-Revert) — Labels nur
  auf Tablet.

### 4. Tablet: Platz nutzen
- Über `LocalConfiguration.screenWidthDp` (≥ 600 = Tablet, bestehendes Muster `StructureEditorScreen.kt`)
  bzw. die gemessene Breite ergibt sich automatisch: bei viel Breite zeigt `AdaptiveActionBar` ohnehin
  **alle** P1 inline. Zusätzlich auf Tablet erlaubt: P1-Buttons mit **Icon + Text** statt nur Icon
  (über ein `compact`-Flag), damit der Platz sinnvoll gefüllt wird.

### 5. Integration
- `EditorTopBar`: `actions: RowScope.() -> Unit` durch `actions: List<EditorAction>` ersetzen (oder
  zusätzliche Overload) und intern `AdaptiveActionBar` rendern. Die `actions`-Region in der `TopAppBar`
  in der Breite **begrenzen**, damit der Titel nicht verdrängt wird (Switcher P0 hat Vorrang vor P1).
- `StructureEditorTopBar` und `PageEditorScreen`: ihre Icon-Listen + Overflow-Items auf das neue
  `EditorAction`-Modell umstellen. Multiselect-Modus (`BulkActionTopBar`) bleibt unverändert.

## Phasen (Arbeitsreihenfolge, keine Zwischen-Commits)

> **Kein Commit zwischen den Phasen** — der Nutzer testet die Leiste am Stück (Phone-Portrait,
> Phone-Landscape, Tablet), erst dann ein Commit.

1. **`EditorAction` + `AdaptiveActionBar`** in `core-ui` bauen (mit SubcomposeLayout oder Fallback) — isoliert, ohne Integration.
2. **Prioritäten verdrahten:** Stil-Toggle als P1-Action (Sofort-Fix für das verschwindende Icon), restliche Struktur-Aktionen auf `EditorAction` umstellen, `EditorTopBar` darauf umbauen + actions-Breite begrenzen.
3. **Grid-Bar** (`PageEditorScreen`) auf dasselbe Modell ziehen.
4. **Tablet-Ausbau:** Icon+Text-Variante / Labels bei breitem Screen.

## Test (manuell, vor dem Commit)
- Phone-Portrait: Switcher + Pflicht-Icons sichtbar; überzählige Aktionen sauber im `⋮`; **Stil-Toggle erreichbar** (inline oder Menü).
- Phone-Landscape & Tablet: mehr/alle Aktionen inline; nichts abgeschnitten.
- Dreh-/Resize-Wechsel: Anzahl sichtbarer Buttons passt sich live an.
- Multiselect-Modus unverändert.

## Arbeitsteilung
Gemini setzt um, Claude reviewt — Phase für Phase, **ohne** Zwischen-Commits.
