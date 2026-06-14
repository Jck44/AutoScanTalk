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
