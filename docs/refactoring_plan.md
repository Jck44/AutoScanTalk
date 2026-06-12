# Refactoring-Plan: OOP- und SOLID-Bereinigung

Dieser Schritt-für-Schritt-Plan beschreibt, wie die identifizierten Entwurfsfehler (OOP- und SOLID-Prinzipien) in der App schrittweise behoben werden. 

Jeder Schritt ist so konzipiert, dass die App danach vollständig kompilierbar, funktionsfähig und durch automatisierte Tests abzusichern ist. Die Reihenfolge ist nach dem geschäftlichen Mehrwert für zukünftige Feature-Entwicklungen priorisiert.

**Arbeitsmodus:** Claude reviewt und plant, Gemini setzt um. Nach jeder Phase: kompilieren, Tests, Review durch Claude, Commit.

## Status (Stand 2026-06-12)

| Phase | Thema | Stand |
|---|---|---|
| 1 | ScanContext / Strategy-Pattern | ✅ umgesetzt |
| 2 | FallbackTtsProvider (Decorator) | ✅ umgesetzt |
| 3 | CacheableTtsProvider (ISP) | ✅ umgesetzt |
| 4.1 | SettingsMigrationManager | ✅ umgesetzt |
| 4.2 | Direktbinding Sub-Interfaces | ✅ umgesetzt + reviewt (inkl. Must-Fix Book-Sync), abnahmereif |
| 5 | Konstruktor-Injektion Sub-Repos | ✅ umgesetzt |
| 6 | PageViewModel-Aufteilung | ✅ umgesetzt (Call-/GridEditor-/PageSplit-/BookRestructure-VM) |
| 7 | Law of Demeter | ✅ umgesetzt (Delegates `private`/`internal`) |
| 8 | Button-Konfig-UI | ✅ umgesetzt + reviewt, Must-Fixes behoben, abnahmereif |
| 8B | Gemini Nano entfernen (inkl. lokaler Vision) | 📋 Detailplan fertig — nach Phase-8-Commit |
| 9 | Analytics-Tabs | 📋 Detailplan fertig |
| 10–12 | MainActivity, SystemCallManager, Rest | 🆕 siehe Ende des Dokuments |

---

## Phase 1: Core Scanning Engine (Strategy Pattern & OCP)
* **Ziel**: Entfernen von totem Code und hardcodierten Scanning-Mustern; dynamische Nutzung von Strategien.
* **Mehrwert**: Ermöglicht zukünftig einfaches Hinzufügen neuer Scan-Muster (z. B. Blicksteuerung oder Custom-Patterns) ohne Änderung der Engine.

### Schritt 1.1: Einführung von `ScanContext`
* **Beschreibung**: Kapseln Sie die 14 Parameter der Schnittstelle `ScanStrategy.executeScan` in einer neuen Klasse `ScanContext`.
* **Dateien**:
  * [ScanStrategy.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-scanning/src/main/java/com/andreas_kratzer/ghosttalk/core/scanning/ScanStrategy.kt)
* **Änderung**: 
  ```kotlin
  data class ScanContext(
      val scope: CoroutineScope,
      val buttonConfigs: List<ButtonConfig?>,
      val rows: Int,
      val columns: Int,
      val rowNames: List<String>,
      val startIndex: Int,
      val focusedButtonIndex: MutableStateFlow<Int?>,
      val focusedRowIndex: MutableStateFlow<Int?>,
      val onSpeakCue: suspend (String) -> Unit,
      val onPrefetchCue: suspend (String) -> Unit,
      val onCycleCompleted: suspend () -> Unit,
      val delayMillis: Long,
      val featureGuard: FeatureGuardProxy
  )
  
  interface ScanStrategy {
      suspend fun executeScan(context: ScanContext)
  }
  ```
* **Verifikation**: `./gradlew :core-scanning:compileDebugKotlin` ausführen.

### Schritt 1.2: Strategie-Klassen aktualisieren
* **Beschreibung**: Passen Sie `LinearScanStrategy` und `RowByRowScanStrategy` an die neue Schnittstelle an.
* **Dateien**:
  * [LinearScanStrategy.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-scanning/src/main/java/com/andreas_kratzer/ghosttalk/core/scanning/LinearScanStrategy.kt)
  * [RowByRowScanStrategy.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-scanning/src/main/java/com/andreas_kratzer/ghosttalk/core/scanning/RowByRowScanStrategy.kt)
* **Verifikation**: Führen Sie `./gradlew :core-scanning:test` aus.

### Schritt 1.3: Integration der Strategien in die Engine
* **Beschreibung**: Injizieren Sie `LinearScanStrategy` und `RowByRowScanStrategy` in die `ScannerEngine` und ersetzen Sie die Verzweigungen durch den Aufruf der Strategien.
* **Dateien**:
  * [ScannerEngine.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-scanning/src/main/java/com/andreas_kratzer/ghosttalk/core/scanning/ScannerEngine.kt)
* **Änderung**: Ersetzen Sie das inline-Scanning in `startScanning` durch:
  ```kotlin
  val strategy = if (pattern == "row_by_row") rowByRowStrategy else linearStrategy
  strategy.executeScan(context)
  ```
* **Verifikation**: `./gradlew :core-scanning:test` ausführen.

---

## Phase 2: TextToSpeechHelper (Polymorphismus & LSP)
* **Ziel**: Entfernen von konkreten Typprüfungen (`is AndroidTtsProvider`) für Fallback-Logik.
* **Mehrwert**: Fehlerfreie Audioausgabe bei Ausfall der Internetverbindung (nahtloses Umschalten von ElevenLabs auf Google-TTS).

### Schritt 2.1: Kapselung der Sprechprüfung
* **Beschreibung**: Rufen Sie in `TextToSpeechHelper.isSpeaking()` direkt `currentProvider.isSpeaking()` auf, anstatt auf `AndroidTtsProvider` zu casten.
* **Dateien**:
  * [TextToSpeechHelper.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-tts/src/main/java/com/andreas_kratzer/ghosttalk/core/tts/TextToSpeechHelper.kt)
* **Verifikation**: `./gradlew :core-tts:test` ausführen.

### Schritt 2.2: Implementierung des `FallbackTtsProvider` (Decorator-Pattern)
* **Beschreibung**: Erstellen Sie eine Klasse `FallbackTtsProvider`, die zwei `TtsProvider` kapselt. Fällt der primäre Cloud-Provider mit einem Fehler aus, ruft der Decorator automatisch den lokalen Provider auf.
* **Dateien**:
  * [NEW] `FallbackTtsProvider.kt` im Modul `core-tts`.
  * [TextToSpeechHelper.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-tts/src/main/java/com/andreas_kratzer/ghosttalk/core/tts/TextToSpeechHelper.kt)
* **Änderung**: Entfernen Sie die `is ElevenLabsTtsProvider` Prüfungen aus `speak()` und `speakRouted()`. Nutzen Sie stattdessen den `FallbackTtsProvider`.
* **Verifikation**: `./gradlew :core-tts:test` ausführen.

---

## Phase 3: TTS Provider Interface Segregation (ISP/LSP)
* **Ziel**: Schnittstelle `TtsProvider` von caching-spezifischen Methoden bereinigen.
* **Mehrwert**: Klarere Schnittstellen-Definitionen im Audio-Modul.

### Schritt 3.1: Erstellung von `CacheableTtsProvider`
* **Beschreibung**: Erstellen Sie ein neues Interface `CacheableTtsProvider`, das von `TtsProvider` erbt und `prefetch` sowie `isCached` deklariert.
* **Dateien**:
  * [TtsProvider.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-tts/src/main/java/com/andreas_kratzer/ghosttalk/core/tts/TtsProvider.kt)
* **Änderung**: Entfernen Sie `prefetch` und `isCached` aus `TtsProvider`.
  ```kotlin
  interface CacheableTtsProvider : TtsProvider {
      suspend fun prefetch(text: String)
      fun isCached(text: String): Boolean
  }
  ```

### Schritt 3.2: Implementierungen anpassen
* **Beschreibung**: 
  * Ändern Sie `ElevenLabsTtsProvider` so, dass es `CacheableTtsProvider` implementiert.
  * Entfernen Sie die leeren Dummy-Methoden `prefetch` und `isCached` aus `AndroidTtsProvider`.
* **Dateien**:
  * [ElevenLabsTtsProvider.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-tts/src/main/java/com/andreas_kratzer/ghosttalk/core/tts/ElevenLabsTtsProvider.kt)
  * [AndroidTtsProvider.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-tts/src/main/java/com/andreas_kratzer/ghosttalk/core/tts/AndroidTtsProvider.kt)
* **Verifikation**: `./gradlew :core-tts:test` ausführen.

### Schritt 3.3: Sichere Abfrage im Helper
* **Beschreibung**: Passen Sie Aufrufe von `prefetch` und `isCached` im `TextToSpeechHelper` an, indem Sie einen sicheren Cast auf `CacheableTtsProvider` nutzen.
* **Dateien**:
  * [TextToSpeechHelper.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-tts/src/main/java/com/andreas_kratzer/ghosttalk/core/tts/TextToSpeechHelper.kt)
* **Änderung**:
  ```kotlin
  suspend fun prefetch(text: String) {
      (currentProvider as? CacheableTtsProvider)?.prefetch(text)
  }
  ```
* **Verifikation**: `./gradlew :core-tts:test` ausführen.

---

## Phase 4: Zerschlagung des SettingsRepository (ISP/SRP)
* **Ziel**: Brechen des monolithischen Interfaces `SettingsRepository` und Auflösen der God-Class `SettingsRepositoryImpl`.
* **Mehrwert**: Vermeidung von Merge-Konflikten bei neuen Einstellungen; schnellere Kompilierung einzelner Module.

### Schritt 4.1: Extraktion der Migrationslogik
* **Beschreibung**: Erstellen Sie einen `SettingsMigrationManager` und verlagern Sie die Initialisierungs- und Migrationslogik aus dem `init`-Block von `SettingsRepositoryImpl` dorthin.
* **Dateien**:
  * [NEW] `SettingsMigrationManager.kt` im Modul `core-data`.
  * [SettingsRepositoryImpl.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-data/src/main/java/com/andreas_kratzer/ghosttalk/core/data/impl/settings/SettingsRepositoryImpl.kt)
* **Verifikation**: `./gradlew :core-data:test` ausführen.

### Schritt 4.2: Direktes Binding der Sub-Schnittstellen — Detailplan (Rev. 2, 2026-06-12)

**Ziel**: Die Sub-Interfaces (`TtsSettings`, `ScanningSettings`, …) werden in Hilt direkt an die Sub-Repositories gebunden statt an die 1258-Zeilen-Fassade `SettingsRepositoryImpl`. Die Fassade bleibt als `SettingsRepository` für Bestandscode erhalten, schrumpft aber per Kotlin-Interface-Delegation (`by`).

**Ist-Zustand (analysiert)**:
* Nur 3 von 11 Sub-Repos implementieren ihr öffentliches Interface: `AdvancedSettingsRepository`, `CloudSettingsRepository`, `SmartHomeSettingsRepository`. Die übrigen 8 haben die Properties zwar inhaltlich, deklarieren das Interface aber nicht.
* Die Fassade enthält ~265 reine Delegations-Einzeiler plus Eigenlogik (Profil-Write-Back-Listener, `refreshFlows()`, Migration-Kickoff im `init`, AudioDevice-Cache).
* In `DataModule.kt` (Zeilen 146–196) zeigen alle 14 `provideXxxSettings` auf `SettingsRepositoryImpl`.

#### ⚠️ Kritische Falle: Singleton-Scope
**Kein einziges Sub-Repo ist `@Singleton`-annotiert.** Das funktioniert heute nur, weil ausschließlich die (Singleton-)Fassade sie injiziert. Sobald `TtsSettings` direkt an `VoiceSettingsRepository` gebunden wird, bekämen Konsumenten **eine zweite Instanz mit eigenen StateFlows** — Settings-Änderungen kämen dann nicht mehr überall an. **Schritt 4.2.1 ist daher zwingend zuerst.**

#### Interface-Mapping

| Interface (Modul `core`) | Ziel-Implementierung | Aufwand |
|---|---|---|
| `TtsSettings` | `VoiceSettingsRepository` | Interface deklarieren + Overrides aus Fassade verschieben |
| `ScanningSettings` | `ScanningSettingsRepository` | dito |
| `SecuritySettings` | `SecuritySettingsRepository` | dito |
| `GenAiSettings` | `GenAiSettingsRepository` | dito |
| `GeneralSettings` | `GeneralSettingsRepository` | dito |
| `NotificationSettings` | `NotificationSettingsRepository` | dito |
| `UserSettings` | `UserSettingsRepository` | dito |
| `CallSettings` | `CallSettingsRepository` | dito |
| `CloudSettings` | `CloudSettingsRepository` | ✅ implementiert schon — nur Binding ändern |
| `SmartHomeSettings` | `SmartHomeSettingsRepository` | ✅ dito |
| `AdvancedSettings` | `AdvancedSettingsRepository` | ✅ dito |
| `DatabaseSettings`, `FeatureSettings`, `ImportExportSettings`, `KeyEventSettings`, `AudioSettings`, `SpeechSettings`, `ControlDeviceSettings` | **bleiben an der Fassade** (kein eigenes Sub-Repo; AudioDevice-Cache & Co. liegen direkt in der Fassade) | keiner |

#### Schritt 4.2.1: Sub-Repos als Singleton scopen
* Alle 11 Sub-Repos in `core-data/.../impl/settings/` mit `@Singleton` annotieren (`javax.inject.Singleton`).
* `ActiveBookIdManager` ist bereits `@Singleton` — nicht anfassen.
* **Verifikation**: `./gradlew :core-data:compileDebugKotlin` und `./gradlew assembleDebug`.

#### Schritt 4.2.2: Interfaces in Sub-Repos deklarieren (pro Repo ein Mini-Schritt)
Für jedes der 8 Repos ohne Interface-Deklaration, eines nach dem anderen:
1. Interface zur Klassen-Deklaration hinzufügen, z. B. `class VoiceSettingsRepository … : BaseSettingsRepository(…), TtsSettings`.
2. Die zugehörigen Member aus der Fassade ins Sub-Repo verschieben bzw. dort als `override` markieren. **Regel**: Ist die Fassaden-Implementierung reine Delegation (`override var x get() = voiceSettings.x …`), entfällt sie später per `by`-Delegation (4.2.4). Enthält sie **Zusatzlogik** (Seiteneffekte, Mapping), muss diese Logik mit ins Sub-Repo wandern — nichts stillschweigend weglassen.
3. Kompilieren: `./gradlew :core-data:compileDebugKotlin`.

Empfohlene Reihenfolge (steigende Verflechtung): `UserSettings` → `NotificationSettings` → `CallSettings` → `GenAiSettings` → `SecuritySettings` → `ScanningSettings` → `TtsSettings` (`VoiceSettingsRepository`) → `GeneralSettings` (Achtung: `activeProfileId` wird vom Migration-Kickoff in der Fassade gelesen).

#### Schritt 4.2.3: Bindings in DataModule umstellen
* Die 11 `provideXxxSettings(impl: SettingsRepositoryImpl)`-Methoden für die gemappten Interfaces durch Bindings auf die Sub-Repos ersetzen, z. B.:
  ```kotlin
  @Provides @Singleton
  fun provideTtsSettings(impl: VoiceSettingsRepository): TtsSettings = impl
  ```
* Die 7 Fassaden-Interfaces (`DatabaseSettings`, `FeatureSettings`, `ImportExportSettings`, `KeyEventSettings`, `AudioSettings`, `SpeechSettings`, `ControlDeviceSettings`) bleiben unverändert auf `SettingsRepositoryImpl`.
* **Verifikation**: `./gradlew assembleDebug` (Hilt-Graph), danach `./gradlew test`.

#### Schritt 4.2.4: Fassade per Interface-Delegation schrumpfen
* In `SettingsRepositoryImpl` die ~265 Delegations-Einzeiler durch Kotlin-`by`-Delegation ersetzen:
  ```kotlin
  class SettingsRepositoryImpl @Inject constructor(…) :
      SettingsRepository,
      TtsSettings by voiceSettings,
      ScanningSettings by scanningSettings,
      // … usw. für alle 11 gemappten Interfaces
  ```
  Hinweis: Damit `by` auf Konstruktorparameter zugreifen kann, müssen diese als `val` deklariert sein (sind sie bereits, `private val` reicht).
* **Nicht verschieben**: `init`-Block (Migration, Prefs-Listener mit Profil-Write-Back), `refresh()`/`refreshFlows()`, `resetToDefaults()`, AudioDevice-Cache, alle `…ForBook(bookId)`-Methoden, Profile-Management. Das bleibt Fassaden-Verantwortung (bzw. ist Kandidat für eine spätere Phase).
* **Erwartung**: Fassade schrumpft von ~1258 auf grob 400–500 Zeilen.
* **Verifikation**: `./gradlew test` (gesamtes Projekt).

#### Schritt 4.2.5: Abnahme-Smoke-Test (manuell)
1. App starten, TTS-Stimme in den Einstellungen ändern → Änderung greift sofort (StateFlow-Propagation über Modul-Grenzen, z. B. `core-tts`).
2. Profil wechseln → Settings-Werte wechseln mit.
3. Settings-Import ausführen → `refresh()` aktualisiert alle Flows.
4. Buch wechseln → buchspezifische Scan-Einstellungen greifen.

#### Wichtige Invarianten (für Review)
* Die Fassade bleibt als `@Singleton` an `SettingsRepository` gebunden und wird weiterhin früh instanziiert (u. a. von `GhosTTalkApplication`) — sonst laufen Migration und Profil-Write-Back-Listener nie an.
* Es darf **keine zweite Instanz** eines Sub-Repos entstehen (Hilt-Graph nach 4.2.3 prüfen: ein Knoten pro Repo).
* Verhalten bleibt identisch; das Refactoring ist rein strukturell.

#### Review-Befund 4.2 (Claude, 2026-06-12) — 1 Must-Fix vor Commit

Umsetzung weitgehend sauber: alle 11 Sub-Repos `@Singleton` ✅, Interfaces deklariert ✅, 11 Direktbindings in `DataModule` ✅ (inkl. 5 vorher gar nicht gebundener Interfaces), `by`-Delegation ✅, PIN-Logik korrekt in `SecuritySettingsRepository` umgezogen ✅, Listener/Migration/`refreshFlows()` intakt ✅, Tests grün ✅. Interface-Überlappungen (`appLanguage` in `TtsSettings`, `cuesAudioDeviceAddress` in `ScanningSettings`, `isSmartPredictionEnabled` in `GenAiSettings`) wurden per `Provider`-Querdelegation gelöst — vertretbar, die ISP-Bereinigung der Interfaces selbst ist Folgearbeit.

**🐛 Must-Fix: `syncBookSettings()` ist toter Code geworden.**
* **Vorher**: Die Fassaden-Setter für `limitScanCycles`, `scanCycleLimit`, `actionLogLimit`, `logIgnoredActions`, `logStopActions` riefen zusätzlich `syncBookSettings()` auf, das diese 5 Werte in die `Book`-Entität der DB spiegelt.
* **Jetzt**: Durch die `by`-Delegation gehen die Setter direkt ins Sub-Repo; `syncBookSettings()` (Fassade, ~Zeile 441) hat **keinen Aufrufer mehr**. Die Book-Spiegelfelder veralten.
* **Auswirkung (tragend!)**: `PageViewModel` liest die Scan-Limits aus dem Book (`scanCoordinator.setScanLimitSettings(book.limitScanCycles, …)`), `ActivateButtonUseCase`/`NavigationDelegate`/`InteractionDelegate` lesen die Log-Flags bzw. das Log-Limit aus dem Book, `BookMergeEngine` (Cloud) merged diese Felder.
* **Fix (im Fassaden-`init`-Listener, NICHT per Setter-Override)**: Der `changeListener` der Sub-Repos wird bei jedem Schreibpfad aufgerufen (auch bei direkt gebundenen Sub-Repos) und erhält den ungescopten Basis-Key. In der Listener-Lambda der Fassade ergänzen:
  ```kotlin
  val bookMirroredKeys = setOf(
      SettingsConstants.KEY_LIMIT_SCAN_CYCLES,
      SettingsConstants.KEY_SCAN_CYCLE_LIMIT,
      SettingsConstants.KEY_ACTION_LOG_LIMIT,
      SettingsConstants.KEY_LOG_IGNORED_ACTIONS,
      SettingsConstants.KEY_LOG_STOP_ACTIONS
  )
  // im listener:
  if (key in bookMirroredKeys) syncBookSettings()
  ```
  Ein Setter-Override in der Fassade wäre falsch, weil direkt injizierte `ScanningSettings`/`AdvancedSettings`-Konsumenten die Fassade umgehen.
* **Test dazu**: Unit-Test, der über das direkt gebundene Sub-Repo (nicht die Fassade) `limitScanCycles` setzt und prüft, dass `bookRepository.updateBook` mit dem gespiegelten Wert aufgerufen wird.

**Kleinkram (optional, kein Blocker)**:
* Doppelte `activeBookIdFlow.collect { refreshFlows() }`-Collection im `init` (Zeile ~80 und ~223) — war schon vor 4.2 so, eine reicht.
* `elevenLabsTtsLanguage`/`elevenLabsModel` & Co. sind doppelt deklariert (`TtsSettings` **und** `CloudSettings`, gleicher Pref-Key, zwei StateFlows). Der Dual-Write in der Fassade hält die Flows synchron; richtige Lösung (Deklaration nur in `TtsSettings`) gehört zur ISP-Folgearbeit.

---

## Phase 5: Dependency Inversion für Settings-Sub-Repositories (DIP)
* **Ziel**: Konstruktor-Injektion für Sub-Repositories anstelle von Direktschreibung.
* **Mehrwert**: Sauberes Testen und Mocken der einzelnen Einstellungs-Subsysteme.

### Schritt 5.1: `@Inject` für Sub-Repositories
* **Beschreibung**: Versehen Sie Klassen wie `VoiceSettingsRepository`, `ScanningSettingsRepository` etc. mit `@Inject constructor()`.
* **Dateien**:
  * [VoiceSettingsRepository.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-data/src/main/java/com/andreas_kratzer/ghosttalk/core/data/impl/settings/VoiceSettingsRepository.kt) (und alle weiteren 10 Sub-Repos).
* **Verifikation**: `./gradlew :core-data:compileDebugKotlin` ausführen.

### Schritt 5.2: Injektion in `SettingsRepositoryImpl`
* **Beschreibung**: Injizieren Sie die 11 Sub-Repositories über den Konstruktor in `SettingsRepositoryImpl`, anstatt sie intern per Konstruktoraufruf zu instanziieren.
* **Dateien**:
  * [SettingsRepositoryImpl.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-data/src/main/java/com/andreas_kratzer/ghosttalk/core/data/impl/settings/SettingsRepositoryImpl.kt)
* **Verifikation**: `./gradlew :core-data:test` ausführen.

---

## Phase 6: Aufteilung des PageViewModels (SRP / MVVM)
* **Ziel**: Zerschlagung der God-Class `PageViewModel` in fokussierte Feature-ViewModels.
* **Mehrwert**: Bessere Testbarkeit der Benutzeroberfläche und Einhaltung von MVVM.

### Schritt 6.1: Extraktion von `CallViewModel`
* **Beschreibung**: Erstellen Sie ein eigenständiges `CallViewModel` für die Telefonie-Steuerung. Migrieren Sie die Zustände und Logik von `CallManagementDelegate` in dieses ViewModel.
* **Dateien**:
  * [NEW] `CallViewModel.kt` im Modul `app`.
  * [PageViewModel.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/app/src/main/java/com/andreas_kratzer/ghosttalk/ui/pages/PageViewModel.kt)
* **Verifikation**: Passen Sie die Compose-Injektion in `MainActivity.kt` an. `./gradlew :app:testDebugUnitTest` ausführen.

### Schritt 6.2: Extraktion von `GridEditorViewModel`
* **Beschreibung**: Erstellen Sie ein `GridEditorViewModel` zur Verwaltung von Button-Konfigurationen, Vorlagen (`ButtonTemplateDelegate`) und Undo/Redo.
* **Dateien**:
  * [NEW] `GridEditorViewModel.kt` im Modul `app`.
  * [PageViewModel.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/app/src/main/java/com/andreas_kratzer/ghosttalk/ui/pages/PageViewModel.kt)
* **Verifikation**: Führen Sie die Tests im App-Modul aus.

### Schritt 6.3: Weitere Extraktionen
* **Beschreibung**: Extrahieren Sie sukzessive:
  * `PageSplitViewModel` (für den Split-Wizard).
  * `BookRestructureViewModel` (für KI-Restrukturierungen).
* **Verifikation**: Nach jeder Extraktion App-Modul testen und kompilieren.

---

## Phase 7: Kapselung & Law of Demeter (LoD)
* **Ziel**: Beseitigung tiefer Objektabfragen aus der UI-Schicht.
* **Mehrwert**: Stabileres UI-Code, das unabhängig von internen Refactorings im ViewModel bleibt.

### Schritt 7.1: Sichtbarkeit einschränken
* **Beschreibung**: Machen Sie die Delegate-Klassen in den verbleibenden ViewModels `private`.
* **Dateien**:
  * [PageViewModel.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/app/src/main/java/com/andreas_kratzer/ghosttalk/ui/pages/PageViewModel.kt)
* **Verifikation**: Suchen Sie nach Kompilierfehlern in Compose-Views.

### Schritt 7.2: Kapselung in Views auflösen
* **Beschreibung**: Ersetzen Sie tiefe Abfragen wie `pageViewModel.callManagementDelegate.systemCallManager.answerCall()` durch direkte Aufrufe des jeweiligen ViewModels (z. B. `callViewModel.answerCall()`).
* **Dateien**:
  * [MainActivity.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/app/src/main/java/com/andreas_kratzer/ghosttalk/MainActivity.kt)
  * [PageScreen.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/app/src/main/java/com/andreas_kratzer/ghosttalk/ui/pages/PageScreen.kt)
* **Verifikation**: Führen Sie das gesamte Projekt-Test-Suite aus: `./gradlew test`.

---

## Phasen 8–12: Monolith-Roadmap (Stand 2026-06-12)

Die größten verbliebenen Monolithen, je Phase: Code-Review (Bugs/Design) → Aufteilen → Tests → Commit. Detailpläne werden je Phase erstellt, wenn sie an der Reihe ist (analog zum 4.2-Detailplan oben).

### Phase 8: Button-Konfig-UI — Detailplan (Rev. 1, 2026-06-12)

**Betroffene Dateien**: `ui/pages/ButtonConfigDialog.kt` (1077 Z.), `ui/pages/components/ButtonSettingsTabContent.kt` (1120 Z.). Bereits extrahiert und funktionsfähig: `DialogActionBar.kt` (234), `PreviewTabContent.kt` (347), `components/ButtonSettingsState.kt` (150, enthält `ButtonSettingsUiState`/`ButtonSettingsActions`/`ActionTypeResolver`), `ActionConfigFields.kt`, `actions/*ActionFields.kt`, `core/model/ActionCategoryRegistry.kt`. Aufrufer des Dialogs: `GridEditorContent.kt`, `GridEditorDialogs.kt`.

**Ist-Analyse (Claude, im Code verifiziert)**:
1. **Totes Duplikat im Dialog (~180 Zeilen)**: `handlePlayClick` (Z. 683), `handleFocusLost` (Z. 719) und `getLocalLabelSuggestion` (Z. 979) sind in `ButtonConfigDialog.kt` definiert, werden dort aber **nirgends aufgerufen** — die lebenden Kopien stecken wortgleich in `ButtonSettingsTabContent.kt`. Dazu: verwaistes `rememberCoroutineScope()` (Z. 459) und leere Kommentarblöcke (Z. 178–183).
2. **~40 einzelne `remember`-States** im Dialog (ein Feld pro Action-Parameter), die per ~100 Zeilen Boilerplate in `ButtonSettingsUiState`/`ButtonSettingsActions` (40 Callbacks!) umkopiert werden.
3. **Der `buttonConfig.copy(label = …, spokenText = …, …)`-Block existiert 4×**: `handleAutoSave`, `saveWithAction` (Dialog) sowie im KI-Vorschlag-Button und in `onRankChange` (TabContent).
4. **Action-Identität ist der lokalisierte Anzeige-String** (`selectedActionType: String`): Der Dialog hat eine eigene Inline-Tabelle `actionTypeXxx` (Z. 196–247), die `ActionTypeResolver` dupliziert; `buildCurrentAction()` (Z. 461–593) und der Titel-Badge switchen auf Display-Strings. Teils hartkodiertes Deutsch (`"Spotify abspielen"`, `"Zu Startseite"`, Tab-Titel).
5. **🐛 Echter Bug dabei**: `GeminiNanoButtonAction` wird beim Öffnen auf den Anzeige-String von `actionTypeGemini` gemappt (Z. 257), `buildCurrentAction()` baut daraus aber `GeminiButtonAction` — **beim ersten Auto-Save wird eine Nano-Action still in eine Cloud-Action konvertiert**.
6. Audio-Aufnahme-Logik (AudioRecorder, MediaPlayer, Keep-Screen-On, Mic-Permission) liegt lose im Dialog; die zugehörige Aufnahme-UI (~250 Z.) in TabContent.
7. `val isElevenLabs = remember { isTtsElevenLabs() }` wird einmalig gecached und reagiert nicht auf Engine-Wechsel bei offenem Dialog (vorbestehend, in beiden Dateien).

**Vorgehen** (jeder Schritt einzeln kompilierbar, testbar, committen; Verifikation jeweils `./gradlew :app:compileDebugKotlin` + `./gradlew testDebugUnitTest` + Smoke-Test laut Checkliste unten):

#### Schritt 8.1: Toten Code entfernen (reines Löschen)
* In `ButtonConfigDialog.kt`: `handlePlayClick`, `handleFocusLost`, `getLocalLabelSuggestion`, `rememberCoroutineScope()`-Zeile, leere Kommentarblöcke entfernen. **Keine sonstige Änderung.** (~−180 Zeilen)

#### Schritt 8.2: Geteilte Helfer in eigene Dateien
* `getLocalLabelSuggestion` aus `ButtonSettingsTabContent.kt` nach `ui/pages/components/LabelSuggestion.kt` verschieben (top-level `internal fun`, pure Funktion).
* `handlePlayClick`/`handleFocusLost` + die 3×2 Cache-States (`isXxxCached`/`isXxxPrefetching`) als wiederverwendbaren State-Holder `rememberTtsFieldPlayback(…)` nach `ui/pages/components/TtsFieldPlayback.kt` extrahieren; TabContent nutzt ihn für die 3 Felder (label, spokenText, auditoryCueText).
* **Neuer Unit-Test**: `LabelSuggestionTest` (pure Funktion, alle Action-Typen einmal durchspielen; Context mit Robolectric oder via gemocktem `getString`).

#### Schritt 8.3: `ButtonActionFactory` + stabile Action-IDs (Kern-Schritt, fixt Bug Nr. 5)
* Neues `enum class ActionTypeId` (z. B. `SPEAK, NAVIGATE, NAVIGATE_BACK, …, GEMINI, GEMINI_NANO, …` — **eigener Eintrag für `GEMINI_NANO`!**) in `ui/pages/components/` oder `core/model/` neben `ActionCategoryRegistry`.
* Neue pure Datei `ButtonActionFactory.kt` mit zwei Funktionen:
  * `fun actionTypeIdOf(action: ButtonAction): ActionTypeId` (ersetzt das Mapping-`when` Z. 249–309)
  * `fun buildAction(id: ActionTypeId, params: ActionParams): ButtonAction` (ersetzt `buildCurrentAction`, Z. 461–593; `ActionParams` = data class mit den Action-Feldern)
* `selectedActionType` im Dialog/UiState wird `ActionTypeId` statt String; `ActionTypeResolver` liefert nur noch das Display-Label pro Id (eine Richtung: Id → String). Der Titel-Badge switcht auf die Id.
* **Bugfix dabei dokumentieren**: `GEMINI_NANO` round-trippt jetzt korrekt (`buildAction(GEMINI_NANO, …) = GeminiNanoButtonAction(intent = geminiPrompt)`).
* **Neuer Unit-Test** `ButtonActionFactoryTest`: Für jeden Action-Typ Roundtrip `action → actionTypeIdOf → buildAction → gleicher Typ + gleiche Felder` (deckt auch den Nano-Bug ab).

#### Schritt 8.4: State-Holder statt 40 Einzelstates
* Neue Klasse `ButtonConfigDialogState` (`@Stable`, Datei `ui/pages/components/ButtonConfigDialogState.kt`) mit `rememberButtonConfigDialogState(buttonConfig)`: kapselt alle Feld-States (mutableStateOf-Properties), plus genau **eine** Methode `buildConfig(): ButtonConfig` (ersetzt die 4 `copy()`-Blöcke) und `buildAction()` (delegiert an `ButtonActionFactory`).
* `ButtonSettingsUiState`/`ButtonSettingsActions` (40-Callback-Objekt) entfallen: TabContent bekommt den State-Holder direkt + die wenigen externen Callbacks (`onSave`, `onDismiss`, TTS-/Spotify-/Hue-Lambdas).
* `handleAutoSave`/`saveWithAction` werden Einzeiler im Dialog: `if (state.label.isNotBlank()) onSave(state.buildConfig())`.

#### Schritt 8.5: Audio-Aufnahme kapseln
* `rememberAudioRecordingController(context, buttonId)` in `ui/pages/components/AudioRecordingController.kt`: kapselt AudioRecorder, MediaPlayer, isRecording/isPlaying, Keep-Screen-On-Effekt, Start/Stop/Play/Delete (heute verteilt über Dialog Z. 122–159, 629–681 und TabContent).
* Die Aufnahme-UI (TabContent Z. 567–736 inkl. Lösch-Dialog) wird eigenes Composable `SpokenTextAudioSection.kt`.

#### Schritt 8.6: TabContent in Sektions-Composables aufteilen
* Je eigene Datei unter `ui/pages/components/`:
  * `ActionTypeDropdownSection.kt` (Gruppen-Dropdown + Icon-Mapping, heute Z. 209–351)
  * `LabelFieldSection.kt` (Label-Feld + KI-/Lokal-Vorschlag, Z. 378–483)
  * `SpokenTextSection.kt` (TTS/Audio-Karte, nutzt 8.5, Z. 485–739)
  * `CueAndTogglesSection.kt` (Auditory-Cue-Feld + isActive/playAsCue-Karte + FeatureGuard-Warnung, Z. 741–862)
* `ButtonSettingsTabContent` bleibt als Orchestrierung (< ~250 Zeilen) inkl. `ActionConfigFields`-Wiring.

#### Schritt 8.7: Abschluss-Review
* Zielgrößen: `ButtonConfigDialog.kt` < 300 Z., `ButtonSettingsTabContent.kt` < 250 Z., keine Datei der Phase > 400 Z.
* Claude reviewt: Verhaltensgleichheit (außer dokumentiertem Nano-Bugfix), keine doppelte Logik mehr, keine String-basierte Action-Identität mehr.

**Smoke-Test-Checkliste (nach jedem Schritt, mind. nach 8.3/8.4/8.5)**:
1. Dialog im Grid-Editor öffnen, alle 3 Tabs durchklicken.
2. Aktionstyp wechseln (Sprechen → Navigation → Gemini → Spotify → Hue) — Felder erscheinen passend, Label-Autovorschlag greift.
3. Speichern + erneut öffnen — Werte bleiben erhalten; **Gemini-Nano-Button öffnen + schließen → bleibt Nano** (Bugfix 8.3).
4. Audio: aufnehmen, abspielen, löschen; Mic-Permission-Fall.
5. TTS-Vorhören der 3 Textfelder (mit ElevenLabs: Prefetch-Spinner, Cache-Färbung).

**Explizit NICHT in Phase 8**: i18n der hartkodierten deutschen Strings (Tab-Titel, Toasts, „Zu Startseite" …) — nur sammeln und als eigenes Ticket notieren; `isElevenLabs`-Caching-Schwäche (Nr. 7) nur dokumentieren.

#### Review-Befund Phase 8 (Claude, 2026-06-12) — 3 Must-Fixes vor Commit

Struktur stimmt: Dialog 1077→443 Z., TabContent 1120→338 Z., 9 neue fokussierte Dateien, State-Holder + Factory wie geplant, Nano-Bug gefixt, `buildConfig()` existiert genau 1×, Tests grün. Abweichung: Schritt 8.5 (AudioRecordingController) wurde nicht extrahiert — Audio-Logik liegt weiter im Dialog (~70 Z.), akzeptiert, bleibt Folgearbeit.

**🐛 Must-Fix 1: `INSTALL_UPDATE` wird zu `TOGGLE_SCANNING` korrumpiert.**
[ButtonActionFactory.kt:89] mappt `DeviceActionType.INSTALL_UPDATE -> ActionTypeId.TOGGLE_SCANNING`; ein `INSTALL_UPDATE`-Eintrag fehlt im Enum komplett. Ein bestehender Update-Button wird beim Öffnen als „Scanning umschalten" angezeigt und beim ersten Auto-Save in `TOGGLE_SCANNING` umgeschrieben — exakt die Bug-Klasse, die Schritt 8.3 beseitigen sollte.
* Fix: `ActionTypeId.INSTALL_UPDATE` ergänzen; Mapping in `actionTypeIdOf`, Case in `buildAction`, Label in `ActionTypeResolver.getLabel` (String `R.string.button_device_control_install_update` wieder aufnehmen). Im Dropdown war INSTALL_UPDATE auch vorher nicht — das bleibt so.
* Test: siehe Must-Fix-Test unten.

**🐛 Must-Fix 2: `TtsFieldPlaybackState` friert `text`/`playingField` ein.**
`rememberTtsFieldPlayback` ist auf `text`/`playingField` gekeyt und die Klasse hält beide als Konstruktor-Snapshot. Folgen:
* Nach Abspielende prüft der Completion-Callback `if (playingField == fieldName)` gegen den **eingefrorenen** Wert (beim Klick `null`) → Bedingung nie wahr → Play-Icon bleibt dauerhaft im „spielt"-Zustand.
* Nach KI-Label-Vorschlag ruft `LabelFieldSection` `labelPlayback.handleFocusLost(...)` auf der **alten** Instanz auf → es wird der alte Text geprefetcht, nicht der Vorschlag (alter Code übergab den Text explizit).
* Nebenwirkung: `isPrefetching` wird bei jedem Tastendruck zurückgesetzt (Instanz-Neubau).
Fix: Instanz nur auf `fieldName` keyen; `text` als Methodenparameter übergeben (`handlePlayClick(text)`, `handleFocusLost(text, onAutoSave)`) und `playingField` als Live-Getter (`getPlayingField: () -> String?`). Aufrufstellen in den 3 Sektionen anpassen (beim Vorschlag den Vorschlagstext übergeben).

**🐛 Must-Fix 3: Auto-Save bei jedem Tastendruck.**
In `ButtonSettingsTabContent` wurde an **alle** `ActionConfigFields`-Callbacks `onAutoSave()` gehängt. Vorher waren 15 davon bewusst reine Setter — die Textfelder speichern selbst bei Focus-Verlust (`DeviceActionFields`: `if (!it.isFocused) onAutoSave()`, `onValueChangeFinished`). Jetzt feuert `onSave → updateButtonConfig → Room-Write + Sync-Timestamp` bei jedem Zeichen in: geminiPrompt, volumeValue, contactName, contactPhone, messageText, prefixText, suffixText, offsetValue, smartHomeDeviceId/-Name/-Intent/-Value.
Fix: Verhaltensparität wiederherstellen — diese Callbacks wieder als reine Setter (`{ state.x = it }`); `onAutoSave()` nur dort behalten, wo es der alte Code hatte: onRankChange, onPredictionTypeChange, onIncludeWeekdayChange, onIgnoreEmojisChange, onUseCloudChange, onPlayShutterSoundChange, alle 5 media*-Callbacks, onTargetPageIdChange via `NavigationActionFields.onPageSelected` (dort war es schon), onContactSelected→saveWithAction.

**Must-Fix-Test (ersetzt die Stichproben-Tests)**: `ButtonActionFactoryTest` erschöpfend machen — hätte Must-Fix 1 gefangen:
```kotlin
@Test fun roundtripAllDeviceActionTypes() {
    for (type in DeviceActionType.entries) {
        val id = ButtonActionFactory.actionTypeIdOf(ControlDeviceButtonAction(type))
        val rebuilt = ButtonActionFactory.buildAction(id, ActionParams())
        assertEquals(type, (rebuilt as ControlDeviceButtonAction).actionType)
    }
}
@Test fun buildActionAllIdsRoundtrip() {
    for (id in ActionTypeId.entries) {
        assertEquals(id, ButtonActionFactory.actionTypeIdOf(ButtonActionFactory.buildAction(id, ActionParams())))
    }
}
```
(Achtung: zweiter Test deckt z. B. SPOTIFY↔PlayMedia korrekt ab, weil provider in params steckt — `ActionParams(mediaProvider=…)` je Id nicht nötig, da Id den Provider bestimmt.)

**Kleinkram (kein Blocker)**:
* Ungenutzte Imports in `ButtonConfigDialog.kt` (u. a. `rememberCoroutineScope`, diverse `core.model.*`, `Activity`/`ContextWrapper` — `findActivity` nutzt FQNs).
* Feature-Warnung nutzt jetzt `state.buildAction()` (live) statt der gespeicherten Action — Verbesserung, bewusst so lassen.
* Datei-Header-`@Suppress("UNUSED_VALUE", "ASSIGNED_VALUE_IS_NEVER_READ", …)` in TabContent stammt aus der alten Datei und kann weg.

### Phase 8B: Gemini Nano komplett entfernen — Detailplan (Rev. 1, 2026-06-12)

**Entscheidung (Andreas, 2026-06-12)**: Gemini Nano wird vollständig entfernt — zu wenige Geräte (v. a. im AAC-Bereich) unterstützen AICore. **Inklusive lokaler Bildbeschreibung**: Gemini-Vision nutzt künftig immer die Cloud.

**Reihenfolge**: Erst nach Commit der Phase-8-Must-Fixes starten (dieselben Dateien betroffen: `ActionTypeId`, `ButtonActionFactory`, `ButtonConfigDialogState`, …).

**Ist-Analyse (Claude, im Code verifiziert)**:
* `GeminiNanoButtonAction` ist bereits `@Deprecated`-Tombstone („kept only for backup import compatibility") und hat **keinen Runtime-Handler** — als Action ist Nano schon tot.
* Die Einstellung `useLocalGenerativeAi` wird **zur Laufzeit nirgends gelesen** — nur Settings-Plumbing (GenAiSettings, FeatureSettings, GenAiSettingsRepository, ProfileConfig, SettingsMapper, ProfileBootstrapper, GoTalkNowImportModels).
* Einzige echte Nano-Ausführung: `VisionUseCase.describeImageLocally()` (`core-ai`) via ML Kit `genai-prompt` (`Generation.getClient()`), erreichbar über `GeminiVisionButtonAction(useCloud = false)`.
* Built-in-Template `builtin_gemini_nano` in `ButtonTemplateRepositoryImpl` (Z. 163–175) erzeugt weiterhin Nano-Buttons!
* Dependency `google-generativeai-mlkit` (genai-prompt) in `app/build.gradle.kts:199` **und** `core-ai/build.gradle.kts:55`; Version in `gradle/libs.versions.toml`.

#### Schritt 8B.1: Nano-Action aus der UI entfernen
* Entfernen: `ActionTypeId.GEMINI_NANO`, die Nano-Cases in `ButtonActionFactory` (`actionTypeIdOf`, `buildAction`), `ActionTypeResolver.getLabel`-Case, Nano-Zweig in `ButtonConfigDialogState.geminiPrompt`-Init, Badge-Case im Dialog, Icon-Case in `GridButton.kt:121`, Nano-Format in `PreviewTabContent.kt` (Param + `geminiNanoFormat`), `GeminiNanoActionFields` in `GeminiActionFields.kt`, Nano-Fälle in `ButtonActionFactoryTest`.
* **Mapping-Regel für Bestandsdaten**: `actionTypeIdOf(GeminiNanoButtonAction)` → `ActionTypeId.GEMINI` (bewusste, jetzt gewollte Konvertierung Nano→Cloud beim nächsten Speichern; `intent` wird `prompt`). Dafür den `else`-Zweig nutzen oder expliziten Case mit Kommentar.
* Strings `button_action_gemini_nano`, `button_preview_gemini_nano` (+ values-en) entfernen.

#### Schritt 8B.2: Tombstone & Datenkompatibilität
* `GeminiNanoButtonAction` in `ButtonAction.kt` **bleibt** als `@Deprecated`-Tombstone (Deserialisierung alter Backups/Cloud-Daten!).
* `ActionMapper` (core-data): Nano beim Laden auf `GeminiButtonAction(prompt = intent)` mappen, falls dort ein Mapping-`when` existiert; sonst Konvertierung dem UI-Mapping aus 8B.1 überlassen.
* Built-in-Template `builtin_gemini_nano` aus `ButtonTemplateRepositoryImpl` entfernen **und** prüfen, wie Built-ins auf Bestandsgeräten aktualisiert werden — falls sie nur additiv geseedet werden, expliziten Cleanup (Delete by id `builtin_gemini_nano`) ergänzen.
* `ActionCategoryRegistry`: `ActionCategory.GEMINI_NANO` + zugehörige `when`-Zweige entfernen; `LocalIcons.kt:51` anpassen. Vorher grep: Kategorie darf nirgends persistiert sein.

#### Schritt 8B.3: Lokale Vision entfernen
* `VisionUseCase`: `describeImageLocally()` + ML-Kit-Imports entfernen; `describeImage(bitmap, prompt, useCloud)` → `useCloud`-Parameter entfernen, immer `describeCloud`. Aufrufer anpassen (Executor/Handler der Vision-Action).
* `GeminiVisionButtonAction.useCloud` **bleibt im Modell** (Serialisierung), wird aber ignoriert — KDoc-Hinweis ergänzen.
* UI: `useCloud`-Toggle entfernen (`ButtonConfigDialogState.geminiVisionUseCloud`, `ActionParams.geminiVisionUseCloud`, `ActionConfigFields`-`useCloud`/`onUseCloudChange`-Wiring, zugehörige Composable-Teile in `GeminiActionFields.kt`). `buildAction(GEMINI_VISION, …)` setzt `useCloud = true`.
* `GeminiUseCase.getLocalCapabilities()` (toter Nano-Kommentar) entfernen, falls ungenutzt.

#### Schritt 8B.4: Tote Einstellung `useLocalGenerativeAi` entfernen
* Entfernen aus: `GenAiSettings` (+ Flow), `FeatureSettings`, `GenAiSettingsRepository`, `SettingsRepositoryImpl` (ProfileConfig-Write-Back-Block!), `SettingsConstants.KEY_USE_LOCAL_GENERATIVE_AI`, `SettingsMapper`, `ProfileBootstrapper`, `ProfileConfig` (Feld raus — alle Json-Parser haben `ignoreUnknownKeys = true`, alte Profile bleiben lesbar), `GoTalkNowImportModels`, betroffene Tests (`SettingsRepositoryTest`, `SettingsRoundTripTest`, `PageImportExportManagerTest`).
* ⚠️ Cloud-Settings-Sync: prüfen, dass `SettingsMapper` den Key nur weglässt (ältere App-Versionen ignorieren fehlende Keys / nutzen Default).

#### Schritt 8B.5: Dependency raus
* `google-generativeai-mlkit` aus `app/build.gradle.kts` und `core-ai/build.gradle.kts`; `generativeAiMlKit`-Version aus `libs.versions.toml`. (`mediapipe-tasks-audio` bleibt — anderes Feature.)
* Verifikation: `./gradlew assembleDebug` + voller Testlauf + grep `mlkit.genai|GeminiNano|useLocalGenerativeAi` über `src/` → nur noch Tombstone-Klasse + ggf. Mapping-Kommentare.

**Smoke-Test**: Bestehenden Nano-Button öffnen → erscheint als Gemini (Cloud), Speichern konvertiert; Vision-Button ausführen → Cloud-Beschreibung; Profil laden/exportieren/importieren → keine Fehler; Template-Liste zeigt kein „Gemini Nano (Lokal)" mehr.

---

### Phase 9: Analytics-Bereich — Detailplan (Rev. 1, 2026-06-12)

**Betroffene Dateien**: `ui/pages/analytics/AnalyticsRecommendationsTab.kt` (1251 Z.) und `ui/pages/ButtonStatisticsTabContent.kt` (752 Z.). Umfeld (bleibt unangetastet): `AnalyticsDashboardScreen.kt` (510, Aufrufer), `AnalyticsOverviewTab/DetailsTab/FatigueCharts/DurationCharts/ErrorRateCharts`.

**Ist-Analyse (Claude, im Code verifiziert)**:
* Beide Dateien bestehen aus **je genau einem Composable**.
* `AnalyticsRecommendationsTab` hat **42 Parameter** (21 State-Werte + 21 Callbacks) und drei klar markierte Sektionen: Shortcut-Wizard (Z. 116–258), Layout-Optimierung mit Filter/Sortierung (Z. 259–569), KI-Buchrestrukturierung (Z. 570–1251, ~680 Zeilen inkl. Seitenauswahl-Dialog, Knoten-Editor, Token-Warnung, Feedback-Karte, Layout-Lade-Logik).
* `ButtonStatisticsTabContent` hat vier Sektionen: KPI-Karten (Z. 115), Smart-Prediction-KPIs (Z. 406), Button-Empfehlungen (Z. 588), Verlaufsliste (Z. 659). Liegt falsch in `ui/pages/` statt `ui/pages/analytics/`.
* Berechnungslogik liegt überwiegend schon in ViewModel/Delegates (`AiRestructureDelegate`, `AnalyticsDelegate`, `PathAnalyzer`) — Phase 9 ist primär ein UI-Zuschnitt, kein Logik-Umbau.

**Vorgehen** (jeder Schritt kompilierbar + committen; Verifikation: `:app:compileDebugKotlin` + `testDebugUnitTest` + Smoke unten):

#### Schritt 9.1: ButtonStatisticsTabContent verschieben & aufteilen
* Datei nach `ui/pages/analytics/buttonstats/` verschieben (Package anpassen, Aufrufer: `ButtonConfigDialog`).
* In 4 Sektions-Dateien aufteilen: `ButtonKpiSection.kt`, `SmartPredictionKpiSection.kt`, `ButtonRecommendationsSection.kt`, `ButtonHistorySection.kt`; `ButtonStatisticsTabContent` bleibt Orchestrierung (< 150 Z.).
* Inline-Aggregationen (falls in den KPI-Sektionen welche stecken) als pure `internal fun` in `ButtonStatsCalculations.kt` herausziehen + Mini-Unit-Test.

#### Schritt 9.2: Parameter der RecommendationsTab bündeln (behavior-neutral)
* Statt 42 Einzelparametern drei `@Immutable`-Bündel in `ui/pages/analytics/recommendations/`:
  * `ShortcutWizardState/Actions` (recommendations, isCalculating / onApplyShortcutRecommendation)
  * `LayoutProposalsState/Actions` (layoutProposals, currentFilter, currentSort / onSetProposalFilter, onSetProposalSort, onGeneratePageSplitProposal, onChangePageScanPattern, onChangeScanDelay, onApplySpacerRelocate)
  * `AiRestructureState/Actions` (alles ab `aiProposal` … / die On-Lambdas ab `onSelect…`)
* `AnalyticsDashboardScreen` (Aufrufer) baut die Bündel; reine Signatur-Umstellung, kein Logik-Move.

#### Schritt 9.3: RecommendationsTab in Sektions-Dateien aufteilen
* `recommendations/ShortcutWizardSection.kt` (Z. 116–258)
* `recommendations/LayoutOptimizationSection.kt` (Z. 259–569, inkl. Filter-/Sort-Dropdowns)
* `recommendations/AiRestructureSection.kt` (Z. 570–1251) — wegen Größe intern weiter aufteilen:
  * `AiPageSelectionDialog.kt`, `AiHierarchyNodeEditDialog.kt`, `AiTokenWarningDialog.kt` (Dialoge)
  * `AiHierarchyProposalCard.kt` (Hierarchie-Karten inkl. Seitenlayout-Laden), `AiFeedbackCard.kt`
* `AnalyticsRecommendationsTab.kt` bleibt Orchestrierung (< 200 Z.). Lokale `remember`-States wandern in die jeweilige Sektion, sofern sie nur dort gebraucht werden.

#### Schritt 9.4: Abschluss-Review
* Zielgrößen: keine Datei > 400 Z.; `AnalyticsRecommendationsTab` < 200 Z.
* Claude reviewt Verhaltensgleichheit (insb. die `showAll…`-Toggles, Filter/Sort-State und der mehrstufige KI-Flow).

**Smoke-Test-Checkliste**:
1. Analytics-Dashboard öffnen, alle Tabs durchklicken.
2. Shortcut-Empfehlung anwenden (Erfolgs-Toast), „alle anzeigen"-Toggle.
3. Layout-Vorschläge filtern + sortieren, einen Vorschlag anwenden.
4. KI-Restrukturierung: Seiten auswählen → Hierarchie generieren → Knoten bearbeiten → Layouts laden → anwenden; Token-Warnung und Fehlerfall (Gemini deaktiviert) prüfen.
5. Button-Statistik-Tab im ButtonConfigDialog (KPIs, Verlauf, Empfehlung anwenden).

### Phase 10: MainActivity entschlacken (731 Zeilen)
* Lifecycle-, Permission-, Locale- und Screen-State-Logik in eigene Klassen/Manager herauslösen.

### Phase 11: SystemCallManager (637 Zeilen)
* Review (Telefonie ist sicherheitskritisch), danach Aufteilung z. B. in Call-State-Tracking, Audio-Routing und Intent-Handling.

### Phase 12: Restliche Kandidaten
* `ui/pages/PageSplitDialogs.kt` (614), `ui/components/GridEditorContent.kt` (591), `ui/pages/actions/DeviceActionFields.kt` (578).
