package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.cloud.SyncConcurrencyGuard
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.services.drive.Drive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ProfileSyncOrchestrator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    importExportManager: PageImportExportManager,
    private val syncLogProvider: SyncLogProvider,
    private val logger: Logger,
    private val storageResolver: SyncStorageResolver
) {
    private val TAG = "ProfileSyncOrchestrator"
    private val configSyncHelper = ConfigSyncHelper(context, importExportManager, syncLogProvider, logger)

    suspend fun syncProfiles(
        drive: Drive?,
        onProgress: (Float, String) -> Unit = { _, _ -> },
        runExclusive: Boolean = true
    ): Boolean {
        val block = suspend {
            withContext(Dispatchers.IO) {
                logger.d(TAG, "syncProfiles: Starting lightweight profiles sync")
                try {
                    val profilesProvider = storageResolver.resolveProfilesStorageProvider(drive)
                    val remoteProfileFiles = profilesProvider.listFiles()

                    // 1. Sync all local profiles in parallel
                    val localProfiles = settingsRepository.getAllProfiles()
                    logger.d(TAG, "syncProfiles: Syncing ${localProfiles.size} local profiles in parallel")
                    coroutineScope {
                        localProfiles.mapIndexed { index, profile ->
                            async {
                                try {
                                    onProgress(0.1f + 0.4f * (index.toFloat() / localProfiles.size.coerceAtLeast(1).toFloat()), "Synchronisiere Profil: ${profile.name}")
                                    configSyncHelper.syncProfile(profilesProvider, remoteProfileFiles, profile, settingsRepository)
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logger.e(TAG, "Failed to sync profile: ${profile.name}", e)
                                }
                            }
                        }.awaitAll()
                    }

                    // 2. Scan and download/import all other remote profiles in parallel
                    val jsonSerializer = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
                    val newRemoteFiles = remoteProfileFiles.filter { file ->
                        if (file.name.startsWith("profile_") && file.name.endsWith(".json")) {
                            val remoteProfileId = file.name.substringAfter("profile_").substringBefore(".json")
                            settingsRepository.getProfileById(remoteProfileId) == null
                        } else {
                            false
                        }
                    }
                    logger.d(TAG, "syncProfiles: Auto-importing ${newRemoteFiles.size} new remote profiles")
                    coroutineScope {
                        newRemoteFiles.mapIndexed { index, file ->
                            async {
                                val remoteProfileId = file.name.substringAfter("profile_").substringBefore(".json")
                                onProgress(0.5f + 0.4f * (index.toFloat() / newRemoteFiles.size.coerceAtLeast(1).toFloat()), "Importiere Profil: ${file.name}")
                                logger.d(TAG, "syncProfiles: Auto-importing new remote profile: ${file.name}")
                                val tempFile = File(context.cacheDir, "import_${file.name}_${System.currentTimeMillis()}")
                                try {
                                    if (profilesProvider.downloadFile(file.id, tempFile)) {
                                        val profileJson = tempFile.readText()
                                        val importedProfile = try {
                                            jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.SettingsProfile.serializer(), profileJson)
                                        } catch (_: Exception) {
                                            try {
                                                val config = jsonSerializer.decodeFromString(com.andreas_kratzer.ghosttalk.core.model.ProfileConfig.serializer(), profileJson)
                                                com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
                                                    id = remoteProfileId,
                                                    name = file.description ?: "Importiertes Profil",
                                                    config = config,
                                                    profileVersionSequence = file.version ?: 1L,
                                                    updatedAt = file.modifiedTime
                                                  )
                                            } catch (e2: Exception) {
                                                logger.e(TAG, "syncProfiles: Failed to parse imported profile JSON", e2)
                                                null
                                            }
                                        }

                                        if (importedProfile != null) {
                                            settingsRepository.insertProfile(importedProfile)
                                            syncLogProvider.addLogEntry("Remote-Profil ${importedProfile.name} importiert", importedProfile.id, importedProfile.name)
                                        }
                                    }
                                } catch (ex: kotlinx.coroutines.CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    logger.e(TAG, "syncProfiles: Failed to auto-import remote profile ${file.name}", ex)
                                } finally {
                                    tempFile.delete()
                                }
                            }
                        }.awaitAll()
                    }
                    onProgress(1.0f, "Fertig")
                    true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(TAG, "syncProfiles: Profile-only sync failed", e)
                    false
                }
            }
        }
        return if (runExclusive) {
            SyncConcurrencyGuard.runExclusive { block() } ?: false
        } else {
            block()
        }
    }
}
