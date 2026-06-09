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

@Suppress("unused", "SameParameterValue")
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

    private fun calculateDeterministicMd5(profile: com.andreas_kratzer.ghosttalk.core.model.SettingsProfile): String {
        val cleanConfig = profile.config.copy(
            securityPinHash = null,
            securityPinSalt = null,
            hueUsername = "",
            hueBridgeFingerprint = ""
        )
        val cleanProfile = profile.copy(
            config = cleanConfig,
            profileVersionSequence = 0L,
            updatedAt = 0L
        )
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val jsonStr = json.encodeToString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), cleanProfile)
        return calculateMd5(jsonStr)
    }

    @android.annotation.SuppressLint("HardwareIds")
    private fun buildConfigPropertiesAndDescription(
        type: String,
        name: String,
        updatedAt: Long,
        extraProperties: Map<String, String> = emptyMap()
    ): Pair<Map<String, String>, String> {
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
        val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        val properties = mapOf(
            "app_name" to "GhostTalk",
            "sync_type" to type,
            "name" to name,
            "updated_at" to updatedAt.toString(),
            "source_device" to device,
            "device_id" to androidId,
            "app_version" to versionName
        ) + extraProperties
        val displayType = if (type == "profile") "Profile" else "Config"
        val description = "$name $displayType (Uploaded by $device - App v$versionName - Device ID: $androidId)"
        return Pair(properties, description)
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

    /*
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
                val (properties, description) = buildConfigPropertiesAndDescription("book_config", bookName, System.currentTimeMillis())
                val updateSuccess = storageProvider.updateFile(remoteFile.id, tempFile, "application/json", description, properties) { _ -> }
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
            SyncMode.BACKUP_ONLY -> hasLocalChanged
            SyncMode.TWO_WAY -> hasLocalChanged
        }

        val shouldDownload = when (syncMode) {
            SyncMode.BACKUP_ONLY -> false
            SyncMode.RESTORE_ONLY -> hasRemoteChanged
            SyncMode.TWO_WAY -> hasRemoteChanged
        }

        if (shouldUpload) {
            logger.d(TAG, "Uploading config...")
            val tempFile = File(context.cacheDir, configFileName)
            try {
                tempFile.writeText(localConfigJson)
                saveToLocalBackupFolder(configFileName, tempFile)
                val (properties, description) = buildConfigPropertiesAndDescription("book_config", bookName, localLastModified)
                if (remoteFile != null) {
                    storageProvider.updateFile(remoteFile.id, tempFile, "application/json", description, properties) { _ -> }
                } else {
                    storageProvider.uploadFile(tempFile, "application/json", description, properties) { _ -> }
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
    */

    private fun getTransitSeed(settingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository): String {
        val email = settingsRepository.googleUserEmail
        return if (!email.isNullOrBlank()) email else "ghosttalk_transit_fallback"
    }

    private fun encryptProfileForTransit(
        profile: com.andreas_kratzer.ghosttalk.core.model.SettingsProfile,
        seed: String
    ): com.andreas_kratzer.ghosttalk.core.model.SettingsProfile {
        val encryptor = com.andreas_kratzer.ghosttalk.core.data.impl.settings.SecuritySettingsEncryptor
        val encryptedConfig = profile.config.copy(
            securityPinHash = profile.config.securityPinHash?.let { encryptor.encryptForTransit(it, seed) },
            securityPinSalt = profile.config.securityPinSalt?.let { encryptor.encryptForTransit(it, seed) },
            hueUsername = encryptor.encryptForTransit(profile.config.hueUsername, seed),
            hueBridgeFingerprint = encryptor.encryptForTransit(profile.config.hueBridgeFingerprint, seed)
        )
        return profile.copy(config = encryptedConfig)
    }

    private fun decryptProfileFromTransit(
        profile: com.andreas_kratzer.ghosttalk.core.model.SettingsProfile,
        seed: String
    ): com.andreas_kratzer.ghosttalk.core.model.SettingsProfile {
        val encryptor = com.andreas_kratzer.ghosttalk.core.data.impl.settings.SecuritySettingsEncryptor
        val decryptedConfig = profile.config.copy(
            securityPinHash = profile.config.securityPinHash?.let { encryptor.decryptFromTransit(it, seed) },
            securityPinSalt = profile.config.securityPinSalt?.let { encryptor.decryptFromTransit(it, seed) },
            hueUsername = encryptor.decryptFromTransit(profile.config.hueUsername, seed),
            hueBridgeFingerprint = encryptor.decryptFromTransit(profile.config.hueBridgeFingerprint, seed)
        )
        return profile.copy(config = decryptedConfig)
    }

    suspend fun syncProfile(
        storageProvider: SyncStorageProvider,
        remoteFiles: List<RemoteSyncFile>,
        activeProfile: com.andreas_kratzer.ghosttalk.core.model.SettingsProfile,
        settingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
    ) = withContext(Dispatchers.IO) {
        val profileFileName = "profile_${activeProfile.id}.json"
        val remoteFile = remoteFiles.find { it.name == profileFileName }

        val transitSeed = getTransitSeed(settingsRepository)
        val encryptedActiveProfile = encryptProfileForTransit(activeProfile, transitSeed)

        val jsonSerializer = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
        val localJson = jsonSerializer.encodeToString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), encryptedActiveProfile)
        val localMd5 = com.andreas_kratzer.ghosttalk.core.cloud.CloudSyncOptimizer().calculateMD5(localJson)

        val baseBackupFile = File(File(context.filesDir, "local_backups"), profileFileName)
        val baseJson = if (baseBackupFile.exists()) baseBackupFile.readText() else null

        val remoteDeterministicMd5 = remoteFile?.properties?.get("deterministic_md5")
        val localDeterministicMd5 = calculateDeterministicMd5(activeProfile)

        if (remoteFile != null && remoteDeterministicMd5 != null && remoteDeterministicMd5 == localDeterministicMd5) {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logSkipped(logger, TAG, profileFileName, "Deterministic MD5 matches remote", localDeterministicMd5, remoteDeterministicMd5)
            return@withContext
        }

        if (remoteFile != null && remoteFile.md5Checksum == localMd5) {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logSkipped(logger, TAG, profileFileName, "Encrypted MD5 matches remote", localMd5, remoteFile.md5Checksum)
            return@withContext
        }

        if (remoteFile == null) {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logAction(logger, TAG, profileFileName, "Uploading profile", "deterministic MD5: $localDeterministicMd5")
            val tempFile = File(context.cacheDir, profileFileName)
            try {
                tempFile.writeText(localJson)
                saveToLocalBackupFolder(profileFileName, tempFile)
                val (properties, description) = buildConfigPropertiesAndDescription(
                    "profile",
                    activeProfile.name,
                    activeProfile.updatedAt,
                    mapOf("deterministic_md5" to localDeterministicMd5)
                )
                storageProvider.uploadFile(tempFile, "application/json", description, properties) { _ -> }
                syncLogProvider.addLogEntry("Profil ${activeProfile.name} in die Cloud hochgeladen", activeProfile.id, activeProfile.name)
            } finally {
                tempFile.delete()
            }
            return@withContext
        }

        var remoteJson: String? = null
        val downloadFile = File(context.cacheDir, "temp_$profileFileName")
        try {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logAction(logger, TAG, profileFileName, "Downloading profile for evaluation", "remote MD5: ${remoteFile.md5Checksum}, remote deterministic MD5: $remoteDeterministicMd5, local deterministic MD5: $localDeterministicMd5")
            if (storageProvider.downloadFile(remoteFile.id, downloadFile) { _ -> }) {
                remoteJson = downloadFile.readText()
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to download remote profile $profileFileName", e)
        } finally {
            downloadFile.delete()
        }

        if (remoteJson == null) return@withContext

        // Decode remote profile config to check if the settings are actually different
        val remoteProfileConfig = try {
            val parsedProfile = jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), remoteJson)
            val decryptedRemoteProfile = decryptProfileFromTransit(parsedProfile, transitSeed)
            decryptedRemoteProfile.config
        } catch (_: Exception) {
            try {
                jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.ProfileConfig.serializer(), remoteJson)
            } catch (e2: Exception) {
                logger.e(TAG, "Failed to parse remote profile config for comparison: $profileFileName", e2)
                null
            }
        }

        if (remoteProfileConfig != null && activeProfile.config == remoteProfileConfig && activeProfile.name == (
            try { jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), remoteJson).name } catch(_: Exception) { remoteFile.description ?: activeProfile.name }
        )) {
            com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logSkipped(logger, TAG, profileFileName, "Profile settings and name are identical (backfilling deterministic MD5: $localDeterministicMd5)")
            if (remoteDeterministicMd5 != localDeterministicMd5) {
                try {
                    storageProvider.updateProperties(remoteFile.id, mapOf("deterministic_md5" to localDeterministicMd5))
                    logger.d(TAG, "Successfully backfilled deterministic MD5 metadata on remote profile file: $profileFileName")
                } catch (e: Exception) {
                    logger.w(TAG, "Failed to backfill deterministic MD5 metadata on remote file $profileFileName: ${e.message}")
                }
            }
            return@withContext
        }

        com.andreas_kratzer.ghosttalk.core.cloud.SyncLogger.logAction(logger, TAG, profileFileName, "Profile conflict/delta detected", "Performing 3-way merge")
        
        // Decode remote and base profiles (handling both SettingsProfile and legacy ProfileConfig format)
        val remoteProfile = try {
            val parsedProfile = jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), remoteJson)
            decryptProfileFromTransit(parsedProfile, transitSeed)
        } catch (_: Exception) {
            try {
                val config = jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.ProfileConfig.serializer(), remoteJson)
                com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
                    id = activeProfile.id,
                    name = remoteFile.description ?: activeProfile.name,
                    config = config,
                    profileVersionSequence = 0L,
                    updatedAt = 0L
                )
            } catch (e2: Exception) {
                logger.e(TAG, "Failed to parse remote profile JSON as SettingsProfile or legacy ProfileConfig", e2)
                return@withContext
            }
        }

        val baseProfile = baseJson?.let {
            try {
                val parsedProfile = jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), it)
                decryptProfileFromTransit(parsedProfile, transitSeed)
            } catch (_: Exception) {
                try {
                    val config = jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.ProfileConfig.serializer(), it)
                    com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
                        id = activeProfile.id,
                        name = activeProfile.name,
                        config = config,
                        profileVersionSequence = 0L,
                        updatedAt = 0L
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }

        val localConfigJson = jsonSerializer.encodeToString(com.andreas_kratzer.ghosttalk.core.model.ProfileConfig.serializer(), activeProfile.config)
        val remoteConfigJson = jsonSerializer.encodeToString(com.andreas_kratzer.ghosttalk.core.model.ProfileConfig.serializer(), remoteProfile.config)
        val baseConfigJson = baseProfile?.let { jsonSerializer.encodeToString(com.andreas_kratzer.ghosttalk.core.model.ProfileConfig.serializer(), it.config) }

        val mergedConfigJson = mergeConfigJsons(localConfigJson, remoteConfigJson, baseConfigJson)
        val mergedConfig = jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.ProfileConfig.serializer(), mergedConfigJson)

        val tempFile = File(context.cacheDir, profileFileName)
        try {
            val mergedName = if (remoteProfile.updatedAt > activeProfile.updatedAt) remoteProfile.name else activeProfile.name
            val mergedIsDeleted = if (remoteProfile.updatedAt > activeProfile.updatedAt) remoteProfile.isDeleted else activeProfile.isDeleted
            val mergedSequence = maxOf(activeProfile.profileVersionSequence, remoteProfile.profileVersionSequence) + 1
            val mergedUpdatedAt = System.currentTimeMillis()

            val updatedProfile = activeProfile.copy(
                name = mergedName,
                config = mergedConfig,
                profileVersionSequence = mergedSequence,
                updatedAt = mergedUpdatedAt,
                isDeleted = mergedIsDeleted
            )

            val encryptedUpdatedProfile = encryptProfileForTransit(updatedProfile, transitSeed)
            val updatedProfileJson = jsonSerializer.encodeToString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), encryptedUpdatedProfile)
            tempFile.writeText(updatedProfileJson)
            saveToLocalBackupFolder(profileFileName, tempFile)
            
            // Save local merge
            settingsRepository.updateProfile(updatedProfile)
            if (updatedProfile.id == settingsRepository.activeProfileId) {
                settingsRepository.loadProfile(updatedProfile.id)
            }

            // Update to cloud
            val deterministicMd5 = calculateDeterministicMd5(updatedProfile)
            val (properties, description) = buildConfigPropertiesAndDescription(
                "profile",
                updatedProfile.name,
                updatedProfile.updatedAt,
                mapOf("deterministic_md5" to deterministicMd5)
            )
            storageProvider.updateFile(remoteFile.id, tempFile, "application/json", description, properties) { _ -> }
            syncLogProvider.addLogEntry("Profil ${updatedProfile.name} (gemergt) synchronisiert", updatedProfile.id, updatedProfile.name)
        } finally {
            tempFile.delete()
        }
    }
}
