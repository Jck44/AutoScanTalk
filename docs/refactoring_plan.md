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
| **4.2** | **Direktbinding Sub-Interfaces** | ❌ **offen → Detailplan unten** |
| 5 | Konstruktor-Injektion Sub-Repos | ✅ umgesetzt |
| 6 | PageViewModel-Aufteilung | ✅ umgesetzt (Call-/GridEditor-/PageSplit-/BookRestructure-VM) |
| 7 | Law of Demeter | ✅ umgesetzt (Delegates `private`/`internal`) |
| 8–12 | Monolith-Roadmap UI/Call | 🆕 siehe Ende des Dokuments |

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

### Phase 8: Button-Konfig-UI (~2200 Zeilen, größter Brocken)
* `ui/pages/ButtonConfigDialog.kt` (1077 Zeilen) und `ui/pages/components/ButtonSettingsTabContent.kt` (1120 Zeilen) hängen zusammen.
* Ziel: Aufteilung in fokussierte Composables pro Tab/Sektion; State-Hoisting prüfen; gemeinsame Bausteine extrahieren.

### Phase 9: Analytics-Bereich
* `ui/pages/analytics/AnalyticsRecommendationsTab.kt` (1251 Zeilen) und `ui/pages/ButtonStatisticsTabContent.kt` (752 Zeilen).
* Ziel: Berechnungslogik (Empfehlungen, Aggregationen) aus den Composables in testbare Klassen/UseCases ziehen; UI in Sektions-Composables aufteilen. `ButtonStatisticsTabContent` gehört vermutlich nach `ui/pages/analytics/` verschoben.

### Phase 10: MainActivity entschlacken (731 Zeilen)
* Lifecycle-, Permission-, Locale- und Screen-State-Logik in eigene Klassen/Manager herauslösen.

### Phase 11: SystemCallManager (637 Zeilen)
* Review (Telefonie ist sicherheitskritisch), danach Aufteilung z. B. in Call-State-Tracking, Audio-Routing und Intent-Handling.

### Phase 12: Restliche Kandidaten
* `ui/pages/PageSplitDialogs.kt` (614), `ui/components/GridEditorContent.kt` (591), `ui/pages/actions/DeviceActionFields.kt` (578).
