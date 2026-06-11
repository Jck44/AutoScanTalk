# GhosTTalk: Kombinierter Plan — Bug-/Security-Fixes + SOLID-Refactoring

## Kontext

GhosTTalk ist eine AAC-App (unterstützte Kommunikation): Button-Grids, TTS (Android/ElevenLabs), Scanning-Eingabe, Vocal-Switch, Gemini-AI mit Google-Tools, Telefonie, Cloud-Sync (Drive/SAF). ~600 Kotlin-Dateien, 10 Module, Hilt, Room v34, Compose. App soll **öffentlich in den Play Store**.

**Anforderungen des Users:**
1. Bugs und Security-Probleme finden (Audit) — Findings als Report.
2. Den mit Gemini erstellten [docs/refactoring_plan.md](docs/refactoring_plan.md) (7 Phasen OOP/SOLID) integrieren.
3. **Schritt-für-Schritt-Plan: Nach jedem Schritt muss ein Build möglich sein, den der Testuser benutzen kann.** Automatisierte Tests werden je Schritt erweitert, Prüfung nach jedem Schritt.
4. Prioritäten: (a) Scanning/TTS darf den AAC-Nutzer **niemals blockieren** (einzige Kommunikationsmöglichkeit), (b) **kein Datenverlust** (Sync/Import/Migration).

**Exploration hat ergeben (teils von mir im Code verifiziert):**
- `SecuritySettingsEncryptor.kt`: AES-CBC mit Key aus SHA-256(Android ID), kein Keystore; **alle catch-Blöcke geben bei Fehler Klartext zurück** → Secrets können unbemerkt unverschlüsselt landen (verifiziert, Zeilen 41–43, 67–69).
- `ZipArchiver.kt`: Zip-Slip-Schutz vorhanden (verifiziert) — gut.
- Agent-Befunde (je Schritt vor dem Fix zu verifizieren): Spotify-Refresh-Token im Klartext; Hue-`HostnameVerifier { _, _ -> true }`; FileProvider exponiert kompletten Cache; `allowBackup=true` mit leeren Backup-Regeln; Prompt-Injection-Kette GmailReadTool→CalendarCreateTool; Races in ScannerEngine/ScanCoordinator/TextToSpeechHelper/GeminiUseCase; Tombstone-Pruning vor Export-Erfolg; BookMergeService meldet Erfolg trotz Upload-Fehler; 25+ verschluckte Exceptions in PageImportExportManager; Migration 10→11 verliert Buttons bei kaputtem Legacy-JSON still.

## Prüfschema nach JEDEM Schritt (Definition of Done)

1. `./gradlew test` (alle Module) grün — inkl. der in dem Schritt neu geschriebenen Tests.
2. `./gradlew assembleDebug` erfolgreich.
3. Manueller Smoke-Test für den Testuser (Checkliste): App-Start, User-Mode aktivieren, Scanning läuft + Button auslösen, TTS spricht (beide Engines, falls betroffen), Buch bearbeiten + speichern, Sync-Durchlauf ohne Fehler (falls betroffen).
4. Commit pro Schritt → jederzeit installierbarer Stand für den Testuser.

---

## Block 0 — Fundament

### Schritt 0.1: Audit-Report erstellen
Alle oben genannten Befunde direkt im Quelltext verifizieren (Datei:Zeile) und als priorisierten Report nach `docs/security_bug_audit_2026-06.md` schreiben (Kritisch/Hoch/Mittel/Niedrig, je Finding: Fundstelle, Schadensszenario, Fix-Empfehlung, Verweis auf den umsetzenden Schritt dieses Plans). Unbestätigte Agent-Befunde werden markiert oder gestrichen; die nachfolgenden Schritte werden bei Bedarf angepasst.
**Prüfung:** Kein Code geändert → nur Review des Reports. Build unverändert lauffähig.

### Schritt 0.2: Repo-Hygiene & Test-Baseline
- Build-/Log-Artefakte aus dem Repo entfernen (`logcat_dump.txt`, `build_*.txt`, `test_*.txt`, `htmlReport/`, `sample.zip`, `gotalknow_extracted/` …) und in `.gitignore` aufnehmen — bei öffentlichem Repo potenzielles Datenleck.
- Einmal `./gradlew test` als Baseline laufen lassen und dokumentieren, was aktuell grün ist.
**Prüfung:** Build + Tests unverändert grün (reine Hygiene, kein Produktivcode).

---

## Block 1 — Datenverlust verhindern (User-Priorität 2, aber kleinste/risikoärmste Fixes zuerst)

### Schritt 1.1: Tombstone-Pruning absichern
`PageImportExportManager` (core-data): 90-Tage-Pruning der `deleted_entities` passiert vor/während des Exports — schlägt der Export fehl, sind Tombstones weg → gelöschte Elemente kommen beim nächsten Merge zurück bzw. Löschungen gehen verloren. Pruning erst **nach** bestätigtem Export-Erfolg ausführen (bzw. in Transaktion mit Erfolgspfad).
**Tests:** Neuer Unit-Test: Export wirft Exception → Tombstones unverändert; Export ok → Tombstones >90 Tage entfernt.

### Schritt 1.2: Import atomar machen, Fehler nicht verschlucken
`PageImportExportManager.importFromJson(...)`: Import in eine Room-Transaktion (`withTransaction`) packen; die Catch-all-Blöcke so umbauen, dass Fehler gesammelt und als Ergebnis (z. B. `ImportResult` mit Warnungen/Fehlern) an den Aufrufer gemeldet werden statt still weiterzulaufen. Partial Imports (Seite ohne Buttons) dürfen nicht mehr entstehen.
**Tests:** Import mit defektem Button-JSON → Rollback, Fehler im Ergebnis; valider Import → vollständig.

### Schritt 1.3: Sync-Ergebnis ehrlich melden + Audio-Extraktion atomar
`BookMergeService` (core-cloud): (a) Wenn lokaler Merge klappt, aber Cloud-Upload fehlschlägt, darf nicht "Erfolg" zurückkommen — eigener Status (z. B. `MERGED_LOCALLY_UPLOAD_PENDING`), damit UI/Worker Retry planen; Konflikt-Dateien erst nach vollständigem Abschluss (inkl. Audio-Sync) löschen. (b) Audio-ZIP-Extraktion in Temp-Verzeichnis + atomarer Move; bei Abbruch keine halben Dateien.
**Tests:** `BookMergeServiceTest` erweitern: Upload-Fehler → Status korrekt, Konfliktdateien bleiben; Extraktionsabbruch → kein Orphan.

### Schritt 1.4: Migrations-Sicherheitsnetz
Room-Migrationstest für MIGRATION_10_11 (Legacy-JSON→buttons-Tabelle) mit `MigrationTestHelper`, inkl. Fall "kaputtes JSON" — heutiges Verhalten (Buttons werden still verworfen) zumindest testbar machen und Fehler loggen/zählen statt komplett still. Schemaversion 34 bleibt unverändert.
**Tests:** androidTest-Migrationstests 10→11 und 33→34 (Durchstich).

---

## Block 2 — Scanning/TTS-Zuverlässigkeit (User-Priorität 1) — verzahnt mit Refactoring-Phasen 1–3

Reihenfolge bewusst: Erst das TTS-Fundament (Phasen 2+3 + Bugfixes), dann die Scanning-Engine (Phase 1 + Bugfixes), weil Scanning auf TTS-Cues aufbaut.

### Schritt 2.1: Refactoring-Phase 2 — TextToSpeechHelper entkoppeln + Race fixen
- Gemini-Plan Schritt 2.1 + 2.2: `isSpeaking()` polymorph, neuer `FallbackTtsProvider` (Decorator: ElevenLabs-Fehler → automatisch Android-TTS), `is ElevenLabsTtsProvider`-Checks entfernen.
- Zusätzlich (Audit): Provider-Switch in `TextToSpeechHelper.switchProvider()` mit Mutex absichern (aktuell kann `speakRouted()` einen veralteten Provider verwenden); Fallback-Fehler nicht still schlucken, sondern als Status exponieren (UI kann "Offline-Stimme aktiv" zeigen).
**Tests:** `core-tts`-Unit-Tests: Fallback bei Provider-Exception, kein Sprachverlust bei gleichzeitigem Engine-Wechsel + speak().

### Schritt 2.2: Refactoring-Phase 3 — `CacheableTtsProvider` (ISP)
Gemini-Plan Schritte 3.1–3.3 unverändert: Interface-Split, ElevenLabs implementiert `CacheableTtsProvider`, AndroidTts verliert Dummy-Methoden, sichere Casts im Helper.
**Tests:** `./gradlew :core-tts:test`; bestehende Tests anpassen.

### Schritt 2.3: TTS-Blockaden entfernen
`TtsScannerFeedbackProvider` (core-scanning): 2-Sekunden-Spin-Wait auf TTS-Readiness (20×100 ms) ersetzen durch suspendierendes Warten auf einen Ready-Flow mit Timeout; Scanning darf bei nicht-initialisiertem TTS nicht hängen, sondern überspringt den Cue (visuelles Scanning läuft weiter).
**Tests:** Unit-Test: TTS nie ready → Scan-Schritt läuft trotzdem in <Timeout weiter.

### Schritt 2.4: Refactoring-Phase 1 — `ScanContext` + Strategien
Gemini-Plan Schritte 1.1–1.3: `ScanContext`-Datenklasse statt 14 Parameter, `LinearScanStrategy`/`RowByRowScanStrategy` anpassen, `ScannerEngine` nutzt injizierte Strategien statt Inline-Logik (toter Code der Strategien wird damit wieder lebendig bzw. die Duplikate in der Engine entfernt).
**Tests:** Bestehende `ScannerEngineTest`/`*StrategyTest` anpassen + Strategie-Parität testen (gleiche Schrittfolge wie vorher).

### Schritt 2.5: Scanning-Races und Job-Lifecycle fixen
Auf der in 2.4 bereinigten Engine: (a) mutablen Zustand der `ScannerEngine` (currentButtonConfigs, scanJob, …) über Mutex/StateFlow konsolidieren, `startScanning`/`stopScanning` atomar; (b) `predictionTimeoutJob` im `ScanCoordinator` bei Seitenwechsel/Stop sauber canceln; (c) doppelte Call-State-Beobachtung (ScanCoordinator vs. CallManagementDelegate) auf eine Quelle reduzieren — Ziel: Es gibt keinen Zustand, in dem Scanning dauerhaft aus bleibt, obwohl User-Mode aktiv ist.
**Tests:** `ScanCoordinatorTest` erweitern: schnelle Start/Stop-Folgen, Seitenwechsel während Prediction-Timeout, Call-Ende → Scanning läuft wieder an.

---

## Block 3 — Security-Härtung (Play-Store-Maßstab)

### Schritt 3.1: Verschlüsselung auf Android Keystore umstellen
`SecuritySettingsEncryptor`: AES-GCM-Schlüssel im Android Keystore statt SHA-256(Android ID); **kein Klartext-Fallback mehr** — bei Fehler Exception/leerer Wert + Logging, niemals Klartext persistieren. Transparente Migration: alte `enc_local:`-Werte beim ersten Lesen entschlüsseln (Altverfahren) und neu verschlüsseln. `encryptForTransit` (User-Seed) bleibt vorerst, wird im Report bewertet.
**Tests:** Roundtrip-Tests, Migrationspfad alt→neu, Fehlerfall persistiert keinen Klartext.

### Schritt 3.2: Tokens & Smart-Home-Credentials verschlüsseln
Spotify-Refresh-/Access-Token (`SpotifyManager`) und Hue-Credentials/Fingerprint (`SmartHomeSettingsRepository`) über den neuen Keystore-Encryptor speichern. Hue-`bypassHostnameVerifier` strikt auf die TOFU-Erstverbindung begrenzen (nach gespeichertem Fingerprint nie mehr verwenden).
**Tests:** Repository-Tests: Werte landen nur verschlüsselt in SharedPreferences.

### Schritt 3.3: Backup- und FileProvider-Scope schließen
- `backup_rules.xml`/`data_extraction_rules.xml` befüllen: SharedPreferences mit Secrets (Tokens, PIN-Hash, API-Keys) vom Auto-Backup/Device-Transfer ausschließen (sonst Exfiltration über Cloud-Backup).
- `file_paths.xml`: statt Cache-Root nur dediziertes Unterverzeichnis (z. B. `logs/`) freigeben; Log-Export-Code entsprechend anpassen.
**Tests:** Manuell prüfen (Backup-Regeln sind nicht unit-testbar); Smoke: Log-Upload/Share funktioniert weiter.

### Schritt 3.4: AI-Tool-Kette härten (Prompt Injection)
Aktionen mit Schreibwirkung (CalendarCreateTool, SpotifyTool-Play) bekommen eine Nutzerbestätigung, wenn der Trigger aus Tool-Ergebnissen mit Fremdinhalt stammt (z. B. Gmail-Snippets in derselben Konversation); Tool-Inputs validieren (maxResults-Bounds, String-Längen). Zusätzlich `GeminiUseCase`: companion-object-State (lockoutUntilTime, cachedModelsJson, activeModelName) thread-sicher machen (Mutex existiert bereits für Requests — State einbeziehen).
**Tests:** Unit-Tests für Bestätigungslogik und State-Synchronisierung.

---

## Block 4 — Struktur-Refactoring (Gemini-Plan Phasen 4–7, unverändert übernommen)

### Schritt 4.1: Phase 4 — SettingsRepository zerschlagen
`SettingsMigrationManager` extrahieren (4.1), Hilt-Bindings der Sub-Interfaces direkt auf Sub-Repos (4.2). **Tests:** `:core-data:test` + `assembleDebug` (Hilt-Graph).

### Schritt 4.2: Phase 5 — DIP für Sub-Repositories
`@Inject constructor()` für alle 11 Sub-Repos (5.1), Konstruktor-Injektion in `SettingsRepositoryImpl` (5.2). **Tests:** `:core-data:test`.

### Schritt 4.3: Phase 6 — PageViewModel aufteilen (3 Teilschritte, je einzeln baubar)
6.1 `CallViewModel`, 6.2 `GridEditorViewModel`, 6.3 `PageSplitViewModel` + `BookRestructureViewModel`. Nach jedem Teilschritt Build + `:app:testDebugUnitTest` + Smoke-Test (Telefonie bzw. Editor).

### Schritt 4.4: Phase 7 — Law of Demeter
Delegates `private` machen (7.1), tiefe Aufrufketten in `MainActivity`/`PageScreen` durch ViewModel-Methoden ersetzen (7.2). **Tests:** `./gradlew test` komplett.

---

## Restliche Audit-Findings (nur Report, kein eigener Schritt)
Mittlere/niedrige Findings ohne eigenen Schritt (z. B. E-Mail-Logging in `GoogleAuthManager`, `SystemCallManager`-Timer, VocalPatternMatcher-Threshold-Jitter, fehlende Validierung von AI-Restructure-Vorschlägen, UserModeSessionTracker-Loop) werden in `docs/security_bug_audit_2026-06.md` dokumentiert und können später priorisiert werden.

## Kritische Dateien (Kern der Änderungen)
- core-data: `PageImportExportManager.kt`, `settings/SecuritySettingsEncryptor.kt`, `settings/SettingsRepositoryImpl.kt`, `di/DataModule.kt`
- core-cloud: `domain/BookMergeService.kt`, `SpotifyManager.kt`, `PhilipsHueManager.kt`
- core-tts: `TextToSpeechHelper.kt`, `TtsProvider.kt`, `ElevenLabsTtsProvider.kt`, `AndroidTtsProvider.kt`, [NEU] `FallbackTtsProvider.kt`
- core-scanning: `ScannerEngine.kt`, `ScanCoordinator.kt`, `ScanStrategy.kt`, beide Strategien, `TtsScannerFeedbackProvider.kt`
- app: `ui/pages/PageViewModel.kt` + Delegates, `MainActivity.kt`, `PageScreen.kt`
- res/xml: `backup_rules.xml`, `data_extraction_rules.xml`, `file_paths.xml`
- core-database: `AppDatabase.kt` (nur Tests, keine Schemaänderung)

## Verifikation (gesamt)
- Je Schritt: Definition of Done oben (Tests + assembleDebug + Smoke-Checkliste + Commit).
- Blöcke 1–2 zuerst, weil sie die Testuser-Prioritäten direkt schützen; jeder Block ist unabhängig pausierbar — der Testuser hat nach jedem Commit einen nutzbaren Stand.
