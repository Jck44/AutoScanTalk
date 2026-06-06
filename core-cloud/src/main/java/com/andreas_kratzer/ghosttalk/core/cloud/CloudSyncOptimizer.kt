package com.andreas_kratzer.ghosttalk.core.cloud

import java.io.File
import java.security.MessageDigest

class CloudSyncOptimizer {

    fun calculateMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read = stream.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = stream.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Evaluiert den Sync-Zustand inhalts- und versionsbasiert.
     * Schützt vor Endlosschleifen durch mutierte Datei-Zeitstempel.
     */
    fun evaluateSync(localFile: File, remoteMd5: String?, localSeq: Long, remoteSeq: Long): SyncAction {
        // Regel 1: Wenn der Inhalt identisch ist, mache absolut gar nichts (No-Op)
        if (calculateMD5(localFile) == remoteMd5) {
            return SyncAction.NO_OP
        }

        // Regel 2: Wenn eine der beiden Seiten Legacy ist (Sequenznummer 0), erzwinge MERGE_CONFLICT,
        // damit ein granularer Abgleich anhand der Detail-Zeitstempel (updatedAt) stattfindet.
        if (localSeq == 0L || remoteSeq == 0L) {
            return SyncAction.MERGE_CONFLICT
        }
        
        // Regel 3: Inhalt unterscheidet sich, entscheide anhand der logischen Sequenz
        return when {
            localSeq > remoteSeq -> SyncAction.UPLOAD
            remoteSeq > localSeq -> SyncAction.DOWNLOAD
            else -> SyncAction.MERGE_CONFLICT // Gleiche Sequenz, abweichender MD5-Inhalt
        }
    }
}

enum class SyncAction { NO_OP, UPLOAD, DOWNLOAD, MERGE_CONFLICT }
