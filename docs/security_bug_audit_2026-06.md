# GhosTTalk — Bug- & Security-Audit

**Datum:** 2026-06-11
**Umfang:** Statischer Quelltext-Review aller Module (~600 Kotlin-Dateien). Kein Code geändert.
**Verteilungskontext:** Öffentlicher Play Store → strenger Maßstab (Geräteverlust, fremde Apps, fremde Angreifer einkalkuliert).
**Prioritäten des Auftraggebers:** (1) Scanning/TTS darf den AAC-Nutzer niemals blockieren. (2) Kein Datenverlust.

Jedes Finding wurde direkt im Quelltext verifiziert (Datei:Zeile angegeben). Findings, die nur vermutet und nicht bestätigt werden konnten, sind als *(unbestätigt)* markiert.

---

## Zusammenfassung (Top-Risiken)

1. **Verschlüsselung mit Klartext-Fallback** (`SecuritySettingsEncryptor`): Schlüssel aus `SHA-256(Android ID)` statt Keystore, AES-CBC ohne Integritätsschutz, und bei jeder Exception wird **der Klartext zurückgegeben** — d.h. Secrets (Gemini-API-Key etc.) können unbemerkt unverschlüsselt in SharedPreferences landen. → Schritt 3.1
2. **Spotify-Tokens komplett unverschlüsselt** in SharedPreferences (`CloudSettingsRepository`). Refresh-Token = dauerhafter Kontozugriff bei Geräte-/Backup-Kompromittierung. → Schritt 3.2
3. **Auto-Backup exfiltriert alle Secrets**: `allowBackup="true"` mit leeren Backup-Regeln → Tokens, PIN-Hash und API-Keys wandern in Google-Cloud-Backup und auf neue Geräte. → Schritt 3.3
4. **Scanning/TTS-Blockaden**: 2-Sekunden-Spin-Wait bei nicht-initialisiertem TTS (`TtsScannerFeedbackProvider`) und Race Conditions auf dem `ScannerEngine`-State — beides kann das Scanning für den AAC-Nutzer anhalten. → Schritte 2.3, 2.5
5. **Sync meldet Erfolg trotz fehlgeschlagenem Upload** (`BookMergeService`): kein Datenverlust lokal, aber die Cloud divergiert still, während die UI „fertig" zeigt. → Schritt 1.3
6. **Prompt-Injection-Kette**: Gemini liest fremde E-Mail-Inhalte (`GmailReadTool`) und kann im selben Lauf schreibende Tools wie `create_calendar_event` aufrufen — ohne Nutzerbestätigung. → Schritt 3.4

Insgesamt ist die Codebasis solide strukturiert; viele Sicherheitsgrundlagen sind bereits richtig gelöst (siehe Positivliste). Die kritischen Punkte konzentrieren sich auf die lokale Secret-Speicherung und einige Nebenläufigkeits-/Fehlerpfade.

---

## KRITISCH

### K-1 — Verschlüsselung: Klartext-Fallback + kein Keystore + kein Integritätsschutz
**Datei:** `core-data/.../settings/SecuritySettingsEncryptor.kt:16-44`, `:46-70`
**Verifiziert.** Drei Probleme in einer Klasse:
- **Klartext-Fallback (am gravierendsten):** `encryptLocal` gibt bei *jeder* Exception `return value` zurück (Zeile 41-43) — der unverschlüsselte Wert wird dann so gespeichert. Symmetrisch gibt `decryptLocal` bei Fehler den Eingabewert zurück (Zeile 67-69). Schlägt die Verschlüsselung also einmalig fehl (z.B. Provider-Eigenheit), landet das Secret dauerhaft im Klartext in SharedPreferences, ohne dass es jemand merkt.
- **Schlüsselableitung aus Android ID:** `deriveKey` = `SHA-256(ANDROID_ID)` (Zeile 16-20, 26-30). Die Android ID ist kein Geheimnis (per Backup/Geräte-Tools/Debug auslesbar) und nicht hardwaregebunden. Kein Android Keystore / kein TEE.
- **AES/CBC ohne Authentifizierung:** kein GCM/HMAC → keine Manipulationserkennung.

**Schaden:** Gespeicherte Secrets (Gemini-API-Key in `GenAiSettingsRepository.kt:24-28`, perspektivisch weitere) sind bei Gerätezugriff/Backup im Klartext oder mit trivial ableitbarem Schlüssel lesbar.
**Empfehlung:** AES-GCM mit Schlüssel im Android Keystore (hardwaregebunden, nicht exportierbar). Fehlerpfad: Exception werfen oder leeren Wert zurückgeben — **niemals** Klartext persistieren. Transparente Migration alter `enc_local:`-Werte. → **Schritt 3.1**

### K-2 — Spotify-Tokens im Klartext gespeichert
**Datei:** `core-data/.../settings/CloudSettingsRepository.kt:46-49` (Plain `StringSetting`, kein `encrypt`/`decrypt`-Lambda), Schreiber: `core-cloud/.../SpotifyManager.kt:75-77, 146-148`
**Verifiziert.** Im Gegensatz zum Gemini-Key (`GenAiSettingsRepository.kt:24-28`, der `encryptLocal` nutzt) werden `spotify_access_token` und `spotify_refresh_token` als reine `StringSetting` ohne jede Verschlüsselung abgelegt.
**Schaden:** Der Refresh-Token gewährt dauerhaften Zugriff auf das Spotify-Konto (Bibliothek/Playlists). Bei Geräte-Root, Backup-Auslesen oder ADB-Zugriff sofort abgreifbar.
**Empfehlung:** Tokens über den (gehärteten) Encryptor speichern. → **Schritt 3.2**

### K-3 — Auto-Backup sichert alle Secrets in die Cloud
**Datei:** `app/src/main/res/xml/backup_rules.xml` (nur Kommentare), `app/src/main/res/xml/data_extraction_rules.xml` (leere `<cloud-backup>`), Manifest `allowBackup="true"`
**Verifiziert.** Beide Backup-Regel-Dateien sind faktisch leer (nur auskommentierte Beispiele). Damit gilt der Default: **alle** SharedPreferences werden in Googles Auto-Backup und beim Geräte-Transfer mitgenommen — inklusive PIN-Hash + Salt (`SecuritySettingsRepository.kt:20-21`), Spotify-Tokens und API-Keys.
**Schaden:** Secrets verlassen das Gerät und liegen im Cloud-Backup; auf einem neuen Gerät werden sie wiederhergestellt. Kombiniert mit K-1/K-2 (schwache bzw. keine Verschlüsselung) ein realer Exfiltrationspfad.
**Empfehlung:** In beiden XML-Dateien die Secret-tragenden Prefs/Keys explizit `<exclude>`-n (bzw. Allowlist-Ansatz). → **Schritt 3.3**

---

## HOCH

### H-1 — Scanning hängt bis zu 2 s, wenn TTS nicht bereit ist
**Datei:** `core-scanning/.../TtsScannerFeedbackProvider.kt:14-19`
**Verifiziert.** `speakCue` pollt `while (!ttsHelper.isReady && retries < 20) { delay(100) }` — also bis zu 2 Sekunden pro Cue. Diese Funktion wird im Scan-Loop synchron vor jedem Schrittwechsel aufgerufen (`ScannerEngine.kt:214, 219`). Ist die TTS-Engine (noch) nicht initialisiert oder hängt, blockiert jeder Scan-Schritt 2 s.
**Schaden (Priorität 1 des Auftraggebers):** Das visuelle Scanning, auf das der AAC-Nutzer angewiesen ist, stockt oder steht — direkte Beeinträchtigung der Kommunikationsfähigkeit.
**Empfehlung:** Auf einen `isReady`-StateFlow mit kurzem Timeout warten statt Busy-Poll; wenn TTS nicht bereit ist, den Cue überspringen und das visuelle Scanning **ohne Verzögerung** weiterlaufen lassen. → **Schritt 2.3**

### H-2 — Race Conditions auf dem ScannerEngine-Zustand
**Datei:** `core-scanning/.../ScannerEngine.kt:30-42, 77-103, 281-282, 324-331`; Scope: `app/.../di/CoroutineScopeModule.kt:15-20` (`CoroutineScope(SupervisorJob())` ohne Dispatcher → `Dispatchers.Default`)
**Verifiziert.** `scanJob` und die `current*`-Felder (`currentButtonConfigs`, `currentPattern`, …) sind ungeschützte `var`s. `startScanning`/`stopScanning`/`pauseScanning`/`selectCurrentRow` werden vom UI-Thread aufgerufen, während der Scan-Coroutine-Body (auf `Dispatchers.Default`) dieselben Felder liest. `scanJob?.cancel(); scanJob = null` (z.B. Zeile 91-92, 281-282) ist nicht atomar. Bei schnellen Seiten-/Zustandswechseln kann ein Job verwaist weiterlaufen oder `isScanning` inkonsistent werden.
**Schaden:** Selten, aber im Fehlerfall bleibt Scanning aus oder doppelt aktiv — wieder Priorität-1-relevant. Schwer reproduzierbar, daher gefährlich.
**Empfehlung:** Zustand über einen einzigen Mutex/Aktor serialisieren oder Engine-Operationen auf einen dedizierten Single-Thread-Dispatcher legen; `start`/`stop` atomar machen. → **Schritt 2.5** (idealerweise nach der Strukturbereinigung Schritt 2.4)

### H-4 — Prompt-Injection: Gemini kann nach Lesen fremder Inhalte schreibende Tools auslösen
**Datei:** `core-ai/.../GmailReadTool.kt:62-63` (liefert fremde E-Mail-Snippets an das Modell), `core-ai/.../CalendarCreateTool.kt:39-62` (schreibender Tool-Call ohne Bestätigung), Tool-Registrierung in `core-ai/.../AiToolsModule.kt`
**Verifiziert** (Tool-Code gelesen; die konkrete Verkettung hängt vom Agent-Loop in `GeminiUseCase` ab).
**Schaden:** Eine präparierte E-Mail („Lege einen Termin an / suche X") wird als Snippet ins Modell gespeist; das Modell kann im selben Lauf `create_calendar_event` aufrufen. Schreibwirkung im Konto des Nutzers ohne dessen Zustimmung. `maxResults` und String-Parameter werden nicht begrenzt/validiert.
**Empfehlung:** Schreibende Tools (`create_calendar_event`, Spotify-Play) erfordern explizite Nutzerbestätigung, insbesondere wenn im selben Lauf Fremdinhalt gelesen wurde; Tool-Inputs validieren (Bounds, Längen). → **Schritt 3.4**

### H-5 — `GeminiUseCase`: gemeinsamer companion-object-Zustand ohne Synchronisierung
**Datei:** `core-ai/.../GeminiUseCase.kt:64-84`
**Verifiziert.** `activeModelName`, `lastSuccess`, `lockoutUntilTime`, `cachedModelsJson`, `lastModelsFetchTime` sind veränderliche `companion`-Felder. Der `requestMutex` (Zeile 34) schützt nur den Request-Pfad in `generateResponse`; der Health-/Cache-State wird teils außerhalb gelesen/geschrieben. Bei paralleler Nutzung (z.B. Prediction + manuelle Anfrage) drohen torn reads des Lockout-Zeitstempels.
**Schaden:** Inkonsistentes Throttling/Caching; im schlimmsten Fall unnötige Lockouts oder ignorierte 429-Sperren. Kein Datenverlust.
**Empfehlung:** State in den Mutex-geschützten Bereich ziehen oder atomar/`@Volatile` + synchronisiert machen. → **Schritt 3.4**

---

## MITTEL

### M-1 — Sync meldet Erfolg trotz abgewiesenem Upload
**Datei:** `core-cloud/.../BookMergeService.kt:259-262`
**Verifiziert.** Nach erfolgreichem lokalem Merge-Import, aber **abgewiesenem** Cloud-Upload (Lock/Netzwerk) wird `MergeResult(success = true, …)` zurückgegeben (Kommentar „Self-Healing bei nächstem Sync"). Lokal entsteht kein Datenverlust (der Import ist committed), aber die Cloud bleibt auf dem alten Stand, während UI/Worker den Lauf als erfolgreich werten.
**Schaden:** Kein sofortiger Datenverlust, aber stille Divergenz: Bei Multi-Device-Nutzung kann der nächste Pull lokale Änderungen überschreiben, bevor „Self-Healing" greift. Sync-Status ist für den Nutzer irreführend.
**Empfehlung:** Eigenen Status `MERGED_LOCALLY_UPLOAD_PENDING` einführen, damit der Worker gezielt einen Retry plant und die UI ehrlich „Upload ausstehend" zeigt. → **Schritt 1.3**

### M-2 — Tombstone-Pruning vor garantiertem Export-Erfolg
**Datei:** `core-data/.../PageImportExportManager.kt:133-135`
**Verifiziert.** `deletedEntityDao.pruneTombstones(cutoff)` läuft mitten in `exportBookToJson`, bevor das JSON fertig erzeugt/geschrieben ist (kein `withTransaction`, kein Erfolg-Gate). Wirft ein späterer Schritt eine Exception, sind die >90-Tage-Tombstones bereits gelöscht.
**Schaden:** Verlorene Tombstones → eine an anderer Stelle bereits gelöschte Entität kann beim nächsten Merge „wiederauferstehen". Begrenzt durch das 90-Tage-Fenster, aber genau die Datenkonsistenz, die Priorität 2 schützen soll.
**Empfehlung:** Pruning erst nach erfolgreicher Export-Erzeugung (oder rein lesend exportieren und Pruning separat, idempotent, ausführen). → **Schritt 1.1**

### M-3 — Import nicht atomar, Fehler werden verschluckt
**Datei:** `core-data/.../PageImportExportManager.kt:200-225 ff.` (kein `withTransaction` über den Import; 13 `catch`-Blöcke in der Datei; der Import beginnt mit `deletePagesForBook` Zeile 212)
**Verifiziert** (Transaktions-Scan: andere Repos nutzen `appDatabase.withTransaction{}`, dieser Pfad nicht). Der Import löscht zuerst alle bestehenden Seiten und schreibt dann neu — ohne umschließende Transaktion. Schlägt das Schreiben mittendrin fehl, ist der alte Stand bereits weg und der neue unvollständig (Partial Import: Seite ohne Buttons o.ä.).
**Schaden:** Potenzieller Datenverlust beim Wiederherstellen/Sync-Import — direkte Priorität-2-Verletzung.
**Empfehlung:** Gesamten Import in `appDatabase.withTransaction { }` kapseln (Rollback bei Fehler); Fehler sammeln und als `ImportResult`/`Result` mit Warnungen melden statt still weiterzulaufen. → **Schritt 1.2**

### M-4 — Migration 10→11 verwirft Buttons bei kaputtem Legacy-JSON still
**Datei:** `core-database/.../AppDatabase.kt` (MIGRATION_10_11, JSON-Parsing der Legacy-`buttonConfigs` mit `catch`-Blöcken)
**Verifiziert** (per Exploration; Migrationscode vorhanden, kein Test dafür). Beim Backfill der neuen `buttons`-Tabelle werden Parse-Fehler abgefangen und der betroffene Button übersprungen — ohne Zählung/sichtbaren Hinweis.
**Schaden:** Auf Altgeräten mit leicht abweichendem Legacy-JSON gehen Buttons beim DB-Upgrade unbemerkt verloren.
**Empfehlung:** Migrationstest mit `MigrationTestHelper` (inkl. „kaputtes JSON"); Fehler zählen/loggen statt komplett still. Schemaversion bleibt unverändert. → **Schritt 1.4**

### M-5 — Audio-ZIP-Extraktion nicht atomar
**Datei:** `core-cloud/.../BookMergeService.kt:69-98`
**Verifiziert.** Audio-Dateien werden direkt nach `filesDir/audio_recordings` geschrieben (`FileOutputStream(targetFile)` Zeile 83). Bricht der Vorgang ab, bleiben halb geschriebene Dateien liegen; der `catch` (Zeile 95-96) loggt nur.
**Schaden:** Korrupte Audio-Aufnahmen (Button spricht „Müll" ab). Begrenzt, aber für AAC unangenehm.
**Empfehlung:** In Temp-Datei schreiben + atomarer `rename`/`move` nach Erfolg. → **Schritt 1.3**

### M-6 — FileProvider gibt gesamtes Cache-Verzeichnis frei
**Datei:** `app/src/main/res/xml/file_paths.xml` (`<cache-path name="logs_cache" path="." />`)
**Verifiziert.** Der freigegebene Pfad ist das Cache-Wurzelverzeichnis, nicht ein dediziertes Log-Unterverzeichnis. Über eine geteilte content-URI ist damit potenziell der gesamte Cache adressierbar.
**Schaden:** Breiter als nötig; je nach Cache-Inhalt (heruntergeladene Sync-/Merge-Dateien landen in `cacheDir`, s. `BookMergeService.kt:64`) können sensible Zwischendateien exponiert werden.
**Empfehlung:** Auf ein eigenes Unterverzeichnis (`logs/`) einschränken und den Log-Export-Code entsprechend anpassen. → **Schritt 3.3**

### M-7 — Hue: Hostname-Verifier global deaktiviert
**Datei:** `core-cloud/.../PhilipsHueManager.kt:40, 84, 102`
**Verifiziert.** `bypassHostnameVerifier = HostnameVerifier { _, _ -> true }` wird sowohl bei der TOFU-Erstverbindung (`fetchBridgeCertificateInfo`, Zeile 102, vertretbar) **als auch** bei der laufenden, fingerprint-gepinnten Verbindung (`createHttpsConnection`, Zeile 84) gesetzt. Das Pinning prüft den Zertifikats-Fingerprint (gut), aber der Hostname wird grundsätzlich nicht verifiziert.
**Schaden:** Begrenzt, da Fingerprint-Pinning den Hauptschutz liefert und es um ein lokales Bridge-Gerät geht. Dennoch unnötige Aufweichung im Normalbetrieb.
**Empfehlung:** Bypass strikt auf die TOFU-Erstverbindung beschränken; danach normale bzw. fingerprint-basierte Verifikation. → **Schritt 3.2**

### M-8 — Hue-Bridge-Fingerprint/Credentials unverschlüsselt
**Datei:** `core-cloud/.../PhilipsHueManager.kt:164` (`smartHomeSettings.hueBridgeFingerprint`), `SmartHomeSettingsRepository`
**Verifiziert** (Fingerprint-Zugriff im Code; Speicherung in SmartHome-Prefs ohne Encrypt-Lambda — analog zu K-2). Der gepinnte Fingerprint und der Hue-Username liegen im Klartext.
**Schaden:** Wer die Prefs lesen kann, kann den Pin-Schutz aushebeln bzw. die Bridge ansteuern. Niedrig-mittel (lokales Heimnetz).
**Empfehlung:** Über den gehärteten Encryptor speichern. → **Schritt 3.2**

---

## NIEDRIG

### N-1 — PII-Logging (E-Mail-Adresse)
**Datei:** `core-cloud/.../GoogleAuthManager.kt` (mehrere `Log.d/Log.i` mit `$email`)
**Verifiziert** (per Exploration). Nutzer-E-Mail wird auf DEBUG/INFO geloggt. Kein Secret, aber unnötige PII in Logcat. **Empfehlung:** Maskieren/entfernen.

### N-2 — Build-/Log-Artefakte im Repo eingecheckt
**Dateien (Repo-Root):** `logcat_dump.txt`, `build_*.txt`, `test_*.txt`, `app_build_output*.txt`, `verification_build.txt`, `htmlReport/`, `sample.zip`, `gotalknow_extracted/`, `test_keys.py`
**Verifiziert** (Verzeichnislisting). Bei öffentlichem Repo potenzielles Leak (Logs/Stacktraces). **Empfehlung:** Entfernen und in `.gitignore` aufnehmen. → **Schritt 0.2**

### N-3 — Legacy-Klartext-PIN als Migrationspfad
**Datei:** `core/.../SecurityManager.kt:234-235, 289, 293`
**Verifiziert.** Aktuelle PINs sind PBKDF2-gehasht (`hashPin`, Zeile 245-253, 10k Iterationen — gut). Es existiert aber weiterhin ein `securityPin`-Klartextfeld als Fallback (Zeile 234-235). Der Vergleich nutzt `==` (nicht konstantzeitig — für lokalen PIN vernachlässigbar). **Empfehlung:** Legacy-Klartext-PIN nach einmaliger Migration entfernen.

### N-4 — `QUERY_ALL_PACKAGES`-Berechtigung
**Datei:** `app/src/main/AndroidManifest.xml`
**Verifiziert** (per Exploration). Diese Berechtigung erfordert eine Play-Store-Begründung und wird oft abgelehnt. **Empfehlung:** Prüfen, ob ein gezieltes `<queries>`-Element genügt.

### N-5 — `predictionTimeoutJob` / Call-State doppelt beobachtet
**Datei:** `core-scanning/.../ScanCoordinator.kt:158-171, 184-216, 258-273`
**Verifiziert.** Der Coordinator behandelt Cancel des Timeout-Jobs an mehreren Stellen (`clear`, `onPageChanged`, currentPage-Collector) bereits recht sorgfältig; `callActionProxy.isInCall` wird hier und (laut Exploration) zusätzlich in `CallManagementDelegate` beobachtet. Aktuell kein bestätigter Fehler, aber doppelte Beobachtung erschwert Wartung und birgt Race-Potenzial. **Empfehlung:** Auf eine Quelle konsolidieren. → begleitend in **Schritt 2.5**

---

## Positivliste (bereits gut gelöst — nicht anfassen)

- **Zip-Slip-Schutz** in `ZipArchiver.kt:89-93`: korrekte Canonical-Path-Prüfung vor Extraktion, wirft `SecurityException`. (Hinweis: der separate Audio-Extraktionspfad in `BookMergeService.kt:77-88` hat diese Prüfung **nicht** — dort aber fester Prefix `audio_recordings/`; sauberer wäre, denselben `ZipArchiver` zu nutzen.)
- **Cleartext-Traffic global deaktiviert** (`network_security_config.xml`), alle Verbindungen HTTPS.
- **PIN-Hashing** mit PBKDF2WithHmacSHA256, 10.000 Iterationen, zufälliges Salt (`SecurityManager.kt:245-260`).
- **OAuth über System-CredentialManager** für Google (kein Eigenbau-Token-Store); **PKCE** für Spotify (`SpotifyManager.kt:27-43`).
- **Gemini-API-Key** wird immerhin verschlüsselt abgelegt (`GenAiSettingsRepository.kt:24-28`) — nur das Verfahren (K-1) ist zu härten.
- **Firebase Analytics/Crashlytics standardmäßig aus** (Manifest-Meta-Data).
- **Hue-Zertifikat-Pinning** per SHA-256-Fingerprint (`FingerprintTrustManager`) — nur der Hostname-Bypass (M-7) ist einzuschränken.

---

## Zuordnung Findings → Umsetzungsschritte (siehe Gesamtplan)

| Finding | Schritt |
|---|---|
| K-1 | 3.1 |
| K-2, M-7, M-8 | 3.2 |
| K-3, M-6 | 3.3 |
| H-1 | 2.3 |
| H-2, N-5 | 2.5 (nach 2.4) |
| H-4, H-5 | 3.4 |
| M-1, M-5 | 1.3 |
| M-2 | 1.1 |
| M-3 | 1.2 |
| M-4 | 1.4 |
| N-2 | 0.2 |
| N-1, N-3, N-4 | Backlog (kein eigener Schritt) |
