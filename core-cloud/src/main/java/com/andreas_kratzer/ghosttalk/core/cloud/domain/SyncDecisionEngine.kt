package com.andreas_kratzer.ghosttalk.core.cloud.domain

import com.andreas_kratzer.ghosttalk.core.cloud.CloudSyncOptimizer
import com.andreas_kratzer.ghosttalk.core.cloud.SyncAction
import java.io.File
import javax.inject.Inject

class SyncDecisionEngine @Inject constructor() {
    private val optimizer = CloudSyncOptimizer()

    fun determineSyncAction(
        localFile: File,
        localSeq: Long,
        localLastModified: Long,
        localStructMd5: String,
        remoteFileMd5: String?,
        remoteSeq: Long,
        remoteStructMd5: String?,
        remoteLastModified: Long,
        anchorMd5: String?,
        resolvedBookMode: SyncMode,
        remoteConflictFilesNotEmpty: Boolean
    ): SyncAction {
        // 1. remoteConflictFilesNotEmpty && TWO_WAY -> MERGE_CONFLICT (must be checked first)
        if (remoteConflictFilesNotEmpty && resolvedBookMode == SyncMode.TWO_WAY) {
            return SyncAction.MERGE_CONFLICT
        }

        // 2. Datei-MD5 identisch -> NO_OP
        if (optimizer.calculateMD5(localFile) == remoteFileMd5) {
            return SyncAction.NO_OP
        }

        var action: SyncAction

        val activeAnchor = if (anchorMd5.isNullOrEmpty()) null else anchorMd5
        val activeRemoteStruct = if (remoteStructMd5.isNullOrEmpty()) null else remoteStructMd5

        // 3. Anker-Pfad
        if (activeAnchor != null && activeRemoteStruct != null) {
            val localChanged = localStructMd5 != activeAnchor
            val remoteChanged = activeRemoteStruct != activeAnchor

            action = when (resolvedBookMode) {
                SyncMode.BACKUP_ONLY -> {
                    if (localChanged) SyncAction.UPLOAD else SyncAction.NO_OP
                }
                SyncMode.RESTORE_ONLY -> {
                    if (localChanged || remoteChanged) SyncAction.DOWNLOAD else SyncAction.NO_OP
                }
                SyncMode.TWO_WAY -> {
                    when {
                        localChanged && remoteChanged -> SyncAction.MERGE_CONFLICT
                        localChanged -> SyncAction.UPLOAD
                        remoteChanged -> SyncAction.DOWNLOAD
                        else -> SyncAction.NO_OP
                    }
                }
            }
        } else {
            // 4. Fallback ohne Anker
            if (resolvedBookMode == SyncMode.TWO_WAY) {
                action = SyncAction.MERGE_CONFLICT
            } else {
                // Bei BACKUP_ONLY / RESTORE_ONLY bisherige Logik (Timestamps/Seq) beibehalten
                val baseAction = if (localSeq == 0L || remoteSeq == 0L) {
                    when (resolvedBookMode) {
                        SyncMode.BACKUP_ONLY -> {
                            if (localLastModified > remoteLastModified + 2000) SyncAction.UPLOAD else SyncAction.NO_OP
                        }
                        SyncMode.RESTORE_ONLY -> {
                            SyncAction.DOWNLOAD
                        }
                        SyncMode.TWO_WAY -> SyncAction.MERGE_CONFLICT
                    }
                } else {
                    optimizer.evaluateSync(localFile, remoteFileMd5, localSeq, remoteSeq)
                }

                // Translate based on resolvedBookMode (only for modern clients)
                action = when (resolvedBookMode) {
                    SyncMode.BACKUP_ONLY -> {
                        if (baseAction == SyncAction.UPLOAD) SyncAction.UPLOAD else SyncAction.NO_OP
                    }
                    SyncMode.RESTORE_ONLY -> {
                        if (baseAction != SyncAction.NO_OP) SyncAction.DOWNLOAD else SyncAction.NO_OP
                    }
                    SyncMode.TWO_WAY -> baseAction
                }
            }
        }

        return action
    }
}
