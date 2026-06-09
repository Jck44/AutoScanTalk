# GhostTalk - Benutzerhandbuch & Konfigurationshilfe

**Timestamp:** 2026-03-06T14:32:00+01:00  
**Git Commit Hash:** 9e99afda1cd69b2cc94779f712b39d412a68e8bc

## Willkommen bei GhostTalk
GhostTalk ist eine App zur Unterstützten Kommunikation (AAC), die speziell für die Nutzung mit Tastern (Scanning) oder Touch-Eingabe entwickelt wurde. Sie ermöglicht es Menschen mit kommunikativen Einschränkungen, sich über ein Raster von Schaltflächen auszudrücken.

---

## 1. Grundkonzepte
### Bücher und Seiten
- **Bücher**: Sammlungen von Inhalten für bestimmte Kontexte (z. B. "Schule", "Zuhause").
- **Seiten**: Ein Raster von Schaltflächen (z. B. 4x4). Jede Seite gehört zu einem Buch.
- **Startseite**: Jedes Buch hat eine definierte Startseite, auf die beim Öffnen des Buches zugegriffen wird.

### Schaltflächen (Buttons)
Jeder Button im Raster kann eine oder mehrere **Aktionen** ausführen:
- **Sprache (TTS)**: Der Text wird über die Sprachausgabe vorgelesen.
- **Navigation**: Wechselt zu einer anderen Seite innerhalb des Buches.
- **GenAI (KI)**: Startet eine intelligente Interaktion (z. B. Wetter abfragen, Wikipedia-Suche).

---

## 2. Bedienung (Scanning)
GhostTalk unterstützt das **Scanning-Verfahren**. Ein Rahmen bewegt sich automatisch über die Schaltflächen.

### Scan-Muster
1. **Linear**: Der Rahmen wandert Button für Button durch das Raster.
2. **Zeile-für-Zeile (Row-by-Row)**: Zuerst wird die gewünschte Zeile ausgewählt, dann der Button innerhalb dieser Zeile. Dies ist bei großen Rastern oft schneller.

### Eingabe-Möglichkeiten
- **Touch**: Direktes Antippen eines Buttons oder des Bildschirms zum Auslösen des Scans.
- **Externer Taster**: Anschluss über Bluetooth oder USB (emuliert meist eine Taste wie "Leertaste" oder "Enter").
- **Lautstärketasten**: In den Einstellungen kann aktiviert werden, dass die Lautstärketasten als Taster fungieren.

---

## 3. Einstellungen & Konfiguration
Die Einstellungen finden Sie über das Zahnrad-Symbol im Hauptmenü.

### Scanning-Einstellungen
- **Scan-Intervall**: Zeit (in Millisekunden), wie lange der Fokus auf einem Element bleibt (Standard: 3000ms).
- **Haltezeit**: Zeit, die ein Taster gedrückt gehalten werden muss, bevor die Aktion auslöst (Schutz vor versehentlichem Drücken).
- **Auto-Start**: Startet den Scan automatisch beim Öffnen einer Seite.
- **Tasten-Zuweisung**: Festlegen, welche Taste (z. B. "Space") den Scan auslöst.

### Audio & Sprache
- **Sprachausgabe (TTS)**: Wählen Sie Sprache und Stimme.
- **Audio-Routing**:
    - **Cues (Hinweise)**: Kurze Ansagen für den Nutzer (z. B. beim Scannen). Diese können auf ein privates Gerät (z. B. Bluetooth-Kopfhörer) geleitet werden.
    - **Main Speech**: Die eigentliche Kommunikation. Diese wird meist über den Lautsprecher ausgegeben.
- **Lautstärke-Multiplikator**: Getrennte Regelung für Hinweise und Sprache.

### Cloud-Synchronisierung (Google Drive)
- Ermöglicht das Sichern und Synchronisieren Ihrer Bücher über mehrere Geräte.
- **Zwei-Wege-Sync**: Änderungen werden automatisch hoch- und heruntergeladen.
- **Backup/Restore**: Manuelle Sicherung oder Wiederherstellung.
- **Wichtig**: Erfordert eine Anmeldung mit einem Google-Konto.

### Generative KI (Gemini)
- Aktivieren Sie "Gemini", um dynamische Funktionen zu nutzen.
- Funktionen umfassen: Wikipedia-Suche, Wetter, Google Kalender/Tasks Integration und Spotify-Steuerung.

---

## 4. Editor-Modus
Im Editor können Sie Seiten und Buttons anpassen:
- **Grid-Größe**: Ändern Sie die Anzahl der Zeilen und Spalten.
- **Button-Inhalt**: Text ändern, Symbole hinzufügen.
- **Aktionen**: Legen Sie fest, was beim Drücken passieren soll.
- **Reihenfolge**: Seite können durch langes Drücken und Verschieben neu sortiert werden.

---

## 5. Tipps für die Einrichtung
- **Kontrast**: Verwenden Sie klare Farben und Symbole für eine bessere Sichtbarkeit.
- **Privatsphäre**: Nutzen Sie das Audio-Routing für Auditive Hinweise, damit der Nutzer die Scans diskret über Kopfhörer verfolgen kann.
- **Sicherung**: Aktivieren Sie die Cloud-Synchronisierung, damit Ihre mühsam erstellten Seiten bei einem Gerätewechsel nicht verloren gehen.
