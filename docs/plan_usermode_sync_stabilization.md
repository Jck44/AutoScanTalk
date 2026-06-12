# Plan: Benutzermodus-Stabilität & Sync-Datensicherheit

**Stand:** 2026-06-12, Rev. 2 (Review-Feedback eingearbeitet: SAF-Anker-Pfad, MD5-Normalisierung,
Button-Tombstones als Phase 1B, zentraler onError-Wrapper) · **Basis:** Code-Review von `core-scanning`,
`core-tts`, `core-cloud` auf Branch `develop`
**Zielgruppe:** Umsetzung durch Gemini (oder anderen Agenten). Jede Phase ist eigenständig umsetzbar und endet mit grünen Unit-Tests.

## Build-/Test-Umgebung

Kein System-Java vorhanden. Vor jedem Gradle-Aufruf:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Tests laufen mit JUnit 4 + MockK + `kotlinx-coroutines-test` (`runTest`). Bestehende Tests als Vorlage:
`core-cloud/src/test/.../CloudSyncUseCaseTest.kt`, `SyncDecisionEngineTest.kt`, `core-scanning/src/test/.../ScannerEngineTest.kt`.

Verifikation pro Phase:

```bash
./gradlew :core-cloud:testDebugUnitTest :core-scanning:testDebugUnitTest :core-tts:testDebugUnitTest
```

Am Ende: `./gradlew testDebugUnitTest` (komplett grün, Baseline 2026-06-11 war grün).

---

## Phase 1 — KRITISCH: Datenverlust bei Zwei-Geräte-Sync verhindern

### 1.1 Problemanalyse (verifiziert im Code)

`versionSequence` wird bei **jeder lokalen Änderung pro Gerät** inkrementiert
(`BookRepository.updateLastModified(...)` hat Default `incrementSequence = true`; alle Edit-UseCases in
`core/src/main/java/.../core/domain/pages/` rufen es so auf). Die Sync-Logik behandelt diese Zahl aber als
**globale Ordnung** — das ist sie nicht. Drei konkrete Datenverlust-Szenarien:

**Szenario A — UPLOAD überschreibt fremde Änderungen ohne Merge**
`CloudSyncOptimizer.evaluateSync()` (core-cloud/.../CloudSyncOptimizer.kt, Regel 3):
`localSeq > remoteSeq → UPLOAD`. Ablauf:
1. Betreuer-Tablet und AAC-Gerät sind beide auf Seq 5 synchron.
2. AAC-Gerät: 1 Änderung → Seq 6, Upload → Cloud hat Seq 6 (Änderung des AAC-Users).
3. Betreuer-Tablet: 3 Änderungen (offline) → Seq 8. Sync: `8 > 6` → **UPLOAD**.
4. Die Cloud-Datei wird ersetzt — die Änderung des AAC-Users ist **weg**. Das Optimistic Lock
   (`uploadWithOptimisticLock`) hilft nicht: `expectedVersion` wird im selben Sync-Lauf frisch gelesen, der Lock
   schützt nur gegen Upload-Races, nicht gegen bereits divergierte Historie.

**Szenario B — DOWNLOAD löscht lokale Änderungen**
Spiegelbild: Gerät mit weniger Edits hat `localSeq < remoteSeq` → DOWNLOAD.
`PageImportExportManager.importFromJson()` löscht zuerst **alle** Seiten des Buchs
(`pageRepository.deletePagesForBook(bookId)`, Zeile ~240) und ersetzt sie vollständig. Lokale Änderungen, die noch
nicht hochgeladen waren, sind weg.

**Szenario C — Stilles Divergieren bei gleicher Sequenz**
`CloudSyncUseCase.syncBook()`, früher NO_OP-Exit (Zeile ~161):

```kotlin
if ((remoteSeqFromProps != null && localSeq == remoteSeqFromProps) || ...)
```

Die erste Bedingung prüft **keinen Inhalt**. Wenn beide Geräte von Seq 5 ausgehend je 1 Änderung machen, stehen
beide auf Seq 6 → NO_OP auf beiden Seiten → die Geräte divergieren still, bis die nächste Änderung per Szenario
A/B eine Seite plattmacht.

Der granulare Merge (`BookMergeEngine.mergeBooks`, LWW pro Seite/Button + Tombstones) ist vorhanden und gut —
er wird nur fast nie ausgewählt (nur bei exakt gleicher Seq **und** unterschiedlichem MD5 oder bei Legacy Seq 0).

### 1.2 Lösungsansatz: Drei-Wege-Erkennung über Sync-Anker

Pro Buch+Gerät wird der **zuletzt gemeinsam synchronisierte Zustand** (Basis) als struktureller MD5 gespeichert.
Beim Sync gilt:

- `localChanged  = localStructMd5  != anchorMd5`
- `remoteChanged = remoteStructMd5 != anchorMd5`
- beide geändert → `MERGE_CONFLICT` (granularer Merge, kein Überschreiben)
- nur lokal → `UPLOAD` · nur remote → `DOWNLOAD` · keiner → `NO_OP`
- **kein Anker vorhanden** (Erstlauf nach Update) und Inhalte verschieden → in `TWO_WAY` immer
  `MERGE_CONFLICT` (Merge ist verlustfrei; Upload/Download nicht). `BACKUP_ONLY`/`RESTORE_ONLY` behalten ihre
  bisherige (bewusst überschreibende) Semantik.

Der strukturelle MD5 existiert bereits: `BookMergeEngine.calculateStructuralMd5FromJson()` und wird schon als
Drive-Property `structure_md5` hochgeladen.

**Woher kommt `remoteStructMd5`?** Primär aus der Drive-Property `structure_md5`. Der
`DocumentFolderSyncStorageProvider` (SAF/lokaler Ordner, SD-Karte) unterstützt **keine** Properties —
`RemoteSyncFile.properties` ist dort immer `null`. In diesem Fall wird die Remote-Datei in `syncBook` ohnehin
zur Evaluierung heruntergeladen (`needDownloadForEvaluation`, greift bei `remoteSeqFromProps == null`); aus dem
heruntergeladenen JSON wird der MD5 direkt berechnet:

```kotlin
val remoteStructMd5 = remoteMasterFile?.properties?.get("structure_md5")
    ?: downloadedRemoteJson?.let { bookMergeEngine.calculateStructuralMd5FromJson(it) }
```

Damit läuft auch der SAF-Sync über den Anker-Pfad statt bei jeder Abweichung in den Merge zu fallen.

**Welcher MD5 wird als Anker gespeichert?** Nach einem DOWNLOAD immer der MD5 des **heruntergeladenen
Remote-JSON** (nicht der eines Re-Exports aus der DB). Begründung: Sollte der Import→Export-Roundtrip nicht
byte-stabil sein, würde ein Re-Export-Anker dauerhaft `remoteChanged=true` melden → endlose Download-Schleife.
Mit dem Remote-JSON-Anker meldet ein instabiler Roundtrip höchstens einmal `localChanged=true` → ein einzelner
Upload, danach stabil. (Bei stabilem Roundtrip sind beide Varianten identisch.)

### 1.3 Umsetzungsschritte

**Schritt 1 — Neue Klasse `SyncAnchorStore`**
Datei: `core-cloud/src/main/java/com/andreas_kratzer/ghosttalk/core/cloud/domain/SyncAnchorStore.kt`

```kotlin
@Singleton
class SyncAnchorStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val prefs get() = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
    fun getAnchorMd5(bookId: String): String? =
        prefs.getString("book_sync_anchor_md5_$bookId", null)
    fun setAnchor(bookId: String, structMd5: String) =
        prefs.edit { putString("book_sync_anchor_md5_$bookId", structMd5) }
    fun clearAnchor(bookId: String) =
        prefs.edit { remove("book_sync_anchor_md5_$bookId") }
}
```

(Gleiches SharedPreferences-File wie `TtsSyncHelper`/`AudioSyncHelper` — bestehende Konvention.)
In Tests wird die Klasse gemockt (`mockk<SyncAnchorStore>`), kein Robolectric nötig.

**Schritt 2 — Struktur-MD5-Normalisierung erweitern (Pflicht, sonst falsche Merges nach App-Updates)**
Datei: `core-cloud/.../domain/BookMergeEngine.kt`, `calculateStructuralMd5FromJson()`. Im `cleanData`-Copy
zusätzlich neutralisieren:

```kotlin
app_version_code = null,
ghosttalk_import_version = null
```

Ohne das ändert ein App-Update den MD5 auf beiden Geräten → der Anker-Pfad meldet fälschlich „beide geändert"
und erzwingt unnötige Merges. **Migrationshinweis:** Bereits hochgeladene `structure_md5`-Properties wurden mit
der alten Normalisierung berechnet und passen nach diesem Fix einmalig nicht mehr → der erste Sync nach dem
Rollout macht pro Buch einmal einen (verlustfreien) Merge und lädt den neuen MD5 hoch; danach stabil. Kein
Migrationscode nötig.

**Schritt 3 — `SyncDecisionEngine` erweitern**
Datei: `core-cloud/.../domain/SyncDecisionEngine.kt`. Signatur erweitern:

```kotlin
fun determineSyncAction(
    localFile: File,
    localSeq: Long,
    localLastModified: Long,
    localStructMd5: String,        // NEU
    remoteFileMd5: String?,
    remoteSeq: Long,
    remoteStructMd5: String?,      // NEU (aus Drive-Property "structure_md5", kann null sein)
    remoteLastModified: Long,
    anchorMd5: String?,            // NEU (aus SyncAnchorStore, kann null sein)
    resolvedBookMode: SyncMode,
    remoteConflictFilesNotEmpty: Boolean
): SyncAction
```

Neue Logikreihenfolge (ersetzt den bisherigen Rumpf) — **Reihenfolge 1/2 ist wichtig: Konfliktdateien
ZUERST**, sonst werden vorhandene `merged_*`-Dateien bei identischem Master nie eingesammelt:
1. `remoteConflictFilesNotEmpty && TWO_WAY` → `MERGE_CONFLICT` (wie im Original-Code: hat Vorrang vor allem).
2. Datei-MD5 identisch (`optimizer.calculateMD5(localFile) == remoteFileMd5`) → `NO_OP`.
3. **Anker-Pfad** (wenn `anchorMd5` und `remoteStructMd5` beide non-null und non-empty — `""` wie `null`
   behandeln, da `calculateStructuralMd5` bei Fehlern `""` liefert):
   `localChanged`/`remoteChanged` wie in 1.2. Mode-Übersetzung — diese Modi sind bewusst überschreibend,
   der Merge-Schutz gilt nur für TWO_WAY:
   - `BACKUP_ONLY`: `localChanged → UPLOAD`, sonst `NO_OP` (auch wenn beide geändert: UPLOAD).
   - `RESTORE_ONLY`: `(localChanged || remoteChanged) → DOWNLOAD`, sonst `NO_OP` (Restore spiegelt die Cloud,
     auch über lokale Änderungen hinweg — entspricht der alten Semantik `action != NO_OP → DOWNLOAD`).
   - `TWO_WAY`: Ergebnis unverändert.
   `remoteStructMd5` kommt aus den Properties oder — bei SAF — aus dem heruntergeladenen JSON (siehe 1.2).
4. **Fallback ohne Anker**: bei `TWO_WAY` und unterschiedlichem Inhalt → `MERGE_CONFLICT` (statt
   seq-basiertem UPLOAD/DOWNLOAD). Bei `BACKUP_ONLY`/`RESTORE_ONLY` bisherige Logik (Timestamps/Seq) beibehalten.
   Die Seq-Vergleichslogik in `CloudSyncOptimizer.evaluateSync` wird damit für TWO_WAY nicht mehr für die
   Richtungsentscheidung verwendet; `evaluateSync` selbst nicht löschen (Tests), aber im Engine-Code nicht mehr
   für TWO_WAY aufrufen.

**Schritt 4 — Frühen NO_OP-Exit in `CloudSyncUseCase.syncBook()` absichern**
Datei: `core-cloud/.../domain/CloudSyncUseCase.kt`, Zeile ~161. Die Bedingung
`(remoteSeqFromProps != null && localSeq == remoteSeqFromProps)` **entfernen**. NO_OP-Frühausstieg nur noch wenn
`remoteStructMd5 != null && localStructMd5 == remoteStructMd5` (Inhaltsgleichheit). Sequenzgleichheit allein ist
kein Beweis für Synchronität (Szenario C).

**Schritt 5 — Anker bei jedem erfolgreichen Sync-Abschluss setzen**
`SyncAnchorStore` in `CloudSyncUseCase`, `BookMergeService` **und** `ImportCloudBackupUseCase` injizieren
(Konstruktor; Hilt löst das auf, Tests reichen Mock durch). Anker setzen bei:
- NO_OP mit Inhaltsgleichheit → `setAnchor(bookId, localStructMd5)`
- erfolgreichem UPLOAD → `setAnchor(bookId, localStructMd5)`
- erfolgreichem DOWNLOAD: direkt **in** `ImportCloudBackupUseCase.downloadAndImport` nach erfolgreichem Import —
  `setAnchor(book.id, bookMergeEngine.calculateStructuralMd5FromJson(remoteJson))`. Das Remote-JSON liegt dort
  bereits im Speicher (`remoteJson`-Variable); kein Rückreichen von Werten durch `syncBook` nötig.
  **Wichtig:** den MD5 aus dem heruntergeladenen JSON berechnen, nicht aus einem DB-Re-Export (Begründung in 1.2,
  Abschnitt „Welcher MD5 wird als Anker gespeichert?").
- Merge mit `MergeStatus.SUCCESS` → `setAnchor(bookId, mergedStructMd5Upload)` (in `BookMergeService` direkt
  nach bestätigtem Upload setzen; bei Trivial-Merge/Fast-Forward: `setAnchor(bookId, remoteMasterStructMd5)`).
- **Nicht** setzen bei `MERGED_LOCALLY_UPLOAD_PENDING` — der nächste Lauf erkennt dann korrekt
  „nur lokal geändert" → UPLOAD des bereits gemergten Stands.
- `ImportCloudBackupUseCase.execute()` (manueller Import): nach Erfolg ebenfalls Anker auf den MD5 des
  importierten JSON setzen (bei ZIP-Importen: MD5 aus dem extrahierten `backup.json`).

**Schritt 6 — Aufrufstelle in `syncBook` anpassen**
`decisionEngine.determineSyncAction(...)` (Zeile ~286) mit den neuen Parametern versorgen:
`localStructMd5` (existiert schon als Variable), `remoteStructMd5` (Property oder aus dem Evaluierungs-Download
berechnet, siehe 1.2), `anchorMd5 = syncAnchorStore.getAnchorMd5(bookId)`. Das heruntergeladene
Evaluierungs-JSON (`needDownloadForEvaluation`-Block, Z. ~254-283) dafür in einer Variable halten statt nur
`remoteSeq` daraus zu lesen. Außerdem das Variable-Shadowing beheben: das innere `val remoteSeqFromProps`
(Zeile ~244) umbenennen oder die äußere Variable verwenden.

### 1.4 Unit-Tests Phase 1

**`SyncDecisionEngineTest.kt` (erweitern):**

| Testname | Setup | Erwartung |
|---|---|---|
| `both sides changed since anchor returns MERGE_CONFLICT even if local seq is higher` | anchor="A", local="L", remote="R", localSeq=8, remoteSeq=6, TWO_WAY | `MERGE_CONFLICT` |
| `both sides changed since anchor returns MERGE_CONFLICT even if remote seq is higher` | wie oben, localSeq=6, remoteSeq=8 | `MERGE_CONFLICT` |
| `only local changed since anchor returns UPLOAD` | anchor="A", local="L", remote="A" | `UPLOAD` |
| `only remote changed since anchor returns DOWNLOAD` | anchor="A", local="A", remote="R" | `DOWNLOAD` |
| `nothing changed since anchor returns NO_OP` | anchor="A", local="A", remote="A", Datei-MD5 ungleich (Whitespace) | `NO_OP` |
| `missing anchor with differing content returns MERGE_CONFLICT in TWO_WAY` | anchor=null, localSeq=8, remoteSeq=6 | `MERGE_CONFLICT` |
| `missing anchor keeps BACKUP_ONLY upload semantics` | anchor=null, BACKUP_ONLY, lokal neuer | `UPLOAD` |
| `anchor path translates local change to DOWNLOAD in RESTORE_ONLY` | nur lokal geändert, RESTORE_ONLY | `DOWNLOAD` (Restore spiegelt die Cloud) |
| `anchor path uploads in BACKUP_ONLY even when both sides changed` | beide geändert, BACKUP_ONLY | `UPLOAD` |

**`BookMergeEngine`-Tests (neue Datei `BookMergeEngineTest.kt` oder bestehende erweitern):**
- `calculateStructuralMd5FromJson ignores app_version_code and import_version` — zwei JSONs, identischer Inhalt,
  unterschiedliche `app_version_code`/`ghosttalk_import_version` → gleicher MD5.

**`CloudSyncUseCaseTest.kt` (erweitern, Setup wie bestehende Tests mit `spyStorageResolver` und gefakten Remote-Files):**

| Testname | Szenario | Erwartung |
|---|---|---|
| `syncBook merges instead of uploading when both devices changed since last sync` | Remote-Props: `version_sequence=6`, `structure_md5="R"`; lokal Seq 8, `structure_md5="L"`; Anker "A" | `BookMergeService.performMergeConflict` wird aufgerufen (coVerify), **kein** `updateFile`/`uploadWithOptimisticLock` mit lokalem Stand davor |
| `syncBook does not early-exit as NO_OP when sequences are equal but content differs` | Remote `version_sequence == localSeq`, `structure_md5` ≠ lokal | Merge wird angestoßen; `addLogEntry("Inhalte sind identisch (NO_OP)", ...)` wird **nicht** geloggt |
| `syncBook sets sync anchor after successful upload` | nur lokal geändert | `syncAnchorStore.setAnchor(bookId, localStructMd5)` verifiziert |
| `syncBook does not set anchor when merge upload is pending` | Merge ok, Upload abgewiesen (`MERGED_LOCALLY_UPLOAD_PENDING`) | `setAnchor` nie aufgerufen, `syncBook` → `false` |
| `second sync after pending merge uploads without re-merging` | Anker = Vor-Merge-Stand, lokal = gemergter Stand, remote unverändert | Aktion `UPLOAD` |
| `syncBook uses anchor path with SAF provider without properties` | SAF-Provider (`RemoteSyncFile.properties == null`), Anker gesetzt, nur lokal geändert | Aktion `UPLOAD` (kein Merge), `remoteStructMd5` aus dem Evaluierungs-Download berechnet |

**`ImportCloudBackupUseCaseTest.kt` (erweitern):**
- `downloadAndImport sets anchor to struct md5 of downloaded remote json` — Mock-Provider liefert JSON;
  nach Erfolg `setAnchor(bookId, <md5 des gelieferten JSON>)` verifiziert.
- `downloadAndImport does not set anchor when import fails` — Import-Result Failure → `setAnchor` nie aufgerufen.

**`BookMergeServiceTest.kt` (erweitern):**
- `performMergeConflict sets anchor to merged struct md5 on full success`
- `performMergeConflict sets anchor to remote struct md5 on trivial fast-forward merge`

### 1.5 Bewusst NICHT ändern
- `BookMergeEngine.mergeBooks` auf **Seitenebene** (Page-LWW + Page-Tombstones) bleibt unverändert.
  Die **Button-Ebene** wird in Phase 1B repariert (echte Lücke, siehe dort).
- Optimistic-Lock-Fallback `UPLOAD → MERGE_CONFLICT` (Zeile ~389) bleibt als zweites Netz bestehen.
- `versionSequence` weiter pflegen (Properties, Merge `maxOf(...)+1`) — dient weiterhin Diagnose & Legacy-Clients.

---

## Phase 1B — KRITISCH: Stille Button-Löschung beim Merge (Button-Tombstones)

### 1B.1 Problemanalyse (verifiziert)

Buttons werden sparse exportiert — ein gelöschter Button fehlt einfach in der Liste. `BookMergeEngine.mergeBooks`
unterscheidet „Button gelöscht" von „Button auf der anderen Seite neu" nur per **Seiten-Zeitstempel-Heuristik**
(Z. ~71-76):

```kotlin
if (localButton == null && remoteButton != null) {
    val remoteTime = remoteButton.updatedAt ?: 0L
    if (localPageTime >= remoteTime) null else remoteButton
}
```

Datenverlust-Szenario (genau der Betreuer/AAC-Fall):
1. Betreuer legt auf seinem Tablet Button B auf Seite P an (`B.updatedAt = T1`).
2. AAC-User bearbeitet **gleichzeitig** auf Seite P den Button A → `localPageTime = T2 > T1`.
3. Merge auf dem AAC-Gerät: B fehlt lokal, `localPageTime (T2) >= remoteTime (T1)` → Engine interpretiert
   „lokal gelöscht" → **Button B des Betreuers wird verworfen.**

Die Infrastruktur für die Lösung existiert bereits: `DeletedEntity.entityType` kennt `"BUTTON"`
(core-database/.../DeletedEntity.kt), und `PageRepositoryImpl.purgeInstallUpdateButtons()` schreibt heute schon
Button-Tombstones. Es fehlt nur das systematische Schreiben bei normalen Löschungen und die Auswertung im Merge.

### 1B.2 Umsetzungsschritte

**Schritt 1 — Button-Tombstones beim Speichern schreiben**
Datei: `core-data/.../impl/PageRepositoryImpl.kt`, Methode `updatePage(page: Page)` (Z. ~53). Der zentrale
Schreibpfad ist „delete-all + insert" — dort den Diff bilden, **innerhalb** der bestehenden Transaktion:

```kotlin
override suspend fun updatePage(page: Page) {
    val now = System.currentTimeMillis()
    val finalUpdatedAt = maxOf(now, page.updatedAt)
    val updatedPage = page.copy(updatedAt = finalUpdatedAt)
    appDatabase.withTransaction {
        // Diff: welche Button-IDs verschwinden mit diesem Update?
        val oldButtonIds = buttonDao.getButtonsForPage(updatedPage.id).map { it.id }.toSet()
        val newButtonIds = updatedPage.buttonConfigs.mapNotNull { it?.id }.toSet()
        (oldButtonIds - newButtonIds).forEach { deletedId ->
            appDatabase.deletedEntityDao().insertDeletedEntity(
                DeletedEntity(entityId = deletedId, entityType = "BUTTON", bookId = updatedPage.bookId)
            )
        }
        pageDao.updatePageEntity(updatedPage)
        buttonDao.deleteButtonsForPage(updatedPage.id)
        buttonDao.insertButtons(updatedPage.toButtonEntities())
    }
}
```

Falls `ButtonDao` keine `getButtonsForPage(pageId)`-Query hat, ergänzen
(`@Query("SELECT * FROM buttons WHERE pageId = :pageId")`).
**Achtung Button-Verschiebung:** `MoveButtonToPageUseCase` u. ä. aktualisieren Quell- und Zielseite — der Diff auf
der Quellseite schreibt dann einen Tombstone, obwohl der Button auf der Zielseite weiterlebt. Das ist
unkritisch: Der Merge-Check (Schritt 2) greift nur, wenn der Button auf einer Seite **fehlt**, und die
Zielseiten-Kopie hat ein neueres `updatedAt` als der Tombstone-Zeitpunkt nie überschreitet. Schlimmster Fall bei
gleichzeitiger Fremd-Bearbeitung: ein Duplikat statt Datenverlust — akzeptiert.
**Prüfen:** Weitere Lösch-Pfade an `buttonDao` per `grep -rn "deleteButton\|deleteEmptyButtons" core*/` suchen;
`deleteEmptyButtons()` (Maintenance) bei Bedarf ebenfalls mit Tombstones versehen oder begründet auslassen
(leere Buttons resurrecten schlimmstenfalls leer — vertretbar, im Code kommentieren).

**Schritt 2 — `BookMergeEngine` auf Button-Tombstones umstellen**
Datei: `core-cloud/.../domain/BookMergeEngine.kt`, Button-Merge-Block (Z. ~64-84). Die Tombstone-Maps sind
bereits am Methodenanfang gebaut (`localTombstones`/`remoteTombstones` per `entityId`) — sie enthalten künftig
auch BUTTON-Einträge. Neue Logik für die Einseitig-fehlt-Fälle (nur für Buttons **mit** stabiler ID; Buttons mit
`id == null`, also `idx_`-Schlüssel, behalten die bisherige Zeitstempel-Heuristik als Legacy-Fallback):

```kotlin
if (localButton == null && remoteButton != null) {
    val tombstone = remoteButton.id?.let { localTombstones[it] }?.takeIf { it.entityType == "BUTTON" }
    if (tombstone != null) {
        val remoteTime = remoteButton.updatedAt ?: 0L
        if (tombstone.deletedAt >= remoteTime) null else remoteButton  // bewusst gelöscht vs. danach geändert
    } else if (remoteButton.id == null) {
        if (localPageTime >= (remoteButton.updatedAt ?: 0L)) null else remoteButton  // Legacy-Heuristik
    } else {
        remoteButton  // kein Tombstone -> nie gelöscht -> ist NEU -> behalten!
    }
}
```

Spiegelbildlich für `remoteButton == null && localButton != null` mit `remoteTombstones`.
Voraussetzung: `ImportExportData.deletedEntities` transportiert `entityType` bereits (im Modell vorhanden,
`importFromJson` schreibt es mit) — verifizieren, dass `exportBookToJson` **alle** Tombstone-Typen des Buchs
exportiert, nicht nur PAGE (grep nach `getTombstonesForBook`).

**Schritt 3 — Tombstone-Konsolidierung im Merge erweitern**
`mergedTombstones` (Z. ~121-131) filtert bisher nur gegen überlebende **Seiten**. Erweitern: einen
BUTTON-Tombstone verwerfen, wenn ein Button mit dieser ID in `mergedPages` existiert; PAGE-Tombstones wie bisher
gegen `mergedPages`-IDs. (Verhindert, dass ein bewusst wiederhergestellter Button einen alten Tombstone
mitschleppt.) 90-Tage-Cutoff bleibt.

**Schritt 4 — Bestandsdaten**
Kein Migrationscode: Für Löschungen, die **vor** diesem Update passierten, existieren keine Button-Tombstones —
dort gilt einmalig weiter die alte Heuristik (Buttons können resurrecten, kein Verlust). Ab dem Update ist jede
neue Löschung abgesichert.

**Schritt 5 — PFLICHT: Sync-Import darf Timestamps nicht überstempeln (im Review 1B gefunden)**
`PageRepositoryImpl.insertPage()` setzt `updatedAt = now` auf die Seite UND auf **alle** Buttons
(`page.copy(updatedAt = now, buttonConfigs = …copy(updatedAt = now))`). `importFromJson` (PageImportExportManager,
`pageRepository.insertPage(page)` bei Z. ~413) läuft durch genau diese Methode — obwohl das Page-Objekt dort
korrekt mit den Remote-Timestamps gebaut wird (Z. ~411, Button-Mapping Z. ~369). Folgen:
1. Nach jedem Download/Merge-Import tragen alle lokalen Seiten/Buttons `now` → im **nächsten** Merge gewinnt
   ungeänderter lokaler Inhalt per LWW gegen echte, früher getätigte Edits des anderen Geräts → stiller
   Datenverlust (genau die Klasse, die Phase 1B schließt). Auch der Tombstone-Vergleich
   (`deletedAt >= updatedAt`) kippt: gelöschte Buttons resurrecten, weil der Import sie „frisch" stempelt.
2. Anker-Churn: Der Re-Export nach einem Download hat andere Timestamps als das Remote-JSON →
   `localStructMd5 != anchorMd5` → jeder Folge-Sync meldet „lokal geändert" und lädt die gestempelten
   Timestamps in die Cloud.

Fix: Neue Methode `insertPageRaw(page: Page)` in `PageRepository`/`PageRepositoryImpl` — identisch zu
`insertPage`, aber **ohne** Timestamp-Stamping (Page und Buttons unverändert übernehmen). `importFromJson`
(Z. ~413) auf `insertPageRaw` umstellen. Alle anderen `insertPage`-Aufrufer (UI-Flows, `defaultStaticRowPage`
Z. ~478) bleiben auf der stempelnden Variante. KDoc an beide Methoden: wer wofür.

Tests:
- `PageRepositoryTest`: `insertPageRaw preserves page and button updatedAt` (Page mit `updatedAt = 1234`,
  Button mit `updatedAt = 999` → an die DAOs unverändert durchgereicht, `coVerify` mit `match`).
- Regression: `insertPage still stamps updatedAt` (Altverhalten der UI-Pfade bleibt).

### 1B.3 Unit-Tests Phase 1B

**`BookMergeEngineTest.kt`:**

| Testname | Setup | Erwartung |
|---|---|---|
| `concurrently added remote button survives merge when local page was edited later` | Remote-Seite mit neuem Button B (`updatedAt=T1`), lokale Seite ohne B aber `pageTime=T2>T1`, **kein** Tombstone | B im Merge-Ergebnis enthalten |
| `locally deleted button stays deleted when tombstone is newer than remote edit` | Lokal: B fehlt + BUTTON-Tombstone `deletedAt=T3`; Remote: B `updatedAt=T1<T3` | B nicht im Ergebnis |
| `remote edit after local delete resurrects button` | Tombstone `deletedAt=T3`; Remote: B `updatedAt=T4>T3` | B im Ergebnis (bewusste spätere Änderung gewinnt) |
| `buttons without id keep legacy page-time heuristic` | Remote-Button mit `id=null`, `localPageTime` neuer | Button verworfen (Altverhalten) |
| `button tombstone is dropped when button exists in merged result` | Tombstone für B, B überlebt den Merge | `deletedEntities` ohne B-Eintrag |

**Neue Datei `core-data/src/test/.../PageRepositoryImplTombstoneTest.kt`** (falls Room-Test-Setup im Modul
existiert; sonst die Diff-Logik in eine pure Funktion extrahieren — z. B.
`computeDeletedButtonIds(old: Set<String>, new: Set<String>): Set<String>` — und diese testen):
- `updatePage writes BUTTON tombstone for removed button id`
- `updatePage writes no tombstone when button set is unchanged or grows`

---

## Phase 2 — KRITISCH: TTS-Fallback ruft `onDone` doppelt/zu früh (Scanning-Timing kaputt)

### 2.1 Problemanalyse

`ElevenLabsTtsProvider` ruft in **allen Fehlerpfaden** `onError` **und** `onDone` auf (API-Key fehlt Z. ~75,
offline Z. ~107, `onFailure` Z. ~141, HTTP-Fehler Z. ~154, Speicherfehler Z. ~179). `FallbackTtsProvider.speakRouted`
reicht bei `onError` denselben `onDone` an den Fallback-Provider weiter. Folge beim Scannen mit ElevenLabs ohne
Netz/Cache:

1. Primary schlägt fehl → `onError` → Android-TTS-Fallback beginnt zu sprechen.
2. Primary ruft direkt danach `onDone` auf → `TtsScannerFeedbackProvider.speakCue` resumed die Coroutine →
   **der Scanner springt zum nächsten Button, während der Fallback den vorherigen Cue noch spricht.**
   Überlappende Ansagen, Fokus und Audio laufen auseinander — genau die Frustration, die vermieden werden soll.
3. Später ruft der Fallback `onDone` ein zweites Mal (vom Guard `continuation.isActive` nur vor Crash geschützt).

Gleiches Muster in `AndroidTtsProvider.onInit` Fehlerpfad (Z. ~136-138: `onError` + `onDone`).

### 2.2 Umsetzung

**Vertrag festlegen und dokumentieren** (KDoc an `TtsProvider.speak`/`speakRouted`):
> Pro Aufruf wird genau einer der Callbacks terminal ausgelöst: `onDone` bei Erfolg, `onError` bei Fehlschlag.
> Niemals beide.

**Schritt 1 — `ElevenLabsTtsProvider`:** in allen fünf Fehlerpfaden den `onDone?.invoke()` entfernen, `onError`
bleibt. Erfolgsfad unverändert (nur `onDone` nach Abspielende).
**Ausnahme Cancel-Pfad (im Review Phase 2 gefunden):** Im `onFailure`-Callback gilt für `isCanceled == true`
weder Fehler noch Erfolg-im-Sprechsinn — aber es muss trotzdem **genau ein** Callback feuern, sonst hängen
Aufrufer, die auf den Abschluss einer geflushten Ansage warten (der Scanner stünde bis zu 5 s im Timeout statt
sofort weiterzulaufen). Richtig: `if (isCanceled) onDone?.invoke() else onError?.invoke(...)` — Cancel beendet
die Ansage ohne Fallback (onError würde fälschlich den Android-Fallback starten).
Test (FallbackTtsProviderTest oder ElevenLabs-Ebene): „canceled call completes via onDone and does not trigger
fallback".

**Schritt 2 — `AndroidTtsProvider`:** im `onInit`-Fehlerpfad nur `onError` aufrufen. In den Pfaden, die heute
bei `tts.speak == ERROR` / `synthesizeToFile == ERROR` nur `onDone` aufrufen (Z. ~205, ~222): auf `onError`
umstellen (das ist ein Fehler, kein Erfolg). Dass dadurch kein Aufrufer hängen bleibt, stellt Schritt 3 zentral
sicher — kein App-weites Aufrufer-Audit nötig.

**Schritt 3 — Zentraler Schutz in `TextToSpeechHelper`: Aufrufer ohne `onError` dürfen nie hängen.**
Viele Aufrufer (`ActionExecutor`, Notification-Vorlesen, …) übergeben nur `onDone` und warten darauf. Damit der
neue Vertrag („bei Fehler nur `onError`") sie nicht blockiert, leitet `TextToSpeechHelper.speakRouted` (und
`speak`) einen fehlenden `onError` auf `onDone` um — an genau einer Stelle, durch die alle Aufrufe laufen:

```kotlin
val resolvedOnError = onError ?: { error ->
    Log.w("TextToSpeechHelper", "No onError provided, completing via onDone: $error")
    onDone?.invoke()
}
provider.speakRouted(text, deviceAddress, queueMode, isForCues, onDone, resolvedOnError)
```

Wichtig: Die Umleitung passiert **nach** dem `FallbackTtsProvider` (der Helper reicht `resolvedOnError` als
äußersten `onError` hinein) — d. h. sie feuert nur, wenn auch der Fallback gescheitert ist. Der Scanner
(`TtsScannerFeedbackProvider`) übergibt kein `onError` und wird so im Totalausfall sauber fortgesetzt; sein
`withTimeoutOrNull(5000)` bleibt als letztes Netz.

**Schritt 4 — `FallbackTtsProvider` defensiv machen** (Schutz auch gegen künftige Vertragsverletzungen):

```kotlin
override fun speakRouted(...) {
    val fallbackTriggered = java.util.concurrent.atomic.AtomicBoolean(false)
    val doneOnce = java.util.concurrent.atomic.AtomicBoolean(false)
    fun safeDone() { if (doneOnce.compareAndSet(false, true)) onDone?.invoke() }
    fun safeError(error: String) { if (doneOnce.compareAndSet(false, true)) onError?.invoke(error) }
    primary.speakRouted(text, deviceAddress, queueMode, isForCues,
        onDone = { if (!fallbackTriggered.get()) safeDone() },
        onError = { error ->
            // Kein Fallback mehr starten, wenn der Primary bereits erfolgreich fertig war
            // (vertragswidriges spätes onError) — und nur genau ein Fallback-Lauf.
            if (!doneOnce.get() && fallbackTriggered.compareAndSet(false, true)) {
                onFallbackTriggered(error)
                fallback.speakRouted(text, deviceAddress, queueMode, isForCues,
                    onDone = { safeDone() }, onError = { e -> safeError(e) })
            }
        })
}
```

Analog für `speak(...)`. Damit terminiert jeder Aufruf gegenüber dem Aufrufer **genau einmal** — entweder
`onDone` oder `onError`, nie beides, nie doppelt.

**Schritt 5 — Thread-Sicherheit `AndroidTtsProvider.pendingRequests`:** Zugriffe in `speakRouted` (add) und
`onInit` (copy+clear) mit `synchronized(pendingRequests)` schützen — `speakRouted` ist von beliebigen Threads
aufrufbar (Scanner-Coroutine), `onInit` läuft auf dem TTS-Binder-/Main-Thread.

**Schritt 6 — Datei-Leak bei QUEUE_FLUSH:** In `speakRouted` (Z. ~183-188) beim Leeren von `playRequests`
zusätzlich `it.file.delete()` aufrufen (analog zu `stopAll`).

### 2.3 Unit-Tests Phase 2

Neue Datei `core-tts/src/test/java/com/andreas_kratzer/ghosttalk/core/tts/FallbackTtsProviderTest.kt`
(reiner JVM-Test, `TtsProvider`-Fakes ohne Android-Abhängigkeiten):

| Testname | Setup | Erwartung |
|---|---|---|
| `onDone fires exactly once when primary fails and fallback succeeds` | Fake-Primary ruft `onError("x")` und (vertragswidrig) danach `onDone` auf; Fake-Fallback ruft `onDone` nach „Sprechen" | Aufrufer-`onDone`-Zähler == 1, ausgelöst **vom Fallback** |
| `onDone is not fired before fallback completes` | Fallback hält `onDone` zurück (manuell auslösbar) | Aufrufer-`onDone` erst nach Fallback-Trigger |
| `onDone fires once when primary succeeds` | Primary ruft nur `onDone` | Zähler == 1, Fallback nie aufgerufen |
| `onFallbackTriggered fires exactly once per utterance` | Primary ruft `onError` doppelt | `onFallbackTriggered`-Zähler == 1 |
| `late onError after successful onDone does not start fallback` | Primary ruft erst `onDone`, dann (vertragswidrig) `onError` | Fallback nie aufgerufen, `onDone`-Zähler == 1 |
| `caller onError fires once when both providers fail` | Primary und Fallback rufen `onError` | Aufrufer-`onError` == 1, `onDone` == 0 |
| `speak delegates with same guarantees` | wie oben für `speak()` | identisch |

`TextToSpeechHelperTest.kt` (besteht): Regressionstests ergänzen —
- `speakRouted with elevenlabs engine and failing primary advances caller exactly once`
  (ElevenLabs-Provider-Mock, der `onError`+`onDone` feuert; Assertion: übergebener `onDone` genau 1×).
- `speakRouted without onError completes via onDone when provider fails`
  (Provider-Mock ruft nur `onError`; Aufrufer übergibt nur `onDone`; Assertion: `onDone` genau 1× — der
  zentrale Wrapper aus Schritt 3 greift).

`TtsScannerFeedbackProviderTest.kt` (besteht): Test ergänzen —
`speakCue waits for single onDone and does not resume early on error-then-done sequence`.

---

## Phase 3 — Scanning: Resume-Logik & Robustheit

### 3.1 Doppelter Offset beim Fortsetzen mit statischer Zeile (Bug)

`ScanCoordinator.resumeScanningIfEnabled()` (Z. ~338) übergibt als `startIndex` den Wert aus
`scannerEngine.focusedButtonIndex` — der ist bereits in **kombinierten** Koordinaten (statische Zeile = Slots 0-48,
Hauptseite ab Slot 49). `ScannerEngine.startScanning()` (Z. ~146-148) addiert bei
`staticRowPage != null && startIndex > 0 && pattern == "linear"` nochmals `staticRowOffset` (49) — **doppelter
Offset**: Resume landet 49 Slots zu weit oder am Listenende.

**Fix:** Die Offset-Addition in `ScannerEngine.startScanning` ersatzlos entfernen. Konvention dokumentieren
(KDoc am Parameter): `startIndex` ist immer in kombinierten Koordinaten. Beide Aufrufer
(`ScanCoordinator.resumeScanningIfEnabled` mit Engine-Fokuswert, `ScanCoordinator.startScanning` mit 0) erfüllen
das bereits.

**Tests (`ScannerEngineTest.kt` erweitern):**
- `startScanning with static row and combined startIndex resumes at that button` — staticRowPage gesetzt,
  `startIndex = 52` (Hauptseite, lokal Slot 3), linear: erster fokussierter Index muss `52` sein (nicht `101`).
- `startScanning with static row and startIndex inside static row resumes there` — `startIndex = 2` → erster
  Fokus `2`.

### 3.2 Row-/Button-Index-Vermischung beim Resume in `RowByRowScanStrategy`

`executeScan` (Z. ~85-95) vergleicht `context.startIndex` sowohl gegen `ScanStep.Button.index`
(0-97, kombiniert) als auch gegen `ScanStep.Row.rowIndex` (0-7) mit **einem** Wert. Resume von
`focusedRowIndex = 2` matcht fälschlich den ersten statischen Button mit Index ≥ 2.

**Fix:** `ScanContext` (Datei `ScanStrategy.kt`) erweitern:

```kotlin
sealed interface ResumePoint {
    data class AtButton(val combinedIndex: Int) : ResumePoint
    data class AtRow(val rowIndex: Int) : ResumePoint
}
// in ScanContext:
val resumePoint: ResumePoint? = null   // ersetzt die Resume-Semantik von startIndex
```

`RowByRowScanStrategy.executeScan`: `indexOfFirst` matcht `AtButton` nur gegen `ScanStep.Button`,
`AtRow` nur gegen `ScanStep.Row`. `LinearScanStrategy`: nur `AtButton` relevant.
`ScanCoordinator.resumeScanningIfEnabled`: baut `ResumePoint` aus `focusedButtonIndex` (falls gesetzt), sonst
`focusedRowIndex`, sonst `null`; `startIndex` als Parameter kann für die Engine-API bleiben (0 = Start), wird aber
intern auf `resumePoint` abgebildet — Gemini darf alternativ `startIndex` komplett durch `resumePoint` ersetzen,
dann alle Aufrufer + Tests anpassen.

**Tests (`RowByRowScanStrategyTest.kt` erweitern):**
- `resume at row skips static row buttons and starts at matching row step`
- `resume at button with static row does not match row steps`
- `resume falls back to first step when resume point not found`

### 3.3 Magic Number 49 zentralisieren

Konstante in `core-scanning` (z. B. `ScanGrid.STATIC_ROW_SLOT_COUNT = 49` als `const val` in neuer Datei oder
Companion von `ScannerEngine`). Alle Vorkommen in `ScannerEngine`, `LinearScanStrategy`, `RowByRowScanStrategy`
ersetzen (`grep -n "49" core-scanning/src/main`). Reine Refactoring-Phase, bestehende Tests müssen grün bleiben.

### 3.4 Scan-Geschwindigkeit live übernehmen (UX)

`ScanContext.delayMillis` ist ein Snapshot beim Scan-Start; Änderungen am Tempo greifen erst nach Neustart des
Scans. Fix: Feld zu `val delayMillis: () -> Long` ändern, Engine übergibt `{ scanTimer.scanDelayMillis }`,
Strategien rufen `delay(context.delayMillis())` pro Tick. Tests anpassen (bestehende Strategy-Tests übergeben
dann `{ 1000L }`), neuer Test:
- `strategy uses updated delay on next tick` — Lambda liefert erst 1000, nach erstem Tick 200; mit
  `runTest`-Virtual-Time prüfen, dass der zweite Tick 200 ms wartet.

### 3.5 Bewusst NICHT ändern
- `synchronized`-Monitor in `ScannerEngine` (dokumentiert, getestet).
- Prediction-Timeout-Logik in `ScanCoordinator` (funktional ok; Timeout = Scan-Delay ist vertretbar).

---

## Phase 4 — Kleinere Härtungen (niedrige Priorität, einzeln umsetzbar)

**4.1 `AudioCacheRepository`: Base64-Trennzeichen-Bug.**
URL-safe Base64 enthält legitim `-`; `parts[1].substringBefore("-")` (Z. 37) zerschneidet daher normale Texte →
Cache-Verwaltung zeigt falsche/fehlende Einträge. Fix: In `ElevenLabsTtsProvider.getCacheFile` den Hash-Suffix mit
`~` statt `-` anhängen (`"${base64Text.take(100)}~$hash"`), `AudioCacheRepository` parst `substringBefore("~")`;
für Alt-Dateien zusätzlich: wenn kein `~` enthalten, kompletten `parts[1]` dekodieren und bei
`IllegalArgumentException` Eintrag wie bisher überspringen (kein Crash).
Test (neu, `AudioCacheRepositoryTest`): Roundtrip mit Text, dessen Base64 ein `-` enthält (z. B. `"Hallo Welt?"`)
→ dekodierter Text stimmt exakt.

**4.2 `TtsSyncHelper` TWO_WAY bei beidseitiger Änderung:** aktuell wird nur heruntergeladen und der Anker
überschrieben — lokal neue Cache-Dateien werden nie hochgeladen. Wie `AudioSyncHelper` (Z. 45-63) lösen: erst
Download+Import (Datei-Merge auf Platte), dann `hasLocalChanged=true/hasRemoteChanged=false` setzen → Upload des
gemergten Stands. Test: `syncTtsCache merges and re-uploads when both sides changed` (Storage-Provider-Mock:
erst `downloadFile`, dann `updateFile` verifizieren).

**4.3 `TextToSpeechHelper.isReadyFlow`:** 100-ms-Endlos-Polling ersetzen. `AndroidTtsProvider` bekommt
`private val _isReadyFlow = MutableStateFlow(false)` (auf `true` in `onInit`-Erfolg), `TtsProvider` erhält
`val isReadyFlow: StateFlow<Boolean>` (ElevenLabs: aus `isInitialized` speisen; Fallback: `combine(primary, fallback) { p, f -> p || f }`
— als `stateIn` im Helper). Helper-`isReadyFlow` wird `currentProviderFlow.flatMapLatest { it.isReadyFlow }`.
Bestehende `TextToSpeechHelperTest` anpassen.

**4.4 `CloudSyncUseCase`:** Variable-Shadowing `remoteSeqFromProps` (Z. 244) bereinigen (siehe Phase 1 Schritt 5)
und die auskommentierten `configSyncHelper`-Blöcke (Z. 169-181, 432-445) löschen oder per Issue reaktivieren —
toter Code im kritischsten Pfad.

**4.5 Clock-Skew-Warnung:** `BookMergeEngine`-Entscheidungen sind wall-clock-basiert. Minimal: in
`CloudSyncUseCase.syncBook` warnen, wenn `remoteMasterFile.modifiedTime > System.currentTimeMillis() + 5min`
(Sync-Log-Eintrag, kein Abbruch). Kein Test nötig, reine Diagnose.

---

## Empfohlene Reihenfolge & Abnahme

1. **Phase 1** (Sync-Richtungsentscheidung / Anker) — größtes Risiko, klar abgegrenzt, primär `core-cloud`.
2. **Phase 1B** (Button-Tombstones) — schließt die zweite Datenverlust-Lücke auf Merge-Ebene; baut nicht auf
   Phase 1 auf und kann bei Bedarf vorgezogen oder parallel umgesetzt werden (`core-data` + `core-cloud`).
3. **Phase 2** (TTS-Doppel-onDone) — direkt spürbare Benutzermodus-Stabilität, rein in `core-tts`.
4. **Phase 3** (Scanning-Resume) — Bugs treffen nur Konfigurationen mit statischer Zeile + Resume.
5. **Phase 4** — nach Bedarf.

Abnahmekriterien pro Phase:
- Alle neuen Tests grün, keine bestehenden Tests rot: `./gradlew testDebugUnitTest`.
- Keine API-Änderung ohne Anpassung aller Aufrufer (`grep` vor Commit).
- Manuelle Smoke-Checks (nicht automatisierbar): Zwei-Geräte-Sync mit gegenläufigen Änderungen → beide Änderungen
  nach beidseitigem Sync auf beiden Geräten vorhanden. Speziell Phase 1B: Gerät A legt Button auf Seite P an,
  Gerät B bearbeitet gleichzeitig einen anderen Button auf P → nach Sync existieren beide; Gerät A löscht einen
  Button → nach Sync auf B ebenfalls gelöscht. Scannen mit ElevenLabs im Flugmodus → Cues kommen einzeln
  und nacheinander vom Android-TTS.
