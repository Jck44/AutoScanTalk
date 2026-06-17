# Hand-off-Paket für Gemini — Review-Nachzügler (Teil 2 & 3)

> **Kontext:** Review der committeten Teile 1–4 (siehe `structure_editor_handoff_search_perf_indices.md`).
> Teil 1 und 4 sind sauber. Zwei kleine Nachzügler aus Teil 2 und Teil 3 bleiben offen.
> **Workflow:** Gemini setzt um, Claude reviewt. Bitte **zwei getrennte Commits**.
>
> **Globale Regeln:** `JAVA_HOME` = Android-Studio-JBR; bestehendes Verhalten nicht brechen; `core`-Tests grün.

---

## Finding A — `StructureTreeNavigator` (standalone) sucht synchron auf dem Main-Thread

**Befund (eng abgegrenzt):** Im **Struktur-Editor** ist alles korrekt — dort bekommt der TreeNavigator die
bereits debounced/off-main berechneten `searchResults` als Parameter von `StructureEditorContent`
(`produceState` + `debounce(200)` + `Dispatchers.Default`).
**Aber:** Der **Fallback-Zweig** `state == null` rechnet die Suche synchron im Compose-Frame:

`app/.../ui/pages/structure/StructureTreeNavigator.kt:158-165`
```kotlin
val localSearchResults = if (state == null) {
    val searchPagesUseCase = remember { SearchPagesUseCase() }
    remember(searchQuery, pages) {              // <-- synchron, Main-Thread, kein Debounce
        searchPagesUseCase.execute(pages, searchQuery)
    }
} else {
    searchResults
}
```

Dieser Zweig wird vom **standalone-Aufruf in `GridEditorContent.kt:330`** getroffen (ruft `StructureTreeNavigator`
ohne `state`/`searchResults` auf). Seit Teil 1 durchsucht `execute(...)` **alle** Button-Textfelder — bei großen
Büchern kann Tippen dort ruckeln.

### Umsetzung
Den `state == null`-Zweig auf dieselbe debounced/off-main-Quelle umstellen wie `StructureEditorContent`, damit
beide Pfade konsistent sind. Self-contained im TreeNavigator über `produceState`:

```kotlin
val localSearchResults: List<PageSearchResult> = if (state == null) {
    val searchPagesUseCase = remember { SearchPagesUseCase() }
    val produced by produceState(emptyList<PageSearchResult>(), searchQuery, pages) {
        snapshotFlow { searchQuery }
            .debounce(200)
            .mapLatest { q ->
                if (q.isBlank()) emptyList()
                else withContext(Dispatchers.Default) { searchPagesUseCase.execute(pages, q) }
            }
            .collect { value = it }
    }
    produced
} else {
    searchResults
}
```
- Imports/Annotation analog zu `StructureEditorContent` (`@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)`).
- `searchQuery` ist hier `localSearchQuery` (der `state == null`-Fall), also die live getippte Query — genau die,
  die gedrosselt werden soll.
- Verhalten unverändert: Trefferliste/Highlights identisch, nur nicht mehr synchron im Frame.

**Akzeptanz:** In der standalone-Einbettung (GridEditorContent) ruckelt Tippen auch bei großem Buch nicht;
In-Editor-Pfad (`state != null`) unverändert; keine doppelte Berechnung.

---

## Finding B — Redundante Index-Zeile in `MIGRATION_35_36`

**Befund:** `MIGRATION_35_36` legt zwei Indizes an, aber nur einer ist neu:

`core-database/.../AppDatabase.kt` (MIGRATION_35_36)
```kotlin
db.execSQL("CREATE INDEX IF NOT EXISTS `index_pages_bookId` ON `pages` (`bookId`)")                                  // ✅ neu, behalten
db.execSQL("CREATE INDEX IF NOT EXISTS `index_button_usage_history_bookId_timestamp` ON `button_usage_history` ...") // ⚠️ redundant
```
Der `button_usage_history`-Index `(bookId, timestamp)` wird **bereits** beim Anlegen der Tabelle in einer
älteren Migration erzeugt und ist auf `ButtonUsageHistoryEntity` deklariert. Durch `IF NOT EXISTS` ist die Zeile
harmlos, aber toter Code (Folge eines fehlerhaften Vor-Audits — die History-Tabelle war schon vollständig indiziert).

### Umsetzung
- Die **zweite** `execSQL`-Zeile (`index_button_usage_history_bookId_timestamp`) aus `MIGRATION_35_36` **entfernen**.
- `index_pages_bookId` bleibt; `version = 36` und die Registrierung in `.addMigrations(...)` bleiben unverändert.
- `Page`-Entity-Annotation (`Index("bookId")`) **nicht** anfassen.

> Unbedenklich, weil Version 36 noch nicht ausgeliefert ist: Geräte, die 35→36 schon migriert haben, besitzen den
> (ohnehin bereits existierenden) History-Index weiterhin; neue/zukünftige Migrationen sind nicht betroffen.

**Akzeptanz:** App startet sauber auf bestehender **und** frischer DB; `index_pages_bookId` weiterhin vorhanden
(`EXPLAIN QUERY PLAN` für `SELECT * FROM pages WHERE bookId = ?` zeigt `USING INDEX`).

---

## Liefer-Checkliste
- [ ] Finding A und B als **getrennte Commits**.
- [ ] App-Start auf bestehender + frischer DB getestet (Finding B).
- [ ] Standalone-Baumansicht (GridEditorContent) + In-Editor-Baumansicht beide unverändert im Verhalten (Finding A).
- [ ] Claude-Review vor Merge.
