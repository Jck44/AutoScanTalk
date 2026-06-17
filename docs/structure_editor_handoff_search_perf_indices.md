# Hand-off-Paket für Gemini — Suche (alle Textfelder) · Such-Performance · DB-Indizes

> **Kontext:** Struktur-Editor Phase 0–4 ist umgesetzt + committet. Dieses Paket schließt die
> offenen Punkte aus dem Review (siehe `docs/structure_editor_master_plan.md`, Phase 3/3b + Anhang A).
> **Workflow:** Gemini setzt um, Claude reviewt. Bitte die vier Teile in **getrennten Commits** liefern,
> damit das Review fokussiert bleibt.
>
> **Globale Regeln**
> - `JAVA_HOME` = Android-Studio-JBR für Gradle.
> - Bestehendes Verhalten nicht brechen (Cards/Graph/Tree/Overview, Seitenlisten-Filter).
> - Domänen-Logik im `core`-Modul **test-gedeckt**.
> - Keine neuen öffentlichen APIs ohne Not.

---

## Teil 1 — Suche über ALLE Button-Textfelder (`searchableText()`)

**Problem:** `SearchPagesUseCase` (`core/.../domain/pages/SearchPagesUseCase.kt`) durchsucht aktuell nur
`page.name` + `button.label`. Gewünscht: alle textbehafteten Felder eines Buttons.

### 1.1 Neuer Helper (core)
Neue Datei `core/.../core/model/ButtonSearchText.kt` (oder neben dem UseCase) mit einer Extension:

```kotlin
/** Alle für die Volltextsuche relevanten Textfelder eines Buttons, leere/null gefiltert. */
fun ButtonConfig.searchableText(): List<String> {
    val texts = mutableListOf<String?>()
    texts += label
    texts += spokenText
    (auditoryCue as? AuditoryCue.TextToSpeechCue)?.let { texts += it.text }
    when (val a = buttonAction) {
        is GeminiButtonAction       -> texts += a.prompt
        is GeminiSearchButtonAction -> texts += a.prompt
        is GeminiNanoButtonAction   -> texts += a.intent
        is GeminiVisionButtonAction -> texts += a.prompt
        is ControlDeviceButtonAction -> { texts += a.messageText; texts += a.prefixText; texts += a.suffixText; texts += a.contactName }
        is SmartHomeButtonAction    -> { texts += a.deviceName; texts += a.intent; texts += a.value }
        is PlayMediaButtonAction    -> texts += a.contentName
        else -> { /* andere Actions tragen keinen durchsuchbaren Freitext */ }
    }
    return texts.filterNot { it.isNullOrBlank() }.map { it!! }
}
```

- **Bewusst NICHT durchsuchen:** `audioFileName`, `pageId`, `contentUri`, sämtliche IDs.
- Die `when`-Zweige müssen zu den realen Feldern in `core/.../model/ButtonAction.kt` passen — vor dem Schreiben kurz gegenprüfen (Feldnamen können abweichen).

### 1.2 UseCase umstellen
In `SearchPagesUseCase.execute(...)` den Button-Match ersetzen:

```kotlin
val buttonHits = page.buttonConfigs.mapIndexedNotNull { index, btn ->
    if (btn != null && btn.isActive &&
        btn.searchableText().any { it.contains(trimmedQuery, ignoreCase = true) }
    ) ButtonHit(index, btn.label) else null
}
```

- `ButtonHit(index, label)` bleibt unverändert (Label ist der Anzeigetext des Treffers — auch wenn der
  Match in einem anderen Feld lag). **Optional** `matchedField: String?` ergänzen, falls die UI später
  „gefunden in: Gesprochener Text" anzeigen soll — nur wenn es ohne UI-Bruch geht.

### 1.3 Tests (Pflicht)
`SearchPagesUseCaseTest` erweitern um je einen Treffer in:
- `spokenText`, `auditoryCue` (TTS), einem Gemini-`prompt`, `ControlDeviceButtonAction.messageText`.
- Gegenprobe: Treffer **nur** in einem ausgeschlossenen Feld (z. B. `audioFileName`) liefert **keinen** Hit.
- Inaktiver Button mit Treffer in `spokenText` → kein Hit (bestehende „ignores inactive"-Regel gilt weiter).

**Akzeptanz:** Treffer in beliebigem durchsuchbaren Feld werden gefunden; ausgeschlossene Felder nicht; Tests grün.

---

## Teil 2 — Such-Performance (Debounce + Off-Main)

**Problem:** `StructureOverviewCanvas.kt` rechnet die Suche **synchron im `remember`-Block auf dem
Main-Thread** (`SearchPagesUseCase().execute(...)`, ~Zeile 125–132). Nach der Feld-Ausweitung (Teil 1)
soll das nicht mehr im Compose-Frame laufen.

### Umsetzung
- Suche **debouncen** (~200 ms) und auf `Dispatchers.Default` rechnen. Bevorzugt das Ergebnis als State
  bereitstellen, z. B. via `snapshotFlow`:

```kotlin
val matchingPageIds by produceState(emptySet<String>(), searchQuery, pages) {
    snapshotFlow { searchQuery }
        .debounce(200)
        .mapLatest { q ->
            if (q.isBlank()) emptySet()
            else withContext(Dispatchers.Default) {
                searchUseCase.execute(pages, q).map { it.pageId }.toSet()
            }
        }
        .collect { value = it }
}
```

- `SearchPagesUseCase` **einmal** halten (DI-injiziert oder `remember { SearchPagesUseCase() }`), nicht pro
  Recomposition neu `new`-en.
- Falls dieselbe Suche zusätzlich an anderer Stelle synchron läuft (z. B. `StructureTreeNavigator`,
  `StructureEditorContent`) → dort konsistent dieselbe debounced/off-main Quelle nutzen, nicht doppelt rechnen.
- Treffer-Objekte nur für Matches allokieren (erledigt `mapNotNull` bereits).

**Akzeptanz:** Tippen im Suchfeld ruckelt auch bei großem Buch nicht; Suche läuft nicht mehr synchron im
`remember`; Auto-Center-auf-Single-Match funktioniert weiterhin.

---

## Teil 3 — DB-Indizes (Migration 35 → 36)

**Problem (Anhang A):** `pages.bookId` und `button_usage_history` haben **keine** Indizes, werden aber heiß
abgefragt. Datei: `core-database/.../AppDatabase.kt` (aktuell `version = 35`, `exportSchema = false`).

### 3.1 Entities annotieren
- `core/.../core/model/Page.kt`:
  `@Entity(tableName = "pages", indices = [Index("bookId")])`
- `core-database/.../ButtonUsageHistoryEntity.kt`:
  `@Entity(tableName = "button_usage_history", indices = [Index(value = ["bookId", "timestamp"])])`
- Jeweils `import androidx.room.Index` ergänzen.

### 3.2 Migration 35 → 36
> **Kritisch:** Der Index-Name im `CREATE INDEX` muss **exakt** Rooms Auto-Namen entsprechen
> (`index_<tabelle>_<spalte>[_<spalte>]`), sonst schlägt Rooms Schema-Validierung beim Start fehl.
> Muster steht bereits in `MIGRATION_30_31` (`index_deleted_entities_bookId`).

In `AppDatabase.kt` neben den anderen Migrationen:

```kotlin
val MIGRATION_35_36: Migration = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_bookId` ON `pages` (`bookId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_button_usage_history_bookId_timestamp` ON `button_usage_history` (`bookId`, `timestamp`)")
    }
}
```

### 3.3 Verdrahten
- `@Database(... version = 36)` setzen.
- `MIGRATION_35_36` in die `.addMigrations(...)`-Liste aufnehmen (nach `MIGRATION_34_35`, ~Zeile 609).

**Akzeptanz:**
- App startet sauber mit bestehender DB (Upgrade 35→36 läuft ohne Crash/Datenverlust).
- Frische Installation startet sauber (kein Schema-Mismatch — Index-Namen passen).
- Stichprobe mit `EXPLAIN QUERY PLAN` für `SELECT * FROM pages WHERE bookId = ?` zeigt `USING INDEX`
  statt `SCAN` (optional, aber erwünscht als Beleg).

---

## Teil 4 (optional, niedrige Prio) — kleine Korrekturen am Overview-Canvas

Nur wenn Zeit; sonst als Folge-Ticket. Datei `StructureOverviewCanvas.kt`:
- **Reduced-Motion:** Fling (`animateDecay`) und Zoom-Animationen bei aktivem Reduced-Motion überspringen/verkürzen
  (Projektkonvention: Erkennung über `ANIMATOR_DURATION_SCALE`, **nicht** über Composes `LocalAccessibilityManager`).
- **Toter Branch:** `unfocusedBackward` ist immer leer (Backward-Kanten werden nur fokus-bezogen gesammelt) →
  Partition/Branch entfernen.
- **Ungenutzt:** berechnete `rotation` wird nie angewandt → entfernen oder bewusst kommentieren.

**Akzeptanz:** Verhalten unverändert, nur aufgeräumt; bei aktivem Reduced-Motion kein Fling/Zoom-Schwung.

---

## Teil 5 — Sauberere Positionierung im Overview-Graph

**Kontext:** Die Sichtbarkeits-Optimierungen (Backward-Kanten nur am Fokus, Highlight aktiver Kanten) sind
bereits drin. Offen ist das **Layout selbst** — Knoten stehen noch unruhig, Kanten kreuzen unnötig.
Alle Änderungen in `app/.../ui/pages/structure/StructureOverviewCanvas.kt`. **Wichtig:** rein visuelle
Verbesserung — Tap/Fokus/Fling/Zoom-Verhalten und die bestehende Kanten-Sichtbarkeitslogik dürfen sich
**nicht** ändern. Bitte als **getrennte Commits** in der Reihenfolge 5.1 → 5.2 → 5.3 (5.3 zuletzt, weil heikelster).

### 5.1 Kanten-Reihenfolge je Spalte sortieren (Barycenter) — sicher, größter Effekt
**Problem:** In `calculateOverviewLayout` (~`:681-690`) werden Knoten je Level in **BFS-Entdeckungsreihenfolge**
gestapelt → Kinder desselben Elternknotens liegen weit auseinander, Kanten kreuzen quer durch die Spalte.

**Umsetzung:** Nach dem Leveln, **vor** der y-Zuweisung, jede Spalte ab Level 1 nach dem **Mittelwert der
Positionen ihrer Eltern in der vorherigen Spalte** sortieren (Barycenter-Heuristik). 1–2 Sweeps (abwärts,
dann optional aufwärts) genügen. Reine Umsortierung **innerhalb** bestehender Spalten — Spaltenzuordnung
und Knotenmenge bleiben unverändert.

- Barycenter eines Knotens = Durchschnitt der (Zeilen-)Indizes seiner Eltern im Level davor; Eltern = Quellen
  aus `graph.outgoing`, deren Ziel dieser Knoten ist und die im vorherigen Level sitzen.
- Knoten ohne Eltern im Vorgänger-Level behalten ihre relative Position (stabil sortieren).

**Akzeptanz:** Sichtbar weniger Kantenkreuzungen bei mehrstufigen Büchern; Spaltenanzahl/-zuordnung identisch;
kein Verhaltensbruch.

### 5.2 Parallele Kanten deduplizieren — trivial, sichtbar
**Problem:** `graph.outgoing` hat **eine Kante pro Navi-Button**. Zwei Buttons derselben Seite zur selben
Zielseite → in `edgesToDraw` (~`:139-162`) **zwei deckungsgleiche Linien + doppelter Pfeil**.

**Umsetzung:** Beim Aufbau von `forward`/`backward` nach `(sourceId, targetId)` deduplizieren (z. B. ein
`HashSet<Pair<String,String>>` als Guard, ersten Treffer behalten). Die Highlight-/Backward-Sichtbarkeitslogik
bleibt unangetastet.

**Akzeptanz:** Pro Seitenpaar höchstens eine gezeichnete Linie/ein Pfeil; keine sonstige Änderung.

### 5.3 Spalten per längstem Pfad statt Erst-Entdeckung — strukturell sauberer, HEIKEL
**Problem:** Der **erste** BFS-Fund fixiert die Spalte (~`:655-670`). Bei kurzer **und** langer Route zu einer
Seite (z. B. `A→C` und `A→B→C`) landet `C` zu weit links → `B→C` überspringt eine Spalte, erzeugt Kreuzungen.

**Umsetzung:** Knoten in `max(Elternspalte) + 1` schieben (Longest-Path-Ranking, Sugiyama-Stil).
> ⚠️ **Zyklen-Caveat:** Der Navigationsgraph hat Zyklen — reines „längster Pfad" terminiert nicht. Das Ranking
> **nur auf Vorwärtskanten des BFS-Spannbaums** anwenden (Rückkanten, also Ziel mit bereits ≤ eigenem Level,
> ignorieren). Nach dem Verschieben Barycenter (5.1) erneut anwenden, da sich Levelbelegungen ändern.

**Akzeptanz:** Keine spaltenüberspringenden Vorwärtskanten mehr in den getesteten Büchern; **keine** Endlosschleife
bei zyklischen Büchern (explizit mit einem Buch testen, das einen Navigations-Zyklus enthält); kein Crash bei
abgekoppelten Clustern / Incoming-only-Knoten.

### 5.4 (optional, Kosmetik)
- **Incoming-only-Knoten** nicht pauschal in Spalte 0 werfen (~`:672-676`), sondern neben einem Nachbarn platzieren.
- **Pfeilköpfe** mehrerer Kanten auf denselben Knoten leicht am Zielrand auffächern (~`:414-431`), statt exakt zu stapeln.

---

## Liefer-Checkliste
- [ ] Teil 1, 2, 3 (und ggf. 4) als **getrennte Commits**.
- [ ] Teil 5: 5.1 / 5.2 / 5.3 als **getrennte Commits** (5.3 zuletzt).
- [ ] `core`-Tests grün (inkl. neuer Such-Tests).
- [ ] App-Start auf bestehender + frischer DB getestet (Teil 3).
- [ ] Overview-Layout mit einem **zyklischen** Buch getestet (Teil 5.3 — keine Endlosschleife).
- [ ] Keine Regression in Seitenlisten-Filter / den vier Struktur-Ansichten (Tap/Fokus/Fling/Zoom unverändert).
- [ ] Claude-Review vor Merge.
