# Refactoring-Plan Phase 15: `feature-settings`-Modul (Rev. 1, 2026-06-12)

Fortsetzung der Monolith-Roadmap aus `docs/refactoring_plan.md` (Phasen 1–14 ✅ komplett). Das Settings-Modul wurde von der bisherigen Roadmap nie berührt und enthält die vier größten UI-Dateien des Projekts.

**Arbeitsmodus:** Claude reviewt und plant, Gemini setzt um. Jeder Schritt einzeln kompilierbar, testbar, committen. Verifikation je Schritt: `./gradlew :feature-settings:compileDebugKotlin` + `./gradlew :feature-settings:testDebugUnitTest` (bei App-übergreifenden Änderungen zusätzlich `./gradlew assembleDebug` + voller Testlauf) + Smoke-Checkliste unten.

**Oberste Regel (wie Phase 11):** Verhalten strikt beibehalten — das Refactoring ist rein strukturell. Insbesondere die Profil-Draft-Mechanik (siehe Ist-Analyse Nr. 2) ist tragend für das Profil-Feature und darf in keinem Schritt semantisch verändert werden.

## Status

| Schritt | Thema | Stand |
|---|---|---|
| 15.1 | Quick Wins: toter Code, Duplikate, i18n der angefassten Strings | ✅ fertig |
| 15.2 | SettingsScreen aufteilen | ✅ fertig |
| 15.3 | CloudSettingsSection / ProfileSettingsSection trennen | ✅ fertig |
| 15.4 | PermissionsSettingsSection aufteilen + SettingsToggleItem-Duplikat | ✅ fertig (Must-Fix 3 behoben) |
| 15.5 | MessagingAppsDetector extrahieren + Test | ✅ fertig |
| 15.6 | ProfileDraftCoordinator extrahieren + Test | ✅ fertig (Must-Fixes 1 & 2 und Test behoben) |
| 15.7 | Sektions-Delegates (Call / Security / Notifications) | ✅ fertig |
| 15.8 | Abschluss-Review | ✅ fertig (Review-Befunde komplett behoben) |

---

## Betroffene Dateien

| Datei | Zeilen | Ziel |
|---|---|---|
| `ui/SettingsViewModel.kt` | 1548 | < 700 |
| `ui/SettingsScreen.kt` | 1302 | < 400 |
| `ui/sections/CloudSettingsSection.kt` | 1043 | 2 Dateien, je < 450 |
| `ui/sections/PermissionsSettingsSection.kt` | 785 | < 400 |

Umfeld (bleibt unangetastet): die übrigen `sections/*` (81–433 Z.), `dialogs/*`, `delegates/*` (9 bestehende Delegates), `VocalTrainingScreen/-ViewModel`.

## Ist-Analyse (Claude, im Code verifiziert, 2026-06-12)

### SettingsViewModel.kt (1548 Z.)

1. **27 Konstruktor-Parameter** (Z. 67–100), davon 9 bereits existierende Delegates (`ttsDelegate`, `scanningDelegate`, `cloudSyncDelegate`, `genAiDelegate`, `experimentalDelegate`, `hueDelegate`, `spotifyDelegate`, `prefetchDelegate`, `backupDelegate`). Das Delegate-Muster ist also etabliert — aber die VM-Klasse selbst ist trotzdem eine flache Liste aus **~110 Flow-Properties** (Z. 117–283) und **~130 Setter-Methoden**.
2. **Profil-Draft-System ist der tragende Querschnitt**: `profileScopedFlow()` (Z. 300–311) routet jede Property entweder auf den Repo-Flow oder — bei aktivem Profil-Entwurf — auf den `ProfileDraftManager` (Klasse am Dateiende, Z. 1530–1547). `updateSetting()` (Z. 313–323) ist das Gegenstück für Setter (Doppelpfad: Repo-Write **oder** Draft-`config.copy(...)`). Dazu: Profil-CRUD (Z. 1384–1484), Draft-Lifecycle (`startEditingProfile`/`saveEditingProfile`/`cancelEditingProfile`) und der **debounced Profil-Cloud-Upload** (Z. 344–395, 5-s-Debounce, Sequence-/Timestamp-Vergleich gegen Sync-Echo).
3. **Messaging-App-Heuristik** (Z. 1208–1337, ~130 Z.): `initializeDefaultMessagingAppsIfNeeded`, `resetMonitoredNotificationAppsToMessagingDefaults`, `isMessagingOrSocialApp` — fachlich eigenständig, komplett **ungetestet** (Keyword-Listen, Exclude-Listen, Package-Prefix-Matching).
4. **Wortgleiche Duplikate**: `handlePasswordManagerResult` (Z. 631–656) und `handleGeminiPasswordManagerResult` (Z. 672–697) unterscheiden sich nur in den String-Ressourcen.
5. **Hartkodierte deutsche Strings**: Z. 711 (`"ElevenLabs Verbindung wird getestet..."`), Z. 1126 (`"Fehler beim Löschen des Buches"`), Z. 1362 (`"Fehler beim Teilen des Fehlerberichts: …"`), Z. 1369 (`"Fehlerbericht wird hochgeladen..."`), Z. 1378 (`"Upload fehlgeschlagen: …"`), Z. 1472 (`"Das einzige verbleibende Profil kann nicht gelöscht werden."`).
6. Kleinere eigenständige Blöcke: Audio-Cache-Verwaltung (Z. 1144–1168), Default-Dialer-Logik (Z. 1028–1048), Update-Check (Z. 841–850), `restoreApiKeysFromPasswordManager` (Z. 1486–1527).
7. **Bekannte Quirks — dokumentieren, NICHT fixen**:
   * `syncModeSettings` mappt im Draft-Pfad auf `syncModeBook` (Z. 152, Kommentar steht dabei) — bewusste Lücke, `ProfileConfig` kennt das Feld nicht.
   * `securityPin`/`isBiometricEnabled` liefern im Draft Konstanten (Z. 211/214) — by design, gerätespezifisch.
   * `testElevenLabsConnection` (Z. 704–738) schaltet die **globale** TTS-Engine temporär um und verlässt sich auf `delay(800)` — fragil, aber Verhaltensänderung wäre eigenes Ticket.
   * `@Suppress("unused")` an der Klasse (Z. 64) — nach der Aufteilung prüfen, ob es entfallen kann.

### SettingsScreen.kt (1302 Z.)

1. **Suchinfrastruktur** (Z. 132–241): `SettingsSearchItem` + `getSearchableItems()` — manuell gepflegter Katalog mit ~50 Einträgen, ~110 Zeilen.
2. **Haupt-Composable** (Z. 256–760) orchestriert zwei Layouts (Sidebar ≥ 720 dp vs. Single-Pane) — der **Suchergebnis-Block ist dabei 2× wortgleich** enthalten (Z. 478–512 und Z. 619–653: Filterung + LazyColumn + `SearchResultItem`).
3. **`SettingsTopBar` (Z. 848–906) ist toter Code** — kein einziger Aufrufer (Grep über `app/` + `feature-settings/` verifiziert); der Screen nutzt `GhostTalkScaffold`.
4. **`handleLocalImport`/`handleLocalExport`** (Z. 1078–1168, ~90 Z.): ZIP-vs-Legacy-JSON-Erkennung, global-vs-Buch-Import, Toasts — Nicht-Compose-Logik in der Screen-Datei.
5. **`SubmenuContent`** (Z. 1170–1285): Router Sektion → Section-Composable. Auffällig, aber Design (nicht anfassen): `VOICE`/`AUDIO_HARDWARE` teilen sich `VoiceSettingsSection` per `isGlobal`-Flag; `GENERAL` und `MAINTENANCE` rendern ihre Sektion 2× (global + buchspezifisch); nur `SECURITY` bekommt Primitive statt der ganzen VM.
6. **Hartkodierte deutsche Strings**: Z. 331 (`"Ausgewählter SAF-Ordner"`), Z. 335 (`"Fehler beim Importieren: …"`), Z. 390/398/403 (`"Globales Profil bearbeiten"`, `"Globale Einstellungen"`, `"Einstellungen"`, `"Profil bearbeiten"`), Z. 564/602/671 (`"Entwurf"`), Z. 799/805 (ProfileEditBanner-Texte), Z. 1096/1123 (`"Buch erfolgreich importiert."`), Z. 1144/1165 (`"Fehler beim Import/Export: …"`).

### CloudSettingsSection.kt (1043 Z.)

Enthält **zwei unabhängige Top-Level-Sektionen in einer Datei**: `CloudSettingsSection` (Z. 64, genutzt für die Kategorien CLOUD_SYNC **und** ACCOUNTS via `showSyncSettings`-Flag) und `ProfileSettingsSection` (Z. 702–1043, Kategorie PROFILE).

### PermissionsSettingsSection.kt (785 Z.)

* Haupt-Composable Z. 79–486 (~400 Z.), dazu `PermissionRow` (Z. 486), `PreferredAppsPicker` (Z. 628).
* **🐛 Duplikat**: `SettingsToggleItem` (Z. 573) existiert wortähnlich auch in `core-ui/.../components/PreferenceItems.kt:93`. Die 11 Aufrufer in `feature-settings/ui/sections/` lösen wegen gleichen Packages auf die lokale Kopie auf, die 4 Aufrufer im `app`-Modul auf die core-ui-Version — zwei Implementierungen für dasselbe UI-Element.

---

## Vorgehen

### Schritt 15.1: Quick Wins (toter Code, Duplikate, i18n der angefassten Strings)

1. `SettingsTopBar` (SettingsScreen.kt Z. 848–906) ersatzlos löschen (inkl. dann ungenutzter Imports `TopAppBar`, `TopAppBarDefaults`, ggf. `ExperimentalMaterial3Api` an der Stelle).
2. `handlePasswordManagerResult` + `handleGeminiPasswordManagerResult` zu **einer** Funktion zusammenführen: `handlePasswordManagerResult(result, successRes, importedRes, notFoundRes, noManagerRes, errorRes)` oder ein kleines `data class PasswordManagerStrings` — Aufrufer (ElevenLabs/Gemini) übergeben ihre R-Strings.
3. Alle in der Ist-Analyse gelisteten hartkodierten deutschen Strings (VM Nr. 5, Screen Nr. 6) nach `strings.xml` überführen — **DE + values-en** (gleiche Ausnahme von der i18n-Nicht-Ziel-Regel wie in Phase 10: was sowieso angefasst wird, wird sauber gemacht).
4. **Keine Logikänderung.** Verifikation: kompilieren + Tests; Smoke 1 (Settings öffnen, Profil-Editieren-Banner, Import-/Export-Toasts stichprobenartig).

### Schritt 15.2: SettingsScreen aufteilen (mechanisch, verhaltensgleich)

Neue Dateien unter `feature-settings/ui/`:

* `SettingsSectionCatalog.kt`: `enum SettingsSection` + `ProfileEditSections` (Z. 110–130, 762–774).
* `search/SettingsSearch.kt`: `SettingsSearchItem`, `getSearchableItems()`, `SettingsSearchBar`, `SearchResultItem` **plus** ein neues gemeinsames Composable `SettingsSearchResults(query, visibleItems, onResultClick)` — ersetzt die zwei wortgleichen Blöcke (Z. 478–512 / 619–653) durch genau eine Implementierung.
* `SettingsSectionHost.kt`: `SettingsSubMenu` + `SubmenuContent` (Z. 1042–1285) unverändert verschieben.
* `LocalBackupHandlers.kt`: `handleLocalImport` + `handleLocalExport` (Z. 1078–1168) als top-level `internal fun` verschieben. Die ZIP-Erkennung (`fileName.endsWith(".zip") || contentResolver.getType(uri) == "application/zip"`) als pure Funktion `isZipUri(fileName, mimeType)` herauslösen → **Mini-Unit-Test**.
* `SettingsScreenComponents.kt`: `ProfileEditBanner`, `ProfileNameEditCard`, `SettingsMainMenu`, `VersionInfo`, `highlightSetting`.

`SettingsScreen.kt` bleibt Orchestrierung (Layout-Weiche, Launcher, Back-Handling, Dialoge) — Ziel < 400 Z. **Achtung**: die synchrone Edit-Mode-Übergangs-Erkennung (Z. 346–361, Kommentar erklärt das 1-Frame-Flash-Problem) unverändert lassen.

#### Review-Befund 15.1 + 15.2 (Claude, 2026-06-12) — 3 Must-Fixes vor Commit

> **Nachreview (Claude, 2026-06-12): ✅ abnahmereif.** Alle 3 Must-Fixes verifiziert (DE-String korrekt, `verticalAlignment` wieder da, Imports bereinigt — 2 übersehene Reste `testTag`/`launch` von Claude direkt entfernt). SettingsScreen jetzt 519 Z. (Ziel <400 nicht erreicht; Rest ist genuin Zwei-Layout-Orchestrierung — akzeptiert, optionale `SettingsSidebarList`-Extraktion bleibt notiert). Build + Tests grün. **Vor Commit: Smoke** (Settings beide Layouts, Suche → Highlight, Profil-Edit-Banner, ZIP-Import/-Export).

Struktur stimmt: `SettingsTopBar` (tot) entfernt ✅, ein gemeinsamer `handlePasswordManagerResult` mit String-Res-Parametern ✅, alle gelisteten DE-Strings nach `strings.xml` (DE+EN) ✅, 5 neue Dateien wie geplant (`SettingsSectionCatalog`, `search/SettingsSearch` inkl. dedupliziertem `SettingsSearchResults`, `SettingsSectionHost`, `LocalBackupHandlers` mit purer `isZipUri` + Test, `SettingsScreenComponents`) ✅, Verschiebungen 1:1 verifiziert (SubmenuContent, Edit-Mode-Übergangslogik, Launcher) ✅, `:feature-settings:test` + `:app:compileDebugKotlin` grün ✅.

**🐛 Must-Fix 1: Falsche Sprache in `values/strings.xml`.** `settings_error_upload_failed` steht in der **deutschen** Datei auf Englisch (`"Upload failed: %1$s"`). Alt war „Upload fehlgeschlagen: …" → so wiederherstellen.

**🐛 Must-Fix 2: `ProfileEditBanner` hat `verticalAlignment` verloren.** Die alte Row hatte `verticalAlignment = Alignment.CenterVertically` (alt SettingsScreen.kt Z. 788); in `SettingsScreenComponents.kt` (Z. 68–70) fehlt es → Warning-Icon klebt oben statt mittig. Eine Zeile ergänzen.

**🐛 Must-Fix 3: ~50 tote Imports in `SettingsScreen.kt`.** Die Imports der verschobenen Composables sind komplett zurückgeblieben: alle 16 `sections.*`-Imports, `getSearchableItems`, `java.io.BufferedReader`/`InputStreamReader`, `Dispatchers`/`launch`/`withContext`, diverse Icons (`Home`, `Person`, `Phone`, `PlayArrow`, `Settings`, `Clear`, `Search`, `Warning`, `ArrowBack`), `LazyVerticalGrid`/`GridCells`, `animateFloatAsState`/`tween`/`background`/`clickable`, `Card`/`CardDefaults`, `LinearProgressIndicator`, `OutlinedTextField`, `ImageVector`, `TextOverflow`, `testTag` u. a. → IDE „Optimize Imports" über die Datei (Projekt-Standard seit Phase 13). Auch die übrigen neuen/angefassten Dateien prüfen.

**Empfehlung (kein Blocker)**: Die Sidebar-Sektionsliste (SettingsScreen.kt Z. 348–381) als `SettingsSidebarList`-Composable nach `SettingsScreenComponents.kt` — damit fällt der Screen nach Import-Cleanup von ~500 auf ~440 Z. Richtung 400er-Ziel.

**Akzeptierte Deltas (dokumentiert)**:
* Import-/Export-Fehlertoasts vereinheitlicht zu `settings_error_import_export` („Fehler beim Import/Export: %1$s", vorher zwei getrennte Texte) — ok.
* VM minimal gewachsen (1548→1558) durch die named-args an 4 Aufrufstellen des deduplizierten Handlers — ok, das eigentliche Schrumpfen kommt mit 15.6/15.7.
* `ProfileEditSections` jetzt public (vorher `private`) — nötig fürs `search`-Package, ok.

---

### Schritt 15.3: CloudSettingsSection / ProfileSettingsSection trennen

* `ProfileSettingsSection` (Z. 702–1043) **unverändert** in eigene Datei `sections/ProfileSettingsSection.kt` verschieben (reines Verschieben inkl. privater Helfer, die nur sie nutzt).
* `CloudSettingsSection` danach intern entlang der bestehenden Karten-/Themengrenzen in private Sektions-Composables aufteilen (Konto/Sign-In, Sync-Ziel & -Intervalle, Backup/Restore/Import — genauer Zuschnitt nach innerer Struktur, analog Phase 12a „je nach innerer Struktur"). Das `showSyncSettings`-Flag bleibt am öffentlichen Einstiegspunkt.
* Zielgröße: keine der zwei Dateien > 450 Z.

### Schritt 15.4: PermissionsSettingsSection aufteilen + SettingsToggleItem-Duplikat

1. **Duplikat konsolidieren**: Signaturen von `SettingsToggleItem` (lokal, Z. 573) und `core-ui/components/PreferenceItems.kt:93` vergleichen. Sind sie kompatibel (oder mit minimaler Anpassung kompatibel zu machen), lokale Kopie löschen und in den 11 `sections/`-Dateien den core-ui-Import ergänzen. Bei optischen Unterschieden: **vorher Screenshot-Vergleich**, im Zweifel die core-ui-Version anpassen statt zwei Varianten zu behalten. Falls die Varianten bewusst unterschiedlich sind (unwahrscheinlich), lokale Version in `SettingsToggleItemLegacy` umbenennen + TODO — nicht stillschweigend lassen.
2. Haupt-Composable (Z. 79–486) entlang der Themengrenzen in private Composables bzw. eigene Dateien aufteilen (Berechtigungs-Liste mit `PermissionRow`, Benachrichtigungs-Vorlesen-Block mit App-Auswahl/`PreferredAppsPicker` — genauer Zuschnitt nach innerer Struktur).
* Zielgröße: < 400 Z. pro Datei.

### Schritt 15.5: MessagingAppsDetector extrahieren + Test

* Neue Klasse `domain/MessagingAppsDetector.kt`: kapselt `initializeDefaultMessagingAppsIfNeeded`-Erkennung + `resetMonitoredNotificationAppsToMessagingDefaults`-Scan + `isMessagingOrSocialApp`.
* Für Testbarkeit die Kernheuristik als **pure Funktion** schneiden: `fun isMessagingOrSocialApp(packageName: String, category: Int): Boolean` (statt `ApplicationInfo` direkt) — der PackageManager-Scan bleibt eine dünne Hülle darum.
* Das „nur einmal initialisieren"-Flag (`ghosttalk_app_meta`-SharedPrefs, Z. 1211–1213) wandert mit; VM ruft im `init` nur noch `detector.initializeIfNeeded(...)`.
* **Neuer Unit-Test** `MessagingAppsDetectorTest` (pure Funktion): WhatsApp/Telegram/Signal-Pakete → true; `com.android.chrome`, `com.google.android.gm` (mail), `…watch…`-Companion → false; CATEGORY_SOCIAL-Fallback; Keyword-Fallback nur wenn nicht ausgeschlossen.

### Schritt 15.6: ProfileDraftCoordinator extrahieren + Test (Kern-Schritt)

* Neue Klasse `ui/ProfileDraftCoordinator.kt` (plain class, von der VM mit `(settingsRepository, performProfilesSyncUseCase, cloudSyncDelegate, viewModelScope)` konstruiert — kein Hilt nötig). Sie übernimmt 1:1:
  * `ProfileDraftManager` (Z. 1530–1547) + `_draftManager`-Flow + abgeleitete Flows (`editingProfileId`, `editingProfileName`, `hasUnsavedChanges`)
  * `profileScopedFlow()` und `updateSetting()` als öffentliche Methoden (`fun <T> scopedFlow(repoFlow, getConfigVal)`, `fun update(updateRepo, updateConfig)`)
  * Draft-Lifecycle: `startEditingProfile`, `saveEditingProfile`, `cancelEditingProfile`, `updateEditingProfileName`
  * Profil-CRUD: `setActiveProfileId`, `renameActiveProfile`, `createNewProfile`, `deleteProfile` (Toast-Callback an die VM zurückgeben statt selbst Toast zeigen — Coordinator bleibt Android-frei bis auf Log)
  * Debounced-Upload: `setupDebouncedProfileUpload`, `scheduleDebouncedProfileSync`, `autoSyncProfilesOnOpen`, `isProfileSyncing` (Z. 325–414)
* Die VM behält dünne Durchreichungen (`val editingProfileId = draftCoordinator.editingProfileId`, …) — **die ~110 `profileScopedFlow`-Aufrufe in der VM ändern nur ihren Empfänger** (`draftCoordinator.scopedFlow(...)`), sonst nichts.
* **Semantik-Invarianten (Review-Punkte)**: `SharingStarted.Eagerly` + `initialValue = repoFlow.value` beibehalten; Echo-Erkennung des Debounce (Sequence/Timestamp + `isSyncing`-Check) unverändert; `saveEditingProfile` lädt das Profil neu, wenn es das aktive ist, und stößt den Sync an.
* **Neue Unit-Tests** `ProfileDraftCoordinatorTest`:
  1. `update()` ohne Draft → Repo-Lambda läuft, Draft-Lambda nicht; mit Draft → umgekehrt, `hasUnsavedChanges == true`.
  2. `scopedFlow()` liefert ohne Draft den Repo-Wert, mit Draft den Config-Wert, nach `cancelEditing` wieder den Repo-Wert.
  3. Debounce: Profil-Änderung während `isSyncing == true` löst **keinen** Upload aus (Echo-Schutz).

### Schritt 15.7: Sektions-Delegates für Setter-/Flow-Gruppen (je Gruppe ein Commit)

Ziel: die verbleibenden Boilerplate-Blöcke (Flow + Setter im `updateSetting`-Muster) gruppenweise aus der VM in neue Delegates verschieben. Die Delegates sind **plain classes**, von der VM konstruiert mit `(settingsRepository, draftCoordinator, viewModelScope)` und als `val` öffentlich exponiert; Sektionen greifen genau **eine** Ebene tief zu (`viewModel.call.setMaxCallDurationSeconds(...)`). 

> **Abgrenzung zu Phase 7 (LoD)**: Dort wurden *zweistufige* Durchgriffe (`vm.delegate.manager.method()`) eliminiert. Ein einstufiger Zugriff auf ein fachliches Bündel ist gewollt und kein Verstoß. Verboten bleibt Durchgriff auf Interna (`viewModel.call.settingsRepository…` o. Ä. — Repo im Delegate `private`).

* **15.7a `CallSettingsDelegate`**: die 16 Call-Flows (Z. 234–246) + 14 Setter (Z. 988–1026) + `isDefaultDialer`/`requestDefaultDialer`/`simulateIncoming-/OutgoingCall`. Aufrufer: `CallSettingsSection.kt` (347 Z.) anpassen.
* **15.7b `SecuritySettingsDelegate`**: Security-Flows (Z. 211–218) + Setter (Z. 910–944) + `lock()`. Aufrufer: der SECURITY-Zweig in `SubmenuContent` (übergibt heute schon Primitive — nur die Quelle wechselt).
* **15.7c `NotificationSettingsDelegate`**: Notification-Reading-Flows (Z. 189–193) + Setter (Z. 792–810) + Verdrahtung zum `MessagingAppsDetector` aus 15.5. Aufrufer: `PermissionsSettingsSection`.
* Weitere Gruppen (Stats/Analytics, Background-Scheduler, Allgemein) **optional** nach demselben Muster, wenn 15.7a–c sich bewährt haben — nicht zwingend Teil dieser Phase.
* **Regel je Verschiebung**: Setter behalten exakt ihren `updateSetting`-Doppelpfad (jetzt `draftCoordinator.update`), Seiteneffekte wandern mit (z. B. `cleanupOldStats` bei `setStatsRetentionDays`, `reschedule()` bei Sync-Intervallen — falls diese Gruppen drankommen).

#### Review-Befund 15.3–15.8 (Claude, 2026-06-12) — 3 Must-Fixes + 1 Pflicht-Test vor Commit

**Was stimmt** (im Code verifiziert): `ProfileSettingsSection` sauber herausgelöst (389 Z.), `CloudSettingsSection` intern in 7 private Kategorien strukturiert ✅; `SettingsToggleItem`-Duplikat aufgelöst, alle 11 Sektionen nutzen die core-ui-Version ✅; `PreferredAppsPicker` extrahiert ✅; `MessagingAppsDetector` mit purer `isMessagingOrSocialApp(packageName, category)` + Tests über alle 4 Heuristik-Stufen ✅; VM-Verdrahtung des Coordinators und der 3 Delegates (`call`/`security`/`notifications`) durchgängig, Setter behalten den `update`-Doppelpfad, Seiteneffekte korrekt mitgewandert ✅; `deleteProfile` per Ergebnis-Callback (Coordinator Android-frei) ✅. Voller Build + komplette Test-Suite grün (nach Must-Fix 4).

**🐛 Must-Fix 1: Debounce-Gate semantisch geändert (kritischer Pfad!).**
Alt (VM): `scheduleDebouncedProfileSync` prüfte nach dem 5-s-Delay `if (userEmail.value == null) return@launch`. Neu (`ProfileDraftCoordinator.kt:99`): `if (!isProfileSyncing.value && settingsRepository.isDataCloudSyncEnabled)`. Das ist eine **andere Bedingung**: `isDataCloudSyncEnabled` ist das *Buch*-Sync-Flag — eingeloggte Nutzer ohne aktivierten Daten-Sync verlieren den Profil-Sync (auch beim expliziten `saveEditingProfile`!), und mit Flag aber ohne Login würde ein Sync versucht. Fix: Gate paritätisch wiederherstellen — `if (cloudSyncDelegate.userEmail.value == null) return@launch` (der Delegate ist injiziert).

**🐛 Must-Fix 2: `CancellationException` wird geschluckt.**
Alt: `catch (e: CancellationException) { throw e }` vor dem generischen Catch. Neu (`ProfileDraftCoordinator.kt:109`): `catch (_: Exception)` fängt auch die Cancellation — und der Debounce **cancelt diesen Job bei jeder weiteren Änderung**. Rethrow wiederherstellen.

**🐛 Must-Fix 3: Schritt 15.4 unvollständig — Haupt-Composable nicht aufgeteilt.**
`PermissionsSettingsSection` (Z. 80–486, ~406 Z.) ist unverändert ein Composable; nur Picker/Toggle wurden extrahiert (785→572 Z., Ziel < 400). Bitte wie geplant entlang der Themengrenzen aufteilen (Berechtigungs-Block mit `PermissionRow` / Benachrichtigungs-Vorlesen-Block).

**🐛 Must-Fix 4 (von Claude direkt behoben): App-Modul kompilierte nicht.**
`GhosTTalkNavHost.kt:221` rief noch `settingsViewModel.requestDefaultDialer(...)` auf (nach 15.7a → `viewModel.call.requestDefaultDialer`). Gemini hatte nur `:feature-settings:` verifiziert — **bei API-Änderungen an der VM ist `assembleDebug` Pflicht** (steht im Verifikations-Abschnitt oben).

**Pflicht-Test nachreichen**: Der dritte geplante Coordinator-Test fehlt (Debounce-Echo-Schutz: Profil-Änderung während `cloudSyncDelegate.isSyncing == true` → **kein** Upload). Gerade wegen Must-Fix 1/2 wichtig — bitte zusammen mit dem Gate-Fix schreiben.

**Ungeplante Deltas — von Claude geprüft, zur Freigabe durch Andreas empfohlen**:
* `autoReadMode` wird im Draft-Pfad jetzt zum Enum geparst (`NotificationSettingsDelegate.kt:18`). Das **fixt einen latenten Bug**: `autoReadModeFlow` ist `StateFlow<AutoReadMode>`, der alte Draft-Zweig lieferte den ProfileConfig-**String** (Flow lief als `StateFlow<Any>`). Empfehlung: behalten.
* `lastUploadedSequence/-Timestamp` werden nach dem Sync nur noch bei `Result.Success` aktualisiert (alt: immer). Folge: bei `Result.Error` wird beim nächsten Trigger erneut versucht — defensiver, empfohlen zu behalten.
* `scheduleDebouncedProfileSync` bekommt das beobachtete Profil als Parameter statt `activeProfileId` neu zu lesen — bei Profilwechsel feuert der `combine`-Collect ohnehin neu, ok.

**Akzeptiert mit Notiz (keine Nacharbeit nötig)**:
* `SettingsViewModel` 1558→1130 Z. (Ziel < 700 verfehlt): der Rest sind die verbleibenden `profileScopedFlow`-Gruppen (Stats/Background/General/TTS-Reste) — deren Migration war im Plan ausdrücklich „optional nach gleichem Muster". Kandidat für 15.7d+, kein Blocker.
* `CloudSettingsSection` 733 Z. (Ziel < 450 verfehlt), aber intern sauber in 7 Kategorien + Dialog strukturiert (größte ~257 Z.) — Datei-Split bleibt optional.

---

### Schritt 15.8: Abschluss-Review (Claude)

* Zielgrößen: `SettingsViewModel.kt` < 700 Z., `SettingsScreen.kt` < 400 Z., keine Datei der Phase > 450 Z.
* Review-Schwerpunkte: Draft-Doppelpfad lückenlos (jeder migrierte Setter), `profileScopedFlow`-Semantik, Debounce-Echo-Schutz, Suchkatalog vollständig (Klick auf jedes Suchergebnis landet in der richtigen Sektion + Highlight), keine zweite `SettingsToggleItem`-Implementierung mehr.

---

## Smoke-Test-Checkliste (mind. nach 15.2, 15.6, je 15.7-Teilschritt)

1. Settings auf Phone-Layout **und** Tablet-Layout (≥ 720 dp) öffnen, alle 15 Kategorien durchklicken.
2. Suche: Begriff eingeben → Ergebnis anklicken → richtige Sektion öffnet sich mit 2-s-Highlight; Suche im Profil-Edit-Modus zeigt nur Profil-Sektionen.
3. **Profil-Workflow (kritisch)**: Profil anlegen → bearbeiten (Draft-Banner sichtbar) → Wert ändern (z. B. Scan-Delay) → prüfen, dass der **Live-Wert unverändert** bleibt → Speichern → Wert greift; erneut bearbeiten → Zurück mit ungespeicherten Änderungen → Verwerfen-Dialog; Profil wechseln; Profil löschen (letztes Profil: Fehlermeldung).
4. Profil-Cloud-Sync: Einstellung ändern → nach ~5 s Upload im Log; Settings öffnen → Auto-Sync läuft (Progressbar).
5. Lokales Backup: ZIP-Export, ZIP-Import, Legacy-JSON-Import (global + buchspezifisch), SAF-Ordner-Import.
6. Cloud: Sign-in/Sign-out (Sign-out deaktiviert Sync/Gemini), Sync Now mit Progress-Dialog, Drive-Ordnerwahl.
7. Anruf-Einstellungen ändern + simulierter Anruf (nach 15.7a); Security: PIN setzen/entfernen, Sperren (nach 15.7b); Benachrichtigungs-Vorlesen: App-Liste, Reset auf Messaging-Defaults (nach 15.5/15.7c).
8. TTS: Engine wechseln, Stimme testen, ElevenLabs-Verbindungstest, Prefetch-Dialog.

## Explizit NICHT in Phase 15

* **Vollständige i18n des Moduls** — nur die in 15.1 gelisteten, sowieso angefassten Strings; Rest bleibt im bestehenden i18n-Sammelticket.
* `testElevenLabsConnection`-Redesign (globaler Engine-Switch + `delay(800)`) — nur dokumentieren.
* `syncModeSettings`→`syncModeBook`-Draft-Lücke (Z. 152) — bewusst, nicht „fixen".
* Suchkatalog automatisch aus den Sektionen ableiten — Redesign, eigenes Ticket.
* Aufteilung der VM in mehrere Hilt-ViewModels — bewusst verworfen: die Draft-Mechanik ist ein Querschnitt über alle Sektionen; mehrere VMs bräuchten einen geteilten Draft-Scope (Mehraufwand/Risiko ohne Nutzen gegenüber dem Coordinator + Delegates).

## Ausblick (Kandidaten für Phase 16+, Detailpläne folgen bei Bedarf)

1. **`core-data`-Schwergewichte**: `PageImportExportManager.kt` (1043 Z.), `CloneBookUseCase.kt` (817 Z.) — datenkritisch, Vorgehen wie Phase 11: erst Logik testbar herausziehen, dann schneiden.
2. **ISP-Restschuld Settings-Interfaces** (aus 4.2): Doppel-Deklarationen `elevenLabsTtsLanguage`/`elevenLabsModel` in `TtsSettings` + `CloudSettings` (Dual-Write), `Provider`-Querdelegationen (`appLanguage`, `cuesAudioDeviceAddress`, `isSmartPredictionEnabled`), doppelte `activeBookIdFlow`-Collection im Fassaden-`init`; Fassaden-Rest (`…ForBook`, Profile-Management) aus `SettingsRepositoryImpl` (725 Z.).
3. **Kleintickets**: `ImportResult.ReadError` statt `contains("ZIP")`-Heuristik (Phase-10-Rest), `isElevenLabs`-Caching im Button-Dialog, `TEST_PLAN_75.md` aktualisieren, Settings-UI: prüfen ob Scan-Limit 0 anbietbar ist (Phase-11-Delta), Call-Ansagetexte nach `strings.xml` (Phase-11-Option).
