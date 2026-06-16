# GhosTTalk – i18n-Sammelticket (zentraler Backlog, Rev. 1, 2026-06-13)

**Zielgruppe:** Gemini (ausführend) — **Arbeitsmodus:** Claude reviewt/plant, Gemini setzt um.

Dies ist **das** zentrale i18n-Ticket, auf das mehrere Pläne bisher nur verwiesen haben (es existierte vorher nicht als Datei). Es sammelt die hartcodierten Strings, die **nicht** im Zuge anderer Arbeitspakete miterledigt wurden, und trennt sie sauber in **übersetzen** vs. **bewusst lassen**.

> **Kein „alles übersetzen".** Ein Großteil der ~270 deutschen String-Literale im Code sind **KI-Prompts, Seed-Daten, persistierte Entitätsnamen oder Log-/Exception-Texte** — die dürfen **nicht** angefasst werden (Abschnitt 3). Übersetzt wird nur, was der Endnutzer/Betreuer auf dem Bildschirm sieht (Abschnitt 2).

---

## 0. Regeln für Gemini (verbindlich)

1. **Nur user-sichtbare UI-Strings** übersetzen: `Text("…")`, `label=/title=/placeholder=`, `Toast`/`Snackbar`, sowie StateFlow-Werte, die in der UI angezeigt werden (z. B. `_huePairingStatus`, VocalTraining-Status).
2. **Jede neue Ressource zweisprachig**: Eintrag in `values/strings.xml` **und** `values-en/strings.xml` des passenden Moduls. Namenskonvention `<bereich>_<zweck>` (z. B. `setup_profile_loaded`, `cloudsync_error_loading_backups`).
3. **String-Interpolation erhalten**: `"Fehler: ${e.message}"` → `getString(R.string.x_error, e.message)` mit `%1$s`-Platzhalter. Zahlen → `%1$d`. Niemals den interpolierten Wert verlieren.
4. **Abschnitt 3 ist tabu** (Prompts, Seed-/persistierte Daten, Logs/Exceptions). Im Zweifel: nachfragen statt übersetzen — ein falsch übersetzter KI-Prompt oder persistierter Default-Name verändert Verhalten/Daten.
5. **Ein Bereich = ein Commit** (Arbeitspakete I1–I4). Nach jedem AP `./gradlew assembleDebug` grün (`JAVA_HOME` = JBR).
6. **`testTag`s unverändert.** Reine String-Ersetzung, keine Logik-/Layout-Änderung.

---

## 1. Live-Liste reproduzieren (Erkennungs-Greps)

Statt einer veraltenden Zeilenliste: die aktuelle Schuld jederzeit so erzeugen (`export LC_ALL=en_US.UTF-8` voranstellen):

```bash
# Toasts mit deutschem Literal in der UI-Schicht
grep -rnP 'Toast\.makeText\([^)]*"[^"]*[äöüÄÖÜß]' --include="*.kt" app/src/main feature-settings/src/main | grep -v build/

# Composable-Text / Label / Title / Status-Flow mit deutschem Literal
grep -rnP '(\bText\(\s*"|label\s*=\s*"|title\s*=\s*"|placeholder\s*=\s*"|\.value\s*=\s*")[^"]*[äöüÄÖÜß]' --include="*.kt" app/src/main feature-settings/src/main | grep -v build/

# Snackbar
grep -rnP 'showSnackbar\([^)]*"[^"]*[äöüÄÖÜß]' --include="*.kt" app/src/main feature-settings/src/main | grep -v build/
```

Jede Fundstelle einzeln gegen Abschnitt 2/3 prüfen (manche Dateien sind **gemischt**, s. u.).

---

## 2. ÜBERSETZEN — user-sichtbare Strings (Scope)

Gruppiert nach Bereich; je Datei stehen Beispiele, die genaue Zeilenmenge liefert der Grep aus Abschnitt 1.

### I1 — Seiten-/Button-Editor & Analytics (Modul `app`)
- `ui/components/GridEditorDialogs.kt` — z. B. „Vorlage gespeichert".
- `ui/pages/PageLayoutAssistantDialog.kt` — **nur die Toasts** („Scan-Muster … geändert", „Rastergröße … geschrumpft", „Deaktivierte Buttons gelöscht") — **nicht** etwaige Prompt-/Erklärtexte.
- `ui/pages/components/AudioRecordingController.kt`, `ui/pages/components/SpokenTextSection.kt` — Aufnahme-Toasts („Fehler bei der Aufnahme: …", „Aufnahme gelöscht").
- `ui/pages/delegates/PageSplitDelegate.kt`, `ui/pages/delegates/AiRestructureDelegate.kt`, `ui/pages/delegates/SuggestionsDelegate.kt` — Ergebnis-/Fehler-Toasts.
- `ui/pages/analytics/recommendations/LayoutOptimizationSection.kt`, `…/AiRestructureSection.kt`, `…/AiRestructureDialogs.kt`, `…/AiProposalCards.kt` — Toasts + sichtbare Labels.
- [x] `ui/pages/analytics/AnalyticsDashboardScreen.kt:259` — „Seite wird verwendet" (Dialog-Titel; **Rest aus dem Consistency-Plan AP4 ist erledigt**).
- [x] `ui/pages/analytics/AnalyticsOverviewTab.kt`, `…/AnalyticsDetailsTab.kt`, `…/buttonstats/*` — sichtbare Labels (nicht: KPI-Format-Defaults „Keine" als reine Anzeige? → ja, übersetzen).
- `ui/pages/actions/PlayMediaActionFields.kt`, `…/SmartHomeActionFields.kt` — Feld-Labels/Hinweise.
- `ui/pages/pagesplit/PageSplitManualPromptDialog.kt`, `…/PageSplitOptInDialog.kt` — sichtbare Texte (**nicht** der an die KI gesendete Prompt-Body).
- **Neu aus Editor-Kohäsion (Teil B):** `ui/pages/PageWorkbenchScreen.kt` Modus-Umschalter „Raster"/„Struktur"; `core-ui/.../components/EditorTopBar.kt` `contentDescription` „Editor beenden". (Kanonische Modus-Begriffe → als Ressourcen, damit überall identisch.)

### I2 — Setup/Onboarding (Modul `app`)
- `ui/setup/SetupScreen.kt` — „Profil erfolgreich geladen. Bitte schließe die Einrichtung ab.", „Fehler beim Import: …".
- `ui/setup/steps/RestoreProfileDialog.kt`, `…/DeviceSettingsStep.kt`, `…/WelcomeStep.kt` — sichtbare Texte.

### I3 — Einstellungen (Modul `feature-settings`)
- `feature/settings/ui/dialogs/AudioCacheDialog.kt`, `…/DriveFolderPickerDialog.kt` — sichtbare Texte.
- `feature/settings/ui/sections/ProfileSettingsSection.kt`, `…/CallSettingsSection.kt`, `…/SmartHomeSettingsSection.kt` — Labels/Hinweise.
- `feature/settings/ui/VocalTrainingScreen.kt` + `VocalTrainingViewModel.kt` — sichtbare Texte + Status-Strings.
- `feature/settings/ui/delegates/CloudSyncSettingsDelegate.kt` — verbleibende Toasts („Fehler beim Laden der Ordner/Backups: …", „Ungültige ID oder URL", „Fehler beim Importieren: …") — **das frühere `cloud_sync_no_backups*` ist schon erledigt**.
- `feature/settings/ui/delegates/HueSettingsDelegate.kt` — **die 7 verbliebenen `_huePairingStatus.value`-Strings** (Z. 65,68,76,95,144,149,153: „Zertifikat wird abgefragt…", „Kopplung abgebrochen.", „Erfolgreich gekoppelt!", „Warte auf Knopfdruck… (Versuch $i von $maxRetries)" usw.). **Z. 136 ist bereits erledigt** (`hue_pairing_press_link_button`) → demselben Muster folgen, `context.getString(...)`.
- `feature/settings/ui/delegates/BackupSettingsDelegate.kt` — sichtbare Meldung(en).

### I4 — Struktur-Editor (Modul `app`, neueres Feature)
- `ui/pages/structure/StructureEditorScreen.kt`, `…/StructureFocusCanvas.kt` — sichtbare Labels/Hinweise (Knoten „kommt von"/„führt zu" o. ä.).

---

## 3. NICHT ANFASSEN — bewusst lassen (nur dokumentiert)

| Kategorie | Beispiele / Dateien | Warum |
|-----------|---------------------|-------|
| **KI-Prompts** | `core/ai/domain/*` (`BookRestructureProposalUseCase`, `BookHierarchyProposalUseCase`, `PageLayoutProposalUseCase`, `GeminiUseCase` inkl. Confirmation-Keywords, `SplitPageUseCase`), Prompt-Bau in `ui/pages/delegates/LayoutWizardDelegate.kt` | Prompt-Engineering; Übersetzung ändert KI-Verhalten. Die Modellsprache ist bewusst Deutsch. |
| **Seed-/Beispieldaten** | `core/data/impl/SampleDataInitializer.kt`, `ButtonTemplateRepositoryImpl.kt`, `TemplateRepositoryImpl.kt`, Default-Namen in `core/domain/pages/CreatePageUseCase.kt` | Persistierte Inhalte, keine UI-Labels — würden beim Übersetzen zu Daten-Drift/Sync-Konflikten führen. |
| **Persistierte Entitätsnamen / Klon-Nutzdaten** | `"Statische Zeile"` (`PageResolutionDelegate`, `GridEditorDialogs`, `GridEditorGrid`), Klon-Payloads `"Archiv"`, `"Hauptseite"`, `"Weiter"`, `"Importiertes Buch"`, `"Exportierte Seiten"`, `"[Vorschlag]"`, `"Öffne …"` (`CloneBookUseCase`, `BookJsonImporter`, `core/data/impl/clone/*`) | Werden als **Daten** gespeichert/exportiert, nicht angezeigt-als-Label. Siehe `refactoring_plan_phase16.md`. |
| **Log-/Exception-/Entwickler-Texte** | `core/actions/*` Executors, `core/cloud/domain/*`, `ZipArchiver`, `checkNotNull(...)`-Messages, `Log.*` | Nicht endnutzersichtbar. |

> **Mischdateien beachten:** `PageLayoutAssistantDialog.kt`, `LayoutWizardDelegate.kt`, `PageSplit*Dialog.kt` enthalten **beides** (sichtbare Toasts/Labels = übersetzen; KI-Prompt-Bodies = lassen). Pro Zeile entscheiden.

---

## 4. Konsolidierung — Verweise aus anderen Plänen

Diese „→ eigenes Ticket"-Notizen zeigen ab jetzt hierher (keine Aktion in den Ursprungsplänen nötig):

- `refactoring_plan.md:388` (Phase 8) — Tab-Titel/Toasts/„Zu Startseite". → Abschnitt 2.
- `refactoring_plan_phase15.md:208` — „Rest bleibt im bestehenden i18n-Sammelticket". → hier.
- `refactoring_plan_phase16.md:83,100,179,187` — Daten-Schicht-Exceptions + Klon-Nutzdaten. → **Abschnitt 3 (lassen)**.
- `ui_ux_consistency_cleanup_plan.md:347,352` — die 7 Hue-Pairing-Strings (→ I3) + `AnalyticsDashboardScreen:259` (→ I1).

---

## 5. Arbeitspakete

| AP | Bereich | Stand |
|----|---------|-------|
| I1 | Editor/Analytics (app) | offen |
| I2 | Setup/Onboarding (app) | offen |
| I3 | Einstellungen + Delegates (feature-settings) | offen |
| I4 | Struktur-Editor (app) | offen |
| R | Abschluss-Review (Claude) | offen |

Reihenfolge frei (unabhängige Dateien). **Fertig je AP:** Greps aus Abschnitt 1 für den Bereich liefern keine user-sichtbaren Treffer mehr; de+en-Parität; Build grün.

**Abschluss-Check:** App auf Englisch durchklicken (Setup, Editor, Analytics, Einstellungen, Hue-Pairing) — keine deutschen Resttexte an UI-Stellen; KI-/Daten-Strings unverändert.

---

## 6. Review-Notizen (Claude)

_(leer bis zur Umsetzung)_
