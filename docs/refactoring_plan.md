# Refactoring-Plan: OOP- und SOLID-Bereinigung

Dieser Schritt-für-Schritt-Plan beschreibt, wie die identifizierten Entwurfsfehler (OOP- und SOLID-Prinzipien) in der App schrittweise behoben werden. 

Jeder Schritt ist so konzipiert, dass die App danach vollständig kompilierbar, funktionsfähig und durch automatisierte Tests abzusichern ist. Die Reihenfolge ist nach dem geschäftlichen Mehrwert für zukünftige Feature-Entwicklungen priorisiert.

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

### Schritt 4.2: Direktes Binding der Sub-Schnittstellen
* **Beschreibung**: Binden Sie in Hilt die spezifischen Interfaces (z. B. `TtsSettings`, `ScanningSettings`) direkt an ihre konkreten Sub-Klassen (z. B. `VoiceSettingsRepository`, `ScanningSettingsRepository`) anstatt an `SettingsRepositoryImpl`.
* **Dateien**:
  * [DataModule.kt](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/core-data/src/main/java/com/andreas_kratzer/ghosttalk/core/data/di/DataModule.kt)
* **Verifikation**: Führen Sie `./gradlew assembleDebug` aus, um sicherzustellen, dass der Hilt-Dependency-Graph korrekt auflöst.

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
