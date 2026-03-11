# Anleitung: Play Store Automatisierung (GPP & GitHub Actions)

Diese Anleitung hilft dir dabei, die notwendigen Zugangsdaten zu erstellen, damit GitHub Actions deine App automatisch in den Play Store hochladen kann.

## 1. Google Play Service Account erstellen
Um die Play Store API zu nutzen, benötigst du einen Service Account:
1. Gehe in die **Google Play Console** -> **Einrichten** -> **API-Zugriff**.
2. Klicke auf **Neues Google Cloud-Projekt erstellen** (falls noch keins verknüpft ist).
3. Klicke auf **Servicekonto erstellen**. Dies leitet dich zur Google Cloud Console weiter.
4. Erstelle dort ein Servicekonto (Name z.B. "ghosttalk-publisher").
5. Weise ihm keine spezifischen Rollen in Google Cloud zu (Play Console regelt das).
6. Gehe in der Cloud Console auf den Tab **Schlüssel (Keys)** -> **Schlüssel hinzufügen** -> **Neuen Schlüssel erstellen** -> **JSON**.
7. Speichere diese Datei als `service-account.json`. **WICHTIG: Gib diese Datei niemals öffentlich preis!**
8. Gehe zurück zur **Play Console** und klicke auf **Zugriff gewähren** für das neue Konto.
9. Gib ihm die Berechtigung **Releases im Test-Track erstellen und bearbeiten**.

## 2. GitHub Secrets konfigurieren
Gehe in deinem GitHub Repository auf **Settings** -> **Secrets and variables** -> **Actions** und füge folgende "New repository secret" hinzu:

| Secret Name | Wert / Inhalt |
| :--- | :--- |
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` | Kopiere den kompletten Inhalt deiner `service-account.json` und kodiere ihn ggf. als Base64 (oder füge den Text direkt ein, wenn das Skript es unterstützt). |
| `KEYSTORE_BASE64` | Deine Keystore-Datei (`.jks`) als Base64-String (Befehl: `base64 -i my_keystore.jks`). |
| `KEYSTORE_PASSWORD` | Das Passwort deines Keystores. |
| `KEY_ALIAS` | Der Alias des Schlüssels im Keystore. |
| `KEY_PASSWORD` | Das Passwort des Schlüssels. |

## 3. Wie es funktioniert
- Sobald du auf den `main` Branch pushst, startet die GitHub Action.
- Sie baut das App Bundle (`.aab`).
- Sie lädt das Bundle und die Texte aus `app/src/main/play/` direkt in den **Internal Testing** Track der Play Console hoch.

Du musst nach dem ersten automatischen Upload nur noch einmalig in der Play Console die Tester (deine E-Mail) für den Track bestätigen.
