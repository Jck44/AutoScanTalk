package com.andreas_kratzer.ghosttalk.feature.settings.ui

import com.andreas_kratzer.ghosttalk.core.cloud.domain.PerformProfilesSyncUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ProfileConfig
import com.andreas_kratzer.ghosttalk.core.model.SettingsProfile
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.CloudSyncSettingsDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileDraftManager(
    val profileId: String,
    initialName: String,
    initialConfig: ProfileConfig
) {
    val nameState = MutableStateFlow(initialName)
    val configState = MutableStateFlow(initialConfig)
    val hasUnsavedChanges = MutableStateFlow(false)

    fun <T> getScopedValue(getConfigVal: (ProfileConfig) -> T): T {
        return getConfigVal(configState.value)
    }

    fun updateConfig(update: (ProfileConfig) -> ProfileConfig) {
        configState.value = update(configState.value)
        hasUnsavedChanges.value = true
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProfileDraftCoordinator(
    private val settingsRepository: SettingsRepository,
    private val performProfilesSyncUseCase: PerformProfilesSyncUseCase,
    private val cloudSyncDelegate: CloudSyncSettingsDelegate,
    private val scope: CoroutineScope
) {
    private val _draftManager = MutableStateFlow<ProfileDraftManager?>(null)
    val draftManager = _draftManager.asStateFlow()

    val editingProfileId: StateFlow<String?> = _draftManager.map { it?.profileId }.stateIn(scope, SharingStarted.Eagerly, null)
    val editingProfileName: StateFlow<String?> = _draftManager.flatMapLatest { it?.nameState ?: flowOf(null) }.stateIn(scope, SharingStarted.Eagerly, null)
    val hasUnsavedChanges: StateFlow<Boolean> = _draftManager.flatMapLatest { it?.hasUnsavedChanges ?: flowOf(false) }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _isProfileSyncing = MutableStateFlow(false)
    val isProfileSyncing = _isProfileSyncing.asStateFlow()

    private var debouncedSyncJob: kotlinx.coroutines.Job? = null
    private var lastUploadedSequence: Long? = null
    private var lastUploadedTimestamp: Long? = null

    init {
        setupDebouncedProfileUpload()
    }

    private fun setupDebouncedProfileUpload() {
        scope.launch {
            // Initial load of version/timestamp
            val activeId = settingsRepository.activeProfileId
            settingsRepository.getProfileById(activeId)?.let { initialProfile ->
                lastUploadedSequence = initialProfile.profileVersionSequence
                lastUploadedTimestamp = initialProfile.updatedAt
            }

            // Sync flow
            kotlinx.coroutines.flow.combine(
                settingsRepository.activeProfileIdFlow,
                settingsRepository.getAllProfilesFlow()
            ) { activeId, allProfiles ->
                allProfiles.find { it.id == activeId }
            }.collect { profile ->
                if (profile == null) return@collect

                val isLocalChange = !cloudSyncDelegate.isSyncing.value && !_isProfileSyncing.value &&
                        (lastUploadedSequence == null || profile.profileVersionSequence > lastUploadedSequence!! || profile.updatedAt > lastUploadedTimestamp!!)

                if (isLocalChange) {
                    lastUploadedSequence = profile.profileVersionSequence
                    lastUploadedTimestamp = profile.updatedAt
                    scheduleDebouncedProfileSync(profile)
                }
            }
        }
    }

    private fun scheduleDebouncedProfileSync(profile: SettingsProfile) {
        debouncedSyncJob?.cancel()
        debouncedSyncJob = scope.launch {
            kotlinx.coroutines.delay(5000)
            if (cloudSyncDelegate.userEmail.value == null) return@launch
            if (!isProfileSyncing.value) {
                _isProfileSyncing.value = true
                try {
                    val result = performProfilesSyncUseCase.execute()
                    if (result is PerformProfilesSyncUseCase.Result.Success) {
                        settingsRepository.getProfileById(profile.id)?.let { currentProfile ->
                            lastUploadedSequence = currentProfile.profileVersionSequence
                            lastUploadedTimestamp = currentProfile.updatedAt
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // ignore
                } finally {
                    _isProfileSyncing.value = false
                }
            }
        }
    }

    fun autoSyncProfilesOnOpen(userEmail: String?) {
        if (userEmail == null) return

        scope.launch {
            _isProfileSyncing.value = true
            try {
                val result = performProfilesSyncUseCase.execute()
                if (result is PerformProfilesSyncUseCase.Result.Success) {
                    val activeId = settingsRepository.activeProfileId
                    settingsRepository.loadProfile(activeId)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ProfileDraftCoordinator", "Auto profiles sync on open failed", e)
            } finally {
                _isProfileSyncing.value = false
            }
        }
    }

    fun startEditingProfile(profileId: String) {
        scope.launch {
            val profile = settingsRepository.getProfileById(profileId) ?: return@launch
            _draftManager.value = ProfileDraftManager(
                profileId = profile.id,
                initialName = profile.name,
                initialConfig = profile.config
            )
        }
    }

    fun updateEditingProfileName(name: String) {
        _draftManager.value?.nameState?.value = name
        _draftManager.value?.hasUnsavedChanges?.value = true
    }

    fun saveEditingProfile() {
        val draft = _draftManager.value ?: return
        scope.launch {
            val original = settingsRepository.getProfileById(draft.profileId)
            if (original != null) {
                val updatedProfile = original.copy(
                    name = draft.nameState.value,
                    config = draft.configState.value,
                    profileVersionSequence = original.profileVersionSequence + 1,
                    updatedAt = System.currentTimeMillis()
                )
                settingsRepository.updateProfile(updatedProfile)

                // If active profile was updated, update TTS setup
                if (settingsRepository.activeProfileId == draft.profileId) {
                    settingsRepository.loadProfile(draft.profileId)
                }
            }
            _draftManager.value = null
            // Trigger sync immediately to push updated profile json
            settingsRepository.getProfileById(draft.profileId)?.let { updated ->
                scheduleDebouncedProfileSync(updated)
            }
        }
    }

    fun cancelEditingProfile() {
        _draftManager.value = null
    }

    fun <T> scopedFlow(
        repoFlow: StateFlow<T>,
        getConfigVal: (ProfileConfig) -> T
    ): StateFlow<T> {
        return _draftManager.flatMapLatest { draft ->
            if (draft != null) {
                draft.configState.map { config -> getConfigVal(config) }
            } else {
                repoFlow
            }
        }.stateIn(scope, SharingStarted.Eagerly, repoFlow.value)
    }

    fun update(
        updateRepo: () -> Unit,
        updateConfig: (ProfileConfig) -> ProfileConfig
    ) {
        val draft = _draftManager.value
        if (draft != null) {
            draft.updateConfig(updateConfig)
        } else {
            updateRepo()
        }
    }

    fun setActiveProfileId(profileId: String) {
        settingsRepository.activeProfileId = profileId
    }

    fun renameActiveProfile(name: String) {
        scope.launch {
            val activeId = settingsRepository.activeProfileId
            val profile = settingsRepository.getProfileById(activeId)
            if (profile != null && name.isNotBlank() && profile.name != name) {
                settingsRepository.updateProfile(
                    profile.copy(
                        name = name,
                        profileVersionSequence = profile.profileVersionSequence + 1,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun createNewProfile(name: String) {
        scope.launch {
            val currentConfig = settingsRepository.getProfileById(settingsRepository.activeProfileId)?.config 
                ?: ProfileConfig()
            val newProfile = SettingsProfile(
                id = "profile-${java.util.UUID.randomUUID()}",
                name = name,
                config = currentConfig,
                profileVersionSequence = 1L,
                updatedAt = System.currentTimeMillis()
            )
            settingsRepository.insertProfile(newProfile)
            settingsRepository.activeProfileId = newProfile.id
        }
    }

    fun deleteProfile(profile: SettingsProfile, onResult: (Boolean, Int) -> Unit) {
        scope.launch {
            if (profile.id != "profile-default") {
                val allProfiles = settingsRepository.getAllProfiles()
                if (allProfiles.size <= 1) {
                    withContext(Dispatchers.Main) {
                        onResult(false, com.andreas_kratzer.ghosttalk.feature.settings.R.string.settings_error_delete_last_profile)
                    }
                    return@launch
                }
                settingsRepository.deleteProfile(profile)
                if (settingsRepository.activeProfileId == profile.id) {
                    val remainingProfile = allProfiles.find { it.id != profile.id }
                    settingsRepository.activeProfileId = remainingProfile?.id ?: "profile-default"
                }
                withContext(Dispatchers.Main) {
                    onResult(true, 0)
                }
            }
        }
    }
}
