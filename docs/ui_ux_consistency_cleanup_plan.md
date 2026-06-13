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
| 1 | Debug-Logs + toter Code entfernen | trivial | offen |
| 2 | Geteilter Adaptive-Card-Height-Helper | klein | offen |
| 3 | BookListScreen-Dialoge auf `GhostTalkDialog` | klein | offen |
| 4 | Restliche hartcodierte UI-Strings → Resources | mittel | offen |
| 5 | Speichern/Abbrechen-Balken hinter Navigationsleiste (Profil bearbeiten) | klein | offen |
| 6 | Windowed/Freeform offiziell unterstützen (resizeableActivity + Mindestgröße) | klein | offen |
| R | Abschluss-Review (Claude) — **nur Teil A** | — | offen |
| B1 | Geteilte Editor-Top-Bar extrahieren | klein | offen |
| B2 | Geteilte visuelle Token für Seite/Button (Raster/Chip/Graph) | mittel | offen |
| B3 | Modus-Container + Route-Konsolidierung (`editor/{pageId}?mode=`) | **hoch** | offen |
| B4 | Baum-Navigator als geteiltes Element in beiden Modi | mittel | offen |
| B5 | Übergänge/Terminologie entschlacken + Undo-Konsistenz | klein | offen |

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

## 5. Review-Notizen (Claude — wird während des Reviews gefüllt)

_(leer bis zur Umsetzung)_
