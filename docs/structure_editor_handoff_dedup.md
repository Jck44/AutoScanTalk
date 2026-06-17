# Hand-off-Paket für Gemini — Duplikations-Cleanup (Struktur-Editor)

> **Kontext:** Review nach Teil 5 hat eine vermeidbare Code-Duplikation zwischen den beiden Graph-Editoren
> (Fokus & Overview) und der Baum-Ansicht gefunden. Rein interne Aufräumarbeit, **kein** Verhaltens-/UI-Wechsel.
> **Workflow:** Gemini setzt um, Claude reviewt. Zwei Commits (Finding 2 optional).
>
> **Globale Regeln:** `JAVA_HOME` = Android-Studio-JBR; keine sichtbare UI-Änderung; alle vier Struktur-Ansichten
> (Cards/Graph/Tree/Overview) müssen unverändert aussehen und funktionieren.

---

## Finding 1 (Pflicht) — `WarningBadges` vereinheitlichen

**Befund:** Das ⚠/⛔-Badge (Orphan/Dead-End) existiert **4×** mit praktisch identischem Code:

| Stelle | `fontSize` | Spacing | A11y-Semantik |
|---|---|---|---|
| `StructureGraphNode.kt:48` `private fun WarningBadges` | 14.sp | 4.dp | ✅ `contentDescription` |
| `StructureFocusCanvas.kt:85` `private fun WarningBadges` | 16.sp | 4.dp | ✅ |
| `StructureTreeNavigator.kt:65` `private fun WarningBadges` | 16.sp | 4.dp | ✅ |
| `StructureOverviewCanvas.kt:~538-555` **inline** (kein eigenes Composable) | 12.sp | 2.dp | ❌ **fehlt** |

Einziger echter Unterschied: `fontSize`. Die Overview-Inline-Variante hat zudem **keine** Barrierefreiheits-Beschreibung
— die Konsolidierung verbessert das (Overview bekommt sie geschenkt).

### Umsetzung
1. **Neue Datei** `app/.../ui/pages/structure/StructureBadges.kt` mit *einer* geteilten Composable
   (package-sichtbar, also **ohne** `private`):

```kotlin
@Composable
fun WarningBadges(
    isOrphan: Boolean,
    isDeadEnd: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp
) {
    if (!isOrphan && !isDeadEnd) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isOrphan) {
            val orphanDesc = stringResource(R.string.structure_warning_orphan)
            Text("⚠", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold,
                fontSize = fontSize, modifier = Modifier.semantics { contentDescription = orphanDesc })
        }
        if (isDeadEnd) {
            val deadEndDesc = stringResource(R.string.structure_warning_dead_end)
            Text("⛔", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold,
                fontSize = fontSize, modifier = Modifier.semantics { contentDescription = deadEndDesc })
        }
    }
}
```

2. **Die drei `private fun WarningBadges` löschen** (`StructureGraphNode.kt`, `StructureFocusCanvas.kt`,
   `StructureTreeNavigator.kt`) und durch die geteilte ersetzen — Aufrufer bleiben gleich, nur `fontSize` mitgeben:
   - `StructureGraphNode`: `WarningBadges(isOrphan, isDeadEnd)` (Default 14.sp passt).
   - `StructureFocusCanvas` + `StructureTreeNavigator`: `WarningBadges(isOrphan, isDeadEnd, fontSize = 16.sp)`.
3. **Overview-Inline-Block ersetzen** (`StructureOverviewCanvas.kt:~538-555`): den handgeschriebenen
   `if (pageId in orphans || pageId in deadEnds) { Row { … } }`-Block durch
   `WarningBadges(isOrphan = pageId in orphans, isDeadEnd = pageId in deadEnds, fontSize = 12.sp)` ersetzen.
   - Die Spacing-Differenz (2.dp → 4.dp) ist vernachlässigbar; bewusst standardisieren. Falls pixelgenau gewünscht,
     stattdessen optionalen `spacing: Dp = 4.dp`-Param ergänzen und hier `2.dp` übergeben.

**Akzeptanz:** Nur noch **eine** `WarningBadges`-Definition; alle vier Ansichten zeigen die Badges optisch wie zuvor
(Schriftgröße je Ansicht unverändert); Overview-Badges haben jetzt zusätzlich `contentDescription`; keine
ungenutzten Importe übrig.

---

## Finding 2 (optional) — `orphans()/deadEnds()`-Memoization bündeln

**Befund:** In **vier** Dateien wiederholt sich:
```kotlin
val orphans = remember(graph) { graph.orphans().toSet() }
val deadEnds = remember(graph) { graph.deadEnds().toSet() }
```
(`StructureGraphView`, `StructureFocusCanvas`, `StructureOverviewCanvas`, `StructureTreeNavigator`).

### Umsetzung
Kleinen Helper in `StructureBadges.kt` (oder eigener Datei) ergänzen:

```kotlin
@Stable
data class StructureProblems(val orphans: Set<String>, val deadEnds: Set<String>)

@Composable
fun rememberStructureProblems(graph: BookNavigationGraph): StructureProblems =
    remember(graph) { StructureProblems(graph.orphans().toSet(), graph.deadEnds().toSet()) }
```
In den vier Dateien die Doppel-`remember` durch `val problems = rememberStructureProblems(graph)` ersetzen und
`problems.orphans` / `problems.deadEnds` nutzen.

> Achtung `StructureTreeNavigator`: nutzt zusätzlich `graph.orphans()` als **Liste** (Zeile ~157) für `problemPages`
> — entweder die Liste behalten oder `problemPages` aus `problems.orphans`/`deadEnds` ableiten. Nicht versehentlich
> das Sortier-/Reihenfolge-Verhalten ändern.

**Akzeptanz:** Identisches Verhalten, vier Doppel-`remember` ersetzt, keine Regression im Probleme-Panel der Baum-Ansicht.

---

## Liefer-Checkliste
- [ ] Finding 1 (Pflicht) und Finding 2 (optional) als **getrennte Commits**.
- [ ] Alle vier Struktur-Ansichten optisch + funktional unverändert (Badges, Schriftgrößen, Probleme-Panel).
- [ ] Keine toten `private fun WarningBadges` / ungenutzten Importe mehr.
- [ ] Claude-Review vor Merge.
