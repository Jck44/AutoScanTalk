package com.andreas_kratzer.ghosttalk.core.cloud

import com.andreas_kratzer.ghosttalk.core.util.Logger

/**
 * Reusable helper for unified sync log formatting across all sync managers and helpers.
 */
object SyncLogger {
    fun logStatus(
        logger: Logger,
        tag: String,
        fileName: String,
        localVal: Any,
        remoteVal: Any,
        lastSyncedLocal: Any? = null,
        lastSyncedRemote: Any? = null
    ) {
        val extra = if (lastSyncedLocal != null && lastSyncedRemote != null) {
            ", last synced local=$lastSyncedLocal, last synced remote=$lastSyncedRemote"
        } else {
            ""
        }
        logger.d(tag, "Sync status for $fileName: local=$localVal, remote=$remoteVal$extra")
    }

    fun logSkipped(
        logger: Logger,
        tag: String,
        fileName: String,
        reason: String,
        localVal: Any? = null,
        remoteVal: Any? = null
    ) {
        val detail = if (localVal != null && remoteVal != null) " ($localVal vs $remoteVal)" else ""
        logger.d(tag, "Sync skipped for $fileName: $reason$detail")
    }

    fun logAction(
        logger: Logger,
        tag: String,
        fileName: String,
        actionName: String,
        extraInfo: String = ""
    ) {
        val extra = if (extraInfo.isNotEmpty()) " ($extraInfo)" else ""
        logger.d(tag, "Action on $fileName: $actionName$extra")
    }

    fun logDecision(
        logger: Logger,
        tag: String,
        fileName: String,
        hasLocal: Boolean,
        hasRemote: Boolean,
        upload: Boolean,
        download: Boolean
    ) {
        logger.d(tag, "Sync decision for $fileName: hasLocalChanged=$hasLocal, hasRemoteChanged=$hasRemote -> shouldUpload=$upload, shouldDownload=$download")
    }
}
