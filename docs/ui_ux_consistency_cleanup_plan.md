# GhosTTalk – UI/UX-Konsistenz & Anti-Pattern-Bereinigung (Rev. 1, 2026-06-13)

**Zielgruppe dieses Dokuments:** Gemini (Android Studio / CLI) als ausführender Agent.
**Arbeitsmodus:** Claude reviewt und plant, Gemini setzt um. **Abschnitt 0 zuerst lesen.**

Dies ist die **Nachlese zum Facelift** (`docs/ui_ux_redesign_plan.md`, AP 1–8 umgesetzt). Behoben werden Restbestände und Anti-Patterns, die das Review vom 2026-06-13 gefunden hat.

Das Dokument hat **zwei Teile mit unterschiedlichem Charakter**:
- **Teil A (AP 1–6)** — reine Konsistenz-/Aufräumarbeit, kein neues Feature, keine Verhaltensänderung. Es gelten die Regeln in Abschnitt 0.
- **Teil B (AP B1–B5)** — **Editor-Kohäsion „ein Editor, zwei Modi" (Stufe B)**, eine bewusste **Architektur-/Verhaltensänderung** (Modus-Container + Route-Konsolidierung). Hierfür gelten **eigene Regeln** (siehe Teil B), die Abschnitt 0 für diese APs ausdrücklich erweitern. Teil B berührt Navigationslogik und überschneidet sich mit dem Nav-Redesign-Track → **vor Umsetzung mit dem Nav-Plan koordinieren.**

## Abgrenzung zur neuen Navigationsseite (wichtig)

Parallel wird eine **neue Navigationsseite / Informationsarchitektur** geplant. Dieser Plan fasst die Navigation **bewusst nicht inhaltlich an** (keine Bottom-Nav, kein Drawer, keine Hub-Umbauten). Alles, was die IA betrifft, steht in Abschnitt 4 als „ausgeklammert — gehört zum Nav-Plan". Damit gibt es **keine Datei-Kollision** zwischen beiden Arbeitssträngen, solange Gemini sich an die AP-Grenzen hält.

---

## 0. Regeln für Gemini (verbindlich)

1. **Ein Arbeitspaket = ein Commit.** APs in Reihenfolge 1 → 6 umsetzen, nicht mischen.
2. **Nur UI-Schicht.** Keine Änderungen an ViewModels, Delegates, Repositories, Datenmodellen, `core-scanning` oder der **Navigationslogik** (`GhostTalkNavHost` nur dort anfassen, wo AP 1 es ausdrücklich erlaubt — nämlich Log-Zeilen). ViewModel-Signaturen bleiben unverändert. **Ausnahme:** AP 6 ändert ausschließlich `AndroidManifest.xml` (Konfiguration, kein Code).
3. **Nach jedem AP bauen:** `./gradlew assembleDebug` muss grün sein (`JAVA_HOME` = JBR von Android Studio, kein System-Java).
4. **`testTag`-Modifier niemals entfernen oder umbenennen** — die UI-Tests (`app/src/androidTest`) hängen daran. Insbesondere alle `start_card_*`, `book_*`, `content_manage_*`, `page_screen_*` unverändert lassen.
5. **Keine neuen Bibliotheken.** Material 3 + Compose reichen.
6. **Barrierefreiheit vor Optik.** AAC-App mit Scanning-Steuerung: Touchziele ≥ 48 dp, `contentDescription` für jedes funktionale Icon, Kontrast ≥ WCAG AA.
7. **Strings immer zweisprachig.** Jede neue String-Ressource bekommt einen Eintrag in `values/strings.xml` **und** `values-en/strings.xml` des passenden Moduls.
8. Im Zweifel: bestehendes `core-ui`-Muster kopieren statt neu erfinden. **Nicht** den Geltungsbereich erweitern — wenn ein AP „nur diese Dateien" sagt, dann nur diese.

---

## Status

| AP | Thema | Risiko | Stand |
|----|-------|--------|-------|
| 1 | Debug-Logs + toter Code entfernen | trivial | ✅ umgesetzt+reviewt |
| 2 | Geteilter Adaptive-Card-Height-Helper | klein | ✅ umgesetzt+reviewt |
| 3 | BookListScreen-Dialoge auf `GhostTalkDialog` | klein | ✅ umgesetzt+reviewt |
| 4 | Restliche hartcodierte UI-Strings → Resources | mittel | ✅ umgesetzt+reviewt (M1+Nits behoben; Hue-Pairing-Residuum → i18n-Ticket) |
| 5 | Speichern/Abbrechen-Balken hinter Navigationsleiste (Profil bearbeiten) | klein | ✅ umgesetzt+reviewt (manuelle Gerätesicht offen) |
| 6 | Windowed/Freeform offiziell unterstützen (resizeableActivity + Mindestgröße) | klein | ✅ umgesetzt+reviewt (manueller Resize-Test offen) |
| R | Abschluss-Review (Claude) — **nur Teil A** | — | offen |
| B1 | Geteilte Editor-Top-Bar extrahieren | klein | ✅ umgesetzt+reviewt |
| B2 | Geteilte visuelle Token für Seite/Button (Raster/Chip/Graph) | mittel | ✅ umgesetzt+reviewt |
| B3 | Modus-Container + Route-Konsolidierung (`editor/{pageId}?mode=`) | **hoch** | ✅ umgesetzt+reviewt (Nav-Plan nachgezogen) |
| B4 | Baum-Navigator als geteiltes Element in beiden Modi | mittel | ✅ umgesetzt+reviewt (Phone-Raster ohne Inline-Baum, s. Notiz) |
| B5 | Übergänge/Terminologie entschlacken + Undo-Konsistenz | klein | ✅ umgesetzt+reviewt |
| C1 | Assistent-Promotion in die EditorTopBar (sofort) | klein | ✅ umgesetzt+reviewt (Dedup/Dichte → D3) |
| C2 | Vorschlags-Einstieg aus der Statistik | mittel | ✅ umgesetzt+reviewt (Konsum-Guard → D4) |
| C3 | Inhalte=Seitenliste direkt + ruhigere Tokens (gekoppelt an Nav-Redesign) | mittel | ✅ umgesetzt+reviewt (committet `efb495c5`; Doppel-TopBar → D8) |
_(Teil D strikt in Reihenfolge D1 → D7 abarbeiten — Nummern = Ausführungsreihenfolge.)_

| D1 | `EditorMode`-Enum statt Magic-Strings „raster"/„struktur" (Fundament für D3) | klein | ✅ umgesetzt+reviewt (uncommitted; Mini-Nit `initialMode`-Default-Literal) |
| D2 | `EditorTopBar` entkoppeln (testTag-Param + String) — Header-Vorarbeit | klein | ✅ umgesetzt+reviewt (uncommitted) |
| D3 | EditorTopBar verdichten: Titel-Label+Dialog, Icon-Modus-Umschalter, adaptive Actions (Redo→⋮), Assistent-Dedup | mittel | ✅ umgesetzt+reviewt (2. Anlauf vollständig; uncommitted) |
| D4 | `openAssistant`-Intent einmalig konsumieren (kein Re-Open bei Modus-Round-Trip) | klein | ✅ umgesetzt+reviewt |
| D5 | `onExitEditor` robust (page_list-Fallback) | klein | ✅ umgesetzt+reviewt (Route auf „start" + Fallback; page_list-Route weg) |
| D6 | Import-Hygiene in Teil-B-Dateien (nach D3, da Header-Umbau churnt) | trivial | ✅ umgesetzt+reviewt (toter `ValidatedTextField`-Import entfernt) |
| D7 | Rest-Kleinigkeiten (imePadding, B4-Phone, i18n-Verweis) + Restbefunde | trivial | ✅ umgesetzt+reviewt (imePadding bewusst belassen+kommentiert; `route=="page_list"` entfernt) |
| D8 | Shell-Header bereinigen | mittel | ✅ umgesetzt+reviewt (a/b/d ✅; (c) Tag-Rename bewusst belassen, 18 Test-Refs) |

---

## 1. Ist-Befunde (Claude, im Code verifiziert, 2026-06-13)

| # | Befund | Fundstelle |
|---|--------|-----------|
| B1 | `Log.d("NAV_DEBUG", …)` in Produktionscode (4×) | `GhostTalkNavHost.kt:248,409,420` + 1 weiterer |
| B2 | `isLandscape` in `StartScreen` doppelt berechnet — die äußere Variable aus `Configuration` (Z. 46) wird durch die innere aus `BoxWithConstraints` (Z. 83) verschattet → toter Code | `StartScreen.kt:46` |
| B3 | `dynamicCardHeight`-Formel 5× kopiert, Landscape-Faktor divergiert (StartScreen `0.2f`, alle anderen `0.18f`) → uneinheitliche Kartenhöhen | StartScreen, BookListScreen, PageListScreen, TemplateScreen, ContentManagementScreen |
| B4 | `BookListScreen` mischt Dialog-Stile: Löschen nutzt `GhostTalkDialog`, Anlegen/Umbenennen rohe `AlertDialog`; deren Abbrechen-Button ist ein `Button` mit `textButtonColors()` statt `TextButton`; Shape-Mix (Feld `large`, Buttons `medium`) | `BookListScreen.kt:238,310` |
| B5 | Hartcodierte deutsche UI-Strings (Empty-States, ContentManagement-Karte, mehrere user-sichtbare Toasts) statt `stringResource` → bricht EN-Lokalisierung | siehe AP 4 |
| B6 | Speichern/Abbrechen-Balken im Profil-Bearbeiten-Modus verschwindet hinter der Android-Navigationsleiste (Samsung Tablet). `targetSdk=37` erzwingt Edge-to-Edge; der `bottomBar` des verschachtelten `Scaffold` (`contentWindowInsets = WindowInsets(0.dp)`) wendet keine `navigationBarsPadding()` an | `SettingsScreen.kt:221-247` |

Nicht in diesem Plan (Abschnitt 4): Navigations-IA, `safeNavigate`-Wrapper, doppelte Settings-Icons, doppelte Empty-State-Affordance.

---

## 2. Arbeitspakete

### AP 1 – Debug-Logs + toter Code entfernen
**Dateien:** `app/.../ui/main/GhostTalkNavHost.kt`, `app/.../ui/main/StartScreen.kt`

1. Alle `android.util.Log.d("NAV_DEBUG", …)`-Zeilen ersatzlos löschen (4 Stück: in `onNavigateToUserMode`, `safePopBackStack`, `safeNavigate` und der zugehörigen `navigate`-Stelle). **Die umgebende Logik — `safeNavigate`, `safePopBackStack`, `runOnMainThread`, die Lifecycle-State-Checks — unverändert lassen.** (Die Wrapper gehören zum Nav-Redesign, nicht hierher.)
2. In `StartScreen` die ungenutzte äußere `isLandscape`-Berechnung aus `Configuration` (Z. 45–46) entfernen, sofern `configuration` danach nirgends mehr gebraucht wird. Die innere Berechnung aus `BoxWithConstraints` (Z. 83) bleibt. Falls `LocalConfiguration`/`configuration` sonst nicht verwendet wird, auch den Import entfernen.

**Nicht** anfassen: jede andere Zeile in `GhostTalkNavHost`.

**Fertig wenn:** `grep -rn "NAV_DEBUG" app/src/main` ist leer; `StartScreen` hat keine verschattete `isLandscape`-Variable mehr; Build grün; `NavigationIntegrationTest` grün.

---

### AP 2 – Geteilter Adaptive-Card-Height-Helper
**Neue Datei:** `core-ui/.../components/AdaptiveCardHeight.kt` (oder als Ergänzung in `GhostTalkComponents.kt`)

Die 5× kopierte Formel durch **einen** Helper ersetzen und den Landscape-Faktor vereinheitlichen:

```text
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Einheitliche, vom verfügbaren Platz abhängige Höhe für Hub-/Listen-Karten.
 * Innerhalb eines BoxWithConstraints aufrufen.
 */
fun BoxWithConstraintsScope.adaptiveCardHeight(): Dp {
    val isLandscape = maxWidth > maxHeight
    val factor = if (isLandscape) 0.18f else 0.12f
    return (maxHeight * factor).coerceIn(90.dp, 140.dp)
}
```

**Migration** — diese 5 Stellen auf `val dynamicCardHeight = adaptiveCardHeight()` umstellen:
`StartScreen.kt:85` (vereinheitlicht damit `0.2f` → `0.18f`), `BookListScreen.kt:114`, `PageListScreen.kt:334`, `TemplateScreen.kt:123`, `ContentManagementScreen.kt:46`.

Die jeweils lokal berechneten `isLandscape`-Variablen nur entfernen, wenn sie **danach nicht mehr** anderweitig im selben Scope verwendet werden (in StartScreen/BookListScreen ggf. noch für `maxItemsInEachRow`/`weight` gebraucht — dann stehen lassen).

**Fertig wenn:** Keine `* if (isLandscape) 0.… else 0.12f).coerceIn(90.dp, 140.dp)`-Formel mehr im App-/feature-settings-Code (`grep` leer); alle 5 Hubs nutzen den Helper; Build grün; Hub-Karten in Hoch- und Querformat sichtgeprüft.

---

### AP 3 – BookListScreen-Dialoge auf `GhostTalkDialog`
**Datei:** `app/.../ui/books/BookListScreen.kt`

Die beiden rohen `AlertDialog`-Blöcke (**Umbenennen**, Z. 238 ff.; **Neues Buch**, Z. 310 ff.) auf den bestehenden `GhostTalkDialog` umstellen — analog zum bereits migrierten Lösch-Dialog im selben File (Z. 217). Das `OutlinedTextField` (inkl. `forceSoftKeyboard`/`onFocusChanged`-Logik, `isError`, `supportingText`, `singleLine`, `KeyboardOptions`) wandert **unverändert** in den `content`-Slot des `GhostTalkDialog`.

- `confirmText` = `action_save` (Umbenennen) bzw. `action_create` (Neu), `dismissText` = `action_cancel`.
- `confirmEnabled` an `name.isNotBlank()` koppeln **oder** die bestehende `isError`-Logik beibehalten (so wie aktuell) — Verhalten 1:1 erhalten.
- Damit verschwinden die `Button`+`textButtonColors()`-Abbrechen-Buttons und der Shape-Mix automatisch (GhostTalkDialog nutzt `TextButton` + `dialogCornerRadius`).
- `isDestructive` hier **false**.

**Fertig wenn:** Kein `AlertDialog(`-Aufruf mehr in `BookListScreen.kt`; Anlegen/Umbenennen/Löschen sehen identisch aus (Radius, Button-Stil); Tests `book_*` grün; Build grün.

> Bewusst eng: **nur** `BookListScreen`. Die übrigen rohen `AlertDialog`s (Formular-/Wizard-Dialoge) bleiben wie im Vorgängerplan unangetastet.

---

### AP 4 – Restliche hartcodierte UI-Strings → Resources
Folgende **user-sichtbaren** Literale nach `strings.xml` (passendes Modul) auslagern, de + en. Namenskonvention `<screen>_<zweck>` beibehalten.

**Composables (Modul `app`):**
| Datei | Literal(e) |
|-------|-----------|
| `BookListScreen.kt:119–121` | „Keine Bücher" / „Erstelle ein neues Buch, um mit der Kommunikation zu beginnen." / „Buch erstellen" |
| `TemplateScreen.kt:152–153` | „Keine Vorlagen" / „Erstelle eine neue Vorlage, …" |
| `PageListScreen.kt:363–364` | „Keine Seiten" / „Erstelle eine neue Seite, …" |
| `AnalyticsDashboardScreen.kt:112,337` | „Keine Statistiken erfasst" |

**Composable (Modul `feature-settings`):**
| `ContentManagementScreen.kt:70` | „Statische Zeile konfigurieren" |

**User-sichtbare Toasts** (gleiches Vorgehen; `context.getString(...)` statt String-Literal):
| Datei | Literal |
|-------|---------|
| `delegates/SuggestionsDelegate.kt:70` | „Keine aktiven Buttons in dieser Zeile vorhanden." |
| `delegates/SmartIntegrationDelegate.kt:100` | „Bitte zuerst in den Einstellungen koppeln." |
| `feature-settings/.../delegates/HueSettingsDelegate.kt:52,60,102,135` | Hue-Pairing-Meldungen |
| `feature-settings/.../delegates/CloudSyncSettingsDelegate.kt:233,264` | „Keine Backups …" |

**Bewusst ausgeklammert** (kein UI-Text): KI-Prompt-Strings in `LayoutWizardDelegate.kt`, interne Defaults wie `"Statische Zeile"`/`"Zeile X"` als Entitätsnamen in `PageResolutionDelegate`/`TemplateViewModel`/`GridEditor*` (das sind Datenwerte, keine angezeigten Labels — **nicht** anfassen), reine Format-/Debug-Strings.

**Fertig wenn:** Die vier Empty-States + die ContentManagement-Karte + die gelisteten Toasts kommen aus Resources; bei App-Sprache Englisch erscheinen keine deutschen Resttexte an diesen Stellen; Build grün.

---

### AP 5 – Speichern/Abbrechen-Balken hinter der Navigationsleiste (Profil bearbeiten)
**Datei:** `feature-settings/.../ui/SettingsScreen.kt` (bottomBar, Z. 221–247)

Der `bottomBar`-`Surface` des inneren `Scaffold` (das mit `contentWindowInsets = WindowInsets(0.dp)` bewusst die Insets nullt) erhält **keine** Insets → bei `targetSdk=37` (Edge-to-Edge) liegt der Balken hinter der Android-Navigationsleiste und Speichern/Abbrechen sind auf Geräten mit Navigationsleiste (z. B. Samsung-Tablet) nicht erreichbar.

**Fix** — dem Inhalt des Balkens die relevanten Insets geben (robuster als das `navigationBarsPadding()` aus `SetupScreen.kt:225`: `safeDrawing` deckt Navigationsleiste **und** die Caption-Bar im Freeform-Fenster (AP 6) ab; zusätzlich `imePadding()` für die Tastatur). Auf die `Row` (Inhalt) im `bottomBar` setzen:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        )                          // Navigationsleiste + Caption-Bar (Freeform)
        .imePadding()              // hält den Balken über der Tastatur beim Profilnamen-Editieren
        .padding(dimensions.paddingMedium),
    horizontalArrangement = Arrangement.End
) { … }
```

Die `Surface` selbst **voll-bleed** lassen (kein Padding außen), damit ihre Tonal-/Shadow-Elevation bis zum Bildschirmrand zeichnet und nur der Button-Inhalt eingerückt wird. Imports ergänzen: `androidx.compose.foundation.layout.windowInsetsPadding`, `…WindowInsets`, `…WindowInsetsSides`, `…safeDrawing`, `…only`, `…imePadding`.

> Hinweis: Den `bottomBar` im `SetupScreen` **nicht** anfassen — der ist bereits korrekt.

**Fertig wenn:** Build grün; im Profil-Bearbeiten-Modus sind Speichern/Abbrechen auf einem Gerät/Emulator **mit** 3-Knopf-Navigationsleiste **und** mit Gesten-Leiste vollständig sichtbar/tippbar; beim Editieren des Profilnamens schiebt die Tastatur den Balken nicht außer Sicht.

---

### AP 6 – Windowed/Freeform offiziell unterstützen
**Datei:** `app/src/main/AndroidManifest.xml`

Hintergrund: Der Code-Audit (2026-06-13) ergab **keine** strukturellen Bruchstellen für Multi-Window/Freeform — keine echten Display-Metriken, Größenklassen sind fenster-basiert (`Theme.kt` `isTablet` via Root-`BoxWithConstraints`; `SettingsScreen` via `LocalWindowInfo.containerSize`), `configChanges` deckt Resize ohne Neustart ab, keine Orientierungs-Sperre, kein erzwungener Fullscreen, In-App-Overlay. Die App ist derzeit nur durch **eine Manifest-Flag** ausgesperrt.

1. **Resizeable aktivieren:** Im `<application>`-Tag `android:resizeableActivity="false"` → `"true"` ändern.
2. **Mindest-Fenstergröße festlegen**, damit kein absurd schmales Fenster die Hub-/Grid-Layouts staucht. In der `.MainActivity`-`<activity>` ein `<layout>`-Element ergänzen:
   ```xml
   <layout
       android:minWidth="360dp"
       android:minHeight="480dp"
       android:gravity="center" />
   ```
3. **AP 5 ist Voraussetzung** (der `safeDrawing`-Insets-Fix deckt die Freeform-Caption-Bar mit ab). Keine weiteren Code-Änderungen nötig — die UI ist bereits adaptiv.

**Verifikation (manuell, Gerät/Emulator mit Freeform bzw. Splitscreen):**
- App in Splitscreen und (falls verfügbar) Freeform/DeX öffnen, Fenster von groß nach klein ziehen.
- [ ] Layout passt sich live an (kein Neustart, kein Absturz), Hub-Karten/Nav skalieren
- [ ] `SettingsScreen`: Side-by-Side ab ~720 dp, darunter Single-Pane; Speichern/Abbrechen-Balken korrekt über den Insets
- [ ] Nutzermodus (`PageScreen`): Button-Grid bleibt bedienbar; Scanning unverändert
- [ ] Multi-Resume beachten: Beim Teilen des Bildschirms mit einer zweiten App `setUserModeActive`/Scanning-Verhalten prüfen (ON_PAUSE/ON_RESUME-Semantik ändert sich im Multi-Window)

> Falls die manuelle Prüfung an einer Stelle doch staucht: **nicht** die Flag zurückdrehen, sondern die konkrete Stelle melden — laut Audit ist nichts Strukturelles zu erwarten. Eine spätere Umstellung von orientierungs- auf `WindowSizeClass`-basierte Layouts ist dem Nav-Redesign vorbehalten.

**Fertig wenn:** Build grün; App läuft in Splitscreen/Freeform ohne Absturz und ohne abgeschnittene Bedienelemente; Mindestgröße greift.

---

## 3. Abschlussverifikation (vor Übergabe an Claude-Review)

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest   # falls Gerät/Emulator verfügbar
```

Sichtprüfung (Light + Dark, Hoch- + Querformat):
- [ ] StartScreen, BookList, PageList, TemplateScreen, ContentManagement: gleich hohe Hub-Karten
- [ ] BookList: Anlegen / Umbenennen / Löschen optisch identisch (Radius, Abbrechen als TextButton links)
- [ ] Leere Listen zeigen `GhostTalkEmptyState` mit lokalisiertem Text
- [ ] App auf Englisch: keine deutschen Resttexte an den AP-4-Stellen
- [ ] Profil bearbeiten: Speichern/Abbrechen über der Navigationsleiste sichtbar (Gesten- **und** 3-Knopf-Navigation)
- [ ] Splitscreen/Freeform: Fenster groß↔klein ziehen — Layout adaptiert live, kein Absturz, keine abgeschnittenen Bedienelemente
- [ ] Nutzermodus (`PageScreen`) unverändert funktionsfähig (Scanning, Bluetooth, Zurück)

---

## 4. Ausgeklammert — gehört zum parallelen Navigations-Plan

Diese Befunde aus dem Review sind **bewusst nicht** Teil dieses Plans, weil sie die Informationsarchitektur betreffen und mit der neuen Navigationsseite zusammen entschieden werden müssen:

- **Hub-Tiefe** `book_list → start → content_management → page_list → page_editor` (≈4 Taps bis zur Bearbeitung).
- **`safeNavigate`/`safePopBackStack`-Wrapper** in `GhostTalkNavHost` — Symptom fragiler Navigation; Ursache im Zuge des Nav-Redesigns adressieren, nicht vorab herausreißen.
- **String-basiertes Routing & Schutz-Check** (`route.startsWith("settings")`, Sonderfall `launchSingleTop`) → bei Nav-Redesign auf typisierte Routen heben.
- **Zwei identische Settings-Zahnräder** (global vs. buchbezogen, `isGlobal=true/false`) — Disambiguierung im neuen Nav-Konzept lösen.
- **Doppelte Affordance** im BookList-Empty-State (Aktionsbutton + FAB) — bewusst lassen oder im Nav-Konzept klären.

---

## Teil B — Editor-Kohäsion: „Ein Editor, zwei Modi" (Stufe B)

> ⚠️ **Scope-Wechsel ggü. Teil A.** Dies ist eine **Architektur-/Verhaltensänderung**, kein Cleanup.
> Sie führt Seiten-Editor ([PageEditorScreen](../app/src/main/java/com/andreas_kratzer/ghosttalk/ui/pages/PageEditorScreen.kt))
> und Struktur-Editor ([StructureEditorScreen](../app/src/main/java/com/andreas_kratzer/ghosttalk/ui/pages/structure/StructureEditorScreen.kt))
> zu **einer** Editier-Oberfläche zusammen, die zwischen zwei Modi umschaltet.

### B.0 Regeln für Teil B (erweitern Abschnitt 0)

- **Erlaubt** (abweichend von Regel 0.2): Änderungen an `GhostTalkNavHost` (Routen), an den beiden
  Editor-Screens und ihren Einstiegen. **Weiterhin tabu:** Datenmodelle, Repositories, `core-scanning`,
  ViewModel-**Logik** (Signaturen dürfen für die Zusammenführung erweitert, aber nicht in ihrer
  Semantik geändert werden).
- **Koordination mit dem Nav-Redesign-Plan ist Pflicht:** Routen/IA gehören eigentlich dorthin. AP B3
  (Route-Konsolidierung) **erst** umsetzen, wenn mit dem Nav-Plan abgestimmt ist, dass `editor/...`
  dort nicht konkurrierend angefasst wird. Sonst Datei-Kollision in `GhostTalkNavHost`.
- **`testTag` erhalten** — die bestehenden `page_editor_*`/`structure_*`-Tags dürfen NICHT verschwinden
  (Tests). Beim Zusammenführen die Tags an die entsprechenden Mode-Bodies durchreichen.
- **Ein AP = ein Commit**, Reihenfolge B1 → B5, nach jedem AP `assembleDebug` grün.

### Zielbild

Eine Werkbank, fokussiert auf **eine** Seite, mit Umschalter in einer **geteilten Top-Bar**:
- **Raster** = Buttons *innerhalb* der Seite bearbeiten (heutiger `GridEditorContent`).
- **Struktur** = Verbindungen/Graph + Baum *zwischen* Seiten (heutige Struktur-Inhalte).

Moduswechsel behält die fokussierte Seite. Der Baum-Navigator (buchweit) ist in beiden Modi
erreichbar und steuert den Fokus für beide.

### AP B1 — Geteilte Editor-Top-Bar extrahieren (klein, Vorarbeit)

- Neues Composable `EditorTopBar` (in `app/.../ui/pages/` oder `core-ui`): Slots für **Titel**
  (editierbarer Seitenname), **Modus-Umschalter** (zunächst leer/optional), **Aktionen**, **Zurück/Exit**.
- Beide Screens adoptieren es **verhaltenserhaltend** (gleiche Titelbehandlung, gleiche Exit-Semantik,
  gleiche Icon-Anordnung). Noch **keine** Modus-Logik.
- Beseitigt schon hier die Top-Bar-Divergenz (editierbarer Titel + 5 Icons vs. statischer Titel + Menü).

**Fertig wenn:** beide Editoren nutzen `EditorTopBar`; Top-Bars sehen gleich aus; `testTag`s erhalten; Build grün.

### AP B2 — Geteilte visuelle Token für Seite/Button (mittel)

- Ein gemeinsamer Token-/Helper-Satz „so sieht ein **Navigations**- vs. **Sprech**-Button aus"
  (Farbe, Icon) und „so sieht ein **Seiten**-Knoten aus", genutzt in **Rasterzelle, Struktur-Chip
  und Graph-Knoten**. Lage: `core-ui`.
- Rein visuell/verhaltenserhaltend — macht dasselbe Objekt über alle Darstellungen wiedererkennbar
  (der größte „aus-einem-Guss"-Hebel).

**Fertig wenn:** Nav-/Sprech-Button und Seiten-Knoten verwenden in Raster, Chip und Graph denselben
Token; visuell gegengeprüft (Light/Dark); Build grün.

### AP B3 — Modus-Container + Route-Konsolidierung (HOCH — Kernstück)

- Neuer Container (`PageWorkbenchScreen` oder erweitertes Screen) hostet **beide Mode-Bodies** für
  **eine** `pageId`, mit `SegmentedButton`-Umschalter „Raster | Struktur" in der `EditorTopBar`.
- Route `editor/{pageId}?mode={raster|struktur}` (Default `raster`); die bisherigen
  `page_editor/{pageId}` und `structure_editor?focus=` darauf zusammenführen (oder als Aliase
  weiterleiten, bis alle Einstiege migriert sind).
- Moduswechsel **behält `pageId`** (kein Fokusverlust). Bestehende Inhalte werden zu Mode-Bodies:
  Raster = `GridEditorContent`, Struktur = `StructureFocusCanvas`/Baum.
- `testTag`s der alten Screens an die Mode-Bodies durchreichen.

**Fertig wenn:** eine Route, ein Screen, zwei umschaltbare Modi; Wechsel behält die Seite; alle
bisherigen Funktionen beider Editoren erreichbar; Tests grün; Build grün. **Vorher Nav-Plan-Koordination.**

### AP B4 — Baum-Navigator als geteiltes Element (mittel)

- `StructureTreeNavigator` in **beiden** Modi erreichbar (Tablet: Seitenpane; Phone: Drawer/Sheet,
  Muster aus dem heutigen Struktur-Editor). Tap im Baum setzt die fokussierte `pageId` für beide Modi.

**Fertig wenn:** Aus dem Raster-Modus heraus per Baum die Seite wechseln; Struktur-Modus folgt demselben Fokus; Build grün.

### AP B5 — Übergänge entschlacken, Terminologie & Undo-Konsistenz (klein)

- Redundante Übergänge entfernen: der `Link`-Icon **und** der Overflow-Eintrag „Struktur bearbeiten"
  in `PageEditorScreen` werden durch den **Modus-Umschalter** ersetzt.
- Einheitliche Terminologie/Icons („Raster" / „Struktur") überall.
- **Undo-Konsistenz:** Sicherstellen, dass sich Undo in beiden Modi identisch anfühlt — Abstimmung mit
  [docs/undo_redo_history_plan.md](undo_redo_history_plan.md) (falls Undo dort buchweit/historienbasiert wird).

**Fertig wenn:** nur noch der Modus-Umschalter als Übergang; konsistente Begriffe/Icons; Undo gleich in beiden Modi; Build grün.

### Review-Schwerpunkte Teil B (Claude)

- [ ] Keine `testTag`-Regression (page_editor_*, structure_*).
- [ ] Kein Fokusverlust beim Moduswechsel; Round-Trip stabil.
- [ ] AP B3 wurde mit dem Nav-Plan koordiniert (keine konkurrierende `GhostTalkNavHost`-Änderung).
- [ ] Keine Semantikänderung an ViewModels/Delegates (nur Verdrahtung).
- [ ] Nutzermodus (`PageScreen`) unberührt.

---

## Teil C — Editor-Assistent & verschlankte Inhalte (Stufe C)

> **Anlass (Nutzung 2026-06-13):** Drei Wünsche aus dem Caregiver-Editing: (a) den „Seite verwalten"-Zwischenschritt einsparen, (b) den Layout-/Struktur-Assistenten **besser einbinden** (raus aus dem ⋮-Menü), (c) die adaptive Shell **ruhiger/aufgeräumter**. Visuelle Vorlage: Mock-up `adaptive_nav_v2_calm_with_assistant` (in der Session gezeigt).

### C.0 Regeln für Teil C (erweitern Abschnitt 0)
- **C1 ist sofort umsetzbar** (nur Editor, baut auf Teil B `EditorTopBar` auf).
- **C2** berührt die `editor/...`-Route → konsistent mit Teil B/Nav-Plan halten.
- **C3 ist an das Nav-Redesign gekoppelt** (braucht die Shell aus `navigation_redesign_plan.md` AP 2) → dort als Anforderung verankert; zusammen mit/nach Nav-AP 2 umsetzen.
- `testTag`s erhalten/durchreichen; ein AP = ein Commit; nach jedem AP `assembleDebug` grün.

### AP C1 — Assistent-Promotion in die EditorTopBar (klein, sofort)
- Den „Layout- & Struktur-Assistent" aus dem `page_editor`-Overflow (`page_editor_split_wizard_trigger_menu`) in eine **sichtbare, beschriftete Aktion** in der `EditorTopBar` heben (Icon `AutoAwesome`/Funkeln + Label), in **beiden** Modi (Raster + Struktur) sichtbar.
- Verhalten unverändert (öffnet `PageLayoutAssistantDialog`). Den Overflow-Eintrag entfernen; den bestehenden `testTag` an die neue Aktion **durchreichen** (`page_editor_split_wizard_trigger_menu` beibehalten) oder neuen `page_editor_assistant_action` ergänzen **und** betroffene Tests anpassen.
- **Fertig wenn:** Assistent in Raster + Struktur prominent sichtbar/auslösbar; kein Overflow-Eintrag mehr; Tests grün; Build grün.

### AP C2 — Vorschlags-Einstieg aus der Statistik (mittel)
- Aus der Statistik-/Empfehlungs-Ansicht (`AnalyticsDashboardScreen` / `analytics/recommendations/LayoutOptimizationSection`) den Assistenten **kontextnah** für die jeweils empfohlene Seite starten — dort entstehen die Layout-Vorschläge ohnehin.
- Umsetzung: in den Editor der Seite navigieren und den Assistenten direkt öffnen, z. B. neuer optionaler Query-Param `editor/{pageId}?mode=raster&openAssistant=true` (in `PageWorkbenchScreen`/`PageEditorScreen` auswerten) — **konsistent mit der Teil-B-Route halten**.
- **Fertig wenn:** aus der Statistik heraus lässt sich der Assistent für eine empfohlene Seite öffnen; Build grün.

### AP C3 — Inhalte verschlanken + ruhigere Shell-Tokens (gekoppelt an Nav-Redesign)
- **„Inhalte"-Tab der neuen Shell landet direkt auf der Seitenliste** (kein `content_management`-Zwischenhub) → spart den „Seite verwalten"-Schritt; kombiniert mit dem Baum-Navigator (Teil B/B4) entfällt der Umweg für Seitenwechsel ganz. Vorlagen / Statische Zeile als **sekundärer** Zugang (z. B. Segmented Control oder sekundäre Aktion), nicht als eigener Hub.
- **Ruhigere Tokens:** Akzentfarbe sparsam (nur aktives Nav-Ziel + Assistent), **dezenter Buch-Switcher** (Text + Chevron statt gefülltem Chip), mehr Weißraum, dünne Trenner. Als Gestaltungsleitlinie für die Shell.
- **Abhängigkeit:** setzt die Shell aus `navigation_redesign_plan.md` AP 2 voraus → dort als Anforderung vermerkt; dieser AP wird **mit/nach** Nav-AP 2 umgesetzt.
- **Fertig wenn:** „Inhalte" = Seitenliste (max. 1 Tap bis Editor); Shell folgt den ruhigen Tokens; Build grün.

### Review-Schwerpunkte Teil C (Claude)
- [ ] C1: Assistent in beiden Modi sichtbar; `testTag`-Durchreichung + Tests angepasst; Dialog-Verhalten unverändert.
- [ ] C2: Route-Param sauber (kein Aufbrechen der Teil-B-Route); kein Doppel-Öffnen des Dialogs.
- [ ] C3: Inhalte-Flachung ohne Funktionsverlust (Vorlagen/Statische Zeile weiter erreichbar); Tokens konsistent Light/Dark.

---

## Teil D — Feinschliff (Stufe D)

> **Sammel-AP** für die OOP-/Tech-Debt-Befunde aus dem Teil-B-Nachreview (2026-06-13) **plus** verbliebene Kleinigkeiten aus früheren Phasen. **Reine Qualität/Robustheit, kein neues Verhalten** (Ausnahme: D3). Ein AP = ein Commit; `assembleDebug` + Tests nach jedem AP grün; `testTag`s erhalten.
>
> ⚠️ **Die AP-Nummern = Ausführungsreihenfolge: D1 → D7 strikt der Reihe nach.** Nicht umsortieren — D3 braucht D1, baut auf D2; D6 (Import-Hygiene) kommt bewusst **nach** den Header-Umbauten.

### AP D1 — `EditorMode` statt Magic-Strings (klein) — Fundament für D3
`"raster"`/`"struktur"` sind aktuell rohe String-Literale in `GhostTalkNavHost` (Routenliterale `editor/...?mode=…`, `navArgument`-Default, `getString`-Fallback) und `PageWorkbenchScreen` (Vergleiche/Zuweisungen) — ≥ 10 Stellen, typo-anfällig (ein falsch geschriebenes „struktur" schaltet still in den Default).
- `enum class EditorMode { RASTER, STRUKTUR }` mit `val route: String` + `companion fun fromRoute(s: String?): EditorMode` einführen. Das NavHost-Route-Arg bleibt `StringType`; Mapping enum↔String **an genau einer Stelle**. Alle Vergleiche/Zuweisungen über das Enum.
- **Fertig wenn:** keine rohen `"raster"/"struktur"`-Literale mehr außer der Mapping-Stelle; Build + Tests grün.

### AP D2 — `EditorTopBar` entkoppeln (klein) — Header-Vorarbeit vor D3
Die generische core-ui-Komponente `EditorTopBar` hardcodet `testTag("page_editor_exit_button")` und `contentDescription = "Editor beenden"` → leaky Abstraktion + hartcodierter String.
- Exit-`testTag` als Parameter (Default = `page_editor_exit_button`, damit bestehende Tests grün bleiben). `contentDescription` über String-Ressource (Konvention `docs/i18n_backlog_plan.md`) bzw. Parameter.
- **Fertig wenn:** kein page_editor-spezifischer Tag mehr fix in core-ui; „Editor beenden" lokalisiert; Tests grün.

### AP D3 — EditorTopBar verdichten (kompakter Header, Hoch- + Querformat) — braucht D1, baut auf D2
Aus dem C1-Review + Andreas-Feedback (2026-06-13): Der Header ist im Portrait überladen (Titel-Textfeld + Assistent-Label + 6 Icons + Overflow + Modus-Umschalter, der dadurch auf eine eigene Zeile rutscht). Vorlage: Mock-ups `editor_topbar_compact_mode_toggle` + `editor_topbar_portrait_title_label`.
> **Bewusste kleine Verhaltensänderung** (Ausnahme zur Teil-D-Regel „kein Verhalten"): Umbenennen läuft künftig über einen Dialog statt inline.

Vier zusammengehörige Änderungen an `EditorTopBar` (core-ui) + beiden Nutzern (`PageEditorScreen`, `StructureEditorScreen`):

**(a) Titel = antippbares Label statt Dauer-Eingabefeld.**
- `titleContent` zeigt den Seitennamen als Text (ellipsiert) + dezenten Stift-Hinweis; **Tap öffnet einen Umbenennen-Dialog** (`GhostTalkDialog` mit der **bestehenden `ValidatedTextField`-Validierung** — Pflichtfeld, Fehlermeldung, `gridEditorViewModel.updateGridSettings(name=…)` beim Bestätigen).
- **Test-Impact (wichtig):** `PageManagementIntegrationTest` macht `onNodeWithTag("page_editor_name_field").performTextReplacement(...)`. Der Tag `page_editor_name_field` **wandert auf das Feld im Dialog**; der Test muss den Flow anpassen (Titel-Label tippen → Feld im Dialog editieren). Tag-Name **beibehalten**.

**(b) Modus-Umschalter als Icon-Segmented statt Text-Pill.**
- `SingleChoiceSegmentedButtonRow` mit **zwei Icon-Segmenten**: Raster = `layout-grid`, Struktur = `sitemap` (GhostTalkIcons-Pendants). Je Segment `contentDescription`/Tooltip „Raster" / „Struktur".
- Liegt **in der Header-Zeile** (nie eigene Zeile), **konsistent gleich** in Hoch- und Querformat. Nutzt `EditorMode` aus AP D1.

**(c) Adaptive Action-Dichte.**
- Schwelle `screenWidthDp < 600` (Helper aus StructureEditorScreen wiederverwenden).
- **Portrait sichtbar:** Assistent (**icon-only**), Undo, Modus-Umschalter, ⋮. **Ins ⋮:** Redo, Verlauf, Vorschau, eingehende Links, Analytics-Overlay.
- **Landscape inline:** zusätzlich Redo, Verlauf, Vorschau, eingehende Links; Assistent **mit Label**.

**(d) Assistent als geteiltes Composable.**
- Den in C1 doppelten Assistent-Button als **ein** Composable (z. B. `EditorAssistantButton(onClick, compact)`) extrahieren, in beiden Editoren nutzen → kein Duplikat, **eine** String-Ressource. `compact=true` = icon-only (Portrait).

**Barrierefreiheit:** alle Icon-Aktionen + Modus-Segmente mit `contentDescription`; Touchziele ≥ 48 dp.

**Fertig wenn:** Modus-Umschalter nie auf eigener Zeile; Header läuft auf einem Telefon (Portrait) nicht über; Umbenennen-Dialog funktioniert mit Validierung; `EditorMode` + geteiltes Assistent-Composable genutzt; `PageManagementIntegrationTest` angepasst und grün; Build grün; visuell Hoch-/Querformat + Light/Dark geprüft.

### AP D4 — `openAssistant`-Intent einmalig konsumieren (klein, aus C2-Review)
C2 reicht `openAssistant` als Back-Stack-Arg → `PageWorkbenchScreen.initialOpenAssistant` → `PageEditorScreen` durch. Da das Arg `true` bleibt, öffnet ein Moduswechsel Raster→Struktur→Raster (Neukomposition von `PageEditorScreen`) den Assistent-Dialog **erneut**.
- Flag **einmalig konsumieren** auf `PageWorkbenchScreen`-Ebene (bleibt über Moduswechsel bestehen): z. B. `var assistantPending by rememberSaveable { mutableStateOf(initialOpenAssistant) }`, nur beim ersten Eintritt in den Raster-Modus an `PageEditorScreen` durchreichen und danach auf `false` setzen.
- **Fertig wenn:** Deep-Link aus der Statistik öffnet den Assistenten **einmal**; Raster↔Struktur-Round-Trip öffnet ihn nicht erneut; Build grün.

### AP D5 — `onExitEditor` robust gegen fehlendes `page_list` (klein)
`popBackStack("page_list", inclusive=false)` läuft ins Leere, wenn der Editor aus ContentManagement (Statische Zeile) oder Analytics betreten wurde (`page_list` nicht im Back-Stack) → „Editor beenden" tut nichts. Altlast, hier mit-fixen.
- Fallback: wenn `page_list` nicht im Back-Stack ist, auf das nächste sinnvolle Ziel poppen (z. B. einfaches `popBackStack()` / bis zur Inhalte-Ebene). Idealerweise nach dem Nav-Redesign auf das Shell-/Inhalte-Ziel abstimmen.
- **Fertig wenn:** „Editor beenden" führt aus **jedem** Einstieg zu einem sinnvollen Ziel; manuell geprüft.

### AP D6 — Import-Hygiene in den Teil-B-Dateien (trivial) — nach den Header-Umbauten
Fully-qualified Inline-Referenzen durch Imports ersetzen: `PageEditorScreen` (u. a. `…BookNavigationGraph`, `…structure.StructureTreeNavigator`, `androidx.compose.foundation.layout.Row/Spacer/Card`), `GridButton` (`…ActionVisualTokens.getColors`), `PageWorkbenchScreen` (`…hiltViewModel`). **(Stellen/Zahl nach D3 erneut per Grep prüfen — der Header-Umbau ändert PageEditorScreen.)**
- Rein kosmetisch, keine Verhaltensänderung.
- **Fertig wenn:** keine `com.andreas_kratzer…`/`androidx…`-Vollpfade mehr inline in diesen Dateien; Build grün.

### AP D7 — Rest-Kleinigkeiten (trivial)
- **AP5-Insets:** `.imePadding()` am Profil-bottomBar ist redundant (`safeDrawing` enthält `ime` bereits) → entfernen **oder** als bewusst belassen kommentieren.
- **B4 Phone-Raster:** kein Inline-Baum im Raster-Modus am Telefon — optional Sheet-Zugang wie im Struktur-Modus ergänzen **oder** bewusst lassen (Baum via Moduswechsel).
- **i18n der neuen Teil-B/C-Strings** („Raster"/„Struktur", „Editor beenden", „Mehr Optionen", Assistent-Label, Snackbar „Magische Bereinigung…") → laufen über `docs/i18n_backlog_plan.md`, **nicht** hier doppelt tracken (nur Verweis).

### AP D8 — Shell-Header bereinigen (mittel, aus C3-Review)
Aus dem C3-Review (`efb495c5`): Die Shell-Panes bringen ihr eigenes Chrome mit → doppelte/dreifache App-Bars und kleinere Folgepunkte.
- **(a) Doppel-TopBar weg (Kern):** `PageListScreen`, `AnalyticsDashboardScreen`, `SettingsScreen` rendern in der Shell ihr eigenes `GhostTalkScaffold` (TopAppBar), obwohl `BookShellScreen` schon eine TopAppBar (Buch-Switcher) hat. Den **Inhalt** dieser Screens als Scaffold-lose Composable-Funktion herauslösen (`…Content(...)`) und in der Pane einbetten; die Standalone-Variante (mit Scaffold) nur noch außerhalb der Shell nutzen. Settings: die verschachtelten Scaffolds (inkl. bottomBar-Insets B6) dabei sauber halten.
- **(b) `BookShellScreen.currentTab`** von `remember` auf `rememberSaveable` (Tab überlebt Rotation/Prozess-Tod).
- **(c) Legacy-Tag:** `testTag("start_card_user_mode")` am „Sprechen"-Nav-Item → `nav_item_speak` umbenennen und `NavigationIntegrationTest` entsprechend anpassen.
- **(d) Tote Route:** `composable("page_list")` entfernen (nichts navigiert mehr dorthin) und `onExitEditor` auf reines `safePopBackStack()` vereinfachen (deckt sich mit D5 — zusammen erledigen). Security-Check-Eintrag `route == "page_list"` mit aufräumen.
- optional: Pane-State über Tab-Wechsel via `rememberSaveableStateHolder` erhalten.
- **Fertig wenn:** in der Shell genau **eine** TopAppBar; Tab überlebt Rotation; keine tote `page_list`-Route; Tags/Tests grün; Build grün; visuell Hoch-/Querformat geprüft.

### Review-Schwerpunkte Teil D (Claude)
- [ ] D1: kein Magic-String mehr; Route-Mapping an genau einer Stelle; kein Verhaltensbruch beim Moduswechsel.
- [ ] D8: in der Shell nur eine TopAppBar (kein Doppel-Header); Panes ohne eigenes Scaffold; Tab rememberSaveable; keine tote page_list-Route.
- [ ] D3: Modus-Umschalter nie eigene Zeile; Portrait-Header kein Überlauf; Umbenennen-Dialog mit Validierung; `page_editor_name_field`-Tag im Dialog + Test angepasst; Modus-Segmente mit `contentDescription`.
- [ ] Sonst reiner Feinschliff — keine ungewollte Semantikänderung, bestehende `testTag`s/Tests grün (D3 ist die bewusste Ausnahme: Umbenennen via Dialog).

---

## 5. Review-Notizen (Claude — wird während des Reviews gefüllt)

### Review Teil A (AP 1–6), Claude, 2026-06-13 — gegen uncommitteten Working Tree

**Gesamt:** Sauber umgesetzt, Scope eingehalten (nur die erwarteten Dateien), `assembleDebug` + `testDebugUnitTest` grün. **Ein Must-Fix (klein) in AP 4**, sonst nur Kosmetik.

**Pro AP:**
- **AP 1 ✅** Alle 4 `NAV_DEBUG`-Logs entfernt (`onNavigateToUserMode` ×2, `safePopBackStack`, `safeNavigate`); `safeNavigate`/`safePopBackStack`/`runOnMainThread`-Wrapper unangetastet. `StartScreen`: toter äußerer `isLandscape` + `Configuration`/`LocalConfiguration`-Imports raus, innerer `isLandscape` (für `maxItemsInEachRow`) korrekt erhalten.
- **AP 2 ✅** `adaptiveCardHeight()` in `GhostTalkComponents.kt` (plan-konform), alle 5 Call-Sites migriert, Landscape-Faktor auf 0.18 vereinheitlicht (StartScreen 0.2 → 0.18).
- **AP 3 ✅** Beide rohen `AlertDialog` (Umbenennen/Neu) auf `GhostTalkDialog` migriert; Validierungs-, `forceSoftKeyboard`/`onFocusChanged`-, `isError`-Logik 1:1 erhalten; `isDestructive=false`.
- **AP 4 ⚠️** Empty-States (Book/Template/Page/Analytics) + ContentManagement-Karte + Toasts (Suggestions, SmartIntegration, CloudSync, **alle** Hue-Meldungen) lokalisiert; **de/en-Parität vollständig**, Format-Args (`%1$s`/`%1$d`) korrekt. Hue über den Minimal-Scope hinaus (positiv, im Sinne von AP 4).
- **AP 5 ✅** `WindowInsets.safeDrawing.only(Horizontal+Bottom)` + `imePadding()` auf die bottomBar-`Row`. (`imePadding` ist redundant, da `safeDrawing` `ime` bereits enthält — harmlos dank Inset-Consumption.)
- **AP 6 ✅** `resizeableActivity="true"` + `<layout minWidth=360dp minHeight=480dp gravity=center>`.

**🐛 Must-Fix M1 (klein, AP 4): `HueSettingsDelegate.kt:136` noch hartcodiert.** ✅ **behoben** (Gemini, 2026-06-13): `hue_pairing_press_link_button` (de+en) + `context.getString(...)`. Verifiziert, Build grün.

**Nachtrag (Claude, 2026-06-13): i18n-Residuum erkannt, NICHT Teil A.** `_huePairingStatus.value` wird noch an 7 weiteren Stellen mit hartcodiertem Deutsch gesetzt (`HueSettingsDelegate.kt:65,68,76,95,144,149,153`) → Pairing-Status jetzt gemischt lokalisiert. War **nicht** in der AP-4-Liste (nur 52/60/102/135 genannt), daher kein Teil-A-Blocker. **→ i18n-Sammelticket** (zusammen mit `AnalyticsDashboardScreen.kt:259`).

**Kosmetik (keine Blocker):** ✅ **beide behoben** (Gemini, 2026-06-13), verifiziert.
- Doppelter `import androidx.compose.ui.unit.dp` in `GhostTalkComponents.kt` → entfernt.
- Ungenutzte Imports `AlertDialog`/`Button`/`ButtonDefaults` in `BookListScreen.kt` → entfernt.
- **Out-of-scope-Residuum (nicht in AP-4-Liste):** `AnalyticsDashboardScreen.kt:259` „Seite wird verwendet" noch hartcodiert → fürs i18n-Sammelticket.

**Noch offen (manuell, headless nicht prüfbar):** Sichtprüfung Light/Dark + Hoch/Quer; Profil-Save/Cancel über Navigationsleiste auf echtem Gerät; Splitscreen/Freeform-Resize (AP 6); App-Sprache Englisch ohne dt. Resttexte.

### Review Teil B (B1–B5), Claude, 2026-06-13 — gegen uncommitteten Working Tree

**Gesamt:** Sauber und kohärent umgesetzt, `assembleDebug` + `testDebugUnitTest` grün, alle `page_editor_*`/`structure_*`-testTags erhalten. **Keine Code-Must-Fixes.** Eine Doku-Koordination (B3↔Nav-Plan) + kleinere Notizen.

- **B1 ✅** `EditorTopBar` (core-ui, neu) in **beiden** Editoren adoptiert; `titleContent`/`modeSwitcher`/`actions`/`onExitEditor`-Slots; Exit-Button trägt weiterhin `page_editor_exit_button`.
- **B2 ✅** `ActionVisualTokens` (core-ui, neu) — geteilte Badge-Farben für Nav/Sprech/SmartHome/Gemini/… über Rasterzelle, Chip, Graph, Baum.
- **B3 ✅** `PageWorkbenchScreen` (neu) hostet beide Modi für **eine** `pageId`; `mode`/`focusedPageId`/`buttonId`/`triggerSplit` in `rememberSaveable` → **kein Fokusverlust** beim Umschalten. Route konsolidiert: `page_editor` + `structure_editor` → `editor/{pageId}?mode={raster|struktur}&buttonId=&triggerSplit=`; Security-Gate auf `route.startsWith("editor")` angepasst; Settings-Nav-Events + alle Einstiege migriert. **Geteilte `GridEditorViewModel`** (workbench-scoped) an beide Modi → gemeinsamer Edit-/Undo-State (= B5-Undo-Konsistenz).
- **B4 ✅** `StructureTreeNavigator` in beiden Modi: Tablet = 300dp-Seitenpane (auch im Raster), Phone = Bottom-Sheet (im Struktur-Modus). **Notiz:** Phone-**Raster** hat keinen Inline-Baum — Baum dort nur nach Moduswechsel erreichbar (Plan B4 wollte Sheet in beiden Modi). Akzeptabel via Umschalter, ggf. Folgeschliff.
- **B5 ✅** Plain-„Struktur bearbeiten" entfernt → Modus-Umschalter ist der kanonische Übergang; Overflow hat nur noch Analytics-Toggle + „Layout-/Struktur-Assistent" (eigenständige KI-Split-Aktion); Link-Icon = eingehende Links (eigene Funktion, korrekt behalten).

**Befunde (keine Blocker):**
- **Koordination B3↔Nav-Plan (erledigt):** `navigation_redesign_plan.md` referenzierte noch `page_editor`/`structure_editor`. Da B3 zuerst kam, **Nav-Plan auf `editor/{pageId}?mode=` nachgezogen** (Ist-Diagramm + AP 2/AP 5). Kein Code-Konflikt (Nav-Redesign noch nicht implementiert).
- **Neue hartcodierte UI-Strings durch Teil B** → ins i18n-Sammelticket aufgenommen: `PageWorkbenchScreen` „Raster"/„Struktur", `EditorTopBar` „Editor beenden".
- **Pre-existing/minor:** `onExitEditor` macht `popBackStack("page_list")` — wenn der Editor aus ContentManagement (Statische Zeile) o. Analytics betreten wird, ist `page_list` evtl. nicht im Back-Stack (Altlast, nicht durch B3 verursacht). → Teil D / AP D5.

**OOP-/Tech-Debt-Nachreview Teil B (Claude, 2026-06-13):** Überwiegend **sauber**, kaum neue Schuld.
- ✅ DRY: Start-Seiten-Auflösung als `private suspend fun resolveStartPage(...)` extrahiert (4 Aufrufer) — **nicht** dupliziert (frühere Notiz korrigiert).
- ✅ `ActionVisualTokens` zentralisiert (erschöpfendes `when`, kein `else`; wiederverwendet vorhandene Color.kt-Tokens; `GridButton`/`DraggableChip` delegieren; keine Farb-Duplikate).
- ✅ `EditorTopBar`/`PageWorkbenchScreen` saubere Extraktion/Wiederverwendung; geteilte `GridEditorViewModel` korrekt durchgereicht.
- 🔧 **Debt (→ Teil D):** D1 Magic-Strings `"raster"/"struktur"` (kein Enum); D2 `EditorTopBar` hardcodet page_editor-Tag + dt. String (leaky); D5 `onExitEditor`-Altlast; D6 Import-Hygiene (fully-qualified Inline-Refs).

### Review Teil C — AP C1 (Assistent-Promotion), Claude, 2026-06-13 — uncommitteter Working Tree

**Gesamt: funktional sauber, abnahmefähig.** `assembleDebug` + `testDebugUnitTest` grün.
- ✅ Assistent jetzt als sichtbarer `TextButton` (Icon + „Assistent") in **beiden** Editoren (`PageEditorScreen` = Raster, `StructureEditorScreen` = Struktur); Overflow-Einträge entfernt; **bestehende `testTag`s durchgereicht** (`page_editor_split_wizard_trigger_menu`, `structure_editor_split_wizard_trigger_menu`) → Tests bleiben grün.
- ✅ Verhalten unverändert (Raster öffnet `PageLayoutAssistantDialog`; Struktur startet Opt-in/`generatePageSplitProposal`). Overflow-Menüs **nicht** leer (Analytics bzw. 3 Einträge bleiben). Imports sauber (kein fully-qualified).

**Befunde (keine Blocker, → Teil D / Sichtprüfung):**
- 🔧 **Duplikation:** Der Assistent-`TextButton`-Block (Icon+Spacer+Label) ist in beiden Editoren nahezu identisch (nur `onClick`) → als geteiltes Composable extrahieren. **→ Teil D / AP D3.**
- ⚠️ **Top-Bar-Dichte:** Die Action-Zeile in `PageEditorScreen` hat jetzt 1 beschrifteten Button + 6 Icons + Overflow, dazu der Modus-Umschalter in der Titelzeile → auf Telefonen eng; widerspricht leicht dem „ruhiger"-Ziel (C3). Sichtprüfung schmale Breite; ggf. Assistent **icon-only auf schmal** (`isNarrow`-Flag in StructureEditorScreen existiert bereits). **→ Teil D / AP D3.**
- hartcodierte „Assistent"/„Layout- & Struktur-Assistent" → i18n-Ticket (löst sich mit der Composable-Extraktion).

### Review Teil C — AP C2 (Vorschlags-Einstieg aus Statistik), Claude, 2026-06-13 — uncommitted

**Funktional sauber, abnahmefähig.** `assembleDebug` + `testDebugUnitTest` grün.
- ✅ `LayoutOptimizationSection` navigiert jetzt echt in den Editor mit geöffnetem Assistenten (`onNavigateToEditorWithAssistant(proposal.pageId)`) statt nur Toast — der hartcodierte Toast „…Öffne den Assistenten im Editor." entfällt (Bonus i18n).
- ✅ Saubere Durchreichung: `RecommendationsState`-Callback → `AnalyticsDashboardScreen.onEditPage(String, Boolean)` → NavHost-Route `…&openAssistant={openAssistant}` (BoolType, Default false) → `PageWorkbenchScreen.initialOpenAssistant` → `PageEditorScreen`. `rememberSaveable` für den Dialog-State (rotationssicher).

**Befund (klein, → Teil D):**
- ⚠️ **`openAssistant` nicht einmalig konsumiert:** Flag bleibt am Back-Stack-Eintrag; Moduswechsel Raster→Struktur→Raster komponiert `PageEditorScreen` neu und **öffnet den Assistenten erneut**. Guard auf `PageWorkbenchScreen`-Ebene (Flag nach erstem Öffnen löschen). → **Teil D / AP D4.**
- trivial: positionaler `Boolean` in `onEditPage` (Lesbarkeit); Magic-String `"raster"` → D1.

### Review Teil C — AP C3 (committet `efb495c5`), Claude, 2026-06-14

**Achtung Scope:** Der „C3"-Commit `efb495c5` enthält faktisch das **gesamte Nav-Redesign** (AP1 Dependency `material3-adaptive-navigation-suite`, AP2 `BookShellScreen`, AP3 Buch-Switcher, AP4 PIN-Exit-Sperre im Nutzermodus, AP5 `StartScreen`/`content_management`/`analytics_dashboard` entfernt) **plus** C3 (Inhalte=Seitenliste) **plus** `EditorMode`-Enum (= Teil D / D1). Ein sehr großer Commit — Build + `testDebugUnitTest` grün, `NavigationIntegrationTest` angepasst (nicht ausgehöhlt).

**Korrekt:**
- ✅ AP4 PIN-Exit: `main` ruft beim Eintritt `securityManager.lock()` (wenn `isPinSet()`), Zurück (BackHandler + Top-Bar) über `SecurityEntryDialog` gated, erst bei Erfolg `safePopBackStack()`. Nutzt vorhandenes `isPinSet()` (kein neuer Core-Code).
- ✅ AP3 Buch-Switcher (Dropdown: Bücher + „Zu Büchern" + globale Einstellungen). ✅ Nutzermodus-Isolation (eigene Route, kein Shell-Chrome). ✅ `EditorMode.RASTER.route` in Routen (D1 erledigt). ✅ `onExitEditor` mit Fallback (`if(!popped) safePopBackStack()`).

**🐛 Befund 1 (mittel, → Teil D / AP D8): Doppelte TopAppBar in der Shell.** Die Panes (`PageListScreen`, `AnalyticsDashboardScreen`, `SettingsScreen`) rendern **ihr eigenes `GhostTalkScaffold`** (TopAppBar), obwohl `BookShellScreen` bereits eine TopAppBar (Buch-Switcher) hat → zwei gestapelte App-Bars (Settings: dreifach verschachtelte Scaffolds). Verschwendet vertikalen Platz, widerspricht dem „ruhiger"-Ziel. Nav-Plan AP2 wollte genau das vermeiden (Inhalt **ohne** eigenes Scaffold einbetten).

**Befunde (klein):**
- `BookShellScreen.currentTab` ist `remember` statt `rememberSaveable` → Tab-Reset bei Rotation/Prozess-Tod. → D8.
- Tote Route `composable("page_list")` (nichts navigiert mehr dorthin) + vestigialer `popBackStack("page_list")`-Zielpunkt → entfernen, `onExitEditor` auf `safePopBackStack()` vereinfachen. → D5.
- Legacy-`testTag("start_card_user_mode")` klebt auf dem neuen „Sprechen"-Nav-Item (irreführend) → auf `nav_item_speak` umbenennen + Test anpassen. → D8.
- Panes werden beim Tab-Wechsel neu komponiert (kein State-Erhalt) — optional `rememberSaveableStateHolder`. → D8 (optional).

**Manuell offen (headless):** adaptive Bar↔Rail (Hoch/Quer/Tablet), PIN-Exit-Flow auf Gerät, Doppel-Bar visuell.

### Review Teil D — D1–D3, Claude, 2026-06-14 — uncommitteter Working Tree

Build + `testDebugUnitTest` grün.
- **D1 ✅** `EditorMode { RASTER, STRUKTUR }` (+`fromRoute`/`route`) neu in `core-ui`, verdrahtet in `GhostTalkNavHost` + `PageWorkbenchScreen`. Keine rohen Mode-Magic-Strings mehr außer dem Default-Literal `initialMode: String = "raster"` (PageWorkbenchScreen:24) — Mini-Nit, via `fromRoute` konvertiert.
- **D2 ✅** `EditorTopBar`: `exitTestTag`-Parameter (Default `page_editor_exit_button` → Tests grün) + `exitContentDescription` via `R.string.editor_exit`. Leaky-Hardcodes weg.

**🚧 D3 ist NICHT umgesetzt — es existiert nur ungenutztes Gerüst (Build grün, weil nichts integriert wurde):**
- `app/.../ui/components/EditablePageTitle.kt` (vollständige Tap→Rename-Dialog-Komponente) — **0× verwendet**; beide Editoren nutzen weiter `ValidatedTextField` als Titel (PageEditorScreen:127, StructureEditorScreen:299). → D3(a) Titel-Label **offen**.
- `GhostTalkIcons.Sitemap` neu — **0× verwendet**; der Modus-Umschalter in `PageWorkbenchScreen` zeigt weiter `Text("Raster")`/`Text("Struktur")` (Text-Pill). → D3(b) Icon-Umschalter **offen**.
- `PageEditorScreen`/`StructureEditorScreen` **unverändert** → D3(c) adaptive Actions (Redo→⋮) und D3(d) Assistent-Dedup **offen**.

**Empfehlung:** D3 vor dem Commit **fertigstellen** (sonst wandert totes `EditablePageTitle` + ungenutztes Sitemap-Icon in die History): EditablePageTitle in beiden Editoren als `titleContent` verdrahten (ersetzt ValidatedTextField), Modus-Umschalter auf Icon-Segmented (`layout-grid`/`Sitemap`) umstellen, adaptive Action-Verlagerung + geteiltes Assistent-Composable in den Editoren umsetzen. D3 bleibt **offen**.

### Review Teil D — D3 Nachreview (2. Anlauf), Claude, 2026-06-14 — uncommitted

**D3 jetzt vollständig & sauber umgesetzt** (ersetzt den vorherigen „nur totes Gerüst"-Befund). Build + `testDebugUnitTest` grün.
- **(a) ✅** `EditablePageTitle` als `titleContent` in **beiden** Editoren (PageEditorScreen:130, StructureEditorScreen:302); Tap → `GhostTalkDialog` mit `ValidatedTextField` (Validierung wiederverwendet); `onRename` → `updateGridSettings(name=…)`. `PageManagementIntegrationTest` korrekt umgestellt (Row-Tag `editable_page_title_row` klicken → Feld-Tag `page_editor_name_field` im Dialog `performTextReplacement` → Speichern).
- **(b) ✅** Modus-Umschalter Icon-Segmented (`GhostTalkIcons.GridView`/`Sitemap`), Text-Pill weg.
- **(c) ✅** adaptive Actions: `isNarrow = screenWidthDp < 600` → Redo inline (`!isNarrow`) bzw. im ⋮ (`isNarrow`); Assistent `compact = isNarrow`.
- **(d) ✅** `EditorAssistantButton` geteiltes Composable (`compact`-Param = icon-only), in beiden Editoren; testTag `page_editor_split_wizard_trigger_menu` erhalten.

**Kleinkram (→ D6/D7, kein Blocker):** `ValidatedTextField` jetzt toter Import in beiden Editoren (D6); `EditorAssistantButton.description`/Label hartcodiert dt. (i18n/D7); fully-qualified `LocalConfiguration`-Inline (D6); StructureEditor-Header zeigt jetzt den Seitennamen statt „Struktur-Editor" (bewusst, Kohäsion).

### Review Teil D — D4–D8, Claude, 2026-06-14 — uncommitteter Working Tree (D1–D3 committet `acddb72b`)

Build + `testDebugUnitTest` grün.
- **D4 ✅** `assistantPending` (rememberSaveable) in `PageWorkbenchScreen` + `onAssistantConsumed`-Callback; `PageEditorScreen` feuert per `LaunchedEffect(initialOpenAssistant)`. Round-Trip-Re-Open behoben.
- **D5 ✅** `onExitEditor` → `popBackStack("start", false)` + Fallback `safePopBackStack()`; `composable("page_list")` entfernt.
- **D6 ✅ (überwiegend)** viele fully-qualified Inline-Refs in `PageEditorScreen` → Imports. **Offen:** toter `import …ValidatedTextField` in `PageEditorScreen` + `StructureEditorScreen` (0 Nutzung seit D3) — gerade von D6 zu fangen gewesen.
- **D7 ⚠️ offen** `.imePadding()` am Profil-bottomBar unverändert (weder entfernt noch kommentiert). B4/i18n waren ohnehin optional/Verweis.
- **D8 teilweise:** **(a) ✅ Doppel-TopBar behoben** — neuer `GhostTalkScaffold(showTopBar=…)`; BookShell gibt allen 3 Panes `showTopBar=false`. **(d) ✅** page_list-Route weg. **(b) ❌ `currentTab` weiter `remember`** (nicht `rememberSaveable`) → Tab-Reset bei Rotation. **(c) Tag-Rename nicht gemacht** — `start_card_user_mode` wird in ~18 Integrationstest-Stellen genutzt → Rename invasiv; **Empfehlung: bewusst belassen + dokumentieren** statt 18 Tests zu churnen.

**`gradle/libs.versions.toml` (Bumps `appcompat 1.7.0→1.7.1`, `okhttp 5.3.2→5.4.0`):** **bewusst von Andreas** eingebracht — kein Gemini-Scope-Drift, ok. Da `okhttp` Netz/Cloud-Sync berührt: bei Gelegenheit Sync-Smoke-Test; idealerweise in eigenem Commit halten.

**Restpunkte → klein, bündeln (D7-Rest):** toter ValidatedTextField-Import (2 Dateien), D8(b) currentTab rememberSaveable (1 Zeile), vestigiales `route=="page_list"` im Security-Check, imePadding-Entscheidung. D8(c) als „bewusst belassen" schließen (sofern Andreas zustimmt).

### Review Teil D — D-Abschluss (Restpunkte), Claude, 2026-06-14 — uncommitted, verifiziert

Alle Batch-Review-Reste behoben, Build + `testDebugUnitTest` grün:
- ✅ `libs.versions.toml`-Bumps (appcompat/okhttp) zurückgesetzt (waren ohnehin bewusst von Andreas — jetzt wieder auf 1.7.0/5.3.2, Scope sauber).
- ✅ D8(b) `currentTab` → `rememberSaveable` (BookShellTab-Enum, Default-Saver) — überlebt Rotation.
- ✅ D6-Rest: toter `ValidatedTextField`-Import aus beiden Editoren entfernt.
- ✅ D8(d)-Rest: vestigiales `route == "page_list"` aus dem Security-Check entfernt.
- ✅ D7: `.imePadding()` bewusst belassen + Kommentar.
- ◻️ D8(c): Legacy-Tag `start_card_user_mode` **bewusst belassen** (in ~18 Integrationstest-Stellen genutzt; Rename = reine Namenskosmetik, nicht den Test-Churn wert).

**Teil D abgeschlossen** (D1–D8 ✅, D8(c) bewusst belassen). Gesamter Plan: Teil A ✅, Teil B ✅, Teil C ✅, Teil D ✅. Offen nur noch: separates `docs/i18n_backlog_plan.md` (I1–I4) sowie manuelle Gerätesicht (Freeform/Resize, PIN-Exit, adaptive Bar↔Rail, Doppel-TopBar visuell).
