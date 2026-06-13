# Plan: Undo/Redo mit Verlauf (Edit-History) im Page Editor

**Ziel:** Das heutige Snackbar-only-Undo wird durch eine echte, beschriftete
Edit-History ersetzt: dauerhafte Undo-/Redo-Buttons in der Editor-Topbar, ein
Verlaufs-Panel mit Mehrschritt-Rücksprung und Redo. Geltungsbereich ist **pro Buch**;
der Verlauf überlebt Seitenwechsel innerhalb desselben Buchs.

Erstellt für: Umsetzung durch Gemini, Review durch Claude.
Datum: 2026-06-13.

**Getroffene Entscheidungen (vorab):**
- **Scope:** Pro Buch (ein gemeinsamer Verlauf über alle Seiten/Operationen eines Buchs,
  Reset beim Buchwechsel). Kein DB-Persist über App-Neustart.
- **Schwerpunkt:** Voller, beschrifteter Verlauf mit Mehrschritt-Rücksprung.
- **Obergrenze:** 50 Schritte In-Memory pro Buch.

---

## 1. Ausgangslage / relevante Architektur

**Heutiges Undo** (`ui/pages/delegates/PageManagementDelegate.kt:92–128`):
- `undoStack: MutableList<Page>` mit max. 10 **Voll-Snapshots einer einzelnen Seite**.
- `saveUndoStateForPage(pageId)` (Z. 105) lädt die Seite **asynchron in einer eigenen
  Coroutine** und pusht eine Kopie; die Mutation läuft in einer **parallelen** Coroutine.
  → **Race-Bug:** Reihenfolge nicht garantiert, der Snapshot kann den bereits
  geänderten Zustand erfassen.
- `undo()` (Z. 113) poppt und schreibt via `pageRepository.updatePage`.
- Kein Redo. `canUndo: StateFlow<Boolean>` existiert, wird aber **nicht** als
  Toolbar-Button gerendert — Auslösung fast nur über die transiente Snackbar
  „Rückgängig" (`ui/components/GridEditorContent.kt:139` `showUndoSnackbar`).

**Aufrufer von `saveUndoStateForPage`** (alle in `PageManagementDelegate.kt`):
`updateButtonConfig` (197), `insertButtonConfig` (207), und Z. 284/301/311/321/334.

**Interface / VM-Verdrahtung:**
- `ui/util/GridEditorActions.kt:34–35`: `fun undo(onSuccess)` + `val canUndo`.
- `ui/pages/GridEditorViewModel.kt:86–90`: delegiert `undo`/`canUndo` an
  `pageManagementDelegate`.

**Weitere mutierende Delegates** (heute nicht undoable):
- `ui/pages/delegates/PageManagementDelegate.kt` — Seiten anlegen/löschen/umbenennen.
- `ui/pages/bulkreorder/` — Bulk-Reorder.
- `ui/pages/delegates/PageSplitDelegate.kt`, `AiRestructureDelegate.kt` — AI-Restructure / Split.
- Buch-Duplizieren (Book-Clone-Delegate).

**Konventionen:**
- Sichtbare Labels / `contentDescription` → String-Resource in
  `res/values/strings.xml` **und** `res/values-en/strings.xml`.
- Snackbar-Texte im DragDropHandler sind heute hardcodiert deutsch; bei der History
  führen wir lokalisierte Labels ein (siehe Phase 2).

---

## 2. Zielarchitektur — Command-Pattern statt Snapshot-Stack

Zentrales `EditHistory`-Objekt mit zwei Stacks, geteilt von allen Delegates.

```kotlin
// neu: ui/pages/history/EditCommand.kt
interface EditCommand {
    val label: EditLabel          // lokalisierbares Label (siehe unten)
    val icon: EditIcon            // DELETE / MOVE / EDIT / REORDER / PAGE / BOOK
    suspend fun apply()           // ausführen / wiederholen (Redo)
    suspend fun revert()          // rückgängig (Undo)
    fun mergeWith(next: EditCommand): EditCommand? = null  // optional: Coalescing
}

// Label als Resource-Id + Argumente, damit i18n + Panel funktionieren
data class EditLabel(@StringRes val resId: Int, val args: List<Any> = emptyList())

enum class EditIcon { DELETE, MOVE, EDIT, REORDER, PAGE, BOOK }
```

```kotlin
// neu: ui/pages/history/EditHistory.kt
data class HistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val entries: List<EditLabel> = emptyList(),   // neueste zuerst, für das Panel
)

class EditHistory(
    private val scope: CoroutineScope,
    private val limit: Int = 50,
) {
    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    /** Command ausführen UND registrieren. Löscht den Redo-Stack. */
    fun execute(command: EditCommand) = scope.launch {
        command.apply()
        val merged = undoStack.lastOrNull()?.mergeWith(command)
        if (merged != null) { undoStack.removeLast(); undoStack.addLast(merged) }
        else {
            undoStack.addLast(command)
            if (undoStack.size > limit) undoStack.removeFirst()
        }
        redoStack.clear()
        publish()
    }

    fun undo() = scope.launch {
        val cmd = undoStack.removeLastOrNull() ?: return@launch
        cmd.revert(); redoStack.addLast(cmd); publish()
    }

    fun redo() = scope.launch {
        val cmd = redoStack.removeLastOrNull() ?: return@launch
        cmd.apply(); undoStack.addLast(cmd); publish()
    }

    /** Mehrschritt-Rücksprung: bis Verlaufseintrag [index] (0 = neuester) zurück. */
    fun undoTo(index: Int) = scope.launch {
        repeat(index + 1) {
            val cmd = undoStack.removeLastOrNull() ?: return@repeat
            cmd.revert(); redoStack.addLast(cmd)
        }
        publish()
    }

    fun reset() { undoStack.clear(); redoStack.clear(); publish() }

    private fun publish() {
        _state.value = HistoryState(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            entries = undoStack.reversed().map { it.label },
        )
    }
}
```

**Race-Bug-Fix:** Der Snapshot/Inverse wird **synchron vor** der Mutation gebildet
(im Command-Konstruktor bzw. direkt vor `execute`), nicht in einer parallelen Coroutine.

**Inverse statt Voll-Snapshot, wo möglich** (speicherarm):
- Button löschen → Command merkt sich `(index, entfernter ButtonConfig)`.
- Button-Move → `(fromIndex, toIndex)`.
- Label/Config ändern → `(index, alteConfig, neueConfig)`.
- Komplexe Ops (Bulk-Reorder, AI-Restructure, Seite löschen inkl. Referenzen) dürfen
  als Command-Body weiterhin Voll-Snapshots der betroffenen Seite(n) halten.

**Scope „pro Buch":** `EditHistory` lebt im `GridEditorViewModel` und wird bei Buchwechsel
(`setActiveBookId`) per `reset()` geleert. Commands tragen die `bookId` und dürfen
mehrere Seiten desselben Buchs anfassen (z. B. „Seite gelöscht" stellt Seite **und**
eingehende Referenzen wieder her — passt zur Incoming-References-View).

---

## 3. UI / UX

**Topbar (dauerhaft):** Undo- und Redo-IconButton mit `enabled = canUndo/canRedo`,
Tooltip = nächstes Label („Rückgängig: Button 'Apfel' gelöscht"). Icons:
`Icons.AutoMirrored.Filled.Undo` / `...Redo`.

**Verlaufs-Panel:** Bottom-Sheet/Dropdown mit `HistoryState.entries` (neueste zuerst),
je Zeile Icon + lokalisiertes Label + relative Zeit. Tippen auf Eintrag `i` →
`history.undoTo(i)` (Mehrschritt-Rücksprung). Redo bleibt verfügbar, solange keine
neue Aktion erfolgt.

**Snackbar bleibt** als sofortiges Ein-Tipp-Undo direkt nach einer Aktion
(`showUndoSnackbar` ruft künftig `history.undo()`).

---

## 4. Arbeitspakete (phasenweise)

### Phase 1 — Fundament & Sichtbarkeit
- [ ] `EditCommand`, `EditLabel`, `EditIcon`, `EditHistory`, `HistoryState` neu anlegen
      (`ui/pages/history/`).
- [ ] `EditHistory` in `GridEditorViewModel` instanziieren (mit `viewModelScope`),
      `reset()` bei Buchwechsel.
- [ ] Bestehende Button-Operationen in `PageManagementDelegate` auf Commands umstellen
      (`updateButtonConfig`, `insertButtonConfig`, Move/Delete) — **Race-Bug fixen**
      (Inverse synchron vor Mutation bilden). `undoStack`/`saveUndoStateForPage` entfernen.
- [ ] `GridEditorActions` erweitern: `undo()`, `redo()`, `val historyState: StateFlow<HistoryState>`
      (ersetzt `canUndo`). VM verdrahten.
- [ ] Undo-/Redo-Buttons in die Editor-Topbar; `showUndoSnackbar` → `history.undo()`.

### Phase 2 — Verlaufs-Panel & Komfort
- [ ] Verlaufs-Panel (Bottom-Sheet) mit `entries`, Icons, relativer Zeit, `undoTo(i)`.
- [ ] Lokalisierte Labels je Command-Typ in `values/strings.xml` + `values-en/strings.xml`
      (`history_delete_button`, `history_move_button`, `history_edit_label`,
      `history_reorder`, `history_page_*`, `history_book_*`), mit Formatargumenten.
- [ ] Coalescing via `mergeWith` für schnelle Label-Edits (gleicher Button-Index in
      kurzem Zeitfenster → ein Schritt).

### Phase 3 — Abdeckung ausweiten
- [ ] Seiten-Ops (anlegen/löschen/umbenennen/reorder) als Commands; Löschen stellt
      Referenzen mit wieder her.
- [ ] Bulk-Reorder (`ui/pages/bulkreorder/`) als ein Command (Voll-Snapshot der Seite ok).
- [ ] Buch-Duplizieren + AI-Restructure/Split als Commands.

---

## 5. Risiken / offene Details
- **Mutationen außerhalb des Editors** (z. B. Sync): Der In-Memory-Verlauf kann durch
  Fremd-Updates inkonsistent werden. Mitigation: bei eingehendem Sync-Update für das
  aktive Buch `history.reset()` (konservativ) oder Commands gegen aktuellen
  Repo-Stand validieren und bei Konflikt überspringen.
- **`undoTo` Teilfehler:** Wenn ein `revert()` mittendrin scheitert, Schleife abbrechen
  und Zustand publizieren (kein „alles oder nichts" nötig, aber konsistent halten).
- **Redo nach Snackbar-Undo:** Snackbar-Undo nutzt denselben `history.undo()` → Redo
  bleibt korrekt verfügbar.

---

## 6. Review-Checkliste (Claude)
- [ ] Race-Bug behoben: Inverse/Snapshot synchron vor der Mutation.
- [ ] `EditHistory.reset()` bei Buchwechsel; kein Verlust/Leak über Seitenwechsel.
- [ ] Redo-Stack wird bei neuer `execute`-Aktion geleert.
- [ ] `limit`/50 greift (ältester Eintrag fällt raus).
- [ ] Labels lokalisiert (de + en), Formatargumente korrekt.
- [ ] Toolbar-Buttons `enabled`-Zustand korrekt an `historyState` gebunden.
- [ ] `undoTo(i)` macht genau die erwarteten i+1 Schritte rückgängig.
- [ ] Keine doppelte Persistenz/Repo-Schreibvorgänge pro Command.
