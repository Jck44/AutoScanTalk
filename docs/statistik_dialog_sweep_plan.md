# GhosTTalk – UI/UX-Sweep: Statistik aufwerten + Dialog-Vereinheitlichung (Rev. 1, 2026-06-16)

**Zielgruppe dieses Dokuments:** Gemini (Android Studio / CLI) als ausführender Agent.
**Arbeitsmodus:** Claude reviewt und plant, Gemini setzt um. **Abschnitt „Regeln" zuerst lesen.**

Fortsetzung der UI/UX-Konsistenz-Arbeit (nach `ui_ux_consistency_cleanup_plan.md` Teil A–D,
`navigation_redesign_plan.md`). Zwei Stränge: **AP 1–7** (Statistik aufräumen & aufwerten) und
**Teil E** (app-weite Dialog-Vereinheitlichung).

## Context

Befunde aus dem Code-Audit (2026-06-15/16):
- Es gibt **drei** Statistik-Oberflächen, davon eine **redundant und schwach**:
  `AnalyticsDashboardScreen` (Tab „Statistik", modern ✅), `ButtonStatisticsTabContent`
  (kontextbezogen pro Button ✅) und der **`UsageStatisticsDialog`** in
  Settings→Wartung, der Endnutzern **rohe IDs** zeigt (`ID: a3f2…`, `Seite: 4f8e…`) und
  inhaltlich „Meistgenutzte Buttons" aus dem Statistik-Tab dupliziert.
- Mehrere Felder werden **erfasst, aber nirgends angezeigt** (`ButtonUsageRepository.ButtonUsageEvent`):
  `scanCyclesBeforeClick`, `isHardwareTriggered`, `isTouchIntervention` (sowie `wifiSsid` —
  in dieser Runde **bewusst ausgeklammert**).
- Konsistenzbrüche im Analytics-Bereich: **Emoji als Überschriften-Icons** (`📅 🔄 📄 📊 ⚠️ ✅ ⏳ ➡️`)
  statt `GhostTalkIcons`; **viel hartcodiertes Deutsch** (bricht EN); ein **rohes `AlertDialog`**
  (Lösch-Dialog) statt `GhostTalkDialog`.
- Robustheits-Regression: `BookShellScreen.currentTab` ist wieder `remember` statt `rememberSaveable`
  (Tab-Reset bei Rotation/Prozess-Tod) — entgegen dem früheren D8(b)-Stand.
- App-weit: **39 Dateien / 51 rohe `AlertDialog`-Aufrufe** neben dem geteilten `GhostTalkDialog`.

**Entscheidungen (Andreas, 2026-06-15/16):** Scope = **breiter Konsistenz-Sweep**; den Alt-Dialog
**in einen Verweis** auf den Statistik-Tab umwandeln; neue Kennzahlen = **Scan-Effizienz,
Hardware-vs-Touch, Touch-Eingriffe** (kein Nutzungsort/WLAN); **alle** rohen Dialoge vereinheitlichen
und `GhostTalkDialog` dafür rückwärtskompatibel erweitern.

**Ziel:** Eine kanonische Statistik (Tab), die die ohnehin erfassten Daten sichtbar macht,
in konsistenter visueller Sprache und vollständig lokalisiert — kein doppelter Pflegeweg mehr;
dazu eine app-weit einheitliche Dialog-Sprache.

## Arbeitsmodus

**Claude plant & reviewt, Gemini (Android Studio/CLI) setzt um.** Pro AP: Gemini implementiert +
committet (ein AP = ein Commit, `assembleDebug` grün), danach Claude-Review gegen die Regeln,
bevor das nächste AP startet.

## Regeln (verbindlich)

- **Nur UI-Schicht.** Keine Änderung an Datenmodellen, Repositories, `core-scanning`,
  ViewModel-**Semantik**. Signaturen dürfen für Verdrahtung minimal erweitert werden
  (neues Nav-Event, neuer optionaler Parameter).
- **Ein AP = ein Commit**, in Reihenfolge. Nach jedem AP `./gradlew assembleDebug` grün
  (`JAVA_HOME` = JBR aus Android Studio, kein System-Java).
- **`testTag` nie entfernen/umbenennen** (Integrationstests). Insb. `start_card_*`, `book_*`,
  `page_editor_*`, `structure_*`, `nav_item_*`.
- **Strings zweisprachig**: jede neue Ressource in `values/strings.xml` **und**
  `values-en/strings.xml` des passenden Moduls. Namenskonvention `analytics_*` / `<screen>_<zweck>`.
- **Barrierefreiheit**: jedes funktionale Icon mit `contentDescription`, Touchziele ≥ 48 dp.
- Im Zweifel bestehende `core-ui`-Muster kopieren (`GhostTalkDialog`, `GhostTalkIcons`,
  `LocalDimensions`), nicht neu erfinden.

---

## Arbeitspakete

### AP 1 — Alt-Dialog → Verweis auf Statistik-Tab (mittel)
**Ziel:** Settings→Wartung „Nutzungsstatistik" öffnet künftig den **Statistik-Tab** statt des
ID-lastigen Dialogs; der Dialog wird entfernt (kein doppelter Pflegeweg).

**Verdrahtung (mirror des bestehenden `SettingsNavigationEvent`-Musters):**
1. `feature-settings/.../SettingsViewModel.kt`: neues Event
   `SettingsNavigationEvent.OpenAnalytics` + `fun openUsageStatistics()` (emittiert es; analog
   `onJumpToPageFromHistory`, Z. 884). Den `_showUsageStatsDialog`-State + Setter + `topButtonUsage`
   (sofern nur vom Dialog genutzt) **entfernen**.
   - ⚠️ `onEditButtonFromHistory`/`onJumpToPageFromHistory` **behalten** — werden auch von
     `ActionHistoryDialog` genutzt.
2. `feature-settings/.../sections/MaintenanceSection.kt:96`: Button-`onClick` von
   `setShowUsageStatsDialog(true)` → `viewModel.openUsageStatistics()`.
3. `feature-settings/.../SettingsScreen.kt:511-512`: `UsageStatisticsDialog`-Block entfernen
   (+ Import Z. 62).
4. Datei `feature-settings/.../dialogs/UsageStatisticsDialog.kt` **löschen** (dann tot).
5. `app/.../ui/main/GhosTTalkNavHost.kt` (Settings-Event-Collector, Z. 162ff.): Fall
   `OpenAnalytics` → zurück zum Shell + Statistik-Tab anfordern (siehe AP 2 für den Tab-Mechanismus):
   `navController.popBackStack("start", inclusive=false)` + Tab-Request `BookShellTab.Statistik`.

**Reuse:** vorhandene `navigationEvents`-SharedFlow + Collector; keine neue Nav-Architektur.

**Fertig wenn:** Settings→Wartung „Nutzungsstatistik" landet auf dem Statistik-Tab; kein
`UsageStatisticsDialog` mehr; keine rohen IDs mehr an Endnutzer; Build grün.

### AP 2 — Tab-Anforderung + `rememberSaveable`-Fix in der Shell (klein)
**Datei:** `app/.../ui/main/BookShellScreen.kt`

1. `currentTab` von `remember` → `rememberSaveable` (Tab überlebt Rotation/Prozess-Tod) —
   mit `BookShellTab`-Enum-Saver, analog früherem D8(b)-Stand.
2. Externer Tab-Request für AP 1: `BookShellScreen` collectet einen one-shot
   `SharedFlow<BookShellTab>` (pragmatisch auf dem ohnehin geteilten `pageViewModel` ergänzen,
   z. B. `shellTabRequest`), und setzt bei Empfang `currentTab = it` + `tabSelected = true`.
   NavHost (AP 1, Schritt 5) emittiert nach dem Pop auf diesen Flow.

**Fertig wenn:** Statistik-Tab wird durch das Settings-Event korrekt selektiert; Tab überlebt
Rotation; Landing/`tabSelected`-Logik unverändert; Build grün.

### AP 3 — Neue Kennzahlen aus bereits erfassten Daten (mittel) — Kern
**Dateien:** `app/.../ui/pages/AnalyticsDashboardScreen.kt` (Berechnung),
`app/.../ui/pages/analytics/AnalyticsOverviewTab.kt` + neue Composable(s) in `analytics/`.

Alle drei Kennzahlen aus `historyEvents` (bereits via `pageViewModel.buttonHistory` vorhanden) —
**reine UI-Aggregation**, kein neuer Datenpfad. Trend über das bestehende
`current/previousPeriodEvents`-Schema + `TrendBadge` (AnalyticsDashboardScreen.kt:482) rechnen.

1. **Scan-Effizienz** — `scanCyclesBeforeClick` (nullable): Ø Scan-Zyklen bis Klick über
   Events mit Wert ≠ null; aktuelle vs. vorige Woche → `TrendBadge`. **Niedriger = besser**
   (Trend-Semantik invertiert kommentieren). Empty-State, wenn keine Scan-Daten.
2. **Hardware vs. Touch** — `isHardwareTriggered`: Anteil Taster-Auslösungen (%) an allen Events.
3. **Touch-Eingriffe** — `isTouchIntervention`: Anzahl/Anteil Betreuer-Eingriffe (Maß für
   Selbstständigkeit; sinkend = Fortschritt).

**Darstellung (Vorschlag, an bestehende 2×2-KPI-Grid + Sektionen anlehnen):**
- Scan-Effizienz als **dritte KPI-Zeile** im Overview (gleiche `Card`-Höhe 105 dp,
  `containerColor`-Token wie die bestehenden 4 Karten) **oder** in einer neuen Sektion.
- Hardware/Touch + Touch-Eingriffe als kompakte **Sektion „Eingabe & Selbstständigkeit"**
  (z. B. zwei kleine Kennzahlen + kurzer erklärender Hinweis), platziert vor der Info-Card.
- Nur anzeigen, wenn Daten vorhanden (Scanning-Nutzer); sonst Sektion ausblenden, damit
  Touch-only-Nutzer keine leeren Kacheln sehen.

**Fertig wenn:** Scan-Effizienz, Hardware/Touch-Anteil und Touch-Eingriffe im Statistik-Tab
sichtbar (mit Trend wo sinnvoll), Empty-States sauber; Build grün; Werte gegen einen Datensatz
plausibilisiert.

### AP 4 — Emoji → `GhostTalkIcons` (klein)
**Dateien:** `AnalyticsOverviewTab.kt`, `AnalyticsDetailsTab.kt`,
`analytics/buttonstats/ButtonRecommendationsSection.kt`.

Emoji-Überschriften (`📅 🔄 📄 📊 ⚠️ ✅ ⏳ ➡️`) durch passende `GhostTalkIcons` + `Text`-Label
ersetzen (gleiche Anordnung wie restliche Section-Header der App). Jedes Icon mit
`contentDescription` (oder dekorativ `null`, wo daneben Text steht). Inhaltliche Pfeile in
Transitions (`➡️`) durch `GhostTalkIcons.ArrowForward` o. ä.

**Fertig wenn:** keine Emoji-als-Icon mehr in den drei Dateien (`grep` leer); visuell konsistent
mit den übrigen Section-Headern; Light/Dark geprüft; Build grün.

### AP 5 — Lösch-Dialog im Dashboard auf `GhostTalkDialog` (klein)
**Datei:** `AnalyticsDashboardScreen.kt:207-266`

Den rohen `AlertDialog` (Seiten-Löschung inkl. „Seite wird verwendet"-Variante) auf
`GhostTalkDialog` (`core-ui`) umstellen — analog zur AP-3-Migration im
`ui_ux_consistency_cleanup_plan.md`. `isDestructive=true`, `UsageLocationRow`-Liste in den
`content`-Slot. Die hartcodierten Strings dabei in AP 6 mit auslagern.

**Fertig wenn:** kein `AlertDialog(` mehr in `AnalyticsDashboardScreen.kt`; Lösch-Flow (mit/ohne
Usages) verhaltensgleich; Build grün.

### AP 6 — i18n: hartcodiertes Deutsch im Analytics-Bereich → Ressourcen (mittel)
**Dateien:** alle `app/.../ui/pages/analytics/**` + `AnalyticsDashboardScreen.kt`.

User-sichtbare deutsche Literale nach `app/src/main/res/values/strings.xml` **und**
`values-en/strings.xml`, Konvention `analytics_*`. Betrifft u. a.: „Gesamtaufrufe",
„Aktiver Wortschatz", „Kommunikationsrate", „Statistiken erfasst von … bis …",
„${count} Übergänge", „Häufige Navigations-Wege", „Seiten-Analyse & Aufräum-Assistent",
„Meistgenutzte Seiten", „Aufräum-Empfehlungen", „Letzte Aktivitäten" etc. (vollständige Liste
per `grep -rhoE '"[^"]*[äöüÄÖÜß][^"]*"'` im analytics-Verzeichnis). Format-Args (`%1$d`/`%1$s`)
korrekt setzen. **Verweis auf `docs/i18n_backlog_plan.md`** — dort als erledigt/zugeordnet
vermerken, nicht doppelt tracken.

> Bewusst ausgeklammert (kein UI-Text): KI-Prompt-Strings, interne Default-Entitätsnamen.

**Fertig wenn:** App auf Englisch zeigt im Statistik-Tab keine deutschen Resttexte mehr; de/en-Parität;
Build grün.

### AP 7 — Abschluss-Review (Claude)
Review der APs 1–6 gegen die Regeln; Schwerpunkte:
- Kein `UsageStatisticsDialog`/`showUsageStats`-Rest; `ActionHistoryDialog` weiterhin intakt.
- Neue Kennzahlen rechnen korrekt (null-Handling bei `scanCyclesBeforeClick`; Anteile auf
  Gesamtmenge, nicht versehentlich auf gefilterte Teilmenge); Empty-States für Touch-only.
- Keine `testTag`-Regression; keine ViewModel-Semantikänderung.
- de/en-Parität; keine Emoji-als-Icon mehr.

---

## Teil E — Dialog-Vereinheitlichung (alle rohen `AlertDialog` → `GhostTalkDialog`)

**Anlass:** Über die Statistik hinaus die app-weite Dialog-Inkonsistenz auflösen.
Audit (2026-06-16, nach dem Multi-Select/Bulk-Feature): **39 Dateien, 51 rohe
`AlertDialog`-Aufrufe** neben dem geteilten `GhostTalkDialog` (`core-ui/.../GhostTalkDialog.kt`).
Entscheidung: **alles vereinheitlichen** und die geteilte Komponente dafür
**rückwärtskompatibel erweitern**.

**Befund-Tiers (aus dem Audit):** fast alle sind Standard-Confirm/Info/Listen- oder
Formular-Muster (1 Confirm + optional 1 Dismiss). Einzige Strukturabweichung: **Composable-Titel**
(z. B. Toggle-Buttons im Titel-Slot in `AiRestructureDialogs.kt` #1) → braucht E0. Kein einzelner
Dialog hat mehr als einen Confirm-Button (Zähler ≥2 = mehrere getrennte Dialoge je Datei).

### E.0 Regeln für Teil E (erweitern Abschnitt „Regeln")
- **Verhaltenserhaltend.** Gleiche Buttons/Reihenfolge, gleiche Validierung (`confirmEnabled`),
  gleiche `isDestructive`-Färbung (Löschen rot), gleiche Keyboard-/`forceSoftKeyboard`-Logik.
  TextField samt `isError`/`supportingText`/`KeyboardOptions`/`singleLine` **unverändert** in den
  `content`-Slot verschieben.
- **`testTag` erhalten/durchreichen** (insb. `page_editor_name_field`, `book_*`, Profil-/Settings-Tags).
- **Ein AP = ein/mehrere kleine Commits**, nach jedem `assembleDebug` grün. Große Tiers (E1/E2)
  je nach Umfang in `app`- und `feature-settings`-Teil-Commits splitten.
- Bereits in **AP 5** migrierte Analytics-Lösch-Dialoge (`AnalyticsDashboardScreen.kt`,
  `AnalyticsDetailsTab.kt`) und der in **AP 1** gelöschte `UsageStatisticsDialog.kt` sind in Teil E
  **ausgenommen** (keine Doppelarbeit).

### AP E0 — `GhostTalkDialog` rückwärtskompatibel erweitern (klein) — Fundament
**Datei:** `core-ui/.../components/GhostTalkDialog.kt`
- Optionalen `titleContent: @Composable (() -> Unit)? = null` ergänzen; wenn gesetzt, ersetzt er
  den `title: String`-Slot (für Dialoge mit Toggle/Action im Titel). `title` bleibt Default-Pfad.
- Optionalen dritten/neutralen Aktions-Slot **nur falls** ein konkret reviewter Dialog ihn braucht
  (Audit zeigt aktuell keinen 3-Button-Dialog → vorerst weglassen, erst bei Bedarf in E4 nachziehen).
- **Alle bestehenden Aufrufer bleiben unverändert** (rein additive Parameter).
- **Fertig wenn:** neue optionale Parameter vorhanden, kein bestehender Call-Site-Bruch; Build grün.

### AP E1 — Triviale Confirm/Info/Listen-Dialoge (ohne TextField) (klein, ~14 Dateien)
Standard-Dialoge mit 1 `AlertDialog`, ohne TextField, ggf. mit Listen-Content (LazyColumn → in
`content`-Slot, vorhandene `heightIn`/`Box`-Höhe erhalten). `confirmText`/optional `dismissText`
mappen; reine „Schließen/OK"-Dialoge: `dismissText = null`.
- **`app`:** `pages/IncomingReferencesDialog.kt`, `setup/steps/RestoreProfileDialog.kt`,
  `pages/sections/ActionLogDialog.kt`, `pages/ActivatePageDialog.kt`,
  `pages/pagesplit/PageSplitOptInDialog.kt`, `pages/components/SpokenTextSection.kt`,
  `pages/actions/PhoneNumberPickerDialog.kt`, `pages/ButtonConfigDialog.kt` (der eine Confirm),
  `pages/analytics/recommendations/AiRestructureSection.kt`,
  `components/GridEditorContent.kt` (Bulk-Löschen-Bestätigung, `isDestructive=true`;
  Strings `bulk_action_*` bereits lokalisiert — kein i18n nötig).
- **`feature-settings`:** `dialogs/BackupSelectionDialog.kt`, `dialogs/SyncLogDialog.kt`,
  `dialogs/DriveFolderPickerDialog.kt`, `sections/PermissionsSettingsSection.kt`,
  `sections/SmartHomeSettingsSection.kt`, `SettingsScreen.kt` (der eine Dialog).
- **Fertig wenn:** keine rohen `AlertDialog` mehr in diesen Dateien; Verhalten/Listen-Höhe gleich; Build grün.

### AP E2 — Formular-Dialoge (mit TextField, Validierung erhalten) (mittel, ~11 Dateien)
TextField + Validierung in den `content`-Slot; `confirmEnabled` an die bestehende `isNotBlank`/`isError`-
Logik koppeln. Muster aus AP 3 des `ui_ux_consistency_cleanup_plan.md` (BookListScreen) als Vorlage.
- **`app`:** `pages/AddPageDialog.kt`, `components/RowEditDialog.kt`,
  `components/GridTemplateSaveDialog.kt`, `components/GridEditorDialogs.kt`,
  `pages/MoveButtonDialogs.kt` (Formular-Teil), `pages/pagesplit/PageSplitManualPromptDialog.kt`,
  `pages/actions/DeviceActionFields.kt`, `pages/structure/SearchablePagePicker.kt`,
  `pages/PageListScreen.kt` (rohe Rest-Dialoge; Datei nutzt `GhostTalkDialog` bereits gemischt),
  `templates/TemplateScreen.kt` (dito).
- **`feature-settings`:** `sections/CloudSettingsSection.kt`, `sections/GeneralSettingsSection.kt`.
- **Fertig wenn:** alle Formular-Dialoge über `GhostTalkDialog`; Validierung/Keyboard 1:1; `testTag`s erhalten; Build grün.

### AP E3 — Multi-Dialog-Dateien & verschachtelte Confirms (mittel, ~4 Dateien)
Dateien mit ≥2 `AlertDialog` (primär + „wirklich löschen?"-Bestätigung) — jede Instanz migrieren,
verschachtelte Confirm-Dialoge als eigene `GhostTalkDialog` (`isDestructive=true`).
- `feature-settings/.../dialogs/AudioCacheDialog.kt`, `.../sections/MaintenanceSection.kt`,
  `feature-settings/.../VocalTrainingScreen.kt`,
  `app/.../ui/pages/structure/StructureEditorScreen.kt` (rohe Dialoge; `testTag` `structure_*` erhalten).
- **Fertig wenn:** beide Ebenen je Datei migriert; Lösch-Bestätigungen rot; Build grün.

### AP E4 — Sonderfälle mit erweiterter Komponente (mittel, ~3 Dateien)
- `app/.../analytics/recommendations/AiRestructureDialogs.kt`: Dialog #1 nutzt **`titleContent`**
  (Toggle im Titel) aus E0; Dialoge #2/#3 Standard-Confirm/Dismiss.
- `feature-settings/.../sections/ProfileSettingsSection.kt`: 3 Dialoge (Profil anlegen mit TextField,
  Wechsel-Bestätigung, Lösch-Bestätigung) → je `GhostTalkDialog`; hartcodiertes „Profil wechseln?"
  nach `strings.xml` (de/en).
- `feature-settings/.../dialogs/ActionHistoryDialog.kt`: 2 Standard-`AlertDialog` migrieren;
  der **`BasicAlertDialog` (Bild-Vorschau) bleibt** bewusst roh (Vollbild-Bildbetrachter) — Kommentar setzen.
- **Fertig wenn:** alle außer der dokumentierten Bild-Vorschau migriert; Composable-Titel via `titleContent`; Build grün.

### AP E5 — Abschluss-Audit + Review (Claude)
- `grep -rn "AlertDialog(" app feature-settings` (ohne `/build/`, ohne Tests) zeigt nur noch:
  die interne Nutzung in `GhostTalkDialog.kt` + die bewusst belassene Bild-Vorschau-`BasicAlertDialog`.
- Stichprobe je Tier: Verhalten/Validierung/`isDestructive` unverändert, `testTag`s grün,
  de/en-Parität neuer Strings, keine ViewModel-Semantikänderung.
- `./gradlew testDebugUnitTest` (+ ggf. `connectedDebugAndroidTest`) grün.

---

## Bewusst NICHT in dieser Runde
- **`wifiSsid`/Nutzungsort** (vom User ausgeklammert).
- `ButtonStatisticsTabContent` (Button-Dialog) — kontextbezogen korrekt, bleibt.
- **Bild-Vorschau-`BasicAlertDialog`** in `ActionHistoryDialog.kt` (Vollbild-Bildbetrachter,
  kein Alert) — bleibt bewusst roh; in Teil E dokumentiert.

## Kritische Dateien (Überblick)
- `feature-settings/.../SettingsViewModel.kt`, `.../sections/MaintenanceSection.kt`,
  `.../SettingsScreen.kt`, `.../dialogs/UsageStatisticsDialog.kt` (löschen) — AP 1
- `app/.../ui/main/GhosTTalkNavHost.kt`, `app/.../ui/main/BookShellScreen.kt` — AP 1/2
- `app/.../ui/pages/AnalyticsDashboardScreen.kt` — AP 3/5/6
- `app/.../ui/pages/analytics/AnalyticsOverviewTab.kt`, `AnalyticsDetailsTab.kt`,
  `analytics/buttonstats/ButtonRecommendationsSection.kt` — AP 3/4/6
- `app/src/main/res/values{,-en}/strings.xml` — AP 6
- Reuse: `core-ui/.../GhostTalkDialog.kt`, `GhostTalkIcons`, `TrendBadge`
  (AnalyticsDashboardScreen.kt:482), `ButtonUsageRepository.ButtonUsageEvent`
  (Daten bereits vorhanden).
- **Teil E:** `core-ui/.../GhostTalkDialog.kt` (E0-Erweiterung) + die 39 im Audit gelisteten
  Dialog-Dateien in `app/` und `feature-settings/` (Tiers E1–E4).

## Verifikation
- Nach jedem AP: `./gradlew assembleDebug` grün; abschließend `./gradlew testDebugUnitTest`
  und (falls Gerät/Emulator) `connectedDebugAndroidTest` (insb. `NavigationIntegrationTest`,
  `PageManagementIntegrationTest`).
- Manuell (Light/Dark, Hoch-/Querformat):
  - Settings→Wartung „Nutzungsstatistik" → landet auf Statistik-Tab (kein Dialog, keine IDs).
  - Statistik-Tab: Scan-Effizienz / Hardware-vs-Touch / Touch-Eingriffe sichtbar mit Daten;
    bei Touch-only-Profil ohne Scan-Daten sauber ausgeblendet/Empty-State.
  - Statistik-Tab durchgängig mit Icons statt Emoji; Lösch-Dialog im `GhostTalkDialog`-Stil.
  - App-Sprache Englisch: keine deutschen Resttexte im Statistik-Tab.
  - Tab überlebt Geräterotation.
- **Teil E:** `grep -rn "AlertDialog(" app feature-settings` (ohne `/build/`/Tests) → nur noch
  `GhostTalkDialog.kt` + die Bild-Vorschau-`BasicAlertDialog`. Stichprobe je Tier (Confirm,
  Formular mit Validierung, Lösch-Bestätigung rot, Composable-Titel) visuell + Verhalten prüfen.
