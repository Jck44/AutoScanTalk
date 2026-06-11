package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist
import com.andreas_kratzer.ghosttalk.core.cloud.domain.ExportLogsUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.LogUploadResult
import com.andreas_kratzer.ghosttalk.core.cloud.domain.PerformProfilesSyncUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.RescheduleLogUploadUseCase
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.CloudAuthType
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.tts.AudioCacheRepository
import com.andreas_kratzer.ghosttalk.core.tts.CachedAudioItem
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.domain.DeleteBookUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateActionLogLimitUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.UpdateActiveBookNameUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.BackupSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.CloudSyncSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ExperimentalSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.GenAiSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.HueSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ScanningSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.SpotifySettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.TtsPrefetchSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.TtsSettingsDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Suppress("unused")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val userModeSessionRepository: UserModeSessionRepository,
    val securityManager: SecurityManager,
    getPagesUseCase: GetPagesUseCase,
    val ttsDelegate: TtsSettingsDelegate,
    val scanningDelegate: ScanningSettingsDelegate,
    val cloudSyncDelegate: CloudSyncSettingsDelegate,
    val genAiDelegate: GenAiSettingsDelegate,
    val experimentalDelegate: ExperimentalSettingsDelegate,
    val hueDelegate: HueSettingsDelegate,
    val spotifyDelegate: SpotifySettingsDelegate,
    val prefetchDelegate: TtsPrefetchSettingsDelegate,
    val backupDelegate: BackupSettingsDelegate,
    private val backgroundScheduler: com.andreas_kratzer.ghosttalk.core.domain.BackgroundScheduler,
    private val updateActiveBookNameUseCase: UpdateActiveBookNameUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val updateActionLogLimitUseCase: UpdateActionLogLimitUseCase,
    private val importExportManager: PageImportExportProvider,
    private val hueManager: PhilipsHueManager,
    private val spotifyManager: SpotifyManager,
    private val ttsHelper: TextToSpeechHelper,
    private val audioCacheRepository: AudioCacheRepository,
    private val pageRepository: PageRepository,
    private val syncLogProvider: SyncLogProvider,
    private val callActionProxy: dagger.Lazy<CallActionProxy>,
    private val exportLogsUseCase: ExportLogsUseCase,
    private val rescheduleLogUploadUseCase: RescheduleLogUploadUseCase,
    private val performProfilesSyncUseCase: PerformProfilesSyncUseCase,
    val authManager: com.andreas_kratzer.ghosttalk.core.cloud.AuthManager
) : AndroidViewModel(application) {

    private val _draftManager = MutableStateFlow<ProfileDraftManager?>(null)
    val editingProfileId: StateFlow<String?> = _draftManager.map { it?.profileId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val editingProfileName: StateFlow<String?> = _draftManager.flatMapLatest { it?.nameState ?: flowOf(null) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val hasUnsavedChanges: StateFlow<Boolean> = _draftManager.flatMapLatest { it?.hasUnsavedChanges ?: flowOf(false) }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _activeBookId = settingsRepository.activeBookIdFlow
    val allPages: StateFlow<List<Page>> = getPagesUseCase.execute(_activeBookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeBook: StateFlow<Book?> = settingsRepository.activeBookIdFlow
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(null) else bookRepository.getBookByIdFlow(bookId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // --- Observable State from Repository ---
    val availableLanguages = ttsDelegate.availableLanguages
    val availableVoices = ttsDelegate.availableVoices
    val availableAudioDevices = ttsDelegate.availableAudioDevices
    val cachedAudioDevices = ttsDelegate.cachedAudioDevices
    
    val selectedLanguageTag = profileScopedFlow(settingsRepository.ttsLanguageFlow) { it.ttsLanguage ?: "de" }
    val selectedVoiceName = profileScopedFlow(settingsRepository.ttsVoiceNameFlow) { it.ttsVoiceName }
    
    val autoStartScanning = profileScopedFlow(settingsRepository.autoStartScanningFlow) { it.autoStartScanning }
    val scanDelayMillis = profileScopedFlow(settingsRepository.scanDelayFlow) { it.scanDelayMillis }
    val resumeScanningFromStart = profileScopedFlow(settingsRepository.resumeScanningFromStartFlow) { it.resumeScanningFromStart }
    val defaultScanPattern = profileScopedFlow(settingsRepository.defaultScanPatternFlow) { it.defaultScanPattern }
    val holdingTimeMillis = profileScopedFlow(settingsRepository.holdingTimeMillisFlow) { it.holdingTimeMillis }
    val bluetoothDelay = profileScopedFlow(settingsRepository.bluetoothDelayFlow) { it.bluetoothDelay }
    val lateClickThresholdMillis = profileScopedFlow(settingsRepository.lateClickThresholdFlow) { it.lateClickThresholdMillis }
    val isVocalSwitchEnabled = profileScopedFlow(settingsRepository.isVocalSwitchEnabledFlow) { it.vocalSwitchEnabled }
    
    val staticRowEnabled = profileScopedFlow(settingsRepository.staticRowEnabledFlow) { it.staticRowEnabled }
    
    val defaultStartPageId = profileScopedFlow(settingsRepository.defaultStartPageIdFlow) { it.favoriteBookId }
    val selectedTtsAudioDeviceAddress = profileScopedFlow(settingsRepository.ttsAudioDeviceAddressFlow) { it.preferredMainSpeakerName }
    val selectedCuesAudioDeviceAddress = profileScopedFlow(settingsRepository.cuesAudioDeviceAddressFlow) { it.preferredCueSpeakerName }
    val recordingAudioSource = settingsRepository.recordingAudioSourceFlow
    
    val persistActionLogs = profileScopedFlow(settingsRepository.persistActionLogsFlow) { it.persistActionLogs }
    val switchActivationKey = profileScopedFlow(settingsRepository.switchActivationKeyFlow) { it.switchActivationKey }
    val volumeKeysActivate = profileScopedFlow(settingsRepository.volumeKeysActivateFlow) { it.volumeKeysActivate }
    val showTestButtons = settingsRepository.showTestButtonsFlow
    val showPageIdInLog = profileScopedFlow(settingsRepository.showPageIdInLogFlow) { it.showPageIdInLog }
    
    val isDataCloudSyncEnabled = profileScopedFlow(settingsRepository.isDataCloudSyncEnabledFlow) { it.isDataCloudSyncEnabled }
    val syncModeBook = profileScopedFlow(settingsRepository.syncModeBookFlow) { it.syncModeBook }
    val syncModeTts = profileScopedFlow(settingsRepository.syncModeTtsFlow) { it.syncModeTts }
    val syncModeStats = profileScopedFlow(settingsRepository.syncModeStatsFlow) { it.syncModeStats }
    val syncModeSettings = profileScopedFlow(settingsRepository.syncModeSettingsFlow) { it.syncModeBook } // Map to syncModeBook or default since syncModeSettings is not in ProfileConfig
    val lastSuccessfulSyncTime = settingsRepository.lastSuccessfulSyncTimeFlow
    val syncIntervalMinutes = profileScopedFlow(settingsRepository.syncIntervalMinutesFlow) { it.syncIntervalMinutes }
    val foregroundSyncIntervalMinutes = profileScopedFlow(settingsRepository.foregroundSyncIntervalMinutesFlow) { it.foregroundSyncIntervalMinutes }
    val googleDriveFolderId = profileScopedFlow(settingsRepository.googleDriveFolderIdFlow) { it.googleDriveFolderId }
    val googleDriveFolderName = profileScopedFlow(settingsRepository.googleDriveFolderNameFlow) { it.googleDriveFolderName }
    val syncTargetType = settingsRepository.syncTargetTypeFlow
    val googleAuthType = settingsRepository.googleAuthTypeFlow
    val localFolderSafUri = settingsRepository.localFolderSafUriFlow
    val localFolderSafName = settingsRepository.localFolderSafNameFlow
    val hueBridgeIp = profileScopedFlow(settingsRepository.hueBridgeIpFlow) { it.hueBridgeIp }
    val hueUsername = settingsRepository.hueUsernameFlow
    val huePairingStatus: StateFlow<String?> = hueDelegate.huePairingStatus
    val pendingCertificateInfo: StateFlow<com.andreas_kratzer.ghosttalk.core.cloud.BridgeCertificateInfo?> = hueDelegate.pendingCertificateInfo
    val hueCachedDevices = profileScopedFlow(settingsRepository.hueCachedDevicesFlow) { it.hueCachedDevices }
    val isUpdatingHueCache: StateFlow<Boolean> = hueDelegate.isUpdatingHueCache
    val isSyncing = cloudSyncDelegate.isSyncing
    val userEmail = cloudSyncDelegate.userEmail
    
    val availableBackups = cloudSyncDelegate.availableBackups
    val showBackupSelectionDialog = cloudSyncDelegate.showBackupSelectionDialog
    val syncLogs = cloudSyncDelegate.syncLogs
    val driveFolders = cloudSyncDelegate.driveFolders
    val isBrowsingFolders = cloudSyncDelegate.isBrowsingFolders
    
    val isGeminiEnabled = profileScopedFlow(settingsRepository.isGeminiEnabledFlow) { it.isGeminiEnabled }
    val geminiToolStatus = genAiDelegate.geminiToolStatus
    val geminiApiKey = settingsRepository.geminiApiKeyFlow
    val useGeminiApiKey = profileScopedFlow(settingsRepository.useGeminiApiKeyFlow) { it.useGeminiApiKey }
    val isGeminiVerified = settingsRepository.isGeminiVerifiedFlow
 
    val spotifyUserDisplayName = settingsRepository.spotifyUserDisplayNameFlow
    val spotifyPlaylists: StateFlow<List<SpotifyPlaylist>> = spotifyDelegate.spotifyPlaylists
    val isLoadingPlaylists: StateFlow<Boolean> = spotifyDelegate.isLoadingPlaylists
    
    val isSmartPredictionEnabled = profileScopedFlow(settingsRepository.isSmartPredictionEnabledFlow) { it.isSmartPredictionEnabled }
    
    val isNotificationReadingEnabled = profileScopedFlow(settingsRepository.isNotificationReadingEnabledFlow) { it.isNotificationReadingEnabled }
    val monitoredNotificationApps = profileScopedFlow(settingsRepository.monitoredNotificationAppsFlow) { it.monitoredNotificationApps }
    val autoReadMode = profileScopedFlow(settingsRepository.autoReadModeFlow) { it.autoReadMode }
    val autoReadOnlyInUserMode = profileScopedFlow(settingsRepository.autoReadOnlyInUserModeFlow) { it.autoReadOnlyInUserMode }
    val autoReadInStandby = profileScopedFlow(settingsRepository.autoReadInStandbyFlow) { it.autoReadInStandby }
    
    val selectedAppLanguage = profileScopedFlow(settingsRepository.appLanguageFlow) { it.appLanguage }
    val themeMode = profileScopedFlow(settingsRepository.themeModeFlow) { it.themeMode }
    val buttonHistory = buttonUsageRepository.buttonHistory
 
    val keepScreenOnUserMode = profileScopedFlow(settingsRepository.keepScreenOnUserModeFlow) { it.keepScreenOnUserMode }
    val userModeScreenBehavior = profileScopedFlow(settingsRepository.userModeScreenBehaviorFlow) { it.userModeScreenBehavior }
    val statsRetentionDays = profileScopedFlow(settingsRepository.statsRetentionDaysFlow) { it.statsRetentionDays }
    val statsAggregationHours = profileScopedFlow(settingsRepository.statsAggregationHoursFlow) { it.statsAggregationHours }
    val onlyRecordHardwareStats = profileScopedFlow(settingsRepository.onlyRecordHardwareStatsFlow) { it.onlyRecordHardwareStats }
    val firebaseAnalyticsEnabled = profileScopedFlow(settingsRepository.firebaseAnalyticsEnabledFlow) { it.firebaseAnalyticsEnabled }
    val geminiTimeout = profileScopedFlow(settingsRepository.geminiTimeoutFlow) { it.geminiTimeout }
    val geminiRedoPrediction = profileScopedFlow(settingsRepository.geminiRedoPredictionFlow) { it.geminiRedoPrediction }
    val blockVolumeKeys = profileScopedFlow(settingsRepository.blockVolumeKeysFlow) { it.blockVolumeKeys }
    val speakerVolume = profileScopedFlow(settingsRepository.speakerVolumeFlow) { it.speakerVolume }
    val headphoneVolume = profileScopedFlow(settingsRepository.headphoneVolumeFlow) { it.headphoneVolume }
 
    val securityPin = profileScopedFlow(settingsRepository.securityPinFlow) { "" } // Room settings profile doesn't store hashed pin directly
    val securityPinTimeoutMinutes = profileScopedFlow(settingsRepository.securityPinTimeoutMinutesFlow) { it.securityPinTimeoutMinutes }
    val isPinRequiredForDeletion = profileScopedFlow(settingsRepository.isPinRequiredForDeletionFlow) { it.isPinRequiredForDeletion }
    val isBiometricEnabled = profileScopedFlow(settingsRepository.isBiometricEnabledFlow) { false } // Biometrics is device-specific
    val isSecurityRequiredForEdit = profileScopedFlow(settingsRepository.isSecurityRequiredForEditFlow) { it.isSecurityRequiredForEdit }
    val isSecurityRequiredForSettings = profileScopedFlow(settingsRepository.isSecurityRequiredForSettingsFlow) { it.isSecurityRequiredForSettings }
    val isSecurityRequiredForAnalytics = profileScopedFlow(settingsRepository.isSecurityRequiredForAnalyticsFlow) { it.isSecurityRequiredForAnalytics }
    val startupBehavior = profileScopedFlow(settingsRepository.startupBehaviorFlow) { it.startupBehavior }
    val logIgnoredActions = profileScopedFlow(settingsRepository.logIgnoredActionsFlow) { it.logIgnoredActions }
    val logStopActions = profileScopedFlow(settingsRepository.logStopActionsFlow) { it.logStopActions }
    
    val limitScanCycles = profileScopedFlow(settingsRepository.limitScanCyclesFlow) { it.limitScanCycles }
    val scanCycleLimit = profileScopedFlow(settingsRepository.scanCycleLimitFlow) { it.scanCycleLimit }
    val actionLogLimit = profileScopedFlow(settingsRepository.actionLogLimitFlow) { it.actionLogLimit }
    val forceSoftKeyboard = profileScopedFlow(settingsRepository.forceSoftKeyboardFlow) { it.forceSoftKeyboard }
    val ttsEngine = profileScopedFlow(settingsRepository.ttsEngineFlow) { it.ttsEngine }
    val elevenLabsApiKey = settingsRepository.elevenLabsApiKeyFlow
    val elevenLabsModel = profileScopedFlow(settingsRepository.elevenLabsModelFlow) { it.elevenLabsModel }
    val elevenLabsStability = profileScopedFlow(settingsRepository.elevenLabsStabilityFlow) { it.elevenLabsStability }
    val elevenLabsSimilarityBoost = profileScopedFlow(settingsRepository.elevenLabsSimilarityBoostFlow) { it.elevenLabsSimilarityBoost }
    val ttsPlaybackSpeed = profileScopedFlow(settingsRepository.ttsPlaybackSpeedFlow) { it.ttsPlaybackSpeed }
 
    // --- CallSettings ---
    val maxCallDurationSeconds = profileScopedFlow(settingsRepository.maxCallDurationSecondsFlow) { it.maxCallDurationSeconds }
    val callDurationFeedbackIntervalSeconds = profileScopedFlow(settingsRepository.callDurationFeedbackIntervalSecondsFlow) { it.callDurationFeedbackIntervalSeconds }
    val outgoingCallIntro = profileScopedFlow(settingsRepository.outgoingCallIntroFlow) { it.outgoingCallIntro }
    val incomingCallIntro = profileScopedFlow(settingsRepository.incomingCallIntroFlow) { it.incomingCallIntro }
    val incomingCallScanLimitUserModeActive = profileScopedFlow(settingsRepository.incomingCallScanLimitUserModeActiveFlow) { it.incomingCallScanLimitUserModeActive }
    val incomingCallAutoActionUserModeActive = profileScopedFlow(settingsRepository.incomingCallAutoActionUserModeActiveFlow) { it.incomingCallAutoActionUserModeActive }
    val incomingCallDelayUserModeInactive = profileScopedFlow(settingsRepository.incomingCallDelayUserModeInactiveFlow) { it.incomingCallDelayUserModeInactive }
    val incomingCallAutoActionUserModeInactive = profileScopedFlow(settingsRepository.incomingCallAutoActionUserModeInactiveFlow) { it.incomingCallAutoActionUserModeInactive }
    val callAnnouncementAsCue = profileScopedFlow(settingsRepository.callAnnouncementAsCueFlow) { it.callAnnouncementAsCue }
    val autoEnableSpeakerphone = profileScopedFlow(settingsRepository.autoEnableSpeakerphoneFlow) { it.autoEnableSpeakerphone }
    val simulateCallsEnabled = profileScopedFlow(settingsRepository.simulateCallsEnabledFlow) { it.simulateCallsEnabled }
    val hangUpPressesRequired = profileScopedFlow(settingsRepository.hangUpPressesRequiredFlow) { it.hangUpPressesRequired }
    val filterCallsNotInContacts = profileScopedFlow(settingsRepository.filterCallsNotInContactsFlow) { it.filterCallsNotInContacts }
    
    private val _showActionHistoryDialog = MutableStateFlow(false)
    val showActionHistoryDialog = _showActionHistoryDialog.asStateFlow()
 
    private val _showUsageStatsDialog = MutableStateFlow(false)
    val showUsageStatsDialog = _showUsageStatsDialog.asStateFlow()

    private val _showPrefetchDialog = MutableStateFlow(false)
    val showPrefetchDialog = _showPrefetchDialog.asStateFlow()

    private val _topButtonUsage = MutableStateFlow<List<com.andreas_kratzer.ghosttalk.core.model.GroupedButtonUsageStat>>(emptyList())
    val topButtonUsage = _topButtonUsage.asStateFlow()

    // --- TTS Prefetch State ---
    val selectedPagesForPrefetch = prefetchDelegate.selectedPagesForPrefetch
    val isPrefetching = prefetchDelegate.isPrefetching
    val prefetchProgress = prefetchDelegate.prefetchProgress
    val prefetchCurrentCount = prefetchDelegate.prefetchCurrentCount
    val prefetchTotalCount = prefetchDelegate.prefetchTotalCount
    val currentPrefetchText = prefetchDelegate.currentPrefetchText
    val prefetchStats = prefetchDelegate.prefetchStats

    // --- Backup & Restore State ---
    val isBackupRestoreRunning = backupDelegate.isBackupRestoreRunning
    val backupRestoreProgress = backupDelegate.backupRestoreProgress
    val backupRestoreStatus = backupDelegate.backupRestoreStatus

    // --- Log Sync State ---
    val syncModeLogs = settingsRepository.syncModeLogsFlow
    val syncLogsIntervalHours = settingsRepository.syncLogsIntervalHoursFlow
    val lastLogsSyncTime = settingsRepository.lastLogsSyncTimeFlow

    private val _manualUpdateCheckTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val manualUpdateCheckTrigger = _manualUpdateCheckTrigger.asSharedFlow()

    private val _updateCheckStatus = MutableStateFlow<UpdateCheckStatus?>(null)
    val updateCheckStatus = _updateCheckStatus.asStateFlow()
    
    private val _highlightedSettingKey = MutableStateFlow<String?>(null)
    val highlightedSettingKey = _highlightedSettingKey.asStateFlow()

    fun setHighlightedSettingKey(key: String?) {
        _highlightedSettingKey.value = key
        if (key != null) {
            viewModelScope.launch {
                delay(2000)
                if (_highlightedSettingKey.value == key) {
                    _highlightedSettingKey.value = null
                }
            }
        }
    }

    private fun <T> profileScopedFlow(
        repoFlow: StateFlow<T>,
        getConfigVal: (com.andreas_kratzer.ghosttalk.core.model.ProfileConfig) -> T
    ): StateFlow<T> {
        return _draftManager.flatMapLatest { draft ->
            if (draft != null) {
                draft.configState.map { config -> getConfigVal(config) }
            } else {
                repoFlow
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, repoFlow.value)
    }

    private fun updateSetting(
        updateRepo: () -> Unit,
        updateConfig: (com.andreas_kratzer.ghosttalk.core.model.ProfileConfig) -> com.andreas_kratzer.ghosttalk.core.model.ProfileConfig
    ) {
        val draft = _draftManager.value
        if (draft != null) {
            draft.updateConfig(updateConfig)
        } else {
            updateRepo()
        }
    }

    private val _isProfileSyncing = MutableStateFlow(false)
    val isProfileSyncing = _isProfileSyncing.asStateFlow()

    private var prefetchJob: kotlinx.coroutines.Job? = null
    private var debouncedSyncJob: kotlinx.coroutines.Job? = null
    private var lastUploadedSequence: Long? = null
    private var lastUploadedTimestamp: Long? = null

    init {
        ttsDelegate.initialize(viewModelScope)
        genAiDelegate.updateGeminiToolStatus()
        initializeDefaultMessagingAppsIfNeeded()
        hueDelegate.initialize(viewModelScope)
        spotifyDelegate.initialize(viewModelScope)
        prefetchDelegate.initialize(viewModelScope)
        backupDelegate.initialize(viewModelScope)
        setupDebouncedProfileUpload()
    }

    private fun setupDebouncedProfileUpload() {
        viewModelScope.launch {
            // Initial load of version/timestamp
            val activeId = settingsRepository.activeProfileId
            settingsRepository.getProfileById(activeId)?.let { initialProfile ->
                lastUploadedSequence = initialProfile.profileVersionSequence
                lastUploadedTimestamp = initialProfile.updatedAt
            }

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

                    scheduleDebouncedProfileSync()
                }
            }
        }
    }

    private fun scheduleDebouncedProfileSync() {
        debouncedSyncJob?.cancel()
        debouncedSyncJob = viewModelScope.launch {
            delay(5000)
            if (userEmail.value == null) return@launch
            val activeId = settingsRepository.activeProfileId
            try {
                _isProfileSyncing.value = true
                performProfilesSyncUseCase.execute()
                settingsRepository.getProfileById(activeId)?.let { currentProfile ->
                    lastUploadedSequence = currentProfile.profileVersionSequence
                    lastUploadedTimestamp = currentProfile.updatedAt
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Debounced profiles sync failed", e)
            } finally {
                _isProfileSyncing.value = false
            }
        }
    }

    fun autoSyncProfilesOnOpen() {
        if (userEmail.value == null) return

        viewModelScope.launch {
            _isProfileSyncing.value = true
            try {
                val result = performProfilesSyncUseCase.execute()
                if (result is PerformProfilesSyncUseCase.Result.Success) {
                    val activeId = settingsRepository.activeProfileId
                    settingsRepository.loadProfile(activeId)
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Auto profiles sync on open failed", e)
            } finally {
                _isProfileSyncing.value = false
            }
        }
    }
    
    fun triggerStartSetupWizard() {
        viewModelScope.launch {
            _navigationEvent.emit(SettingsNavigationEvent.StartSetup)
        }
    }

    private val _navigationEvent = kotlinx.coroutines.flow.MutableSharedFlow<SettingsNavigationEvent>()
    val navigationEvents = _navigationEvent.asSharedFlow()

    private val _selectedHistoryItem = MutableStateFlow<ButtonUsageRepository.ButtonUsageEvent?>(null)
    val selectedHistoryItem = _selectedHistoryItem.asStateFlow()

    sealed class SettingsNavigationEvent {
        data class EditButton(val pageId: String, val buttonId: String) : SettingsNavigationEvent()
        data class JumpToPage(val pageId: String) : SettingsNavigationEvent()
        object StartSetup : SettingsNavigationEvent()
    }

    sealed class UpdateCheckStatus {
        object Checking : UpdateCheckStatus()
        object UpToDate : UpdateCheckStatus()
        /** Update found and Play Store download dialog was triggered. */
        object UpdateFound : UpdateCheckStatus()
        /** App is not installed via Play Store (sideloaded / ADB). */
        object NotFromPlayStore : UpdateCheckStatus()
        data class Error(val message: String) : UpdateCheckStatus()
    }
    
    val isBiometricSupported: Boolean = securityManager.isBiometricSupported(application)

    val authIntentFlow = kotlinx.coroutines.flow.merge(
        cloudSyncDelegate.authIntentFlow,
        genAiDelegate.authIntentFlow
    )
    
    val signInErrorMessage = cloudSyncDelegate.signInErrorMessage

    // --- Delegation Methods (UI Actions) ---
    fun refresh() {
        ttsDelegate.loadAvailableLanguages()
        ttsDelegate.loadAvailableAudioDevices()
        genAiDelegate.updateGeminiToolStatus()
        refreshTopButtonUsage()
    }

    fun setTtsLanguage(tag: String) = ttsDelegate.setTtsLanguage(tag)
    fun setTtsVoice(name: String?) = ttsDelegate.setTtsVoice(name)
    fun setTtsAudioDevice(addr: String?) = ttsDelegate.setTtsAudioDevice(addr)
    fun setCuesAudioDevice(addr: String?) = ttsDelegate.setCuesAudioDevice(addr)
    fun setRecordingAudioSource(source: Int) {
        settingsRepository.recordingAudioSource = source
    }
    fun getResolvedDeviceName(addr: String?) = ttsDelegate.getResolvedDeviceName(addr)

    fun setAutoStartScanning(enabled: Boolean) = updateSetting({ scanningDelegate.setAutoStartScanning(enabled) }) { it.copy(autoStartScanning = enabled) }
    fun setScanDelayInput(input: String) = updateSetting({ scanningDelegate.setScanDelayInput(input) }) { 
        val v = input.toLongOrNull() ?: it.scanDelayMillis
        it.copy(scanDelayMillis = v)
    }
    fun setResumeScanningFromStart(b: Boolean) = updateSetting({ scanningDelegate.setResumeScanningFromStart(b) }) { it.copy(resumeScanningFromStart = b) }
    fun setDefaultScanPattern(p: String) = updateSetting({ scanningDelegate.setDefaultScanPattern(p) }) { it.copy(defaultScanPattern = p) }
    fun setHoldingTimeInput(i: String) = updateSetting({ scanningDelegate.setHoldingTimeInput(i) }) { 
        val v = i.toLongOrNull() ?: it.holdingTimeMillis
        it.copy(holdingTimeMillis = v)
    }
    val isCaregiverDevice = settingsRepository.isCaregiverDeviceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsRepository.isCaregiverDevice)

    fun setCaregiverDevice(enabled: Boolean) {
        settingsRepository.isCaregiverDevice = enabled
    }

    fun setBluetoothDelay(d: String) = updateSetting({ scanningDelegate.setBluetoothDelay(d) }) { 
        val v = d.toLongOrNull() ?: it.bluetoothDelay
        it.copy(bluetoothDelay = v)
    }

    fun signIn(ctx: Context) = cloudSyncDelegate.signIn(ctx, viewModelScope)
    
    fun signOut() {
        cloudSyncDelegate.signOut(viewModelScope)
        // Disable cloud-dependent features on sign out
        settingsRepository.isDataCloudSyncEnabled = false
        settingsRepository.isGeminiEnabled = false
        settingsRepository.isGeminiVerified = false
        settingsRepository.googleDriveFolderId = null
        settingsRepository.googleDriveFolderName = null
        genAiDelegate.updateGeminiToolStatus()
    }

    fun switchAccount(ctx: Context) = cloudSyncDelegate.switchAccount(ctx, viewModelScope)

    fun setCloudSyncEnabled(ctx: Context, e: Boolean) = updateSetting({ cloudSyncDelegate.setCloudSyncEnabled(ctx, e, viewModelScope) }) { it.copy(isDataCloudSyncEnabled = e) }
       fun syncNow() {
        backupDelegate.setBackupRestoreRunning(true)
        backupDelegate.setBackupRestoreProgress(0f)
        cloudSyncDelegate.performManualSync(
            mode = com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.TWO_WAY,
            scope = viewModelScope,
            onProgress = { p, s -> backupDelegate.handleCloudProgress(p, s) },
            onComplete = {
                backupDelegate.finishBackupRestoreProgress()
                if (settingsRepository.syncModeLogs == "BACKUP_ONLY") {
                    viewModelScope.launch {
                        exportLogsUseCase.performAutoUpload()
                    }
                }
            }
        )
    }
    
    fun backupNow() {
        backupDelegate.setBackupRestoreRunning(true)
        backupDelegate.setBackupRestoreProgress(0f)
        cloudSyncDelegate.performManualSync(
            mode = com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.BACKUP_ONLY,
            scope = viewModelScope,
            onProgress = { p, s -> backupDelegate.handleCloudProgress(p, s) },
            onComplete = {
                backupDelegate.finishBackupRestoreProgress()
                if (settingsRepository.syncModeLogs == "BACKUP_ONLY") {
                    viewModelScope.launch {
                        exportLogsUseCase.performAutoUpload()
                    }
                }
            }
        )
    }
    
    fun restoreNow() {
        backupDelegate.setBackupRestoreRunning(true)
        backupDelegate.setBackupRestoreProgress(0f)
        cloudSyncDelegate.performManualSync(
            mode = com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.RESTORE_ONLY,
            scope = viewModelScope,
            onProgress = { p, s -> backupDelegate.handleCloudProgress(p, s) },
            onComplete = { backupDelegate.finishBackupRestoreProgress() }
        )
    }
    
    fun fetchAvailableBackupsForImport(folderId: String? = null) = cloudSyncDelegate.fetchAvailableBackupsForImport(folderId, viewModelScope)
    fun fetchAvailableBackupsForImportByUrlOrId(urlOrId: String) = cloudSyncDelegate.fetchAvailableBackupsForImportByUrlOrId(urlOrId, viewModelScope)
    fun fetchAvailableBackupsFromSaf(uri: String, name: String) = cloudSyncDelegate.fetchAvailableBackupsFromSaf(uri, name, viewModelScope)
    
    fun importCloudBackup(backupInfo: com.andreas_kratzer.ghosttalk.core.cloud.domain.RemoteBackupInfo) {
        backupDelegate.setBackupRestoreRunning(true)
        backupDelegate.setBackupRestoreProgress(0f)
        cloudSyncDelegate.importCloudBackup(
            backupInfo = backupInfo,
            scope = viewModelScope,
            onProgress = { p, s -> backupDelegate.handleCloudProgress(p, s) },
            onImported = { _ -> },
            onComplete = {
                viewModelScope.launch {
                    delay(500)
                    backupDelegate.setBackupRestoreRunning(false)
                }
            }
        )
    }
    fun dismissBackupSelectionDialog() = cloudSyncDelegate.dismissBackupSelectionDialog()

    fun fetchDriveFolders(parentFolderId: String = "root") = cloudSyncDelegate.fetchDriveFolders(parentFolderId, viewModelScope)
    fun selectDriveFolder(folderId: String?, folderName: String?) = updateSetting({ cloudSyncDelegate.selectDriveFolder(folderId, folderName) }) { it.copy(googleDriveFolderId = folderId, googleDriveFolderName = folderName) }
    fun selectDriveFolderByUrlOrId(urlOrId: String, onResult: (Boolean, String?) -> Unit) = 
        cloudSyncDelegate.selectDriveFolderByUrlOrId(urlOrId, viewModelScope, onResult)
    
    fun loadSyncLogs() = cloudSyncDelegate.loadSyncLogs(viewModelScope)
    fun clearSyncLogs() = cloudSyncDelegate.clearSyncLogs(viewModelScope)
    
    fun setSyncModeBook(m: String) = updateSetting({ settingsRepository.syncModeBook = m }) { it.copy(syncModeBook = m) }
    fun setSyncModeTts(m: String) = updateSetting({ settingsRepository.syncModeTts = m }) { it.copy(syncModeTts = m) }
    fun setSyncModeStats(m: String) = updateSetting({ settingsRepository.syncModeStats = m }) { it.copy(syncModeStats = m) }
    fun setSyncModeSettings(m: String) { settingsRepository.syncModeSettings = m }
    fun setGoogleAuthType(type: CloudAuthType) {
        settingsRepository.googleAuthType = type
    }
    fun setSyncIntervalMinutes(minutes: Long) = updateSetting({ 
        settingsRepository.syncIntervalMinutes = minutes
        cloudSyncDelegate.reschedule()
    }) { it.copy(syncIntervalMinutes = minutes) }

    fun setForegroundSyncIntervalMinutes(minutes: Long) = updateSetting({ 
        settingsRepository.foregroundSyncIntervalMinutes = minutes
    }) { it.copy(foregroundSyncIntervalMinutes = minutes) }

    fun setSyncTargetType(type: String) {
        settingsRepository.syncTargetType = type
        cloudSyncDelegate.reschedule()
    }
    fun selectLocalFolderSaf(uri: String, name: String) {
        settingsRepository.localFolderSafUri = uri
        settingsRepository.localFolderSafName = name
        cloudSyncDelegate.reschedule()
    }
    fun setHueBridgeIp(ip: String) = updateSetting({ settingsRepository.hueBridgeIp = ip }) { it.copy(hueBridgeIp = ip) }
    fun setHueUsername(username: String) { settingsRepository.hueUsername = username }

    fun setTtsEngine(engine: String?) = updateSetting({ ttsDelegate.setTtsEngine(engine) }) { it.copy(ttsEngine = engine) }
    fun setElevenLabsApiKey(key: String) = ttsDelegate.setElevenLabsApiKey(key)

    fun saveApiKeyToGoogle(activity: android.app.Activity) {
        viewModelScope.launch {
            val result = ttsDelegate.saveApiKeyToGoogle(activity)
            handlePasswordManagerResult(result, isImport = false)
        }
    }

    fun importApiKeyFromGoogle(activity: android.app.Activity) {
        viewModelScope.launch {
            val result = ttsDelegate.importApiKeyFromGoogle(activity)
            handlePasswordManagerResult(result, isImport = true)
        }
    }

    private fun handlePasswordManagerResult(
        result: com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult,
        isImport: Boolean
    ) {
        val message = when (result) {
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.Success -> {
                if (isImport) {
                    application.getString(R.string.elevenlabs_api_key_imported_google)
                } else {
                    application.getString(R.string.elevenlabs_api_key_saved_google)
                }
            }
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.NoKeyFound -> 
                application.getString(R.string.elevenlabs_api_key_not_found_google)
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.NoManager -> 
                application.getString(R.string.elevenlabs_api_key_no_manager_google)
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.Cancelled -> 
                null // Don't show anything on cancel
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.Error -> 
                application.getString(R.string.elevenlabs_api_key_error_google, result.message)
        }

        if (message != null) {
            Toast.makeText(application, message, Toast.LENGTH_LONG).show()
        }
    }

    fun saveGeminiApiKeyToGoogle(activity: android.app.Activity) {
        viewModelScope.launch {
            val result = genAiDelegate.saveGeminiApiKeyToGoogle(activity)
            handleGeminiPasswordManagerResult(result, isImport = false)
        }
    }

    fun importGeminiApiKeyFromGoogle(activity: android.app.Activity) {
        viewModelScope.launch {
            val result = genAiDelegate.importGeminiApiKeyFromGoogle(activity)
            handleGeminiPasswordManagerResult(result, isImport = true)
        }
    }

    private fun handleGeminiPasswordManagerResult(
        result: com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult,
        isImport: Boolean
    ) {
        val message = when (result) {
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.Success -> {
                if (isImport) {
                    application.getString(R.string.gemini_api_key_imported_google)
                } else {
                    application.getString(R.string.gemini_api_key_saved_google)
                }
            }
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.NoKeyFound -> 
                application.getString(R.string.gemini_api_key_not_found_google)
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.NoManager -> 
                application.getString(R.string.gemini_api_key_no_manager_google)
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.Cancelled -> 
                null // Don't show anything on cancel
            is com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.PasswordManagerResult.Error -> 
                application.getString(R.string.gemini_api_key_error_google, result.message)
        }

        if (message != null) {
            Toast.makeText(application, message, Toast.LENGTH_LONG).show()
        }
    }

    fun setElevenLabsModel(model: String) = updateSetting({ ttsDelegate.setElevenLabsModel(model) }) { it.copy(elevenLabsModel = model) }
    fun setElevenLabsStability(value: Float) = updateSetting({ ttsDelegate.setElevenLabsStability(value) }) { it.copy(elevenLabsStability = value) }
    fun setElevenLabsSimilarityBoost(value: Float) = updateSetting({ ttsDelegate.setElevenLabsSimilarityBoost(value) }) { it.copy(elevenLabsSimilarityBoost = value) }
    fun setTtsPlaybackSpeed(value: Float) = updateSetting({ ttsDelegate.setTtsPlaybackSpeed(value) }) { it.copy(ttsPlaybackSpeed = value) }

    fun testElevenLabsConnection() {
        viewModelScope.launch {
            val currentEngine = settingsRepository.ttsEngine
            // Temporary switch engine to elevenlabs for the test
            settingsRepository.ttsEngine = "elevenlabs"
            
            withContext(Dispatchers.Main) {
                Toast.makeText(application, "ElevenLabs Verbindung wird getestet...", Toast.LENGTH_SHORT).show()
            }
            
            // Give the helper time to switch the provider
            // The engine flow collection happens in TextToSpeechHelper's scope
            delay(800)
            
            val sampleText = application.getString(R.string.settings_elevenlabs_test_sample_text)
            ttsHelper.speak(sampleText, onDone = {
                // Restore engine
                settingsRepository.ttsEngine = currentEngine
                viewModelScope.launch {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, application.getString(R.string.settings_elevenlabs_test_success), Toast.LENGTH_LONG).show()
                    }
                }
            }, onError = { error ->
                // Restore engine
                settingsRepository.ttsEngine = currentEngine
                viewModelScope.launch {
                    withContext(Dispatchers.Main) {
                        val message = application.getString(R.string.settings_elevenlabs_test_failure, error)
                        Toast.makeText(application, message, Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
    }

    fun playVoicePreview() {
        viewModelScope.launch {
            val sampleText = application.getString(R.string.settings_voice_preview_sample_text)
            ttsHelper.speak(sampleText)
        }
    }


    fun discoverHueBridges() {
        hueDelegate.discoverHueBridges()
    }

    fun registerLocalHueBridge() {
        hueDelegate.registerLocalHueBridge()
    }

    fun confirmHueBridgeCertificate() {
        hueDelegate.confirmHueBridgeCertificate()
    }

    fun cancelHueBridgeCertificate() {
        hueDelegate.cancelHueBridgeCertificate()
    }

    fun refreshHueDevicesCache(silentOnFailure: Boolean = false, onResult: ((Boolean) -> Unit)? = null) {
        hueDelegate.refreshHueDevicesCache(silentOnFailure, onResult)
    }

    fun setGeminiEnabled(ctx: Context, e: Boolean) = updateSetting({ genAiDelegate.setGeminiCloudEnabled(ctx, e, viewModelScope) }) { it.copy(isGeminiEnabled = e) }

    fun activateGemini(ctx: Context) = genAiDelegate.activateGemini(ctx, viewModelScope)

    fun setPersistActionLogs(e: Boolean) = updateSetting({ settingsRepository.persistActionLogs = e }) { it.copy(persistActionLogs = e) }
    fun setSwitchActivationKey(k: String) = updateSetting({ settingsRepository.switchActivationKey = k }) { it.copy(switchActivationKey = k) }
    fun setVolumeKeysActivate(e: Boolean) = updateSetting({ settingsRepository.volumeKeysActivate = e }) { it.copy(volumeKeysActivate = e) }
    fun setBlockVolumeKeys(e: Boolean) = updateSetting({ settingsRepository.blockVolumeKeys = e }) { it.copy(blockVolumeKeys = e) }
    fun setSpeakerVolume(v: Int) = updateSetting({ settingsRepository.speakerVolume = v }) { it.copy(speakerVolume = v) }
    fun setHeadphoneVolume(v: Int) = updateSetting({ settingsRepository.headphoneVolume = v }) { it.copy(headphoneVolume = v) }
    fun setShowTestButtons(e: Boolean) { settingsRepository.showTestButtons = e }
    fun setShowPageIdInLog(e: Boolean) = updateSetting({ settingsRepository.showPageIdInLog = e }) { it.copy(showPageIdInLog = e) }
    fun setSmartPredictionEnabled(e: Boolean) = updateSetting({ experimentalDelegate.setSmartPredictionEnabled(e) }) { it.copy(isSmartPredictionEnabled = e) }
    fun setDefaultStartPageId(id: String?) = updateSetting({ settingsRepository.defaultStartPageId = id }) { it.copy(favoriteBookId = id) }

    fun setAppLanguage(code: String?) = updateSetting({ 
        val finalCode = if (code == "default") null else code
        settingsRepository.appLanguage = finalCode
        val appLocale = if (finalCode == null) androidx.core.os.LocaleListCompat.getEmptyLocaleList() else androidx.core.os.LocaleListCompat.forLanguageTags(finalCode)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
    }) { it.copy(appLanguage = if (code == "default") null else code) }

    fun setThemeMode(m: String) = updateSetting({ settingsRepository.themeMode = m }) { it.copy(themeMode = m) }

    fun setNotificationReadingEnabled(e: Boolean) = updateSetting({ settingsRepository.isNotificationReadingEnabled = e }) { it.copy(isNotificationReadingEnabled = e) }
    fun setAutoReadMode(mode: com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode) = updateSetting({ 
        settingsRepository.autoReadMode = mode
    }) { it.copy(autoReadMode = mode.name) }

    fun setAutoReadOnlyInUserMode(e: Boolean) = updateSetting({ settingsRepository.autoReadOnlyInUserMode = e }) { it.copy(autoReadOnlyInUserMode = e) }
    fun setAutoReadInStandby(e: Boolean) = updateSetting({ settingsRepository.autoReadInStandby = e }) { it.copy(autoReadInStandby = e) }
    fun toggleMonitoredNotificationApp(pkg: String, e: Boolean) = updateSetting({ 
        val current = settingsRepository.monitoredNotificationApps.toMutableSet()
        if (e) current.add(pkg) else current.remove(pkg)
        settingsRepository.monitoredNotificationApps = current
    }) { 
        val current = it.monitoredNotificationApps.toMutableSet()
        if (e) current.add(pkg) else current.remove(pkg)
        it.copy(monitoredNotificationApps = current)
    }
    fun setMonitoredNotificationApps(apps: Set<String>) = updateSetting({ 
        settingsRepository.monitoredNotificationApps = apps
    }) { it.copy(monitoredNotificationApps = apps) }

    fun clearButtonUsageStats(bookId: String) {
        viewModelScope.launch { 
            buttonUsageRepository.clearStats(bookId)
            refreshTopButtonUsage()
        }
    }

    fun setShowActionHistoryDialog(show: Boolean) {
        _showActionHistoryDialog.value = show
    }

    fun setShowUsageStatsDialog(show: Boolean) {
        _showUsageStatsDialog.value = show
        if (show) {
            refreshTopButtonUsage()
        }
    }

    fun setShowPrefetchDialog(show: Boolean) {
        _showPrefetchDialog.value = show
    }

    fun refreshTopButtonUsage() {
        viewModelScope.launch {
            val stats = buttonUsageRepository.getGroupedUsageStats(activeBookId)
            _topButtonUsage.value = stats.take(50) // Show top 50 groups
        }
    }

    fun checkManualUpdate() {
        viewModelScope.launch {
            _updateCheckStatus.value = UpdateCheckStatus.Checking
            _manualUpdateCheckTrigger.emit(Unit)
        }
    }

    fun setUpdateCheckStatus(status: UpdateCheckStatus?) {
        _updateCheckStatus.value = status
    }

    fun setKeepScreenOnUserMode(e: Boolean) = updateSetting({ settingsRepository.keepScreenOnUserMode = e }) { it.copy(keepScreenOnUserMode = e) }
    fun setUserModeScreenBehavior(m: String) = updateSetting({ settingsRepository.userModeScreenBehavior = m }) { it.copy(userModeScreenBehavior = m) }
    fun setStatsRetentionDays(days: Int) = updateSetting({ 
        settingsRepository.statsRetentionDays = days 
        viewModelScope.launch(Dispatchers.IO) {
            buttonUsageRepository.cleanupOldStats(days)
        }
    }) { it.copy(statsRetentionDays = days) }

    fun setStatsAggregationHours(hours: Int) = updateSetting({ settingsRepository.statsAggregationHours = hours }) { it.copy(statsAggregationHours = hours) }
    fun setOnlyRecordHardwareStats(e: Boolean) = updateSetting({ settingsRepository.onlyRecordHardwareStats = e }) { it.copy(onlyRecordHardwareStats = e) }
    fun setFirebaseAnalyticsEnabled(e: Boolean) = updateSetting({ settingsRepository.firebaseAnalyticsEnabled = e }) { it.copy(firebaseAnalyticsEnabled = e) }
    fun setGeminiTimeoutInput(input: String) = updateSetting({ 
        input.toLongOrNull()?.let { settingsRepository.geminiTimeout = it }
    }) { 
        val v = input.toLongOrNull() ?: it.geminiTimeout
        it.copy(geminiTimeout = v)
    }
    fun setGeminiRedoPrediction(e: Boolean) = updateSetting({ settingsRepository.geminiRedoPrediction = e }) { it.copy(geminiRedoPrediction = e) }
    fun setGeminiApiKey(key: String?) {
        settingsRepository.geminiApiKey = key
        settingsRepository.isGeminiVerified = false
    }
    fun setUseGeminiApiKey(useKey: Boolean) = updateSetting({ 
        settingsRepository.useGeminiApiKey = useKey
        settingsRepository.isGeminiVerified = false
    }) { it.copy(useGeminiApiKey = useKey) }

    fun connectSpotify(ctx: Context) {
        spotifyDelegate.connectSpotify(ctx)
    }

    fun disconnectSpotify() {
        spotifyDelegate.disconnectSpotify()
    }

    fun loadSpotifyPlaylists() {
        spotifyDelegate.loadSpotifyPlaylists()
    }

    fun setLimitScanCycles(enabled: Boolean) = updateSetting({ scanningDelegate.setLimitScanCycles(enabled) }) { it.copy(limitScanCycles = enabled) }
    fun setScanCycleLimitInput(input: String) = updateSetting({ scanningDelegate.setScanCycleLimitInput(input) }) { 
        val v = input.toIntOrNull() ?: it.scanCycleLimit
        it.copy(scanCycleLimit = v)
    }
    
    fun setStaticRowEnabled(enabled: Boolean) = updateSetting({ scanningDelegate.setStaticRowEnabled(enabled) }) { it.copy(staticRowEnabled = enabled) }
    fun setLateClickThresholdInput(input: String) = updateSetting({ scanningDelegate.setLateClickThresholdInput(input) }) { 
        val v = input.toLongOrNull() ?: it.lateClickThresholdMillis
        it.copy(lateClickThresholdMillis = v)
    }
    fun setActionLogLimitInput(input: String) = updateSetting({ 
        updateActionLogLimitUseCase(input)
    }) { 
        val v = input.toIntOrNull() ?: it.actionLogLimit
        it.copy(actionLogLimit = v)
    }

    fun setSecurityPin(pin: String) {
        settingsRepository.securityPin = pin
    }

    fun clearSecurityPin() {
        securityManager.clearPin()
    }

    fun setSecurityPinTimeoutMinutes(minutes: Long) = updateSetting({ 
        settingsRepository.securityPinTimeoutMinutes = minutes
    }) { it.copy(securityPinTimeoutMinutes = minutes) }

    fun setPinRequiredForDeletion(required: Boolean) = updateSetting({ 
        settingsRepository.isPinRequiredForDeletion = required
    }) { it.copy(isPinRequiredForDeletion = required) }

    fun setBiometricEnabled(enabled: Boolean) {
        settingsRepository.isBiometricEnabled = enabled
    }

    fun setSecurityRequiredForEdit(required: Boolean) = updateSetting({ 
        settingsRepository.isSecurityRequiredForEdit = required
    }) { it.copy(isSecurityRequiredForEdit = required) }

    fun setSecurityRequiredForSettings(required: Boolean) = updateSetting({ 
        settingsRepository.isSecurityRequiredForSettings = required
    }) { it.copy(isSecurityRequiredForSettings = required) }

    fun setSecurityRequiredForAnalytics(required: Boolean) = updateSetting({ 
        settingsRepository.isSecurityRequiredForAnalytics = required
    }) { it.copy(isSecurityRequiredForAnalytics = required) }

    fun setStartupBehavior(behavior: String) = updateSetting({ 
        settingsRepository.startupBehavior = behavior
    }) { it.copy(startupBehavior = behavior) }

    fun setForceSoftKeyboard(enabled: Boolean) = updateSetting({ 
        settingsRepository.forceSoftKeyboard = enabled
    }) { it.copy(forceSoftKeyboard = enabled) }

    fun lock() {
        securityManager.lock()
    }

    val weatherCacheTimeout = settingsRepository.weatherCacheTimeoutFlow
    fun setWeatherCacheTimeoutInput(input: String) = updateSetting({ 
        input.toLongOrNull()?.let { settingsRepository.weatherCacheTimeout = it }
    }) { 
        val v = input.toLongOrNull() ?: it.weatherCacheTimeout
        it.copy(weatherCacheTimeout = v)
    }

    val backgroundLocationEnabled = settingsRepository.backgroundLocationEnabledFlow
    val backgroundLocationInterval = settingsRepository.backgroundLocationIntervalFlow
    val backgroundWeatherEnabled = settingsRepository.backgroundWeatherEnabledFlow
    val backgroundWeatherInterval = settingsRepository.backgroundWeatherIntervalFlow

    fun setBackgroundLocationEnabled(enabled: Boolean) = updateSetting({ 
        settingsRepository.backgroundLocationEnabled = enabled
        backgroundScheduler.scheduleLocationUpdate()
    }) { it.copy(backgroundLocationEnabled = enabled) }

    fun setBackgroundLocationInterval(hours: Long) = updateSetting({ 
        settingsRepository.backgroundLocationInterval = hours
        backgroundScheduler.scheduleLocationUpdate()
    }) { it.copy(backgroundLocationInterval = hours) }

    fun setBackgroundWeatherEnabled(enabled: Boolean) = updateSetting({ 
        settingsRepository.backgroundWeatherEnabled = enabled
        backgroundScheduler.scheduleWeatherUpdate()
    }) { it.copy(backgroundWeatherEnabled = enabled) }

    fun setBackgroundWeatherInterval(hours: Long) = updateSetting({ 
        settingsRepository.backgroundWeatherInterval = hours
        backgroundScheduler.scheduleWeatherUpdate()
    }) { it.copy(backgroundWeatherInterval = hours) }

    // --- CallSettings Setters ---
    fun setMaxCallDurationSeconds(seconds: Int) = updateSetting({ 
        settingsRepository.maxCallDurationSeconds = seconds
    }) { it.copy(maxCallDurationSeconds = seconds) }
    fun setCallDurationFeedbackIntervalSeconds(seconds: Int) = updateSetting({ 
        settingsRepository.callDurationFeedbackIntervalSeconds = seconds
    }) { it.copy(callDurationFeedbackIntervalSeconds = seconds) }
    fun setOutgoingCallIntro(text: String) = updateSetting({ 
        settingsRepository.outgoingCallIntro = text
    }) { it.copy(outgoingCallIntro = text) }
    fun setIncomingCallIntro(text: String) = updateSetting({ 
        settingsRepository.incomingCallIntro = text
    }) { it.copy(incomingCallIntro = text) }
    fun setIncomingCallScanLimitUserModeActive(limit: Int) = updateSetting({ 
        settingsRepository.incomingCallScanLimitUserModeActive = limit
    }) { it.copy(incomingCallScanLimitUserModeActive = limit) }
    fun setIncomingCallAutoActionUserModeActive(action: String) = updateSetting({ 
        settingsRepository.incomingCallAutoActionUserModeActive = action
    }) { it.copy(incomingCallAutoActionUserModeActive = action) }
    fun setIncomingCallDelayUserModeInactive(seconds: Int) = updateSetting({ 
        settingsRepository.incomingCallDelayUserModeInactive = seconds
    }) { it.copy(incomingCallDelayUserModeInactive = seconds) }
    fun setIncomingCallAutoActionUserModeInactive(action: String) = updateSetting({ 
        settingsRepository.incomingCallAutoActionUserModeInactive = action
    }) { it.copy(incomingCallAutoActionUserModeInactive = action) }
    fun setCallAnnouncementAsCue(asCue: Boolean) = updateSetting({ 
        settingsRepository.callAnnouncementAsCue = asCue
    }) { it.copy(callAnnouncementAsCue = asCue) }
    fun setAutoEnableSpeakerphone(enable: Boolean) = updateSetting({ 
        settingsRepository.autoEnableSpeakerphone = enable
    }) { it.copy(autoEnableSpeakerphone = enable) }
    fun setSimulateCallsEnabled(enable: Boolean) = updateSetting({ 
        settingsRepository.simulateCallsEnabled = enable
    }) { it.copy(simulateCallsEnabled = enable) }
    fun setHangUpPressesRequired(presses: Int) = updateSetting({ 
        settingsRepository.hangUpPressesRequired = presses
    }) { it.copy(hangUpPressesRequired = presses) }
    fun setFilterCallsNotInContacts(filter: Boolean) = updateSetting({ 
        settingsRepository.filterCallsNotInContacts = filter
    }) { it.copy(filterCallsNotInContacts = filter) }

    val isDefaultDialer: Boolean
        get() {
            val telecomManager = application.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            return telecomManager.defaultDialerPackage == application.packageName
        }

    fun requestDefaultDialer(activity: android.app.Activity) {
        val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
            val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
            activity.startActivityForResult(intent, 123)
            return
        }
        val telecomManager = activity.getSystemService(android.telecom.TelecomManager::class.java)
        if (telecomManager != null && telecomManager.defaultDialerPackage != activity.packageName) {
            val intent = Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
            }
            activity.startActivityForResult(intent, 123)
        }
    }

    val activeBookId: String
        get() = settingsRepository.activeBookId

    suspend fun exportLocalBackup(): String {
        return backupDelegate.exportLocalBackup()
    }

    suspend fun exportLocalBackupZip(outputStream: java.io.OutputStream) {
        backupDelegate.exportLocalBackupZip(outputStream)
    }

    suspend fun importLocalBackupZip(
        inputStream: java.io.InputStream,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        backupDelegate.importLocalBackupZip(inputStream, onSuccess, onError)
    }

    suspend fun importLocalBackup(json: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        backupDelegate.importLocalBackup(json, onSuccess, onError)
    }

    suspend fun importGlobalManualBackup(json: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        backupDelegate.importGlobalManualBackup(json, onSuccess, onError)
    }

    suspend fun importGlobalManualBackupZip(
        inputStream: java.io.InputStream,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        backupDelegate.importGlobalManualBackupZip(inputStream, onSuccess, onError)
    }

    fun onEditButtonFromHistory(pageId: String, buttonId: String) {
        viewModelScope.launch {
            _showActionHistoryDialog.value = false
            _showUsageStatsDialog.value = false
            _navigationEvent.emit(SettingsNavigationEvent.EditButton(pageId, buttonId))
        }
    }

    fun onJumpToPageFromHistory(pageId: String) {
        viewModelScope.launch {
            _showActionHistoryDialog.value = false
            _showUsageStatsDialog.value = false
            _navigationEvent.emit(SettingsNavigationEvent.JumpToPage(pageId))
        }
    }

    fun onDeleteHistoryItem(event: ButtonUsageRepository.ButtonUsageEvent) {
        viewModelScope.launch {
            buttonUsageRepository.deleteUsageEvent(event.timestamp)
        }
    }

    fun onShowHistoryDetail(event: ButtonUsageRepository.ButtonUsageEvent?) {
        _selectedHistoryItem.value = event
    }

    fun updateActiveBookName(newName: String) {
        viewModelScope.launch {
            updateActiveBookNameUseCase.execute(newName)
        }
    }

    fun deleteActiveBook(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = deleteBookUseCase.execute()
            if (result.isSuccess) {
                withContext(Dispatchers.Main) {
                    onDeleted()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "Fehler beim Löschen des Buches", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setLogIgnoredActionsInput(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.logIgnoredActions = enabled
        }
    }

    fun setLogStopActionsInput(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.logStopActions = enabled
        }
    }

    // --- Audio Cache Management ---
    private val _audioCacheItems = MutableStateFlow<List<CachedAudioItem>>(emptyList())
    val audioCacheItems: StateFlow<List<CachedAudioItem>> = _audioCacheItems.asStateFlow()

    fun loadAudioCache() {
        viewModelScope.launch {
            _audioCacheItems.value = audioCacheRepository.getCachedAudios()
        }
    }

    fun deleteAudioCacheItem(item: CachedAudioItem) {
        viewModelScope.launch {
            if (audioCacheRepository.deleteFile(item.file)) {
                loadAudioCache()
            }
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch {
            if (audioCacheRepository.deleteAll()) {
                loadAudioCache()
            }
        }
    }

    // --- TTS Prefetch Actions ---
    fun togglePageSelectionForPrefetch(pageId: String) {
        prefetchDelegate.togglePageSelectionForPrefetch(pageId)
    }

    fun selectAllPagesForPrefetch(pages: List<Page>) {
        prefetchDelegate.selectAllPagesForPrefetch(pages)
    }

    fun deselectAllPagesForPrefetch() {
        prefetchDelegate.deselectAllPagesForPrefetch()
    }

    fun calculatePrefetchStats(allPages: List<Page>): com.andreas_kratzer.ghosttalk.core.model.PrefetchStats {
        return prefetchDelegate.calculatePrefetchStats(allPages)
    }

    fun startPrefetch(allPages: List<Page>) {
        prefetchDelegate.startPrefetch(allPages)
    }

    fun cancelPrefetch() {
        prefetchDelegate.cancelPrefetch()
    }



    fun deleteEmptyButtons(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                pageRepository.deleteEmptyButtons()
            }
            withContext(Dispatchers.Main) {
                onResult(count)
            }
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun initializeDefaultMessagingAppsIfNeeded() {
        try {
            val sharedPrefs = application.getSharedPreferences("ghosttalk_app_meta", Context.MODE_PRIVATE) ?: return
            val hasInitialized = sharedPrefs.getBoolean("has_initialized_monitored_apps", false)
            if (!hasInitialized) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val pm = application.packageManager ?: return@launch
                        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                        }
                        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                        val detectedApps = mutableSetOf<String>()
                        
                        for (resolveInfo in resolveInfos) {
                            val packageName = resolveInfo.activityInfo?.packageName ?: continue
                            if (packageName.isNotEmpty()) {
                                try {
                                    val appInfo = pm.getApplicationInfo(packageName, 0)
                                    if (isMessagingOrSocialApp(appInfo)) {
                                        detectedApps.add(packageName)
                                    }
                                } catch (_: Exception) {
                                    // ignore
                                }
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            if (detectedApps.isNotEmpty()) {
                                val current = settingsRepository.monitoredNotificationApps.toMutableSet()
                                current.addAll(detectedApps)
                                settingsRepository.monitoredNotificationApps = current
                            }
                            sharedPrefs.edit { putBoolean("has_initialized_monitored_apps", true) }
                        }
                    } catch (_: Exception) {
                        // ignore background thread exceptions under test/mock environment
                    }
                }
            }
        } catch (_: Exception) {
            // ignore exceptions under test/mock environment
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun resetMonitoredNotificationAppsToMessagingDefaults() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = application.packageManager ?: return@launch
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                val detectedApps = mutableSetOf<String>()
                
                for (resolveInfo in resolveInfos) {
                    val packageName = resolveInfo.activityInfo?.packageName ?: continue
                    if (packageName.isNotEmpty()) {
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, 0)
                            if (isMessagingOrSocialApp(appInfo)) {
                                detectedApps.add(packageName)
                            }
                        } catch (_: Exception) {
                            // ignore
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    settingsRepository.monitoredNotificationApps = detectedApps
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun isMessagingOrSocialApp(appInfo: android.content.pm.ApplicationInfo): Boolean {
        val pkg = appInfo.packageName.lowercase()
        
        // Exclude system, utility, mail, browser, contacts, dialer, accessibility, and companion apps that might match CATEGORY_SOCIAL
        val excludeKeywords = listOf(
            "browser", "watch", "wear", "companion", "launcher", "keyboard", 
            "weather", "clock", "email", "mail", "gmail", "calendar", "chrome", 
            "firefox", "opera", "edge", "safari", "system", "service", "provider",
            "contacts", "dialer", "phone", "accessibility", "hearing", "speech", 
            "transcribe", "translate"
        )
        if (excludeKeywords.any { pkg.contains(it) }) {
            return false
        }

        // 1. Exact or prefix matches for well-known messaging app package names
        val knownPackages = listOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus",
            "org.thoughtcrime.securesms", // Signal
            "com.facebook.orca", "com.facebook.mlite", // Messenger
            "com.discord",
            "com.skype.raider", "com.skype.m2",
            "com.viber.voip",
            "ch.threema.app", "ch.threema.app.work",
            "jp.naver.line.android",
            "com.tencent.mm", // WeChat
            "com.slack",
            "com.microsoft.teams",
            "com.google.android.apps.dynamite", // Google Chat
            "com.google.android.apps.messaging", // Google Messages
            "com.android.mms" // Default System SMS
        )
        if (knownPackages.any { pkg == it || pkg.startsWith("$it.") }) {
            return true
        }

        // 2. Check if category is social
        if (appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL) {
            return true
        }
        
        // 3. Fallback to general keywords (only if not excluded by excludeKeywords)
        val knownKeywords = listOf(
            "whatsapp", "telegram", "signal", "messenger", "discord", "skype", 
            "viber", "threema", "wechat", "imessage", "sms"
        )
        return knownKeywords.any { pkg.contains(it) }
    }

    fun simulateIncomingCall(name: String, phone: String) {
        callActionProxy.get().simulateIncomingCall(name, phone)
    }

    fun simulateOutgoingCall(name: String, phone: String) {
        callActionProxy.get().simulateOutgoingCall(name, phone)
    }

    fun setSyncModeLogs(mode: String) {
        settingsRepository.syncModeLogs = mode
        rescheduleLogUploadUseCase()
    }

    fun setSyncLogsIntervalHours(hours: Long) {
        settingsRepository.syncLogsIntervalHours = hours
        rescheduleLogUploadUseCase()
    }

    fun shareLogs(ctx: Context) {
        viewModelScope.launch {
            try {
                exportLogsUseCase.shareLogs(ctx)
            } catch (e: Exception) {
                Toast.makeText(application, "Fehler beim Teilen des Fehlerberichts: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun uploadLogsNow() {
        viewModelScope.launch {
            Toast.makeText(application, "Fehlerbericht wird hochgeladen...", Toast.LENGTH_SHORT).show()
            when (val result = exportLogsUseCase.performAutoUpload(force = true)) {
                is LogUploadResult.Success -> {
                    Toast.makeText(application, R.string.settings_logs_upload_success, Toast.LENGTH_LONG).show()
                }
                is LogUploadResult.Skipped -> {
                    Toast.makeText(application, R.string.settings_logs_upload_skipped, Toast.LENGTH_LONG).show()
                }
                is LogUploadResult.Error -> {
                    Toast.makeText(application, "Upload fehlgeschlagen: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- Profile Management ---
    val activeProfileIdFlow = settingsRepository.activeProfileIdFlow
    val allSettingsProfilesFlow = settingsRepository.getAllProfilesFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    fun setActiveProfileId(profileId: String) {
        settingsRepository.activeProfileId = profileId
    }

    fun renameActiveProfile(newName: String) {
        viewModelScope.launch {
            val activeId = settingsRepository.activeProfileId
            val activeProfile = settingsRepository.getProfileById(activeId)
            if (activeProfile != null && newName.isNotBlank() && activeProfile.name != newName) {
                val updatedProfile = activeProfile.copy(
                    name = newName,
                    profileVersionSequence = activeProfile.profileVersionSequence + 1,
                    updatedAt = System.currentTimeMillis()
                )
                settingsRepository.updateProfile(updatedProfile)
            }
        }
    }

    fun startEditingProfile(profileId: String) {
        viewModelScope.launch {
            val profile = settingsRepository.getProfileById(profileId) ?: return@launch
            _draftManager.value = ProfileDraftManager(profile.id, profile.name, profile.config)
        }
    }

    fun saveEditingProfile() {
        viewModelScope.launch {
            val draft = _draftManager.value ?: return@launch
            val original = settingsRepository.getProfileById(draft.profileId) ?: return@launch
            val updatedProfile = original.copy(
                name = draft.nameState.value,
                config = draft.configState.value,
                profileVersionSequence = original.profileVersionSequence + 1,
                updatedAt = System.currentTimeMillis()
            )
            settingsRepository.updateProfile(updatedProfile)
            if (settingsRepository.activeProfileId == draft.profileId) {
                settingsRepository.loadProfile(draft.profileId)
            }
            _draftManager.value = null
            // Trigger sync immediately to push updated profile json
            scheduleDebouncedProfileSync()
        }
    }

    fun cancelEditingProfile() {
        _draftManager.value = null
    }

    fun updateEditingProfileName(name: String) {
        _draftManager.value?.let {
            it.nameState.value = name
            it.hasUnsavedChanges.value = true
        }
    }

    fun createNewProfile(name: String) {
        viewModelScope.launch {
            val currentConfig = settingsRepository.getProfileById(settingsRepository.activeProfileId)?.config 
                ?: com.andreas_kratzer.ghosttalk.core.model.ProfileConfig()
            val newProfile = com.andreas_kratzer.ghosttalk.core.model.SettingsProfile(
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

    fun deleteProfile(profile: com.andreas_kratzer.ghosttalk.core.model.SettingsProfile) {
        viewModelScope.launch {
            if (profile.id != "profile-default") {
                val allProfiles = settingsRepository.getAllProfiles()
                if (allProfiles.size <= 1) {
                    // Cannot delete the only remaining profile
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, "Das einzige verbleibende Profil kann nicht gelöscht werden.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                settingsRepository.deleteProfile(profile)
                if (settingsRepository.activeProfileId == profile.id) {
                    // Fall back to default or another remaining profile
                    val remainingProfile = allProfiles.find { it.id != profile.id }
                    settingsRepository.activeProfileId = remainingProfile?.id ?: "profile-default"
                }
            }
        }
    }

    fun restoreApiKeysFromPasswordManager(activity: android.app.Activity) {
        viewModelScope.launch {
            authManager.getCredentialFromPasswordManager(activity).fold(
                onSuccess = { credential ->
                    if (credential != null) {
                        val (id, password) = credential
                        if (id.contains("gemini", ignoreCase = true)) {
                            settingsRepository.geminiApiKey = password
                            Toast.makeText(
                                application,
                                application.getString(R.string.gemini_api_key_imported_google),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            ttsDelegate.setElevenLabsApiKey(password)
                            Toast.makeText(
                                application,
                                application.getString(R.string.elevenlabs_api_key_imported_google),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            application,
                            application.getString(R.string.elevenlabs_api_key_not_found_google),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onFailure = { e ->
                    val cancelled = e is androidx.credentials.exceptions.GetCredentialCancellationException
                    if (!cancelled) {
                        Toast.makeText(
                            application,
                            String.format(application.getString(R.string.elevenlabs_api_key_error_google), e.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }
}

class ProfileDraftManager(
    val profileId: String,
    initialName: String,
    initialConfig: com.andreas_kratzer.ghosttalk.core.model.ProfileConfig
) {
    val nameState = MutableStateFlow(initialName)
    val configState = MutableStateFlow(initialConfig)
    val hasUnsavedChanges = MutableStateFlow(false)

    fun <T> getScopedValue(getConfigVal: (com.andreas_kratzer.ghosttalk.core.model.ProfileConfig) -> T): T {
        return getConfigVal(configState.value)
    }

    fun updateConfig(update: (com.andreas_kratzer.ghosttalk.core.model.ProfileConfig) -> com.andreas_kratzer.ghosttalk.core.model.ProfileConfig) {
        configState.value = update(configState.value)
        hasUnsavedChanges.value = true
    }
}

