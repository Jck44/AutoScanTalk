# GhosTTalk – Navigation-Redesign: Adaptive Betreuer-Navigation (Rev. 1, 2026-06-13)

**Zielgruppe dieses Dokuments:** Gemini (Android Studio / CLI) als ausführender Agent.
**Arbeitsmodus:** Claude reviewt und plant, Gemini setzt um. **Abschnitt 0 zuerst lesen.**

Ziel: Den tiefen Hub-and-Spoke (`book_list → start → content_management → page_list → …`) durch eine **adaptive Navigation** für den Betreuer-/Verwaltungsbereich ersetzen — **Bottom-Navigation im Hochformat, Navigation Rail im Querformat/Tablet** (`NavigationSuiteScaffold`). Der **Nutzermodus (Sprechoberfläche) bleibt ein isolierter Vollbild-Modus** ohne Navigations-Chrome; das **Verlassen wird per PIN/Biometrie gesichert**. Der **Buchwechsel wandert in die Navigation** (Buch-Umschalter in der Top-Bar).

## Entscheidungen (mit Andreas abgestimmt, 2026-06-13)

| Thema | Entscheidung |
|-------|--------------|
| Nav-Muster | **Variante 1 – Adaptiv** (Bottom-Bar + Rail) via `material3-adaptive-navigation-suite` |
| Top-Level-Ziele | **Sprechen · Inhalte · Statistik · Einstellungen** |
| Nutzermodus | bleibt **separater Vollbild-Modus** (kein Nav-Chrome), Einsprung über „Sprechen" |
| Nutzermodus verlassen | **PIN/Biometrie erforderlich** (bestehender `SecurityManager`) |
| Buchwechsel | **in der Navigation** (Buch-Umschalter in der Shell-Top-Bar); `book_list` bleibt für Buch-CRUD |

## Abhängigkeit / Reihenfolge

Dieser Plan **ersetzt den `StartScreen`** und fasst `content_management` / `analytics_dashboard` neu. Er überschneidet sich damit mit `docs/ui_ux_consistency_cleanup_plan.md` (das u. a. `StartScreen` und `BookListScreen` anfasst).

> **Den Consistency-Cleanup-Plan (AP 1–4) zuerst umsetzen und mergen.** Dieser Nav-Plan baut darauf auf — insbesondere wird der dort eingeführte `adaptiveCardHeight()`-Helper und `GhostTalkDialog` wiederverwendet.

---

## 0. Regeln für Gemini (verbindlich)

1. **Ein Arbeitspaket = ein Commit.** APs in Reihenfolge 1 → 5. Nach jedem AP **muss die App vollständig bedienbar sein** (kein „halb migrierter" Zwischenstand über AP-Grenzen hinweg).
2. **Nach jedem AP bauen:** `./gradlew assembleDebug` grün (`JAVA_HOME` = JBR von Android Studio).
3. **Nutzermodus-Isolation ist eine harte Invariante.** Auf `PageScreen` (Route `main`) darf **niemals** Navigations-Chrome erscheinen (keine Bottom-Bar, keine Rail, kein Drawer, kein Buch-Umschalter). Wird in jedem Review geprüft.
4. **Sicherheits-Invariante.** Das Verlassen des Nutzermodus läuft über den bestehenden `SecurityManager` + `SecurityEntryDialog`. Niemals einen eigenen PIN-Vergleich bauen.
5. **`testTag`-Modifier:** bestehende Tags der **Tiefen-Screens** (`page_editor`, `template_editor`, `page_list`, `book_*`, `page_screen_*`) unverändert lassen. Tags der **abgelösten Hub-Screens** (`start_card_*`, `content_manage_*`) dürfen/müssen ersetzt werden — dann **im selben AP die betroffenen Tests** (`app/src/androidTest`, v. a. `NavigationIntegrationTest`) anpassen und neue stabile Tags vergeben (Namen s. AP 2).
6. **Keine Logik-Schicht umbauen.** ViewModels, Repositories, `core-scanning`, Sync bleiben unangetastet. Erlaubte Ausnahme: **eine** kleine, begründete Methode in `SecurityManager` (AP 4), falls noch nicht vorhanden.
7. **Barrierefreiheit:** Touchziele ≥ 48 dp, jedes Nav-Item mit Label **und** `contentDescription`/`icon`. Material-3-Defaults der Navigation einhalten.
8. **Strings zweisprachig** (`values/` + `values-en/`). Keine neuen Drittanbieter-Libs außer dem genannten AndroidX-Artefakt.

---

## Status

| AP | Thema | Risiko | Stand |
|----|-------|--------|-------|
| 1 | Dependency `material3-adaptive-navigation-suite` + Nav-Strings | klein | offen |
| 2 | `BookShellScreen` mit `NavigationSuiteScaffold` (Inhalte/Statistik/Einstellungen + „Sprechen") | **groß** | offen |
| 3 | Buch-Umschalter in der Shell-Top-Bar | mittel | offen |
| 4 | Nutzermodus-Isolation absichern + Exit-Sperre (PIN/Biometrie) | mittel | offen |
| 5 | Aufräumen: tote Routen/Screens entfernen, Auto-Nav + Tests angleichen | mittel | offen |
| R | Abschluss-Review (Claude) | — | offen |

---

## 1. Ist-Zustand (Claude, im Code verifiziert, 2026-06-13)

Navigationsgraph in [GhostTalkNavHost.kt](../app/src/main/java/com/andreas_kratzer/ghosttalk/ui/main/GhosTTalkNavHost.kt):

```
onboarding_setup
book_list ──(Buch wählen)──> start ──┬─> main            (Nutzermodus, Vollbild)
                                      ├─> content_management ─┬─> page_list ─> page_editor
                                      │                       ├─> templates ─> template_editor
                                      │                       └─> page_editor/static_row_<bookId>
                                      ├─> analytics_dashboard
                                      └─> settings?isGlobal=false  ─> vocal_training
book_list ──(Zahnrad)──> settings?isGlobal=true
```

- `start` = Hub mit 3 Karten (Nutzermodus / Inhalte verwalten / Statistik) + Zahnrad → Buch-Einstellungen.
- `content_management` = Sub-Hub mit 3 Karten (Seiten / Vorlagen / Statische Zeile).
- `main` = Nutzermodus, **bereits eigene Route mit eigenem Scaffold** (kein gemeinsames Chrome) → Isolation ist strukturell schon gegeben, muss aber abgesichert bleiben (AP 4).
- Schutz beim **Betreten** von settings/edit/analytics über `navigateWithSecurity` + `SecurityEntryDialog` (vorhanden). Das **Verlassen** des Nutzermodus ist aktuell **nicht** gesichert.
- `SecurityManager`: `isUnlocked: StateFlow`, `unlock(pin)`, `lock()`, `isSecurityRequiredFor{Edit,Settings,Analytics,Deletion}()`. **Kein** Flag/Helper für Nutzermodus-Exit.

---

## 2. Soll-Konzept

### Informationsarchitektur (neu)

```
onboarding_setup
book_list ──(Buch wählen)──> book_shell        ← NEU: adaptive Betreuer-Shell
   book_shell (NavigationSuiteScaffold, Top-Bar mit Buch-Umschalter):
     • Sprechen      → Aktion: navigate("main")   (Vollbild, verlässt die Shell)
     • Inhalte       → Pane: Seiten / Vorlagen / Statische Zeile → page_editor / template_editor
     • Statistik     → Pane: Analytics-Inhalt
     • Einstellungen → Pane: Buch-Einstellungen (isGlobal=false) → vocal_training
main ──(Zurück, PIN/Biometrie)──> book_shell   ← Exit-Sperre (AP 4)
book_list bleibt erreichbar für Buch-CRUD + globale Einstellungen
```

- **„Sprechen" ist kein Pane**, sondern ein Navigations-Item, das in die isolierte Route `main` springt.
- **Inhalte/Statistik/Einstellungen** sind Panes innerhalb der Shell; der ausgewählte Tab lebt in `rememberSaveable`-State, nicht als separate NavHost-Route.
- **Tiefe Ziele** (`page_editor`, `template_editor`, `vocal_training`, ggf. `page_list`/`templates`) bleiben eigene NavHost-Routen, die aus den Panes heraus per Callback geöffnet werden (Vollbild über der Shell). So bleibt der Editier-Flow unverändert.

### Adaptives Verhalten
`NavigationSuiteScaffold` wählt automatisch: **NavigationBar** (kompakt/Hochformat) bzw. **NavigationRail** (expandiert/Querformat/Tablet). Keine manuelle Orientierungs-Logik nötig.

---

## 3. Arbeitspakete

### AP 1 – Dependency + Nav-Strings (Vorbereitung, keine UI-Änderung)
1. In `gradle/libs.versions.toml` die Library ergänzen (Version über `compose-bom` verwaltet, kein eigener `version.ref` nötig — analog `androidx-material3`):
   ```toml
   androidx-material3-adaptive-navigation-suite = { group = "androidx.compose.material3", name = "material3-adaptive-navigation-suite" }
   ```
   In `app/build.gradle.kts` als `implementation(libs.androidx.material3.adaptive.navigation.suite)` einbinden.
   **Verifizieren, dass das Artefakt über die vorhandene `compose-bom`-Version aufgelöst wird.** Falls nicht: Version auf die zur eingebundenen `material3`-Version passende `material3-adaptive-*`-Release pinnen und im Commit-Text dokumentieren.
2. String-Ressourcen (de + en) für die vier Ziele anlegen, falls noch nicht vorhanden: `nav_speak` („Sprechen"/„Speak"), `nav_content` („Inhalte"/„Content"), `nav_stats` („Statistik"/„Stats"), `nav_settings` (vorhandenes `settings`-Pendant wiederverwenden, sonst „Einstellungen"/„Settings").

**Fertig wenn:** `./gradlew assembleDebug` grün; `NavigationSuiteScaffold` ist importierbar (kurzer Kompiliertest genügt, noch nicht verdrahtet).

---

### AP 2 – `BookShellScreen` mit `NavigationSuiteScaffold` (Kern)
**Neue Datei:** `app/.../ui/main/BookShellScreen.kt`

Struktur (illustrativ — Gemini implementiert vollständig, opt-in ggf. nötig):

```kotlin
enum class BookSection { CONTENT, STATS, SETTINGS }

@Composable
fun BookShellScreen(
    bookName: String,
    onLaunchUserMode: () -> Unit,        // navigate("main")
    onOpenBookSwitcher: () -> Unit,      // AP 3
    // Callbacks für tiefe Ziele (wie bisher in NavHost verdrahtet):
    onEditPage: (String) -> Unit,
    onEditTemplate: (String) -> Unit,
    onEditStaticRow: () -> Unit,
    onOpenVocalTraining: () -> Unit,
    onBookDeleted: () -> Unit,
    /* ViewModels durchreichen wie bisher */
) {
    var section by rememberSaveable { mutableStateOf(BookSection.CONTENT) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = false,
                onClick = onLaunchUserMode,
                icon = { Icon(Icons.Filled.PlayArrow, null) },
                label = { Text(stringResource(R.string.nav_speak)) },
                modifier = Modifier.testTag("nav_item_speak")
            )
            item(BookSection.CONTENT,  Icons.., R.string.nav_content,  "nav_item_content")
            item(BookSection.STATS,    Icons.., R.string.nav_stats,    "nav_item_stats")
            item(BookSection.SETTINGS, Icons.., R.string.nav_settings, "nav_item_settings")
        }
    ) {
        // Top-Bar mit Buch-Umschalter (AP 3) + Pane je section:
        when (section) {
            BookSection.CONTENT  -> /* Inhalt von ContentManagementScreen */
            BookSection.STATS    -> /* Inhalt von AnalyticsDashboardScreen  */
            BookSection.SETTINGS -> /* SettingsScreen(isGlobal=false)       */
        }
    }
}
```

Vorgehen:
- Die **Inhalte** der bisherigen `ContentManagementScreen`, `AnalyticsDashboardScreen` und `SettingsScreen(isGlobal=false)` als Panes wiederverwenden. Bevorzugt deren Composables so refaktorieren, dass der **Inhalt** (ohne eigenes `Scaffold`/`TopAppBar`) als eigene Funktion aufrufbar ist, und ihn im Shell-Pane einbetten. Die Tiefen-Navigation (page_editor etc.) läuft über die durchgereichten Callbacks weiter auf dem **äußeren** NavHost.
- **Neue stabile testTags:** `nav_item_speak`, `nav_item_content`, `nav_item_stats`, `nav_item_settings`, `book_shell`. Die alten `start_card_*` / `content_manage_*` entfallen.
- Im NavHost: Route **`start` rendert jetzt `BookShellScreen`** statt `StartScreen`. Die Callbacks (onLaunchUserMode = bestehende User-Mode-Lade-Logik aus `onNavigateToUserMode`; onEditPage/onEditTemplate/onEditStaticRow/onOpenVocalTraining = bestehende `safeNavigate(...)`-Ziele) 1:1 aus den heutigen `composable("start"|"content_management"|"analytics_dashboard"|"settings")`-Blöcken übernehmen.
- `BackHandler` der Shell: zurück zu `book_list` (wie heute `StartScreen`).

> **Insets beim Einbetten beachten:** Beim Verlegen der `SettingsScreen`-Inhalte in eine Pane ändert sich die `Scaffold`-Verschachtelung. Der Speichern/Abbrechen-`bottomBar` muss seine `navigationBarsPadding()` + `imePadding()` behalten (siehe Consistency-Cleanup AP 5, Befund B6) — sonst verschwindet er erneut hinter der Navigationsleiste. Nach dem Umbau auf Gerät mit Navigationsleiste gegenprüfen.

**Noch nicht** in diesem AP: `StartScreen.kt` / die Routen `content_management`, `analytics_dashboard` löschen (passiert in AP 5) — sie bleiben vorerst als tote, aber kompilierende Routen bestehen, damit AP 2 isoliert testbar ist. Der Buch-Umschalter ist in AP 2 ein Platzhalter (zeigt nur `bookName`), Funktion folgt in AP 3.

**Fertig wenn:** Build grün; nach Buchauswahl erscheint die adaptive Navigation (Bar im Hochformat, Rail im Querformat — auf Emulator beide Orientierungen prüfen); Inhalte/Statistik/Einstellungen erreichbar; „Sprechen" startet den Nutzermodus; Editier-Flows (Seite/Vorlage/Statische Zeile/Vocal Training) funktionieren wie zuvor.

---

### AP 3 – Buch-Umschalter in der Shell-Top-Bar
In der Top-Bar der `BookShellScreen` einen Umschalter zeigen: aktueller Buchname + Chevron (`testTag("book_switcher")`). Klick öffnet ein `DropdownMenu`/`ModalBottomSheet` mit:
- allen Büchern aus `bookViewModel.allBooks` → Auswahl setzt aktives Buch (`pageViewModel.setActiveBookId`, `settingsRepository.activeBookId`, `settingsViewModel.refresh()` — exakt die Logik aus dem heutigen `onBookSelected`), **ohne** die Shell zu verlassen; Section-State bleibt erhalten.
- Eintrag „Bücher verwalten…" → `navigate("book_list")` (Buch-CRUD bleibt dort).
- Eintrag „App-Einstellungen" → `navigate("settings?isGlobal=true")` (globale Einstellungen erreichbar halten).

**Fertig wenn:** Build grün; Buchwechsel über den Umschalter aktualisiert Panes (z. B. Seitenliste des neuen Buchs); `book_list` und globale Einstellungen weiter erreichbar.

---

### AP 4 – Nutzermodus-Isolation absichern + Exit-Sperre (PIN/Biometrie)
1. **Isolation bestätigen/festschreiben:** `main`/`PageScreen` bleibt außerhalb von `NavigationSuiteScaffold` (eigene Route, eigenes Scaffold). Sicherstellen, dass von dort **kein** Shell-Chrome sichtbar ist. (Strukturell bereits gegeben — nicht aufweichen.)
2. **Lock beim Einsprung „Sprechen":** Beim Navigieren nach `main` `securityManager.lock()` aufrufen, **wenn** eine Sicherung konfiguriert ist (s. u.) — damit ist die Tür „scharf".
3. **Exit-Sperre:** Der Zurück-Pfad aus dem Nutzermodus (`PageScreen.onNavigateBack` **und** dessen `BackHandler`) wird im NavHost gegated:
   - Wenn Sicherung konfiguriert **und** `!isUnlocked`: `SecurityEntryDialog` zeigen (gleiche Komponente + `isBiometricEnabled` wie beim Betreten). Erst bei Erfolg `safePopBackStack()` zur Shell.
   - Sonst: direkt zurück.
   - Umsetzung analog zum vorhandenen `pendingRoute`-Muster, z. B. ein paralleler `pendingUserModeExit`-State im NavHost.
4. **`SecurityManager`-Helfer:** Falls es noch keine Methode gibt, die „ist überhaupt eine PIN gesetzt?" beantwortet, **eine** Methode `fun isSecurityConfigured(): Boolean` ergänzen (true, wenn ein PIN-Hash hinterlegt ist). Nur lesend, keine sonstige Logikänderung. Wird für Punkt 2 + 3 als Bedingung genutzt. (Ist eine passende Prüfung schon vorhanden, diese verwenden — **keine** zweite einführen.)

> Bewusst **unbedingt** (keine neue Einstellung): Sperre greift immer, sobald eine PIN existiert. Falls Andreas später eine eigene Option „Exit-Schutz an/aus" will, ist das ein separates Folgepaket (berührt dann `core-data`/Settings).

**Fertig wenn:** Build grün; mit gesetzter PIN verlangt das Verlassen des Nutzermodus PIN/Biometrie und bleibt bei Abbruch im Nutzermodus; ohne gesetzte PIN unverändert direktes Zurück; Scanning/Bluetooth/Anruf im Nutzermodus unverändert funktionsfähig.

---

### AP 5 – Aufräumen: tote Routen/Screens + Auto-Nav + Tests
1. Tote Routen entfernen: `content_management`, `analytics_dashboard` (Inhalte jetzt in der Shell). `StartScreen.kt` löschen, sofern vollständig durch `BookShellScreen` ersetzt. `ContentManagementScreen` entfernen, falls dessen Inhalt in AP 2 in die Shell gewandert ist (sonst als wiederverwendete Pane-Funktion behalten).
2. **Auto-Navigation angleichen** ([GhostTalkNavHost.kt:99-150](../app/src/main/java/com/andreas_kratzer/ghosttalk/ui/main/GhosTTalkNavHost.kt#L99)): `autoOpenBookEvent` und der `startupBehavior == "USER_MODE"`-Pfad zielen weiter korrekt auf `start`(=Shell) bzw. `main`. `popUpTo("book_list")`-Logik prüfen.
3. **Settings-Navigationsevents** (`EditButton`, `JumpToPage`, `StartSetup`) weiter funktionsfähig aus der Einstellungen-Pane heraus.
4. **Tests:** `NavigationIntegrationTest` und alle UI-Tests, die `start_card_*` / `content_manage_*` klickten, auf die neuen Nav-Tags (`nav_item_*`, `book_switcher`) umstellen. Tiefen-Screen-Tests unverändert.
5. **Consistency-Cleanup nachziehen:** `adaptiveCardHeight()` und `GhostTalkDialog` (aus dem Cleanup-Plan) in den neuen/angefassten Composables verwenden, keine neuen Hardcodes.

**Fertig wenn:** `grep` findet keine Referenzen mehr auf entfernte Routen/Screens; `./gradlew testDebugUnitTest` grün; `connectedDebugAndroidTest` grün (falls Gerät/Emulator); keine toten `start_card_*`/`content_manage_*`-Tags mehr.

---

## 4. Abschlussverifikation (vor Übergabe an Claude-Review)

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest   # falls Gerät/Emulator
```

Sichtprüfung (Light + Dark, Hoch- + Querformat, Phone + Tablet):
- [ ] Hochformat: Bottom-Navigation mit 4 Zielen; Querformat/Tablet: Navigation Rail
- [ ] „Sprechen" startet Vollbild-Nutzermodus **ohne** jegliches Nav-Chrome
- [ ] Verlassen des Nutzermodus verlangt (bei gesetzter PIN) PIN/Biometrie; Abbruch bleibt im Nutzermodus
- [ ] Buch-Umschalter wechselt das aktive Buch, ohne die Shell zu verlassen; „Bücher verwalten" + globale Einstellungen erreichbar
- [ ] Inhalte → Seite/Vorlage/Statische Zeile editieren funktioniert wie zuvor
- [ ] Statistik- und Einstellungen-Pane vollständig wie zuvor
- [ ] Scanning-Steuerung im Nutzermodus unverändert

---

## 5. Bewusst nicht in diesem Plan (mögliche Folgepakete)

- **Inhalte-Pane flacher:** „Inhalte" direkt als Seitenliste mit Segmented-Control (Seiten/Vorlagen) statt Zwischen-Hub — spart einen weiteren Tap, aber höheres Risiko. Erst nach stabilem v1.
- **Konfigurierbarer Exit-Schutz** (keine/Bestätigung/PIN) als Einstellung — berührt `core-data`.
- **Globale Einstellungen** als eigenes Ziel statt Umschalter-Eintrag.

---

## 6. Review-Notizen (Claude — wird während der Umsetzung gefüllt)

_(leer bis zur Umsetzung)_
