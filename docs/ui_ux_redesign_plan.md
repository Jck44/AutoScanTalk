# GhosTTalk – UI/UX-Vereinheitlichung & Design-Facelift

**Zielgruppe dieses Dokuments:** Gemini (Android Studio / CLI) als ausführender Agent.
**Regeln für die Umsetzung stehen in Abschnitt 0 — zuerst lesen.**

---

## 0. Regeln für Gemini (verbindlich)

1. **Ein Arbeitspaket = ein Commit.** Arbeitspakete (AP) in der Reihenfolge 1 → 8 umsetzen, nicht mischen.
2. **Nur UI-Schicht anfassen.** Keine Änderungen an ViewModels, Delegates, Repositories, Datenmodellen oder der Scanning-Logik (`core-scanning`). Signaturen von ViewModel-Funktionen bleiben unverändert.
3. **Nach jedem AP bauen:** `./gradlew assembleDebug` muss erfolgreich sein. `JAVA_HOME` auf das JBR von Android Studio setzen (kein System-Java).
4. **Bestehende `testTag`-Modifier niemals entfernen oder umbenennen** – die UI-Tests (`app/src/androidTest`) hängen daran.
5. **Keine neuen Bibliotheken.** Alles geht mit Material 3 + Compose, die schon eingebunden sind.
6. **Barrierefreiheit hat Vorrang vor Optik.** GhosTTalk ist eine AAC-App (Unterstützte Kommunikation) für motorisch eingeschränkte Nutzer mit Scanning-Steuerung. Touchziele nie unter 48 dp, Kontraste mindestens WCAG AA, `contentDescription` für jedes Icon mit Funktion.
7. Im Zweifel: bestehendes Muster aus `core-ui` kopieren statt neu erfinden.

---

## 1. Ist-Zustand (Analyse vom 2026-06-11)

### Was schon gut ist (beibehalten!)
- Material-3-Theme mit Indigo/Sapphire-Palette inkl. Surface-Container-Stufen ([Color.kt](../core-ui/src/main/java/com/andreas_kratzer/ghosttalk/core/ui/theme/Color.kt))
- Google-Font **Outfit** als durchgängige Typografie ([Type.kt](../core-ui/src/main/java/com/andreas_kratzer/ghosttalk/core/ui/theme/Type.kt))
- Expressive Shapes (large = 28 dp Radius) ([Shape.kt](../core-ui/src/main/java/com/andreas_kratzer/ghosttalk/core/ui/theme/Shape.kt))
- `Dimensions` als CompositionLocal ([Dimens.kt](../core-ui/src/main/java/com/andreas_kratzer/ghosttalk/core/ui/theme/Dimens.kt))
- Wiederverwendbare `GhostTalkCard` und `AppBrandHeader` ([GhostTalkComponents.kt](../core-ui/src/main/java/com/andreas_kratzer/ghosttalk/core/ui/components/GhostTalkComponents.kt))
- Semantische Badge-/Status-Farben im Theme

### Gemessene Inkonsistenzen
| # | Problem | Umfang |
|---|---------|--------|
| 1 | Zwei TopAppBar-Varianten: `CenterAlignedTopAppBar` (StartScreen) vs. `TopAppBar` (alle anderen); SettingsScreen färbt die Bar zusätzlich eigenständig mit `secondaryContainer` | 11 Screens |
| 2 | Hartcodierte deutsche Strings statt `stringResource` (z. B. `"Seite wird verwendet"`, `"Änderungen verwerfen?"`, `"Raster: …"`) – bricht die vorhandene EN-Lokalisierung | ~158 Stellen |
| 3 | Rohe `AlertDialog`-Aufrufe ohne gemeinsamen Stil (Button-Reihenfolge, Icons, Form variieren) | 33 Dateien |
| 4 | dp-Literale statt `LocalDimensions` | ~660 Stellen |
| 5 | Uneinheitliches Screen-Padding: horizontal 2/3/4/6/8/10/16 dp gemischt | app-weit |
| 6 | Hartcodierte Hex-Farben außerhalb des Themes | `ButtonStatisticsTabContent.kt` (6), `CallScreenOverlay.kt` (4), `GridButton.kt` (3) |
| 7 | Farbsemantik verletzt: StartScreen nutzt `errorContainer` (rot) für die Analytics-Karte → wirkt wie ein Fehlerzustand | StartScreen |
| 8 | `FloatingActionButton` ohne Label, je Screen anders positioniert/geformt | 3 Screens |

---

## 2. Design-Leitlinien (Soll-Zustand)

**Designsprache: „Calm Expressive“** – ruhige, große, klar unterscheidbare Flächen. Für eine AAC-App gilt: wenige, große Ziele; Farbe trägt Bedeutung, nie Dekoration.

1. **Tonale Hierarchie statt Schatten:**
   `background` → Listen/Karten auf `surfaceContainerLow` → hervorgehobene/aktive Elemente auf `primaryContainer`. Elevation maximal 2 dp (bestehender Wert `cardElevation`).
2. **Farbsemantik (verbindlich):**
   - `primary` = Hauptaktion / Sprechen / Nutzermodus
   - `tertiary` = Verwaltung & Bearbeitung (Inhalte, Vorlagen, Bücher)
   - `secondary` = neutrale Informationen (Analytics, Statistik)
   - `error` = ausschließlich destruktive Aktionen und Fehlerzustände
3. **Eine TopAppBar-Form app-weit:** `CenterAlignedTopAppBar`, Hintergrund `surface`, Titel `titleLarge`, Zurück-Pfeil links, max. 2 Action-Icons rechts.
4. **Ein Screen-Raster:** Content-Padding horizontal 16 dp (`paddingLarge`), vertikaler Abstand zwischen Sektionen 24 dp (`paddingExtraLarge`), zwischen Listenelementen 8 dp (`paddingMedium`).
5. **Dialoge einheitlich:** Icon oben (optional), Titel `titleLarge`, destruktiver Button immer in `error`-Farbe, Bestätigen rechts, Abbrechen links.
6. **FABs:** immer `ExtendedFloatingActionButton` mit Icon + Text (bessere Verständlichkeit), Farbe `primaryContainer`.
7. **Leere Zustände:** nie ein leerer Bildschirm – immer Icon + Erklärtext + Primäraktion (`GhostTalkEmptyState`, siehe AP 4).

---

## 3. Arbeitspakete

### AP 1 – Design-Tokens erweitern
**Datei:** `core-ui/.../theme/Dimens.kt`

Der `Dimensions`-Datenklasse folgende Felder hinzufügen (bestehende nicht ändern):

```kotlin
val screenPaddingHorizontal: Dp = 16.dp,
val screenPaddingVertical: Dp = 16.dp,
val sectionSpacing: Dp = 24.dp,
val listItemSpacing: Dp = 8.dp,
val minTouchTarget: Dp = 48.dp,
val dialogCornerRadius: Dp = 28.dp,
```

**Fertig wenn:** Build grün.

### AP 2 – `GhostTalkScaffold` + einheitliche TopAppBar
**Neue Datei:** `core-ui/.../components/GhostTalkScaffold.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GhostTalkScaffold(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        content = content
    )
}
```

Dazu in `core/src/main/res/values/strings.xml` + `values-en/strings.xml`: `common_back` („Zurück" / "Back") anlegen, falls nicht vorhanden.

**Migration:** Alle Screens mit eigenem `Scaffold`+`TopAppBar` auf `GhostTalkScaffold` umstellen:
`StartScreen`, `PageListScreen`, `BookListScreen`, `TemplateScreen`, `TemplateEditorScreen`, `PageEditorScreen`, `AnalyticsDashboardScreen`, `SettingsScreen`, `ContentManagementScreen`, `VocalTrainingScreen`.
**Ausnahme:** `PageScreen` (Nutzermodus) behält seine Spezialstruktur – dort nur die TopAppBar-Farben angleichen.
Die Sonderfärbung der Settings-TopAppBar (`secondaryContainer`) ersatzlos entfernen.

**Fertig wenn:** Build grün, `NavigationIntegrationTest` grün, kein direkter `TopAppBar(`-Aufruf mehr außerhalb von `core-ui` und `PageScreen`.

### AP 3 – `GhostTalkDialog` als einheitlicher Dialograhmen
**Neue Datei:** `core-ui/.../components/GhostTalkDialog.kt`

```kotlin
@Composable
fun GhostTalkDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dismissText: String? = null,          // null = kein Abbrechen-Button
    isDestructive: Boolean = false,       // färbt Confirm-Button error
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Column { content() } },
        confirmButton = {
            if (isDestructive) {
                TextButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(confirmText) }
            } else {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText) }
            }
        },
        dismissButton = dismissText?.let {
            { TextButton(onClick = onDismiss) { Text(it) } }
        },
        shape = RoundedCornerShape(LocalDimensions.current.dialogCornerRadius)
    )
}
```

**Migration:** Alle Bestätigungs-/Lösch-Dialoge (Löschen von Seite/Buch/Vorlage, „Änderungen verwerfen?", Deaktivieren-Dialoge) auf `GhostTalkDialog` mit `isDestructive = true` umstellen. Komplexe Formular-Dialoge (`ButtonConfigDialog`, `RowEditDialog`, Setup-Dialoge) **nicht** umbauen, nur deren Buttons an die Reihenfolge Abbrechen-links/Bestätigen-rechts angleichen.

**Fertig wenn:** Build grün, alle Lösch-Dialoge nutzen den error-farbenen Confirm-Button.

### AP 4 – `GhostTalkEmptyState` + `SectionHeader`
**Neue Datei:** `core-ui/.../components/GhostTalkEmptyState.kt`

```kotlin
@Composable
fun GhostTalkEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxSize().padding(LocalDimensions.current.paddingDoubleExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon, contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            top = LocalDimensions.current.sectionSpacing,
            bottom = LocalDimensions.current.paddingMedium
        )
    )
}
```

**Einsetzen in:** Buchliste (keine Bücher), Seitenliste (keine Seiten), Vorlagenliste, Analytics ohne Daten. `SectionHeader` in `SettingsScreen` und `ContentManagementScreen` für Gruppentitel verwenden.

**Fertig wenn:** Build grün; jede der vier Listen zeigt bei leerem Zustand Icon + Text + Aktion.

### AP 5 – Hartcodierte Strings nach `strings.xml`
Alle deutschen String-Literale in Composables (`Text("…")`, `title = { Text("…") }`, Snackbar-Texte) in die jeweilige `strings.xml` (Modul `app` bzw. `feature-settings` bzw. `core`) auslagern **und englische Übersetzung in `values-en/strings.xml` ergänzen**.

Bekannte Fundstellen (nicht abschließend, per Suche `Text("` verifizieren):
- `PageListScreen.kt`: „Seite wird verwendet"
- `TemplateScreen.kt`: „Vorlage wird verwendet", „Raster: …"
- `SettingsScreen.kt`: „Änderungen verwerfen?"

Namenskonvention beibehalten: `<screen>_<zweck>` z. B. `page_in_use_title`.

**Fertig wenn:** `grep -rn 'Text("' app/src/main feature-settings/src/main` keine deutschen UI-Literale mehr findet (Ausnahmen: reine Format-/Debug-Strings) und Build grün.

### AP 6 – Hartcodierte Farben ins Theme
Betroffen: `ButtonStatisticsTabContent.kt`, `CallScreenOverlay.kt`, `GridButton.kt`.

Vorgehen: Jede `Color(0x…)`-Konstante prüfen –
1. Gibt es ein passendes `MaterialTheme.colorScheme`-Token oder eine vorhandene semantische Farbe in `Color.kt`? → verwenden.
2. Sonst: neue semantische Farbe **mit Light- und Dark-Variante** in `Color.kt` anlegen (Muster: `StatusActiveBgLight/Dark`) und über die bestehende Theme-Auflösung beziehen.

`CallScreenOverlay` (Anruf-UI): Grün für Annehmen / Rot für Auflegen sind semantisch korrekt – als `CallAcceptLight/Dark`, `CallDeclineLight/Dark` ins Theme heben, nicht durch Theme-Töne ersetzen.

**Fertig wenn:** `grep -rn "Color(0x" --include="*.kt" app/src/main | grep -v theme` leer ist, Build grün, Dark Mode geprüft (keine unlesbaren Kombinationen).

### AP 7 – Screen-Padding vereinheitlichen
In allen Screen-Level-Containern (direktes Kind des Scaffold-Contents) das horizontale Padding auf `LocalDimensions.current.screenPaddingHorizontal` setzen, Sektionsabstände auf `sectionSpacing`, Listenabstände auf `listItemSpacing`.

**Bewusst pragmatisch:** Nur Screen-/Sektions-Ebene umstellen. Feinabstände innerhalb von Komponenten (z. B. 4 dp zwischen Icon und Text) dürfen Literale bleiben – kein Massenersatz aller 660 dp-Stellen.

**Fertig wenn:** Die 10 Hauptscreens (Liste aus AP 2) links/rechts bündig wirken; Build grün.

### AP 8 – StartScreen-Facelift + FABs
1. **StartScreen:** Analytics-Karte von `errorContainer`/`error` auf `secondaryContainer`/`secondary` umstellen (rot ist Fehlern vorbehalten). Karten-Reihenfolge und Tags unverändert lassen.
2. **FABs:** In `PageListScreen`, `BookListScreen`, `TemplateScreen` den `FloatingActionButton` durch `ExtendedFloatingActionButton` mit Icon `Icons.Filled.Add` + Label („Neue Seite" / „Neues Buch" / „Neue Vorlage", als String-Ressourcen de+en) ersetzen. `containerColor = MaterialTheme.colorScheme.primaryContainer`.

**Fertig wenn:** Build grün, `NavigationIntegrationTest` grün.

---

## 4. Abschlussverifikation (nach AP 8)

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest   # falls Gerät/Emulator verfügbar
```

Manuelle Sichtprüfung (Light + Dark):
- [ ] Alle TopAppBars gleich aufgebaut (zentrierter Titel, surface-Hintergrund)
- [ ] Lösch-Dialoge: roter Bestätigen-Button rechts, Abbrechen links
- [ ] Leere Listen zeigen EmptyState
- [ ] Sprachumschaltung auf Englisch: keine deutschen Resttexte
- [ ] Nutzermodus (`PageScreen`): Scanning-Hervorhebung unverändert funktionsfähig
