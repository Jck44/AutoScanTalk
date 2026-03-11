# GhosTTalk Release Checklist (Google Play Store)

Diese Checkliste führt dich durch den Prozess, die App für das **Internal Testing** im Google Play Console vorzubereiten und hochzuladen.

## 1. App-Identifikation & Versionierung
- [x] **Package Name / Application ID**: Geändert zu `com.andreas_kratzer.ghosttalk`.
- [ ] **Version Code**: Muss bei jedem Upload inkrementiert werden (z. B. `1`, `2`, `3`).
- [x] **Version Name**: Eingestellt auf `"0.1.0"`.

## 2. Release Signing (Keystore)
Um eine App im Play Store zu veröffentlichen, muss sie signiert sein.
- [ ] **Keystore erstellen**:
  - In Android Studio: `Build` -> `Generate Signed Bundle / APK...` -> `Android App Bundle` -> `Create new...`
  - WICHTIG: Sichere die Keystore-Datei (`.jks`) und die Passwörter an einem sicheren Ort! Ohne diese Datei kannst du später keine Updates mehr hochladen.

## 3. Build Generierung
- [ ] **Build-Typ**: Stelle sicher, dass `MinifyEnabled = true` in der `build.gradle.kts` gesetzt ist, um die App zu optimieren.
- [ ] **App Bundle (AAB) erstellen**: `Build` -> `Generate Signed Bundle / APK...` und folge den Anweisungen mit deinem Keystore.

## 4. Google Play Console Vorbereitung
- [ ] **App-Eintrag erstellen**: In der Google Play Console ein neues App-Projekt anlegen.
- [ ] **Internal Testing Track**: Navigiere zu `Testing` -> `Internal testing` und erstelle einen neuen Release.
- [ ] **AAB Hochladen**: Die generierte `.aab` Datei hochladen.
- [ ] **Tester hinzufügen**: E-Mail-Adressen der Tester (dich selbst) hinzufügen.

## 5. Rechtliches & Store-Präsenz
- [x] **Datenschutzerklärung**: Erstellt als [PRIVACY_POLICY.md](file:///Users/andreas.kratzer/AndroidStudioProjects/GoSTalk/PRIVACY_POLICY.md). Nach dem Push zu GitHub kannst du den Link (`https://github.com/NUTZERNAME/REPO/blob/main/PRIVACY_POLICY.md`) in der Play Console hinterlegen.
- [ ] **Store-Assets**: App-Icon (512x512), Feature Graphic (1024x500) und Screenshots vorbereiten.

## 6. Update-Verifizierung
- [ ] **Versions-Check**: Wurde `versionCode` in der `build.gradle.kts` erhöht?
- [ ] **Migrationstest**: Bleiben Daten (Bücher/Seiten) nach einem simulierten Update (Installieren einer höheren Version über die alte) erhalten?
- [ ] **Update-Prompt**: Erscheint der In-App-Update Dialog bei Verfügbarkeit einer neuen Version?
