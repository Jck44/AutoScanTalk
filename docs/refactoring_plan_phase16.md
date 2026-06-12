# Refactoring-Plan Phase 16: `core-data`-Monolithen (Rev. 1, 2026-06-12)

Fortsetzung der Roadmap (Phasen 1–14 ✅, Phase 15 läuft — `docs/refactoring_plan_phase15.md`). Phase 16 kann **unabhängig von Phase 15** umgesetzt werden (andere Module, keine Dateiüberschneidung); Schritte 16.1–16.3 und 16.4–16.5 sind auch untereinander unabhängig.

**Arbeitsmodus:** Claude reviewt und plant, Gemini setzt um. Jeder Schritt einzeln kompilierbar, testbar, committen. Verifikation je Schritt: `./gradlew :core-data:compileDebugKotlin` + `./gradlew :core-data:testDebugUnitTest`, vor Commit zusätzlich `./gradlew assembleDebug` + voller Testlauf.

**Oberste Regel — noch strenger als Phase 15:** Beide Klassen sind **datenkritisch** (Backup, Import, Cloud-Sync — im Fehlerfall verlieren Nutzer ihre Bücher). Verhalten strikt paritätisch verschieben, wie bei Phase 11. Die unten gelisteten **Invarianten** sind Review-Pflichtpunkte. Positiv: anders als bei SystemCallManager existiert hier bereits ein **gutes Regressionsnetz** (49 Tests, s. Ist-Analyse) — das ist die Absicherung jedes Schritts und darf nicht „angepasst" werden, um grün zu werden.

## Status

| Schritt | Thema | Stand |
|---|---|---|
| 16.1 | ImportExport: Quick Wins + pure Funktionen + Tests | ⬜ offen |
| 16.2 | ImportExport: Media-/Statistik-/Config-Teile herauslösen | ⬜ offen |
| 16.3 | ImportExport: BookJsonExporter / BookJsonImporter | ⬜ offen |
| 16.4 | CloneBook: pure Helfer (Grid/Slots/NavButton/Chunking) + Tests | ⬜ offen |
| 16.5 | CloneBook: BookDataCloner + RestructureActionApplier | ⬜ offen |
| 16.6 | Abschluss-Review | ⬜ offen |

## Betroffene Dateien

| Datei | Zeilen | Ziel |
|---|---|---|
| `core-data/.../impl/PageImportExportManager.kt` | 1042 | Fassade < 300, keine neue Datei > 400 |
| `core-data/.../impl/CloneBookUseCase.kt` | 817 | < 350, keine neue Datei > 350 |

Umfeld (bleibt unangetastet): `ZipArchiver` (94 Z., getestet), `ActionMapper` (163 Z.), `SettingsMapper` (195 Z.), das Interface `core/.../export/PageImportExportProvider` (s. u.), alle Konsumenten.

## Ist-Analyse (Claude, im Code verifiziert, 2026-06-12)

### PageImportExportManager.kt (1042 Z., `@Singleton`, implementiert `PageImportExportProvider`)

**Sieben vermischte Verantwortungen:**
1. **Book-JSON-Export**: `exportPageListToJson` (Z. 69), `exportBookToJson` (Z. 108) — inkl. Tombstone-Export mit Prune-nach-Serialisierung (Kommentare Z. 134–137 / 209–211 erklären die tragende Reihenfolge).
2. **Book-JSON-Import**: `importFromJson` (Z. 221–496, **~275 Z., eine Methode**): Transaktion, ID-Regeneration, Spatial-Mapping auf 7×7, Buch-Settings, Templates, Static-Row-Reparatur, INSTALL_UPDATE-Purge. Dazu `importCloudBackup` (Z. 516) mit Regex-Heuristiken zur Buch-ID-Extraktion.
3. **Book-ZIP-Container**: `exportBookToZip`/`importFromZip`/`importCloudBackupFromZip` (Z. 560–676, 734–783) inkl. Vocal-Profile-Beifang.
4. **TTS-Cache-ZIP**: Export/Import/`getTtsCacheLastModified` (Z. 678–732).
5. **Audio-Recordings-ZIP**: Export/Import/LastModified (Z. 949–1004) — **nahezu zeilengleiches Duplikat von Nr. 4** (nur Verzeichnis `elevenlabs`↔`audio_recordings` und Endung `.mp3`↔`.ogg`).
6. **Statistik**: `exportStatisticsToJson`/`importStatisticsFromJson` (Z. 785–915) + ZIP-Hüllen + LastModified (Z. 917–947).
7. **Book-Config-JSON**: Z. 1006–1035 (dünne Hülle um `SettingsMapper`).

**Weitere Befunde:**
* **Totes Duplikat**: `isSafeFile` (Z. 1037–1041) wird in der Klasse nirgends aufgerufen — die lebende Kopie steckt (inkl. Test) in `ZipArchiver.kt:89`. Ersatzlos löschen.
* `@Suppress("UNUSED_PARAMETER")` an der Klasse (Z. 45) — nach Aufräumen prüfen, ob es entfallen kann.
* **4× anonyme `ZipArchiver.UnzipHandler`** mit fast identischem `handleFileEntry` (Z. 624, 713, 753, 929).
* **ButtonConfig-Aufbau aus `ImportButton` existiert 2×** (Seiten-Import Z. 346–370, Template-Import Z. 423–446) inkl. dupliziertem `spokenTextMode`-Parsing mit Warning.
* `runBlocking` in Unzip-Callbacks (Z. 633, 932) — Altbestand; **nicht umbauen**, nur mitverschieben (Redesign wäre Verhaltensänderung in der Fehlerreihenfolge).
* Hartkodierte deutsche Strings in Exceptions/Warnings/Defaults (`"Keine backup.json im ZIP gefunden."`, `"Ein Buch mit der ID … existiert bereits lokal…"`, `"Unbekannter spokenTextMode …"`, `"Exportierte Seiten"`, `"Statische Zeile"`, `"Importiertes Buch"`) — Daten-Schicht ohne sauberen i18n-Pfad; **nur sammeln, eigenes Ticket**, nicht in dieser Phase.
* Das Interface `PageImportExportProvider` (core-Modul) hat **22 Methoden** und ~14 Konsumenten in 4 Modulen (`app`: MainActivity/SharedZipImportHandler, `feature-settings`: SettingsViewModel/BackupSettingsDelegate, `core-cloud`: 10 Sync-Helfer, `core-data`: DataModule). Die Cloud-Helfer nutzen je genau eine Teilmenge (TtsSyncHelper→TTS, AudioSyncHelper→Audio, StatisticsSyncHelper→Statistik, ConfigSyncHelper→Config) — **ISP-Aufteilung drängt sich auf, ist aber wegen der Konsumenten-Streuung eigenes Folgeticket** (s. „Explizit NICHT").

**Testlage**: `PageImportExportManagerTest` — **40 Tests, 1466 Z.**, reine JVM-Tests (MockK, echte `SettingsMapper`/`ActionMapper`/`ZipArchiver`-Instanzen, direkte Konstruktor-Aufrufe). Gutes Regressionsnetz. ⚠️ Konstruktor-Änderungen schlagen direkt auf den Test-Setup-Block durch (Z. 57–71) — neue Kollaborateure dort real instanziieren, **Tests selbst nicht umschreiben**.

### CloneBookUseCase.kt (817 Z., `@Singleton`)

Zwei große Transaktionsmethoden: `execute()` (Z. 33–364, Deep-Copy + optionales `BookRestructureProposal`) und `applyHierarchyRestructure()` (Z. 366–816, KI-Hierarchie-Neuaufbau). **Massive interne Duplikate:**

1. **Sessions/Stats/History-Klonen 2×** (Z. 291–332 vs. 735–785) — Unterschiede beachten: `execute` mappt Stats-`pageId` über `pageIdMap` (Id→Id), `applyHierarchyRestructure` über Seiten-**Namen** und überspringt Einträge ohne `mappedButtonId`. Beim Dedupe als injizierbare Mapping-Lambdas abbilden, **nicht vereinheitlichen**.
2. **SharedPrefs-Klonen 2×** (Z. 334–360 vs. 787–813) — Unterschied nur die `defaultStartPageId`-Zeile (`pageIdMap[value]` vs. `pageIdMap["Hauptseite"]`-Heuristik) → gemeinsame Funktion mit `mapStartPageId: (String) -> String`-Parameter.
3. **Grid-Größen-Heuristik 3×** (Z. 225–230, 421–426: bis 5×5; Z. 656–663: bis 7×7) — zwei **bewusst verschiedene** Tabellen, nicht zusammenlegen, sondern als zwei benannte Funktionen extrahieren.
4. **„Ersten freien Slot finden (+ Grid erweitern)" 3×** innerhalb von MOVE_BUTTON (Z. 133–168, 151–167, 174–192).
5. **Nav-Button-Erzeugung 4×** (Z. 266–275, 495–505, 550–559, 643–652) — Label/`"Öffne …"`-SpokenText/Cue/NavigateAction.
6. **48+„Weiter"-Chunking 2×** (Layout-Seiten Z. 523–587, Archiv-Seiten Z. 598–678) — Unterschiede: Namensschema, `isActive`-Flag der Archiv-Buttons, Grid-Tabelle.
7. **NavigateAction-Remap über Seiten-Namen 3×** (Z. 472–479, 622–629, 709–715).

**Dokumentieren, nicht ändern**: History-Klonen ist auf die letzten **1000** Events begrenzt (Z. 319/768); hartkodierte deutsche Inhalte (`"[Vorschlag]"`, `"Öffne …"`, `"Archiv"`, `"Weiter"`, `"Hauptseite"`) sind Nutzdaten im geklonten Buch → i18n-Ticket.

**Testlage**: `CloneBookUseCaseTest` — **9 Szenario-Tests, 775 Z.** (inkl. Displacement-Swap, Archiv-Chunking > 49, Static-Row in beiden Methoden). Regressionsnetz vorhanden.

## Invarianten (Review-Pflichtpunkte in jedem Schritt)

1. **Transaktionsgrenzen unverändert**: gesamter schreibender Import in `pageRepository.runInTransaction`, `settingsRepository.refresh()` erst **nach** Commit; CloneBook komplett in `appDatabase.withTransaction`.
2. **Tombstone-Reihenfolge**: lesende Query → Serialisierung → erst dann `pruneTombstones` (nicht-fatal bei Fehler). Beim Import: 90-Tage-Cutoff-Filter.
3. **Deterministische Export-Sortierung** (`sortedBy` auf Templates/Pages/Buttons/Tombstones) — darauf verlässt sich der Anker-Vergleich des Cloud-Syncs. Byte-Identität des JSON für gleiche Daten muss erhalten bleiben.
4. **ID-Regeneration-Regeln**: `forceRegeneration` bei Fremd-Buch-Id, `static_row_*` → `static_row_$bookId`, Button-Ids nur bei regenerierten Seiten neu.
5. **Spatial-Mapping**: GhostTalk-Backups = Index direkt; Fremdformate = Umrechnung Quellspalten → 7 Spalten; Index ≥ 49 wird verworfen.
6. **Clone-Mapping-Differenzen** aus Ist-Analyse Nr. 1/2 (Id- vs. Namens-Mapping, Skip-Verhalten) bleiben je Methode exakt erhalten.
7. Bestehende Tests bleiben unverändert grün (Anpassung **nur** an Konstruktor-Aufrufen im Setup).

---

## Vorgehen

### Schritt 16.1: ImportExport — Quick Wins + pure Funktionen + Tests

1. Totes `isSafeFile` (Z. 1037–1041) löschen; `@Suppress("UNUSED_PARAMETER")` (Z. 45) prüfen/entfernen.
2. Pure Funktionen in neue Datei `impl/importexport/ImportMappers.kt` (top-level `internal fun`, kein Android):
   * `mapImportIndexToGrid(isGhostTalk: Boolean, index: Long, sourceColumns: Int): Int` (ersetzt Z. 374–382)
   * `autoExpandGrid(rows: Int, cols: Int, buttons: List<ImportButton>): Pair<Int, Int>` (ersetzt Z. 389–397)
   * `extractCloudBookId(bookId: String?, bookName: String?, cloudFileId: String?): String?` (Regex-Heuristik Z. 521–533, inkl. `trim().lowercase()`)
3. `buildButtonConfigFromImport(importButton, idOverride, actionResolver, warnings)` als gemeinsame Funktion — ersetzt die 2 Kopien (Z. 346–370, 423–446) inkl. einmaligem `spokenTextMode`-Parsing.
4. **Neue Tests** `ImportMappersTest` (erschöpfend): GhostTalk- vs. Fremd-Index (mehrere Spaltenzahlen, Index ≥ 49), Auto-Expand-Grenzen (1×1→7×7), Cloud-Id aus Feld/`[uuid]`-Namen/FileId/`book_…json`-Präfix/null.

### Schritt 16.2: ImportExport — Media / Statistik / Config herauslösen

Neue Klassen unter `impl/importexport/` (je `@Inject constructor`, von der Fassade injiziert):
* `MediaArchiveSync`: **dedupliziert TTS-Cache und Audio-Recordings** — eine parametrisierte Implementierung (`dirName`, `zipPrefix`, `extension`), vier dünne öffentliche Methoden + 2× LastModified. Die 2 anonymen UnzipHandler dieser Pfade werden eine gemeinsame private Implementierung.
* `StatisticsImportExport`: Z. 785–947 unverändert verschieben (inkl. `runBlocking`-Altbestand und `static_row_`-Remap).
* `BookConfigImportExport`: Z. 1006–1035 verschieben.
* Fassade delegiert; Interface/`DataModule` unverändert. Test-Setup: neue Kollaborateure real konstruieren.

### Schritt 16.3: ImportExport — BookJsonExporter / BookJsonImporter (Kern-Schritt)

* `BookJsonExporter`: `exportPageListToJson` + `exportBookToJson` (inkl. Tombstone-Logik, Invarianten 2+3!).
* `BookJsonImporter`: `importFromJson` + `importCloudBackup` + `extractBookId-/BookNameFromJson`. Die 275-Zeilen-Methode dabei in private benannte Schritte gliedern (`clearAndRestoreTombstones`, `applyBookMeta`, `buildIdMap`, `restoreBookScopedPrefs`, `importPages`, `importTemplates`, `ensureStaticRow`) — **reine Extraktion innerhalb der Transaktion, keine Reihenfolge-Änderung**.
* ZIP-Container-Methoden (Buch) bleiben in der Fassade (sie orchestrieren Exporter/Importer + `MediaArchiveSync`-Verzeichnisse + Vocal-Profile).
* Gemeinsame `Json`-Konfiguration (Z. 63–67) **einmal** definieren (z. B. `internal val ImportExportJson`) und überall referenzieren — drei abweichende Instanzen wären ein Sync-Risiko (Invariante 3).
* Zielbild: Fassade `PageImportExportManager` < 300 Z. (Interface-Implementierung + ZIP-Orchestrierung).

### Schritt 16.4: CloneBook — pure Helfer + Tests

Neue Datei `impl/clone/CloneHelpers.kt` (top-level/Objekt, kein Android, kein Room):
* `optimalGridUpTo5(buttonCount)` und `optimalGridUpTo7(buttonCount)` (die zwei Tabellen, Ist-Analyse Nr. 3)
* `expandGridByOne(rows, cols)` (das `if (cols<7) cols++ else if (rows<7) rows++`-Muster)
* `findFreeSlot(occupied: Set<Int>, excluded: Int? = null): Int`
* `createNavButton(pageId, targetPageId, label, slot, isActive): ButtonEntity` (ersetzt die 4 Kopien — Achtung: Archiv-Variante hat `isActive = false`)
* `chunkButtonsIntoPages(...)`: das 48+„Weiter"-Chunking als gemeinsame Funktion mit Parametern für Namensschema, `isActive` und Grid-Tabelle (ersetzt beide Kopien aus Ist-Analyse Nr. 6)
* **Neue Tests** `CloneHelpersTest`: Grid-Tabellen erschöpfend (alle Schwellwerte beider Varianten), freier Slot mit/ohne Ausschluss, Chunking mit 1/48/49/50/97 Buttons (Seitenzahl, „Weiter"-Slots auf Index 48, letzte Seite 49).
* `CloneBookUseCase` ruft die Helfer auf — Verhalten identisch, bestehende 9 Tests bleiben grün.

### Schritt 16.5: CloneBook — BookDataCloner + RestructureActionApplier

* `MutablePageWithButtons` in eigene Datei `impl/clone/` (wird von allen Teilen gebraucht).
* `BookDataCloner` (Klasse, bekommt `appDatabase`/`prefs`): `cloneSessions`, `cloneStats(mapButtonId, mapPageId, skipUnmapped: Boolean)`, `cloneHistory(...)`, `cloneBookPrefs(sourceBookId, targetBookId, mapStartPageId: (String) -> String)` — ersetzt die Duplikate aus Ist-Analyse Nr. 1/2; **die Mapping-Differenzen werden an den zwei Aufrufstellen explizit übergeben** (Invariante 6).
* `RestructureActionApplier`: die `proposal?.actions`-Verarbeitung (MOVE_BUTTON inkl. Displacement, DEACTIVATE_BUTTON, SPLIT_PAGE; Z. 103–281) als eigene Klasse über dem In-Memory-Modell.
* `CloneBookUseCase` behält: die zwei öffentlichen Methoden als Orchestrierung (Deep-Copy / Hierarchie-Aufbau + Persistierung), Ziel < 350 Z.
* Kein neuer Pflichttest (Szenarien decken es ab), aber: falls beim Dedupe Unklarheiten zwischen den zwei Stats-/History-Varianten auftauchen, **stoppen und nachfragen** statt vereinheitlichen.

### Schritt 16.6: Abschluss-Review (Claude)

* Zielgrößen erreicht; alle 49 Bestandstests unverändert grün; Invarianten-Checkliste durchgehen (insb. JSON-Byte-Identität: vor/nach Refactoring denselben Buch-Export erzeugen und diffen — idealerweise als einmaliger manueller Vergleich dokumentiert).

## Smoke-Test-Checkliste (vor jedem Commit von 16.2/16.3/16.5)

1. Buch als ZIP exportieren → in zweites Buch importieren (mit/ohne ID-Regeneration) → Seiten, Navigation, Static Row, Start-Seite korrekt.
2. Legacy-JSON-Import (altes Backup, falls vorhanden).
3. Cloud-Sync-Runde: Buch ändern → Sync → zweites Gerät/Neuinstallation zieht korrekt (Anker-Merge schlägt nicht fehlsynchron an — Log prüfen: kein unerwarteter Voll-Upload durch geänderte JSON-Serialisierung!).
4. Statistik-Export/-Restore (RESTORE_ONLY-Modus), TTS-Cache-ZIP, Audio-Recordings.
5. Analytics → KI-Restrukturierung → Vorschlag anwenden (`applyHierarchyRestructure`): neues `[Vorschlag]`-Buch mit Archiv-Seiten; Layout-Vorschlag anwenden (`execute` mit Proposal).

## Explizit NICHT in Phase 16

* **ISP-Aufteilung des `PageImportExportProvider`-Interfaces** (22 Methoden → Book/Media/Statistics/Config) — sinnvoll, aber 14 Konsumenten in 4 Modulen; eigenes Ticket nach 16.3, wenn die internen Schnitte sich bewährt haben.
* `runBlocking`-Redesign in den Unzip-Callbacks — Verhaltensänderung, nur dokumentiert.
* i18n der Daten-Schicht-Strings (Exceptions, „Archiv"/„Öffne …"-Nutzdaten) — Sammelticket.
* Vereinheitlichung der bewusst verschiedenen Grid-Tabellen oder der Stats-Mapping-Varianten.
* Anhebung des 1000-Event-Limits beim History-Klonen.

## Ausblick (Phase 17+)

1. ISP `PageImportExportProvider` (s. o.) + ISP-Restschuld Settings-Interfaces (aus 4.2: `elevenLabs*`-Doppeldeklarationen, Provider-Querdelegationen, Fassaden-Rest in `SettingsRepositoryImpl`, 725 Z.).
2. `CloudSyncUseCase` (677 Z.) / `ConfigSyncHelper` (486 Z.) — erst nach Abschluss der optionalen Sync-Plan-Phase-4-Diskussion.
3. Kleintickets: `ImportResult.ReadError`, i18n-Sammelticket, `TEST_PLAN_75.md`, Scan-Limit-0-Prüfung in der Settings-UI.
