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
        remoteFileMd5: String?,
        remoteSeq: Long,
        remoteLastModified: Long,
        resolvedBookMode: SyncMode,
        remoteConflictFilesNotEmpty: Boolean
    ): SyncAction {
        var action = if (remoteConflictFilesNotEmpty && resolvedBookMode == SyncMode.TWO_WAY) {
            SyncAction.MERGE_CONFLICT
        } else {
            if (localSeq == 0L || remoteSeq == 0L) {
                // Legacy fallback based on timestamps
                if (optimizer.calculateMD5(localFile) == remoteFileMd5) {
                    SyncAction.NO_OP
                } else {
                    when (resolvedBookMode) {
                        SyncMode.BACKUP_ONLY -> {
                            if (localLastModified > remoteLastModified + 2000) SyncAction.UPLOAD else SyncAction.NO_OP
                        }
                        SyncMode.RESTORE_ONLY -> {
                            SyncAction.DOWNLOAD
                        }
                        SyncMode.TWO_WAY -> {
                            SyncAction.MERGE_CONFLICT
                        }
                    }
                }
            } else {
                optimizer.evaluateSync(localFile, remoteFileMd5, localSeq, remoteSeq)
            }
        }

        // Translate action based on resolvedBookMode (only for modern clients)
        if (localSeq > 0L && remoteSeq > 0L) {
            action = when (resolvedBookMode) {
                SyncMode.BACKUP_ONLY -> {
                    if (action == SyncAction.UPLOAD) SyncAction.UPLOAD else SyncAction.NO_OP
                }
                SyncMode.RESTORE_ONLY -> {
                    if (action != SyncAction.NO_OP) SyncAction.DOWNLOAD else SyncAction.NO_OP
                }
                SyncMode.TWO_WAY -> action
            }
        }

        return action
    }
}
