# Plan: Adaptive Editor-Leiste v2 — Platz wirklich nutzen, Switcher schützen

> Ergänzt/korrigiert `editor_action_bar_adaptive_plan.md`. Die v1-Umsetzung steht
> (`core-ui/.../AdaptiveActionBar.kt`, `EditorTopBar.kt`), hat aber zwei Konstruktionsfehler.

## Symptom

Auf dem **Tablet** (genug Platz) liegen trotzdem alle Aktionen außer Assistent, Undo, Multiselect,
Vorlagen im `⋮`-Overflow. Gleichzeitig droht auf schmalen Phones der **Mode-Switcher** geklemmt zu
werden.

## Ursachen (im aktuellen Code)

1. **Starre Klassen statt Platz-Logik.** `AdaptiveActionBar.kt:95-96`:
   `overflowActions = p1Actions.drop(visibleCount) + p2Actions` → **P2 wandert *immer* in den Overflow**,
   unabhängig von der verfügbaren Breite. Auf dem Tablet bleiben dadurch Redo/History/Incoming
   Links/Rename/Exit dauerhaft im `⋮`, obwohl Platz da wäre.
2. **Blinde Breiten-Kappung.** `EditorTopBar.kt:62-69`: `maxActionsWidth = screenWidthDp * 0.5f`. Die
   Action-Leiste kennt die **echte Switcher-Breite nicht** (Switcher liegt im `title`-Slot, Actions im
   `actions`-Slot — getrennte Messräume). 50 % ist mal zu großzügig (klemmt den Switcher), mal zu
   knapp (nur ~2 Icons inline).
3. **48dp-Schätzung** statt echter Messung (`AdaptiveActionBar.kt:82-85`) — ignoriert variable
   Button-Breiten und die Switcher-Breite.

## Ziel

- **Inline vs. Overflow rein nach verfügbarer Breite**, in Prioritätsreihenfolge — kein hartes
  „diese Aktion ist immer im Menü".
- **Mode-Switcher (P0) bekommt seinen Platz garantiert**, bevor Aktionen verteilt werden.
- **Tablet/breite Screens füllen den Platz** automatisch mit mehr Inline-Aktionen (auch den heutigen
  P2). Phone-Portrait kappt sauber in den `⋮`.

## Kernidee: ein gemeinsamer Mess-Durchlauf statt zwei getrennter Slots

Der Grundfehler ist die Aufteilung in `title`-Slot (Switcher) und `actions`-Slot (Aktionen): beide
können ihre Breiten nicht gegeneinander aushandeln. Lösung: **alles in einen Messraum**.

`TopAppBar`-Chrome (Höhe, Insets, Farben) **behalten**, aber:
- `navigationIcon` = Back (wie bisher).
- `title = { AdaptiveEditorBar(...) }` — enthält **Titel + Switcher + Aktionen + ⋮** als *eine*
  gemessene Zeile.
- `actions = {}` (leer) → der `title`-Slot bekommt ~die volle Restbreite (Bar − Nav).

### `AdaptiveEditorBar` (neu, `core-ui`) — Breitenverteilung per `SubcomposeLayout`
Ein Mess-/Layout-Durchlauf, der die Gesamtbreite in fester Prioritätsreihenfolge vergibt:

1. **Switcher messen und fest reservieren** (P0 — darf nie schrumpfen/klippen).
2. **Titel**: Mindestbreite reservieren (z. B. `widthIn(min = 72.dp)`, einzeilig + Ellipsis); darf
   wachsen, gibt aber Platz an Aktionen ab.
3. **Aktionen** in **aufsteigender Priorität** einzeln messen und platzieren, solange Platz reicht;
   sobald etwas nicht mehr passt **und** noch Reste übrig sind → Breite für den `⋮`-Button reservieren
   und abbrechen.
4. **`⋮`-Overflow** nimmt die nicht platzierten Aktionen (in derselben Reihenfolge) als
   `DropdownMenuItem` (Icon + Label) auf.

Reihenfolge der Platz-Vergabe bei Knappheit: **Nav → Switcher → ⋮(falls nötig) → Aktionen (nach
Priorität) → Titel (Rest, Ellipsis)**. Damit ist der Switcher immer sicher, und der Titel weicht
zuerst.

### Aktionsmodell anpassen (`EditorAction`)
- `priority: Int` bleibt, bedeutet aber **nur noch Reihenfolge** (1 = zuerst inline), **nicht** mehr
  „immer Overflow". Greedy-Fit entscheidet inline/Overflow allein über den Platz.
- Optional neues Flag `alwaysOverflow: Boolean = false` für die wenigen, die bewusst **nie** inline
  sollen (Kandidat: nur `Exit`). Default `false` → alles konkurriert um Inline-Plätze.
- `AdaptiveActionBar.kt`: die Zeile `overflowActions = p1.drop(n) + p2` entfällt; stattdessen eine
  **einzige nach Priorität sortierte Liste** greedy füllen.

### Echte Messung statt 48dp
Im `SubcomposeLayout` werden Switcher- und Button-Breiten **gemessen** (`subcompose(...).measure(...)`),
statt 48dp zu raten — deckt variable Breiten (Assistent-Button) und den Switcher korrekt ab. Falls zu
aufwändig: 48dp-Heuristik **nur** für die Buttons behalten, aber die **Switcher-Breite trotzdem messen**
und von der verfügbaren Breite abziehen (behebt Punkt 2 mit minimalem Aufwand).

## Betroffene Dateien
- `core-ui/.../components/AdaptiveActionBar.kt` — Greedy-Fit ohne harte P2-Grenze; `EditorAction`-Flag.
- `core-ui/.../components/EditorTopBar.kt` — Switcher in den gemeinsamen Messraum holen, fixe 50 %-Kappung entfernen.
- `app/.../structure/StructureEditorTopBar.kt` & `app/.../PageEditorScreen.kt` — Prioritäten als reine Reihenfolge vergeben (bisherige „priority = 2" bleiben als *niedrigere* Priorität, werden aber inline, wenn Platz da ist); ggf. `alwaysOverflow` nur für Exit.

## Phasen (keine Zwischen-Commits)
> Nutzer testet am Stück (Phone-Portrait, Phone-Landscape, Tablet), dann erst ein Commit.
1. **Greedy-Fit fixen:** in `AdaptiveActionBar` eine einheitliche prioritätssortierte Liste füllen (P2 inline-fähig machen). Schon das bringt auf Tablet mehr Inline-Aktionen.
2. **Switcher schützen:** `AdaptiveEditorBar` mit gemeinsamem Messraum (oder mind. Switcher-Breite messen und abziehen); 50 %-Kappung raus.
3. **Echte Button-Messung** (optional, Politur) statt 48dp.
4. **Prioritäten final ordnen** in beiden Bars; `alwaysOverflow` nur wo wirklich gewünscht.

## Test (manuell, vor dem Commit)
- **Tablet:** alle (oder fast alle) Aktionen inline, Switcher voll sichtbar, nichts unnötig im `⋮`.
- **Phone-Portrait:** Switcher **nie geklemmt**; überzählige Aktionen sauber im `⋮`, Reihenfolge nach Priorität.
- **Phone-Landscape:** mehr inline als Portrait, weniger als Tablet — stufenlos.
- **Resize/Drehen:** Anzahl Inline-Aktionen passt sich live an.
- Multiselect-Modus (`BulkActionTopBar`) unverändert.

## Arbeitsteilung
Gemini setzt um, Claude reviewt — phasenweise, ohne Zwischen-Commits.
