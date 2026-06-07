package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

class ConfigSyncHelper(
    private val context: Context,
    private val importExportManager: PageImportExportManager,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger
) {
    private val TAG = "ConfigSyncHelper"

    private fun saveToLocalBackupFolder(fileName: String, tempFile: File) {
        try {
            val backupDir = File(context.filesDir, "local_backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            val destFile = File(backupDir, fileName)
            tempFile.copyTo(destFile, overwrite = true)
            logger.d(TAG, "Saved local backup copy to $destFile")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to save local backup copy for $fileName", e)
        }
    }

    private fun calculateMd5(content: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun mergeConfigJsons(localJson: String, remoteJson: String, baseJson: String?): String {
        val jsonParser = Json { ignoreUnknownKeys = true; prettyPrint = true }
        val localMap = jsonParser.parseToJsonElement(localJson).jsonObject
        val remoteMap = jsonParser.parseToJsonElement(remoteJson).jsonObject
        val baseMap = baseJson?.let { jsonParser.parseToJsonElement(it).jsonObject } ?: emptyMap()

        val allKeys = localMap.keys + remoteMap.keys
        val mergedMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

        for (key in allKeys) {
            val localVal = localMap[key]
            val remoteVal = remoteMap[key]
            val baseVal = baseMap[key]

            // 3-way merge logic
            val mergedVal = when {
                localVal == remoteVal -> localVal
                localVal != null && remoteVal == null -> {
                    if (baseVal == localVal) null else localVal
                }
                localVal == null && remoteVal != null -> {
                    if (baseVal == remoteVal) null else remoteVal
                }
                else -> {
                    when {
                        baseVal == remoteVal -> localVal
                        baseVal == localVal -> remoteVal
                        else -> localVal // default fallback
                    }
                }
            }
            if (mergedVal != null) {
                mergedMap[key] = mergedVal
            }
        }
        return jsonParser.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(mergedMap)
        )
    }

    suspend fun syncBookConfig(
        storageProvider: SyncStorageProvider,
        remoteFiles: List<RemoteSyncFile>,
        syncMode: SyncMode,
        bookId: String,
        bookName: String
    ) = withContext(Dispatchers.IO) {
        val configFileName = "config_$bookId.json"

        val remoteFile = remoteFiles.find { it.name == configFileName }

        val localLastModified = importExportManager.getBookConfigLastModified(bookId)

        val localConfigJson = importExportManager.exportBookConfigToJson(bookId)
        val localMd5 = calculateMd5(localConfigJson)

        val baseBackupFile = File(File(context.filesDir, "local_backups"), configFileName)
        val baseConfigJson = if (baseBackupFile.exists()) baseBackupFile.readText() else null
        val baseMd5 = baseConfigJson?.let { calculateMd5(it) } ?: ""

        val hasLocalChanged = localMd5 != baseMd5 && localLastModified > 0L

        var remoteConfigJson: String? = null
        var remoteMd5 = ""
        if (remoteFile != null) {
            val downloadFile = File(context.cacheDir, "temp_config_eval_$bookId.json")
            try {
                if (storageProvider.downloadFile(remoteFile.id, downloadFile) { _ -> }) {
                    remoteConfigJson = downloadFile.readText()
                    remoteMd5 = calculateMd5(remoteConfigJson)
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to download remote config for evaluation", e)
            } finally {
                downloadFile.delete()
            }
        }

        val hasRemoteChanged = remoteFile != null && remoteMd5 != baseMd5

        logger.w(TAG, "[CONFIG-SYNC] syncBookConfig: hasLocalChanged=$hasLocalChanged, hasRemoteChanged=$hasRemoteChanged")

        if (!hasLocalChanged && !hasRemoteChanged) {
            logger.d(TAG, "Config settings are in sync.")
            return@withContext
        }

        if (syncMode == SyncMode.TWO_WAY && hasLocalChanged && hasRemoteChanged) {
            logger.d(TAG, "Config conflict detected. Performing 3-way merge...")
            val mergedJson = mergeConfigJsons(localConfigJson, remoteConfigJson!!, baseConfigJson)
            val tempFile = File(context.cacheDir, configFileName)
            try {
                tempFile.writeText(mergedJson)
                saveToLocalBackupFolder(configFileName, tempFile)
                val updateSuccess = storageProvider.updateFile(remoteFile.id, tempFile, "application/json", bookName) { _ -> }
                if (updateSuccess) {
                    importExportManager.importBookConfigFromJson(mergedJson, bookId)
                    syncLogProvider.addLogEntry("Einstellungen (gemergt) synchronisiert", bookId, bookName)
                }
            } finally {
                tempFile.delete()
            }
            return@withContext
        }

        val shouldUpload = when (syncMode) {
            SyncMode.RESTORE_ONLY -> false
            SyncMode.BACKUP_ONLY -> hasLocalChanged || remoteFile == null
            SyncMode.TWO_WAY -> hasLocalChanged
        }

        val shouldDownload = when (syncMode) {
            SyncMode.BACKUP_ONLY -> false
            SyncMode.RESTORE_ONLY -> hasRemoteChanged || localLastModified == 0L
            SyncMode.TWO_WAY -> hasRemoteChanged
        }

        if (shouldUpload) {
            logger.d(TAG, "Uploading config...")
            val tempFile = File(context.cacheDir, configFileName)
            try {
                tempFile.writeText(localConfigJson)
                saveToLocalBackupFolder(configFileName, tempFile)
                if (remoteFile != null) {
                    storageProvider.updateFile(remoteFile.id, tempFile, "application/json", bookName) { _ -> }
                } else {
                    storageProvider.uploadFile(tempFile, "application/json", bookName) { _ -> }
                }
                syncLogProvider.addLogEntry("Einstellungen in die Cloud hochgeladen", bookId, bookName)
            } finally {
                tempFile.delete()
            }
        } else if (shouldDownload && remoteConfigJson != null) {
            logger.d(TAG, "Downloading/Applying remote config...")
            val tempFile = File(context.cacheDir, configFileName)
            try {
                tempFile.writeText(remoteConfigJson)
                importExportManager.importBookConfigFromJson(remoteConfigJson, bookId)
                saveToLocalBackupFolder(configFileName, tempFile)
                syncLogProvider.addLogEntry("Einstellungen aus der Cloud wiederhergestellt", bookId, bookName)
            } finally {
                tempFile.delete()
            }
        }
    }

    suspend fun restoreBookConfigIfAvailable(
        storageProvider: SyncStorageProvider,
        remoteFiles: List<RemoteSyncFile>,
        bookId: String,
        bookName: String
    ) {
        val configFileName = "config_$bookId.json"
        val remoteFile = remoteFiles.find { it.name == configFileName } ?: return

        logger.d(TAG, "Found separate config on Drive, restoring...")
        val tempFile = File(context.cacheDir, "download_$configFileName")
        try {
            if (storageProvider.downloadFile(remoteFile.id, tempFile) { _ -> }) {
                val configJson = tempFile.readText()
                importExportManager.importBookConfigFromJson(configJson, bookId)
                saveToLocalBackupFolder(configFileName, tempFile)
                syncLogProvider.addLogEntry("Einstellungen aus der Cloud wiederhergestellt", bookId, bookName)
            }
        } finally {
            tempFile.delete()
        }
    }
}
